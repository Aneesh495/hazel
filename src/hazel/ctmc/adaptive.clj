(ns hazel.ctmc.adaptive
  "Restarting finite-state projection with explicit incomplete outcomes."
  (:require [hazel.ctmc.projection :as p]
            [hazel.ctmc.generator :as g]
            [hazel.ctmc.uniformization :as u]
            [hazel.model.structure :as structure]
            [hazel.math.numeric :as n]))

(defn solve
  ([model time] (solve model time {}))
  ([model time {:keys [epsilon bounds max-states max-refinements growth]
                :or {epsilon 1e-8 max-states 20000 max-refinements 10 growth 1.6}
                :as options}]
   (n/tolerance! epsilon) (n/natural! :max-refinements max-refinements)
   (let [limits (structure/nonnegative-bounds model)]
     (loop [bounds (or bounds (p/default-bounds model)) attempt 0 history []]
       (let [projection (p/enumerate model {:bounds bounds :max-states max-states})
             q (g/build projection)
             result (u/solve q (:initial model) time
                             (merge (select-keys options [:max-chunks :max-terms])
                                    {:tolerance (* 0.25 epsilon)}))
             history (conj history {:attempt attempt :bounds bounds :states (count (:states q))
                                    :escape (:escape-probability result)
                                    :series-tail (:series-tail-bound result)
                                    :budget-exhausted? (:budget-exhausted? projection)})
             output (assoc result :generator q :projection projection :history history :epsilon epsilon)
             next-bounds (mapv (fn [bound limit] (if limit (min bound limit) bound))
                               (p/grow-bounds bounds growth) limits)]
         (cond
           (:budget-exhausted? projection) (assoc output :status :state-budget-exhausted)
           (<= (:truncation-bound result) epsilon) (assoc output :status :converged)
           (= attempt max-refinements) (assoc output :status :refinement-budget-exhausted)
           (= bounds next-bounds) (assoc output :status :stalled)
           :else (recur next-bounds (inc attempt) history)))))))
