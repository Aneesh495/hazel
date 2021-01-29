(ns hazel.model.structure
  "Exact stoichiometric invariants and reaction-complex topology."
  (:require [hazel.math.linear :as l]
            [hazel.math.numeric :as n]
            [hazel.math.graph :as graph]
            [hazel.model.network :as net]))

(defn stoichiometry [model]
  (net/compiled! model)
  (if (seq (:reactions model))
    (l/transpose (mapv :delta (:reactions model)))
    (vec (repeat (count (:species model)) []))))

(defn conservation-laws [model]
  (net/compiled! model)
  (let [deltas (mapv :delta (:reactions model))
        basis (l/nullspace deltas (count (:species model)))]
    (mapv (fn [weights]
            {:weights weights :constant (reduce + 0 (map * weights (:initial model)))
             :species (zipmap (:species model) weights)}) basis)))

(defn conserved? [laws state]
  (every? #(= (:constant %) (reduce + 0 (map * (:weights %) state))) laws))

(defn complex-graph [model]
  (reduce (fn [g {:keys [consume produce]}]
            (-> g (update consume (fnil conj #{}) produce)
                (update produce (fnil identity #{})))) {} (:reactions model)))

(defn topology [model]
  (net/compiled! model)
  (let [g (complex-graph model)
        weak (graph/weak-components g)
        strong (graph/strongly-connected g)
        rank (l/rank (stoichiometry model))]
    {:complex-count (count (graph/vertices g))
     :linkage-classes (count weak) :stoichiometric-rank rank
     :deficiency (- (count (graph/vertices g)) (count weak) rank)
     :weakly-reversible? (= (set weak) (set strong))
     :conservation-laws (conservation-laws model)}))

(defn nonnegative-bounds
  "Bounds induced by basis laws with only nonnegative coefficients.
  This is a sufficient detector, not a complete search for the invariant cone."
  [model]
  (let [size (count (:species model))]
    (reduce (fn [bounds {:keys [weights constant]}]
              (if (and (every? #(>= % 0) weights) (>= constant 0))
                (mapv (fn [old coefficient]
                        (if (pos? coefficient)
                          (let [bound (long (Math/floor (double (/ constant coefficient))))]
                            (if old (min old bound) bound)) old)) bounds weights)
                bounds)) (vec (repeat size nil)) (conservation-laws model))))
