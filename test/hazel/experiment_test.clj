(ns hazel.experiment-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.experiment :as experiment]
            [hazel.model.predicate :as predicate]))

(deftest inert-event-predicates
  (let [p (predicate/compile-predicate f/birth [:and {:species :x :op :>= :value 2}
                                              [:not {:species :x :op :> :value 4}]])]
    (is (p [2])) (is (p [4])) (is (not (p [5]))))
  (is (thrown? clojure.lang.ExceptionInfo (predicate/compile-predicate f/birth {:species :typo :op :> :value 2}))))

(deftest orchestration
  (let [study {:model f/birth-source :experiment {:kind :transient :time 1}}
        r (experiment/run-study study)]
    (is (= :converged (get-in r [:result :status])))
    (is (= [:x] (get-in r [:result :species]))))
  (is (thrown? clojure.lang.ExceptionInfo
               (experiment/run-study {:model f/birth-source :experiment {:kind :transient :time 1 :typo 2}}))))
