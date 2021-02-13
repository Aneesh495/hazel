(ns hazel.distribution-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.ctmc.projection :as p]
            [hazel.ctmc.generator :as g]
            [hazel.ctmc.uniformization :as u]
            [hazel.analysis.distribution :as d]
            [hazel.math.numeric :as n]
            [hazel.math.probability :as probability]))

(deftest honest-event-interval
  (let [q (g/build (p/enumerate f/birth {:bounds [2]})) r (u/solve q [0] 2)
        event (d/event q r #(>= (first %) 2))
        truth (- 1 (probability/poisson-pmf 0 4) (probability/poisson-pmf 1 4))]
    (is (<= (:lower event) truth (:upper event)))
    (is (thrown? clojure.lang.ExceptionInfo (d/quantile (d/marginal q r 0) 0.5)))
    (is (< (:mean (d/conditional-moments q r first)) 2))))

(deftest normalized-queries
  (let [q (g/build (p/enumerate f/switch)) r (u/solve q [1 0] 100)]
    (is (n/close? 1/3 (d/expectation q r second)))
    (is (= 0 (d/quantile (d/marginal q r 1) 0.5)))
    (is (n/close? 2/9 (:variance (d/conditional-moments q r second))))))
