(ns hazel.simulation.direct
  "Gillespie direct method, independent of projected generator construction."
  (:require [hazel.model.network :as net]
            [hazel.model.kinetics :as k]
            [hazel.math.numeric :as n]
            [hazel.simulation.random :as random]))

(defn simulate
  ([model horizon seed] (simulate model horizon seed {}))
  ([model horizon seed {:keys [initial max-events record?]
                        :or {max-events 1000000 record? true}}]
   (net/compiled! model) (n/nonnegative! :horizon horizon)
   (random/seed seed) (n/natural! :max-events max-events)
   (let [initial (net/state! model (or initial (:initial model)))]
     (loop [state initial time 0.0 rng seed events [] event-count 0]
       (let [rates (k/hazards model state) total (n/sum rates)
             base {:model-hash (:hash model) :seed seed :initial-state initial
                   :horizon horizon :events events :event-count event-count
                   :rng-state rng :final-state state :final-time time :recorded? record?}]
         (cond
           (or (= time horizon) (zero? total)) (assoc base :status :complete :final-time (double horizon))
           (= event-count max-events) (assoc base :status :event-budget-exhausted)
           :else
           (let [[wait next-rng] (random/exponential rng total) next-time (+ time wait)]
             ;; Draw the channel only if its event lies in the requested window.
             ;; record? changes storage, never the stream's sequence of draws.
             (when-not (> next-time time)
               (n/fail "Reaction time cannot advance at binary64 precision" {:time time :rate total}))
             (if (> next-time horizon)
               (assoc base :status :complete :final-time (double horizon) :rng-state next-rng)
               (let [[reaction next-rng] (random/categorical next-rng rates)
                     next-state (k/fire model state reaction)
                     event {:time next-time :reaction (:id (get (:reactions model) reaction)) :state next-state}]
                 (recur next-state next-time next-rng
                        (if record? (conj events event) events) (inc event-count)))))))))))

(defn replay
  "Validate a recorded reaction path without invoking a random generator."
  [model trace]
  (when-not (= (:hash model) (:model-hash trace))
    (n/fail "Replay model hash mismatch" {}))
  (when-not (:recorded? trace) (n/fail "Trace did not record events" {}))
  (n/nonnegative! :horizon (:horizon trace))
  (n/nonnegative! :final-time (:final-time trace))
  (n/natural! :event-count (:event-count trace))
  (when-not (and (#{:complete :event-budget-exhausted} (:status trace))
                 (<= (:final-time trace) (:horizon trace))
                 (or (not= :complete (:status trace)) (== (:final-time trace) (:horizon trace))))
    (n/fail "Replay censor metadata is inconsistent" {}))
  (let [initial (net/state! model (:initial-state trace))]
    (loop [state initial time 0.0 events (:events trace) count 0]
      (if (empty? events)
        (do
          (when-not (and (= state (:final-state trace)) (= count (:event-count trace)))
            (n/fail "Replay final state or event count mismatch" {}))
          {:status :valid :final-state state :event-count count})
        (let [event (first events) t (n/nonnegative! :event-time (:time event))]
          (when-not (and (< time t) (<= t (:final-time trace)))
            (n/fail "Replay event times are inconsistent" {:time t}))
          (let [next-state (k/fire model state (:reaction event))]
            (when-not (= next-state (:state event))
              (n/fail "Replay transition state mismatch" {:event count}))
            (recur next-state t (rest events) (inc count))))))))

(defn reproduce [model trace]
  (let [actual (simulate model (:horizon trace) (:seed trace)
                         {:initial (:initial-state trace) :max-events (max 1 (inc (:event-count trace)))})]
    (when-not (= (:events actual) (:events trace))
      (n/fail "Seeded simulation does not reproduce recorded events" {}))
    {:status :reproduced :event-count (:event-count actual)}))
