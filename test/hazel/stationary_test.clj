(ns hazel.stationary-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.ctmc.projection :as p]
            [hazel.ctmc.generator :as g]
            [hazel.analysis.stationary :as s]
            [hazel.math.numeric :as n]))

(deftest stationary-switch
  (let [q (g/build (p/enumerate f/switch)) result (s/solve q)
        pi (-> result :classes first :probabilities)]
    (is (:unique? result))
    (is (< (n/distance pi [2/3 1/3 0]) 1e-12))
    (is (< (s/detailed-balance-residual q pi) 1e-12))
    (is (n/close? 0 (:value (s/entropy-production q pi))))))

(deftest absorbing-class-and-truncated-refusal
  (let [q (g/build (p/enumerate f/death)) result (s/solve q)]
    (is (= #{6} (set (-> result :classes first :states))))
    (is (= 1.0 (get (-> result :classes first :probabilities) 6))))
  (is (thrown? clojure.lang.ExceptionInfo (s/solve (g/build (p/enumerate f/birth))))))
