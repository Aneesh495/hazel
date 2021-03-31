(ns hazel.schedule-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.ctmc.schedule :as schedule]
            [hazel.ctmc.generator :as g]
            [hazel.math.probability :as p]
            [hazel.math.numeric :as n]))

(deftest integrated-piecewise-birth-rate
  (let [states (mapv vector (range 31)) q (g/for-states f/birth states)
        segments [{:until 1 :parameters {:birth 1}} {:until 3 :parameters {:birth 3}}]
        result (schedule/solve f/birth states (g/initial-vector q [0]) segments {})]
    (doseq [x (range 20)] (is (n/close? (p/poisson-pmf x 7) (get (:probabilities result) x) 1e-11 1e-9)))
    (is (= 3.0 (:time result)))
    (is (= (schedule/simulate f/birth segments 42 {}) (schedule/simulate f/birth segments 42 {})))))

(deftest invalid-schedule
  (is (thrown? clojure.lang.ExceptionInfo
               (schedule/compile-schedule f/birth [{:until 2} {:until 1}]))))
