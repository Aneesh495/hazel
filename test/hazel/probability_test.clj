(ns hazel.probability-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.math.probability :as p]
            [hazel.math.numeric :as n]))

(deftest exact-combinatorics
  (is (= 1 (p/choose 0 0)))
  (is (= 0 (p/choose 2 3)))
  (is (= 10 (p/choose 5 2)))
  (is (= 100891344545564193334812497256N (p/choose 100 50)))
  (is (thrown? clojure.lang.ExceptionInfo (p/choose -1 2))))

(deftest known-laws
  (is (n/close? 0 (p/log-gamma 1)))
  (is (n/close? (Math/log 24) (p/log-gamma 5)))
  (is (= 1.0 (p/poisson-pmf 0 0)))
  (is (= 0.0 (p/poisson-pmf 1 0)))
  (is (n/close? 1.0 (n/sum (map #(p/poisson-pmf % 3) (range 50)))))
  (is (n/close? 1.0 (n/sum (map #(p/binomial-pmf % 20 0.3) (range 21)))))
  (is (= 1.0 (p/binomial-pmf 20 20 1)))
  (is (= 0.0 (p/binomial-pmf 1 20 0))))

(deftest explicit-truncation
  (doseq [mean [0 0.01 1 10 32 64] tolerance [1e-4 1e-8 1e-13]]
    (let [{:keys [weights tail-bound]} (p/poisson-window mean tolerance)
          actual (n/sum (map #(p/poisson-pmf % mean) (range (count weights) 200)))]
      (is (<= actual (+ tail-bound 1e-14)))
      (is (<= tail-bound tolerance))
      (is (< (abs (- 1.0 (+ actual (n/sum weights)))) 1e-12))))
  (is (thrown? clojure.lang.ExceptionInfo (p/poisson-window 100 1e-8)))
  (is (thrown? clojure.lang.ExceptionInfo (p/poisson-window 10 1e-15 2))))

(deftest distribution-diagnostics
  (is (n/close? (Math/log 2) (p/entropy [0.5 0.5])))
  (is (= ##Inf (p/kl-divergence [1 0] [0 1])))
  (is (= 0.0 (p/kl-divergence [0.2 0.8] [0.2 0.8])))
  (is (= 4.0 (p/effective-sample-size [1 1 1 1])))
  (let [[lo hi] (p/wilson-interval 50 100)]
    (is (< lo 0.5 hi)))
  (is (thrown? clojure.lang.ExceptionInfo (p/wilson-interval 0 0))))
