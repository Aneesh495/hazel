(ns hazel.coarse-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.model.network :as net]
            [hazel.ctmc.projection :as p]
            [hazel.ctmc.generator :as g]
            [hazel.ctmc.uniformization :as u]
            [hazel.analysis.coarse :as coarse]
            [hazel.math.numeric :as n]))

(defn decay-model [rate]
  (net/compile-model {:species [:a :b] :initial {:a 2 :b 2} :parameters {}
                      :reactions [{:id :a :reactants {:a 1} :products {} :rate 1}
                                  {:id :b :reactants {:b 1} :products {} :rate rate}]}))

(deftest exact-population-aggregation
  (let [model (decay-model 1) q (g/build (p/enumerate model))
        blocks (coarse/by-observable q #(reduce + %))
        reduced (coarse/lump q blocks)
        initial (assoc (vec (repeat (:size reduced) 0.0)) (get (:block-of reduced) 0) 1.0)
        full (:probabilities (u/solve q [2 2] 1))
        aggregated (mapv #(n/sum (map full %)) (:blocks reduced))]
    (is (:lumpable? (coarse/check q blocks)))
    (is (= 6 (:size reduced)))
    (is (< (n/distance aggregated (:probabilities (u/propagate reduced initial 1))) 1e-11))))

(deftest asymmetric-rates-break-lumpability
  (let [q (g/build (p/enumerate (decay-model 2))) blocks (coarse/by-observable q #(reduce + %))
        refined (coarse/refine q blocks)]
    (is (not (:lumpable? (coarse/check q blocks))))
    (is (:lumpable? (coarse/check q (:blocks refined))))
    (is (> (count (:blocks refined)) (count blocks)))))
