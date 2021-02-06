(ns hazel.uniformization-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.ctmc.projection :as p]
            [hazel.ctmc.generator :as g]
            [hazel.ctmc.uniformization :as u]
            [hazel.math.probability :as probability]
            [hazel.math.numeric :as n]))

(deftest pure-birth-analytic
  (let [q (g/build (p/enumerate f/birth {:bounds [30]})) r (u/solve q [0] 2)]
    (doseq [[i state] (map-indexed vector (:states q))]
      (is (n/close? (probability/poisson-pmf (first state) 4)
                    (get (:probabilities r) i) 1e-12 1e-10)))
    (is (< (:floating-point-residual r) 1e-13))
    (is (< (:series-tail-bound r) 1e-12))))

(deftest pure-death-analytic
  (let [q (g/build (p/enumerate f/death)) r (u/solve q [6] 3)
        survival (Math/exp (* -0.7 3))]
    (doseq [[i [x]] (map-indexed vector (:states q))]
      (is (n/close? (probability/binomial-pmf x 6 survival)
                    (get (:probabilities r) i) 1e-12 1e-10)))
    (is (zero? (:escape-probability r)))))

(deftest chunking-and-semigroup
  (let [q (g/build (p/enumerate f/switch)) initial (g/initial-vector q [1 0])
        r (u/propagate q initial 200)
        half (u/propagate q initial 100)
        twice (u/propagate q (:probabilities half) 100)]
    (is (> (:chunks r) 1))
    (is (< (n/distance (:probabilities r) (:probabilities twice)) 1e-10))
    (is (n/close? 1/3 (get (:probabilities r) 1)))
    (is (= initial (:probabilities (u/propagate q initial 0)))))
  (let [q (g/build (p/enumerate f/birth))]
    (is (thrown? clojure.lang.ExceptionInfo (u/solve q [0] -1)))
    (is (thrown? clojure.lang.ExceptionInfo (u/solve q [0] 10000 {:max-chunks 1}))))
  (let [q (g/build (p/enumerate f/death)) results (u/trajectory q (g/initial-vector q [6]) [0 1 2])]
    (is (= [0 1 2] (mapv :time results)))))
