(ns hazel.analysis.path
  "Complete reaction-path likelihoods, exposures, and conjugate rate updates."
  (:require [hazel.simulation.direct :as direct]
            [hazel.model.kinetics :as k]
            [hazel.math.numeric :as n]))

(defn statistics [model trace]
  (direct/replay model trace)
  (let [censor (if (= :complete (:status trace)) (:horizon trace) (:final-time trace))]
    (n/nonnegative! :censor-time censor)
    (loop [state (:initial-state trace) time 0.0 events (:events trace)
           counts (zipmap (map :id (:reactions model)) (repeat 0))
           exposure (zipmap (map :id (:reactions model)) (repeat 0.0))
           log-density 0.0]
      (let [event (first events) until (if event (:time event) censor)
            dt (- until time)
            _ (when (neg? dt) (n/fail "Path censor precedes its last event" {}))
            factors (mapv #(double (k/factor % state)) (:reactions model))
            rates (mapv #(* (:constant %1) %2) (:reactions model) factors)
            exposure (reduce (fn [e [reaction factor]] (update e (:id reaction) + (* dt factor)))
                             exposure (map vector (:reactions model) factors))
            log-density (- log-density (* dt (n/sum rates)))]
        (if-not event
          {:counts counts :exposures exposure :log-density log-density :censor-time censor
           :parameter-statistics
           (reduce (fn [stats reaction]
                     (if (keyword? (:rate reaction))
                       (update stats (:rate reaction)
                               (fn [old] {:count (+ (:count old 0) (get counts (:id reaction)))
                                          :exposure (+ (:exposure old 0.0) (get exposure (:id reaction)))})) stats))
                   {} (:reactions model))}
          (let [index (.indexOf ^java.util.List (mapv :id (:reactions model)) (:reaction event))
                rate (n/positive! :observed-reaction-rate (rates index))]
            (recur (:state event) until (rest events) (update counts (:reaction event) inc)
                   exposure (+ log-density (Math/log rate)))))))))

(defn score [model trace]
  (let [stats (statistics model trace)]
    (into {} (for [[parameter {:keys [count exposure]}] (:parameter-statistics stats)
                   :let [value (get (:parameters model) parameter)]]
               [parameter (- (if (zero? count) 0.0 (/ count value)) exposure)]))))

(defn gamma-posterior [model trace parameter shape rate]
  (n/positive! :shape shape) (n/positive! :rate rate)
  (let [stats (get (:parameter-statistics (statistics model trace)) parameter)]
    (when-not stats (n/fail "Parameter does not control a reaction rate" {:parameter parameter}))
    (let [shape (+ shape (:count stats)) rate (+ rate (:exposure stats))]
      {:kind :gamma :shape shape :rate rate :mean (/ shape rate) :variance (/ shape (* rate rate))})))
