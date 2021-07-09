(ns hazel.model.kinetics
  "Combinatorial propensities and exact state transitions."
  (:require [hazel.model.network :as net]
            [hazel.math.numeric :as n]
            [hazel.math.probability :as probability]))

(defn factor [reaction state]
  (n/same-size! state (:consume reaction))
  (reduce *' 1N (map probability/choose state (:consume reaction))))

(defn propensity [reaction state]
  (let [value (*' (:constant reaction) (factor reaction state))]
    (n/nonnegative! [:propensity (:id reaction)] value)
    value))

(defn hazards [model state]
  (net/state! model state)
  (mapv #(propensity % state) (:reactions model)))

(defn fire
  "Only reactant availability enables firing; a zero-rate reaction cannot fire."
  [model state reaction-id]
  (net/state! model state)
  (let [i (if (integer? reaction-id) reaction-id (net/reaction-index model reaction-id))
        reaction (get (:reactions model) i)]
    (when-not reaction (n/fail "Reaction index out of range" {:index i}))
    (when-not (pos? (propensity reaction state))
      (n/fail "Reaction is disabled" {:reaction (:id reaction) :state state}))
    (let [target (mapv +' state (:delta reaction))]
      (net/state! model target)
      target)))

(defn transitions [model state]
  (net/state! model state)
  (into []
        (keep-indexed (fn [i reaction]
                        (let [rate (propensity reaction state)]
                          (when (pos? rate)
                            {:reaction i :id (:id reaction) :rate rate
                             :target (fire model state i)}))))
        (:reactions model)))

(defn dependency-graph
  "For each fired reaction, indexes of propensities its population change affects."
  [model]
  (net/compiled! model)
  (mapv (fn [reaction]
          (into #{}
                (keep-indexed (fn [j other]
                                (when (some true? (map #(and (not (zero? %1)) (pos? %2))
                                                      (:delta reaction) (:consume other))) j)))
                (:reactions model))) (:reactions model)))

(defn parameter-derivative
  "Derivative of mass-action propensity, valid even when the parameter is zero."
  [reaction state parameter]
  (if (= parameter (:rate reaction)) (factor reaction state) 0))

(defn drift [model state]
  (let [rates (hazards model state)]
    (reduce n/add (vec (repeat (count state) 0.0))
            (map #(n/scale %1 (:delta %2)) rates (:reactions model)))))

(defn total-rate [model state]
  (n/sum (hazards model state)))
