(ns hazel.reward-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.ctmc.projection :as p]
            [hazel.ctmc.generator :as g]
            [hazel.analysis.reward :as reward]
            [hazel.math.numeric :as n]))

(deftest poisson-event-count-moments
  (let [q (g/build (p/enumerate f/birth {:bounds [40]}))
        r (reward/reaction-count q (g/initial-vector q [0]) 2 :birth {})]
    (is (= :complete (:status r)))
    (is (n/close? 4 (:mean r) 1e-7 1e-7))
    (is (n/close? 4 (:variance r) 1e-6 1e-7))))

(deftest constant-occupancy-is-deterministic
  (let [q (g/build (p/enumerate f/switch))
        r (reward/occupancy q (g/initial-vector q [1 0]) 3 (constantly 2) {})]
    (is (n/close? 6 (:mean r)))
    (is (< (:variance r) 1e-7))))

(deftest self-event-counts-are-not-discarded
  (let [q (g/build {:states [[1]] :model-hash "self" :closed? true
                    :edges [[{:id :tick :reaction 0 :rate 3 :destination 0}]]})
        r (reward/reaction-count q [1.0 0.0] 2 :tick {})]
    (is (n/close? 6 (:mean r)))
    (is (n/close? 6 (:variance r) 1e-7 1e-7))))
