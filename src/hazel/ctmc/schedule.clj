(ns hazel.ctmc.schedule
  "Piecewise-constant kinetic interventions on a common projection.
  Segment parameter patches apply cumulatively at the preceding boundary."
  (:require [hazel.model.network :as net]
            [hazel.ctmc.generator :as g]
            [hazel.ctmc.uniformization :as u]
            [hazel.simulation.direct :as direct]
            [hazel.math.numeric :as n]))

(defn compile-schedule [model segments]
  (when-not (and (vector? segments) (seq segments)) (n/fail "Schedule must be a nonempty vector" {}))
  (loop [segments segments previous 0.0 model model result []]
    (if (empty? segments) result
        (let [segment (first segments)]
          (net/keys! segment #{:until :parameters} :schedule-segment)
          (let [until (n/positive! :until (:until segment))
                model (net/with-parameters model (or (:parameters segment) {}))]
            (when-not (> until previous) (n/fail "Schedule boundaries must increase strictly" {}))
            (recur (rest segments) until model
                   (conj result {:from previous :until until :duration (- until previous) :model model})))))))

(defn solve
  [model states initial segments options]
  (let [schedule (compile-schedule model segments)
        tolerance (get options :tolerance 1e-12)]
    (loop [segments schedule p initial tail 0.0 results []]
      (if (empty? segments)
        {:status :complete :probabilities p :series-tail-bound tail
         :escape-probability (peek p) :time (:until (peek schedule)) :steps results}
        (let [{:keys [model duration until]} (first segments)
              q (g/for-states model states)
              result (u/propagate q p duration (assoc options :tolerance (/ tolerance (count schedule))))
              tail (+ tail (:series-tail-bound result))]
          (recur (rest segments) (:probabilities result) tail
                 (conj results (assoc result :time until :series-tail-bound tail :model-hash (:hash model)))))))))

(defn simulate
  [model segments seed options]
  (let [schedule (compile-schedule model segments)]
    (loop [remaining schedule state (:initial model) rng seed events [] count 0]
      (if (empty? remaining)
        {:status :complete :final-state state :events events :event-count count
         :seed seed :rng-state rng :horizon (:until (peek schedule))}
        (let [{:keys [model from duration]} (first remaining)
              remaining-budget (- (get options :max-events 1000000) count)
              trace (direct/simulate model duration rng (assoc options :initial state :record? true :max-events remaining-budget))
              events (into events (map #(update % :time + from) (:events trace)))
              count (+ count (:event-count trace))]
          (if-not (= :complete (:status trace))
            {:status (:status trace) :final-state (:final-state trace) :events events :event-count count}
            (recur (rest remaining) (:final-state trace) (:rng-state trace) events count)))))))
