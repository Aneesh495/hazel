(ns hazel.path-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.simulation.direct :as direct]
            [hazel.model.network :as net]
            [hazel.analysis.path :as path]
            [hazel.analysis.reversal :as reversal]
            [hazel.analysis.stationary :as stationary]
            [hazel.ctmc.generator :as g]
            [hazel.ctmc.projection :as p]
            [hazel.math.numeric :as n]))

(deftest birth-path-density-and-conjugacy
  (let [trace (direct/simulate f/birth 3 42) stats (path/statistics f/birth trace)
        count (:event-count trace)]
    (is (n/close? (- (* count (Math/log 2)) 6) (:log-density stats)))
    (is (n/close? (- (/ count 2.0) 3) (get (path/score f/birth trace) :birth)))
    (is (= (+ 1 count) (:shape (path/gamma-posterior f/birth trace :birth 1 1))))
    (is (= 4.0 (:rate (path/gamma-posterior f/birth trace :birth 1 1))))))

(deftest equilibrium-switch-is-time-reversible
  (let [q (g/build (p/enumerate f/switch))
        pi (-> (stationary/solve q) :classes first :probabilities)
        reversed (reversal/reverse-generator q pi)]
    (is (< (n/distance (mapcat identity (g/dense q)) (mapcat identity (g/dense reversed))) 1e-12))))
