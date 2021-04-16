(ns hazel.experiment
  "Pure orchestration of complete EDN studies. All stochastic seeds are explicit."
  (:require [hazel.model.network :as net]
            [hazel.model.predicate :as predicate]
            [hazel.model.structure :as structure]
            [hazel.ctmc.adaptive :as adaptive]
            [hazel.ctmc.projection :as projection]
            [hazel.ctmc.generator :as g]
            [hazel.ctmc.uniformization :as u]
            [hazel.ctmc.sensitivity :as sensitivity]
            [hazel.ctmc.schedule :as schedule]
            [hazel.analysis.first-passage :as first-passage]
            [hazel.analysis.stationary :as stationary]
            [hazel.analysis.backward :as backward]
            [hazel.analysis.design :as design]
            [hazel.analysis.reward :as reward]
            [hazel.inference.observation :as observation]
            [hazel.inference.filter :as filter]
            [hazel.inference.smoother :as smoother]
            [hazel.inference.score :as score]
            [hazel.inference.fit :as fit]
            [hazel.inference.particle :as particle]
            [hazel.inference.mcmc :as mcmc]
            [hazel.simulation.direct :as direct]
            [hazel.simulation.next-reaction :as next-reaction]
            [hazel.simulation.ensemble :as ensemble]
            [hazel.math.numeric :as n]))

(def common-fields #{:kind :projection :solver})
(def fields
  {:transient #{:time :epsilon :max-refinements :growth}
   :trajectory #{:times}
   :first-passage #{:times :target}
   :hitting #{:target}
   :stationary #{}
   :sensitivity #{:parameters :time}
   :filter #{:sensors :observations :smooth? :bridge-seed}
   :score #{:sensors :observations :parameters}
   :fit #{:sensors :observations :parameters :bounds}
   :particle #{:sensors :observations :particles :seed :resample-threshold :max-events}
   :mcmc #{:sensors :observations :parameters :priors :iterations :seed :particles :proposal-scales :max-events}
   :design #{:parameters :times :species :budget :criterion :regularization}
   :schedule #{:segments}
   :reward #{:reaction :species :time}
   :simulate #{:time :seed :algorithm :max-events}
   :ensemble #{:time :seed :samples :workers :max-events}})

(defn validate-study [study]
  (net/keys! study #{:title :description :model :experiment} :study)
  (net/validate (:model study))
  (let [experiment (:experiment study) allowed (get fields (:kind experiment))]
    (when-not allowed (n/fail "Unknown experiment kind" {:kind (:kind experiment)}))
    (net/keys! experiment (into common-fields allowed) :experiment)
    (when-let [p (:projection experiment)] (net/keys! p #{:bounds :max-states} :projection))
    (when-let [s (:solver experiment)]
      (net/keys! s #{:tolerance :max-chunks :max-terms :max-iterations :gradient-tolerance :max-backtracks} :solver)))
  study)

(defn run
  [model experiment]
  (let [{:keys [kind time times seed parameters sensors observations solver]} experiment
        solver (or solver {})
        sensors (when sensors (observation/compile-sensors model sensors))
        standalone? (contains? #{:transient :simulate :ensemble :particle :mcmc} kind)
        projection (when-not standalone? (projection/enumerate model (:projection experiment)))
        q (when projection (g/build projection))
        initial (when q (g/initial-vector q (:initial model)))
        with-states (fn [result] (if q (assoc result :states (:states q)) result))]
    (if (:budget-exhausted? projection)
      {:status :state-budget-exhausted :states (:states q)}
      (with-states
        (case kind
          :transient (let [r (adaptive/solve model time (merge (:projection experiment)
                                                             (select-keys experiment [:epsilon :max-refinements :growth]) solver))]
                       (-> r (assoc :states (get-in r [:generator :states])) (dissoc :generator :projection)))
          :trajectory {:status :complete :trajectory (u/trajectory q initial times solver)}
          :first-passage {:status :complete :first-passage (first-passage/cdf q initial
                                                                                           (predicate/compile-predicate model (:target experiment)) times solver)}
          :hitting (assoc (backward/hitting q (predicate/compile-predicate model (:target experiment))) :status :complete)
          :stationary (assoc (stationary/solve q) :status :complete)
          :sensitivity (assoc (sensitivity/propagate model q initial parameters time solver) :status :complete :time time)
          :filter (let [r (filter/forward q initial sensors observations solver)]
                    (cond-> r
                      (:smooth? experiment) (assoc :smoothing (smoother/smooth q r))
                      (:bridge-seed experiment) (assoc :bridge (smoother/sample-grid-bridge q r (:bridge-seed experiment)))))
          :score (score/forward model q initial parameters sensors observations solver)
          :fit (fit/fit model (:states q) parameters sensors observations (:bounds experiment) solver)
          :particle (particle/filter model sensors observations (:particles experiment) seed experiment)
          :mcmc (mcmc/sample model parameters sensors observations (:priors experiment)
                             (:iterations experiment) seed experiment)
          :design (let [index (get (:index model) (:species experiment))]
                    (when (nil? index) (n/fail "Unknown design species" {}))
                    (assoc (design/greedy (design/snapshot-candidates model q initial parameters times #(nth % index) solver)
                                          (:budget experiment) experiment) :status :complete))
          :schedule (schedule/solve model (:states q) initial (:segments experiment) solver)
          :reward (if-let [reaction (:reaction experiment)]
                    (reward/reaction-count q initial time reaction solver)
                    (let [index (get (:index model) (:species experiment))]
                      (when (nil? index) (n/fail "Unknown reward species" {}))
                      (reward/occupancy q initial time #(nth % index) solver)))
          :simulate ((case (:algorithm experiment :direct)
                       :direct direct/simulate :next-reaction next-reaction/simulate
                       (n/fail "Unknown simulation algorithm" {})) model time seed experiment)
          :ensemble (ensemble/simulate model time seed (:samples experiment) experiment)
          (n/fail "Unknown experiment kind" {:kind kind}))))))

(defn run-study [study]
  (validate-study study)
  (let [model (net/compile-model (:model study))]
    {:model model :request (:experiment study)
     :result (assoc (run model (:experiment study))
                    :title (:title study (:name model)) :species (:species model)
                    :model-hash (:hash model) :structure (structure/topology model))}))
