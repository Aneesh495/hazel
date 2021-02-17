(ns hazel.backward-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.ctmc.projection :as p]
            [hazel.ctmc.generator :as g]
            [hazel.analysis.backward :as b]
            [hazel.math.numeric :as n]))

(deftest exponential-waiting-equations
  (let [q (g/build (p/enumerate f/switch)) r (b/hitting q #(= [0 1] %))]
    (is (= [1.0 1.0 0.0] (:hit-probabilities r)))
    (is (n/close? 2.5 (first (:mean-times r))))
    (is (= 0.0 (second (:mean-times r))))
    (is (nil? (last (:mean-times r))))
    (is (< (:equation-residual r) 1e-12))))

(deftest extinction-and-impossibility
  (let [q (g/build (p/enumerate f/death)) r (b/hitting q #(= [0] %))
        analytic (/ (reduce + (map #(/ 1.0 %) (range 1 7))) 0.7)]
    (is (n/close? analytic (first (:mean-times r)))))
  (let [q (g/build (p/enumerate f/birth {:bounds [3]})) r (b/hitting q #(= [9] %))]
    (is (every? zero? (:hit-probabilities r)))
    (is (every? nil? (:conditional-mean-times r)))))
