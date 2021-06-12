(ns hazel.dispatch-test
  "Every public study kind crosses the CLI, artifact, report, and replay boundary."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [hazel.fixtures :as fixtures]
            [hazel.bundle-test :as temporary]
            [hazel.experiment :as experiment]
            [hazel.cli :as cli]))

(def sensors [{:species :on :kind :gaussian :sigma 0.5}])
(def observations [{:time 1 :values {:on 0.8}} {:time 2 :values {:on 0.2}}])
(def target {:species :on :op := :value 1})

(def experiments
  [{:kind :transient :time 1}
   {:kind :trajectory :times [0 1/2 1]}
   {:kind :certify :time 1 :target target}
   {:kind :first-passage :times [0 1/2 1] :target target}
   {:kind :hitting :target target}
   {:kind :stationary}
   {:kind :sensitivity :parameters [:activate] :time 1}
   {:kind :filter :sensors sensors :observations observations :smooth? true :bridge-seed 42}
   {:kind :score :sensors sensors :observations observations :parameters [:activate]}
   {:kind :fit :sensors sensors :observations observations :parameters [:activate]
    :bounds {:activate [1/100 3]}}
   {:kind :particle :sensors sensors :observations observations :particles 20 :seed 42}
   {:kind :mcmc :sensors sensors :observations observations :parameters [:activate]
    :priors {:activate {:kind :gamma :shape 2 :rate 1}} :iterations 5 :particles 20 :seed 42}
   {:kind :design :parameters [:activate] :times [1/2 1 2] :species :on :budget 1}
   {:kind :schedule :segments [{:until 1 :parameters {:activate 1/2}}]}
   {:kind :reward :reaction :activate :time 1}
   {:kind :moments :order 2 :time 1}
   {:kind :bridge :start [1 0] :end [0 1] :time 1 :seed 42}
   {:kind :importance :proposal {:activate 2} :time 1 :target target :samples 20 :seed 42}
   {:kind :simulate :time 1 :seed 42}
   {:kind :ensemble :time 1 :seed 42 :samples 20 :workers 2}])

(deftest every-study-kind-roundtrips
  (is (= (set (keys experiment/fields)) (set (map :kind experiments))))
  (temporary/with-directory
    (fn [directory]
      (doseq [request experiments]
        (testing (name (:kind request))
          (let [model (if (= :certify (:kind request))
                        (assoc fixtures/switch-source :parameters {:activate 1/2 :deactivate 1})
                        fixtures/switch-source)
                path (io/file directory (str (name (:kind request)) ".edn"))
                output (io/file directory (name (:kind request)))]
            (spit path (pr-str {:model model :experiment request}))
            (let [run (cli/invoke ["run" (str path) "--out" (str output)])]
              (is (= 0 (:exit run)) (pr-str run))
              (when (zero? (:exit run))
                (let [bundle (get-in run [:data :bundle])]
                  (is (= 0 (:exit (cli/invoke ["inspect" bundle]))))
                  (is (= 0 (:exit (cli/invoke ["replay" bundle]))))
                  (is (.isFile (io/file output "index.html")))
                  (when (= :certify (:kind request))
                    (is (= 0 (:exit (cli/invoke ["verify" bundle]))))))))))))))

(deftest incomplete-status-crosses-process-contract
  (temporary/with-directory
    (fn [directory]
      (let [path (io/file directory "budget.edn")]
        (spit path (pr-str {:model fixtures/birth-source
                           :experiment {:kind :transient :time 10 :projection {:max-states 2}}}))
        (let [r (cli/invoke ["run" (str path) "--out" (str (io/file directory "out"))])]
          (is (= 2 (:exit r)))
          (is (= :state-budget-exhausted (get-in r [:data :status])))
          (is (.isFile (io/file (get-in r [:data :bundle])))))))))
