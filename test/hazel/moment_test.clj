(ns hazel.moment-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.math.polynomial :as poly]
            [hazel.math.numeric :as n]
            [hazel.analysis.moment :as moment]
            [hazel.model.network :as net]
            [hazel.model.kinetics :as k]))

(deftest polynomial-shift-and-differentiation
  (let [p {[2 1] 3 [0 0] 2} delta [-2 1]]
    (doseq [state [[1 2] [0 0] [3 4]]]
      (is (= (poly/evaluate p (mapv + state delta)) (poly/evaluate (poly/shift p delta) state))))
    (is (= {[1 1] 6N} (poly/derivative p 0)))))

(deftest symbolic-generator-agrees-with-jumps
  (let [observable {[3] 1N} model f/immigration-death
        action (moment/generator-action model observable)]
    (doseq [state [[0] [1] [2] [9]]]
      (let [direct (reduce + 0 (for [{:keys [target rate]} (k/transitions model state)]
                                (* rate (- (poly/evaluate observable target) (poly/evaluate observable state)))))]
        (is (n/close? direct (poly/evaluate action state)))))))

(deftest closed-linear-moments
  (let [r (moment/solve f/immigration-death 3 2 {}) mean (* 4 (- 1 (Math/exp -1)))]
    (is (n/close? mean (get (:moments r) [1])))
    (is (n/close? (+ mean (* mean mean)) (get (:moments r) [2]) 1e-7 1e-7))))

(deftest nonlinear-hierarchy-is-not-silently-closed
  (let [model (net/compile-model {:species [:x] :initial {:x 10} :parameters {}
                                  :reactions [{:id :dimer :reactants {:x 2} :products {} :rate 1}]})]
    (is (not (:closed? (moment/compile-hierarchy model 2))))
    (is (thrown? clojure.lang.ExceptionInfo (moment/solve model 2 1 {})))))
