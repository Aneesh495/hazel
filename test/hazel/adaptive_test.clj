(ns hazel.adaptive-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.ctmc.adaptive :as a]))

(deftest adaptive-mass-convergence
  (let [r (a/solve f/birth 4 {:bounds [2] :epsilon 1e-8})]
    (is (= :converged (:status r)))
    (is (> (count (:history r)) 1))
    (is (<= (:truncation-bound r) 1e-8))
    (is (apply >= (map :escape (:history r))))
    (is (= [1.0 0.0] (take 2 (:probabilities (a/solve f/birth 0 {:bounds [0]})))))))

(deftest incomplete-is-not-success
  (is (= :state-budget-exhausted (:status (a/solve f/birth 10 {:max-states 2}))))
  (is (= :refinement-budget-exhausted
         (:status (a/solve f/birth 10 {:bounds [1] :max-refinements 0}))))
  (is (= :converged (:status (a/solve f/switch 10)))))
