(ns hazel.structure-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.math.graph :as g]
            [hazel.model.structure :as s]
            [hazel.model.network :as net]))

(deftest conservation-and-deficiency
  (let [laws (s/conservation-laws f/switch) topo (s/topology f/switch)]
    (is (= [[1 1]] (mapv :weights laws)))
    (is (s/conserved? laws [0 1]))
    (is (not (s/conserved? laws [1 1])))
    (is (= [1 1] (s/nonnegative-bounds f/switch)))
    (is (= 0 (:deficiency topo)))
    (is (:weakly-reversible? topo)))
  (is (= [] (s/conservation-laws f/birth)))
  (is (not (:weakly-reversible? (s/topology f/birth))))
  (let [m (net/compile-model {:species [:x :y] :initial {:x 3} :reactions []})]
    (is (= 2 (count (s/conservation-laws m))))
    (is (= 0 (:deficiency (s/topology m))))))

(deftest graph-components
  (let [g {0 #{1} 1 #{0 2} 2 #{3} 3 #{2} 4 #{}}]
    (is (= #{#{0 1} #{2 3} #{4}} (set (g/strongly-connected g))))
    (is (= #{#{2 3} #{4}} (set (g/closed-components g))))
    (is (= #{0 1 2 3} (g/reachable g 0))))
  (let [chain (into {} (map #(vector % #{(inc %)}) (range 2000)))]
    (is (= 2001 (count (g/strongly-connected chain))))))
