(ns hazel.first-passage-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.ctmc.projection :as p]
            [hazel.ctmc.generator :as g]
            [hazel.analysis.first-passage :as fp]
            [hazel.math.numeric :as n]))

(deftest exponential-first-arrival
  (let [q (g/build (p/enumerate f/birth {:bounds [5]}))
        results (fp/cdf q (g/initial-vector q [0]) #(>= (first %) 1) [0 0.1 1 4])]
    (doseq [r results]
      (is (n/close? (- 1 (Math/exp (* -2 (:time r)))) (:lower r) 1e-11 1e-10))
      (is (n/close? (* 2 (Math/exp (* -2 (:time r)))) (:density r) 1e-11 1e-10)))
    (is (apply <= (map :lower results)))))

(deftest target-at-time-zero
  (let [q (g/build (p/enumerate f/switch)) initial (g/initial-vector q [1 0])]
    (is (= 1.0 (:lower (first (fp/cdf q initial #(= [1 0] %) [0 5])))))
    (is (thrown? clojure.lang.ExceptionInfo (fp/competing q initial [(constantly true) (constantly true)] 1 {})))))
