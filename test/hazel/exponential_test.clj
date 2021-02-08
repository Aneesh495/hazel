(ns hazel.exponential-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.math.exponential :as exp]
            [hazel.math.numeric :as n]
            [hazel.math.linear :as l]
            [hazel.fixtures :as f]
            [hazel.ctmc.projection :as p]
            [hazel.ctmc.generator :as g]
            [hazel.ctmc.uniformization :as u]))

(deftest independent-matrix-identities
  (is (= [[1.0 0.0] [0.0 1.0]] (exp/expm [[0 0] [0 0]])))
  (is (= [[1.0 3.0] [0.0 1.0]] (exp/expm [[0 3] [0 0]])))
  (let [a [[0 -1] [1 0]] r (exp/expm (l/scale Math/PI a))]
    (is (n/close? -1 (get-in r [0 0])))
    (is (n/close? -1 (get-in r [1 1])))
    (is (< (abs (get-in r [0 1])) 1e-13))))

(deftest sparse-dense-transients
  (doseq [model [f/birth f/death f/switch f/immigration-death] t [0 0.1 1 5]]
    (let [q (g/build (p/enumerate model)) initial (g/initial-vector q (:initial model))
          reference (exp/action (g/dense q) initial t)
          computed (:probabilities (u/propagate q initial t))]
      (is (< (n/distance reference computed) 2e-11)))))
