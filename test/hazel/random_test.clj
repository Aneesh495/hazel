(ns hazel.random-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.simulation.random :as r]
            [hazel.math.numeric :as n]))

(defn samples [sampler seed count]
  (loop [state seed i 0 values []]
    (if (= i count) values
        (let [[value state] (sampler state)] (recur state (inc i) (conj values value))))))

(deftest deterministic-streams
  (is (= -2152535657050944081 (first (r/next-long 0))))
  (is (= (samples r/uniform 42 20) (samples r/uniform 42 20)))
  (is (not= (r/split 42 1) (r/split 42 2)))
  (is (every? #(< 0 % 1) (samples r/uniform -1 10000)))
  (is (thrown? clojure.lang.ExceptionInfo (r/categorical 1 [0 0]))))

(deftest sampler-moments
  (doseq [mean [0.1 4 30 1000]]
    (let [xs (samples #(r/poisson % mean) 1729 15000)
          avg (/ (n/sum xs) (count xs))
          variance (/ (n/sum (map #(let [d (- % avg)] (* d d)) xs)) (count xs))]
      (is (< (abs (- avg mean)) (+ 0.02 (* 6 (Math/sqrt (/ mean (count xs)))))))
      (is (< (abs (- variance mean)) (+ 0.05 (* 0.06 mean))))))
  (let [xs (samples r/normal 31 20000)]
    (is (< (abs (/ (n/sum xs) (count xs))) 0.03))
    (is (< (abs (- 1 (/ (n/dot xs xs) (count xs)))) 0.04))))
