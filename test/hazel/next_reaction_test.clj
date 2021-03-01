(ns hazel.next-reaction-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.simulation.next-reaction :as nr]
            [hazel.simulation.direct :as direct]
            [hazel.simulation.random :as random]
            [hazel.math.numeric :as n]))

(deftest scheduled-path-replays
  (doseq [model [f/birth f/death f/switch f/immigration-death]]
    (let [trace (nr/simulate model 10 42)]
      (is (= :complete (:status trace)))
      (is (= :valid (:status (direct/replay model trace))))
      (is (= trace (nr/simulate model 10 42)))))
  (is (= :event-budget-exhausted (:status (nr/simulate f/birth 10 1 {:max-events 0})))))

(deftest independent-distribution-test
  (doseq [[model truth] [[f/birth 4.0] [f/immigration-death (* 4 (- 1 (Math/exp -1)))]]]
    (let [values (mapv (fn [i] (first (:final-state (nr/simulate model 2 (random/split 1729 i)
                                                               {:record? false})))) (range 3000))
          mean (/ (n/sum values) (count values))]
      (is (< (abs (- mean truth)) 0.15)))))
