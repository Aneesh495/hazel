(ns hazel.inference.fit
  "Maximum likelihood on a fixed projection. Projection convergence is a separate check."
  (:require [hazel.model.network :as net]
            [hazel.ctmc.generator :as g]
            [hazel.inference.score :as score]
            [hazel.inference.filter :as filter]
            [hazel.math.optimize :as optimize]
            [hazel.math.numeric :as n]))

(defn fit
  [model states parameters sensors observations bounds options]
  (when-not (= (count parameters) (count (set parameters))) (n/fail "Duplicate fit parameters" {}))
  (let [initial (mapv #(get (:parameters model) %) parameters)
        box (mapv #(or (get bounds %) (n/fail "Missing parameter bounds" {:parameter %})) parameters)
        objective (fn [values]
                    (let [candidate (net/with-parameters model (zipmap parameters values))
                          q (g/for-states candidate states)
                          r (score/forward candidate q (g/initial-vector q (:initial candidate))
                                           parameters sensors observations options)]
                      {:value (- (:log-likelihood r))
                       :gradient (mapv #(- (get (:score r) %)) parameters)}))
        result (optimize/minimize objective initial box options)]
    (assoc result :parameters (zipmap parameters (:point result))
                  :log-likelihood (- (:objective result)) :projection-conditioned? true)))

(defn profile
  [model states parameters sensors observations bounds parameter grid options]
  (when-not (contains? (:parameters model) parameter) (n/fail "Unknown profile parameter" {}))
  (let [nuisance (vec (remove #{parameter} parameters))]
    (mapv (fn [value]
            (let [candidate (net/with-parameters model {parameter value})]
              (if (empty? nuisance)
                (let [q (g/for-states candidate states)]
                  {:value value :status :evaluated
                   :log-likelihood (:log-likelihood (filter/forward q (g/initial-vector q (:initial model))
                                                                   sensors observations options))})
                (assoc (fit candidate states nuisance sensors observations bounds options) :value value)))) grid)))
