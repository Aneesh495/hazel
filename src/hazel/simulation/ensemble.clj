(ns hazel.simulation.ensemble
  "Observation grids and deterministic parallel ensembles. Stream identity depends
  on trajectory index, never worker scheduling or completion order."
  (:require [hazel.simulation.random :as random]
            [hazel.simulation.direct :as direct]
            [hazel.math.numeric :as n]
            [hazel.math.probability :as probability])
  (:import [java.util.concurrent Callable Executors]))

(defn observe-grid [trace times]
  (when-not (:recorded? trace) (n/fail "Observation grid needs recorded events" {}))
  (doseq [t times] (n/nonnegative! :observation-time t))
  (when-not (apply <= 0 times) (n/fail "Observation times must be sorted" {}))
  (when (some #(> % (:final-time trace)) times)
    (n/fail "Observation extends beyond completed trace" {}))
  (loop [times times events (:events trace) state (:initial-state trace) result []]
    (if (empty? times) result
        (let [t (first times)
              [events state] (loop [events events state state]
                               (if (and (seq events) (<= (:time (first events)) t))
                                 (recur (rest events) (:state (first events))) [events state]))]
          (recur (rest times) events state (conj result {:time t :state state}))))))

(defn ordered-map [workers f inputs]
  (n/natural! :workers workers)
  (when-not (<= 1 workers 32) (n/fail "Worker count must be in 1..32" {}))
  (if (= workers 1) (mapv f inputs)
      (let [pool (Executors/newFixedThreadPool workers)]
        (try
          (let [tasks (mapv (fn [x] (reify Callable (call [_] (f x)))) inputs)
                futures (.invokeAll pool tasks)]
            (mapv #(.get ^java.util.concurrent.Future %) futures))
          (catch java.util.concurrent.ExecutionException e (throw (.getCause e)))
          (finally (.shutdownNow pool))))))

(defn simulate
  ([model time seed samples] (simulate model time seed samples {}))
  ([model time seed samples {:keys [workers max-events] :or {workers 1 max-events 1000000}}]
   (n/natural! :samples samples)
   (when-not (<= 1 samples 1000000) (n/fail "Ensemble size must be in 1..1000000" {}))
   (let [paths (ordered-map workers
                            (fn [i] (direct/simulate model time (random/split seed i)
                                                      {:record? false :max-events max-events}))
                            (range samples))
         complete? (every? #(= :complete (:status %)) paths)]
     {:status (if complete? :complete :incomplete)
      :samples samples :seed seed :horizon time
      :states (mapv :final-state paths)
      :histogram (frequencies (map :final-state paths))
      :event-count (reduce + (map :event-count paths))
      :incomplete-count (count (remove #(= :complete (:status %)) paths))})))

(defn event-frequency [ensemble predicate]
  (when-not (= :complete (:status ensemble))
    (n/fail "Cannot estimate event frequency from incomplete trajectories" {}))
  (let [successes (count (filter predicate (:states ensemble))) n (:samples ensemble)]
    {:estimate (/ successes (double n)) :successes successes :trials n
     :wilson-95 (probability/wilson-interval successes n)}))

(defn moments [ensemble observable]
  (when-not (= :complete (:status ensemble)) (n/fail "Incomplete ensemble" {}))
  (let [xs (mapv #(n/finite! :observable (observable %)) (:states ensemble))
        count (count xs) mean (/ (n/sum xs) count)
        variance (if (= count 1) 0.0
                     (/ (n/sum (map #(let [d (- % mean)] (* d d)) xs)) (dec count)))]
    {:mean mean :sample-variance variance :standard-error (Math/sqrt (/ variance count))}))
