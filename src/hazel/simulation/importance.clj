(ns hazel.simulation.importance
  "Reaction-path importance sampling with a likelihood-ratio change of measure.
  Proposal kinetics may change rates but must retain every positive target rate."
  (:require [hazel.model.network :as net]
            [hazel.model.kinetics :as k]
            [hazel.simulation.direct :as direct]
            [hazel.simulation.random :as random]
            [hazel.analysis.path :as path]
            [hazel.math.numeric :as n]))

(defn proposal [model changes]
  (let [candidate (net/with-parameters model changes)]
    (doseq [[target proposed] (map vector (:reactions model) (:reactions candidate))]
      (when (and (pos? (:constant target)) (zero? (:constant proposed)))
        (n/fail "Importance proposal removes target support" {:reaction (:id target)})))
    candidate))

(defn log-weight [target proposed trace]
  (when-not (= :complete (:status trace)) (n/fail "Incomplete importance path" {}))
  (let [events (:events trace)
        states (into [(:initial-state trace)] (map :state events))
        has-impossible? (some true?
                              (map (fn [state event]
                                     (zero? (k/propensity
                                             (get (:reactions target) (net/reaction-index target (:reaction event))) state)))
                                   states events))]
    (if has-impossible? ##-Inf
        (- (:log-density (path/statistics target (assoc trace :model-hash (:hash target))))
           (:log-density (path/statistics proposed trace))))))

(defn estimate
  [model changes time predicate samples seed {:keys [max-events] :or {max-events 1000000}}]
  (n/natural! :samples samples)
  (when-not (<= 2 samples 1000000) (n/fail "Importance sample count must be in 2..1000000" {}))
  (let [proposed (proposal model changes)
        paths (mapv #(direct/simulate proposed time (random/split seed %) {:max-events max-events}) (range samples))]
    (if (some #(not= :complete (:status %)) paths)
      {:status :event-budget-exhausted :estimate nil :seed seed}
      (let [weights (mapv #(log-weight model proposed %) paths)
            hits (mapv #(boolean (predicate (:final-state %))) paths)
            event-weights (mapv #(if %2 %1 ##-Inf) weights hits)
            log-total (n/log-sum-exp event-weights)
            total-log-weights (n/log-sum-exp weights)
            max-log (apply max event-weights)
            scaled (if (= max-log ##-Inf) (vec (repeat samples 0.0))
                       (mapv #(Math/exp (- % max-log)) event-weights))
            mean-scaled (/ (n/sum scaled) samples)
            variance-scaled (/ (n/sum (map #(let [d (- % mean-scaled)] (* d d)) scaled)) (dec samples))
            log-estimate (when-not (= log-total ##-Inf) (- log-total (Math/log samples)))
            estimate (if log-estimate (Math/exp log-estimate) 0.0)
            se (if (= max-log ##-Inf) 0.0
                   (* (Math/exp max-log) (Math/sqrt (/ variance-scaled samples))))
            normalized (if (= log-total ##-Inf) [] (mapv #(Math/exp (- % log-total)) event-weights))]
        (when-not (and (n/finite? estimate) (n/finite? se))
          (n/fail "Importance weights exceed floating-point range" {}))
        {:status :complete :estimate estimate :log-estimate log-estimate :standard-error se
         :samples samples :seed seed :proposal-parameters changes :proposal-hits (count (filter true? hits))
         :event-ess (if (empty? normalized) 0.0 (/ 1.0 (n/dot normalized normalized)))
         :mean-weight (Math/exp (- total-log-weights (Math/log samples)))
         :diagnostic (if (= log-total ##-Inf) :no-target-samples :finite-estimate)
         :uncertainty :empirical-standard-error}))))

(defn fit-tilt
  "Cross-entropy pilot updates. Use a fresh, independent seed for final estimates.
  The pilot fits rates from event-weighted reaction counts and integrated exposure."
  [model initial-changes parameters time predicate samples iterations seed
   {:keys [smoothing minimum-rate] :or {smoothing 0.7 minimum-rate 1e-6}}]
  (when-not (< 0 smoothing 1) (n/fail "Tilt smoothing must be in (0,1)" {}))
  (n/positive! :minimum-rate minimum-rate)
  (n/natural! :iterations iterations)
  (loop [iteration 0 changes initial-changes history []]
    (if (= iteration iterations) {:status :complete :parameters changes :history history :pilot-seed seed}
        (let [proposed (proposal model changes)
              iteration-seed (random/split seed iteration)
              paths (mapv #(direct/simulate proposed time (random/split iteration-seed %)) (range samples))
              _ (when (some #(not= :complete (:status %)) paths) (n/fail "Tilt pilot simulation incomplete" {}))
              event-paths (filterv #(predicate (:final-state %)) paths)]
          (if (empty? event-paths)
            {:status :no-pilot-hits :parameters changes :history history :pilot-seed seed}
            (let [logs (mapv #(log-weight model proposed %) event-paths)
                  log-z (n/log-sum-exp logs)]
              (if (= log-z ##-Inf)
                {:status :zero-pilot-weight :parameters changes :history history :pilot-seed seed}
                (let [weights (mapv #(Math/exp (- % log-z)) logs)
                      statistics (mapv #(path/statistics proposed %) event-paths)
                      next (reduce (fn [changes parameter]
                                     (let [counts (n/sum (map #(* %2 (get-in %1 [:parameter-statistics parameter :count] 0)) statistics weights))
                                           exposure (n/sum (map #(* %2 (get-in %1 [:parameter-statistics parameter :exposure] 0)) statistics weights))
                                           old (get (:parameters proposed) parameter)
                                           fitted (if (pos? exposure) (/ counts exposure) old)]
                                       (assoc changes parameter (max minimum-rate (+ (* (- 1 smoothing) old) (* smoothing fitted))))))
                                   changes parameters)]
                  (recur (inc iteration) next
                         (conj history {:iteration iteration :hits (count event-paths) :parameters next}))))))))))
