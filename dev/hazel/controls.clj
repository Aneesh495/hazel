(ns hazel.controls
  "Executed negative controls. Each control must observe a real rejection or a
  semantic disagreement with an independent reference. No expected flag is preset."
  (:refer-clojure :exclude [run!])
  (:require [clojure.java.io :as io]
            [hazel.io.edn :as edn]
            [hazel.io.bundle :as bundle]
            [hazel.model.network :as net]
            [hazel.ctmc.projection :as projection]
            [hazel.ctmc.generator :as generator]
            [hazel.ctmc.sparse :as sparse]
            [hazel.ctmc.uniformization :as uniform]
            [hazel.ctmc.adaptive :as adaptive]
            [hazel.ctmc.certificate :as certificate]
            [hazel.analysis.certificate-check :as checker]
            [hazel.simulation.direct :as direct]
            [hazel.simulation.importance :as importance]))

(defn- rejected [f]
  (try (f) {:detected? false :observation :accepted}
       (catch clojure.lang.ExceptionInfo e {:detected? true :observation (.getMessage e)})))

(defn run! [directory]
  (.mkdirs (io/file directory))
  (let [study (edn/read-file "examples/immigration.edn") model (net/compile-model (:model study))
        q (generator/build (projection/enumerate model {:bounds [8]}))
        p (generator/initial-vector q [0])
        c (certificate/create model q 1/2)
        trace (direct/simulate model 2 42)
        object (bundle/create! directory model {:kind :simulate :time 2 :seed 42} trace)
        corrupt (io/file directory "corrupt.edn")
        _ (spit corrupt (pr-str (assoc-in (edn/read-file (:path object)) [:payload :result :final-state] [999])))
        cases
        [[:reader-evaluation #(rejected (fn [] (edn/read-string "#=(+ 1 2)")))]
         [:trailing-edn #(rejected (fn [] (edn/read-string "{} {}")))]
         [:unknown-model-field #(rejected (fn [] (net/compile-model (assoc (:model study) :paramters {}))))]
         [:negative-population #(rejected (fn [] (net/state! model [-1])))]
         [:negative-rate #(rejected (fn [] (net/with-parameters model {:arrival -1})))]
         [:infinite-rate #(rejected (fn [] (net/with-parameters model {:arrival ##Inf})))]
         [:duplicate-species #(rejected (fn [] (net/compile-model (assoc (:model study) :species [:x :x]))))]
         [:generator-diagonal #(rejected (fn [] (generator/validate (assoc-in q [:rows 0] [[0 2]]))))]
         [:generator-exit #(rejected (fn [] (generator/validate (assoc-in q [:exits 0] 0))))]
         [:escape-not-absorbing #(rejected (fn [] (generator/validate (assoc-in q [:rows (:sink q)] [[0 1]]))))]
         [:uniformization-rate #(rejected (fn [] (sparse/stochastic-product (sparse/compile-kernel q) p 1)))]
         [:negative-time #(rejected (fn [] (uniform/propagate q p -1)))]
         [:probability-normalization #(rejected (fn [] (uniform/propagate q (assoc p 0 2) 1)))]
         [:artifact-content #(rejected (fn [] (bundle/read! corrupt)))]
         [:certificate-probability #(rejected (fn [] (checker/check model (update-in c [:lower-probabilities 0] + 1/100))))]
         [:certificate-generator #(rejected (fn [] (checker/check model (assoc-in c [:rows 0 0 1] 3))))]
         [:certificate-model #(rejected (fn [] (checker/check (net/with-parameters model {:arrival 3}) c)))]
         [:trace-time-order #(rejected (fn [] (direct/replay model (assoc-in trace [:events 0 :time] -1))))]
         [:trace-state #(rejected (fn [] (direct/replay model (assoc-in trace [:events 0 :state] [999]))))]
         [:importance-support #(rejected (fn [] (importance/proposal model {:arrival 0})))]
         [:state-budget #(let [r (adaptive/solve model 10 {:max-states 2})]
                           {:detected? (= :state-budget-exhausted (:status r)) :observation (:status r)})]
         [:refinement-budget #(let [r (adaptive/solve model 10 {:bounds [1] :max-refinements 0})]
                                {:detected? (= :refinement-budget-exhausted (:status r)) :observation (:status r)})]
         [:time-unit-mutation #(let [truth (Math/exp -2.0)
                                    wrong (first (:probabilities (uniform/propagate q p 1/2)))
                                    disagreement (abs (- truth wrong))]
                                {:detected? (> disagreement 0.1) :observation disagreement})]
         [:reflected-frontier-mutation #(let [small (generator/build (projection/enumerate model {:bounds [0]}))
                                             reflected (assoc small :rows [[] []] :exits [0 0])
                                             wrong (first (:probabilities (uniform/propagate reflected [1.0 0.0] 1)))
                                             disagreement (abs (- wrong (Math/exp -2.0)))]
                                         {:detected? (> disagreement 0.8) :observation disagreement})]]
        results (mapv (fn [[id f]] (assoc (f) :id id)) cases)
        result {:status (if (every? :detected? results) :passed :failed)
                :executed (count results) :detected (count (filter :detected? results)) :controls results}]
    (bundle/atomic-write! (io/file directory "controls.edn") (edn/canonical result)) result))
