(ns hazel.direct-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.simulation.direct :as s]
            [hazel.model.structure :as structure]))

(deftest reproducible-conserved-path
  (let [trace (s/simulate f/switch 100 42) laws (structure/conservation-laws f/switch)]
    (is (= :complete (:status trace)))
    (is (pos? (:event-count trace)))
    (is (every? #(structure/conserved? laws (:state %)) (:events trace)))
    (is (= :valid (:status (s/replay f/switch trace))))
    (is (= :reproduced (:status (s/reproduce f/switch trace))))
    (is (thrown? clojure.lang.ExceptionInfo
                 (s/replay f/switch (assoc-in trace [:events 0 :state] [1 1]))))))

(deftest boundaries-and-budgets
  (is (= 0 (:event-count (s/simulate f/birth 0 42))))
  (is (= :event-budget-exhausted (:status (s/simulate f/birth 100 42 {:max-events 1}))))
  (let [trace (s/simulate f/death 100 42)]
    (is (= [0] (:final-state trace)))
    (is (= 6 (:event-count trace))))
  (is (thrown? clojure.lang.ExceptionInfo (s/simulate f/death -1 42))))
