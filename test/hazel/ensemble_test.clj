(ns hazel.ensemble-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.simulation.direct :as direct]
            [hazel.simulation.ensemble :as e]))

(deftest independent-sample-path-check
  (let [ensemble (e/simulate f/birth 2 123 3000 {:workers 2}) moments (e/moments ensemble first)]
    (is (= :complete (:status ensemble)))
    (is (< (abs (- 4 (:mean moments))) (* 6 (:standard-error moments))))
    (is (< (abs (- 4 (:sample-variance moments))) 0.5)))
  (is (= (:histogram (e/simulate f/switch 5 17 100 {:workers 1}))
         (:histogram (e/simulate f/switch 5 17 100 {:workers 3}))))
  (is (thrown? clojure.lang.ExceptionInfo
               (e/event-frequency (e/simulate f/birth 100 1 5 {:max-events 1}) (constantly true)))))

(deftest right-continuous-grid
  (let [trace (direct/simulate f/birth 3 42) first-event (first (:events trace))
        times [0 (:time first-event) 3] grid (e/observe-grid trace times)]
    (is (= [0] (:state (first grid))))
    (is (= [1] (:state (second grid))))
    (is (= (:final-state trace) (:state (last grid))))))
