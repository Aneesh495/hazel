(ns hazel.model.network
  "Validated mass-action networks. Compiled models are immutable data, not closures."
  (:require [clojure.set :as set]
            [hazel.math.numeric :as n]
            [hazel.io.edn :as edn]))

(defn keys! [value allowed context]
  (when-not (map? value) (n/fail "Expected map" {:context context}))
  (let [unknown (set/difference (set (keys value)) allowed)]
    (when (seq unknown) (n/fail "Unknown fields" {:context context :fields unknown}))))

(defn- counts! [m species context]
  (when-not (map? m) (n/fail "Expected population map" {:context context}))
  (doseq [[k v] m]
    (when-not (contains? species k)
      (n/fail "Undeclared species" {:context context :species k}))
    (n/natural! [context k] v))
  m)

(defn validate
  "Reject ambiguous models before any state exploration. Missing counts are zero."
  [model]
  (keys! model #{:name :species :parameters :initial :reactions :description} :model)
  (let [{:keys [species parameters initial reactions]} model
        parameters (or parameters {})]
    (when-not (and (vector? species) (<= 1 (count species) 64)
                   (every? keyword? species) (= (count species) (count (set species))))
      (n/fail "Species must be 1..64 distinct keywords in a vector" {}))
    (when-not (and (map? parameters) (every? keyword? (keys parameters)))
      (n/fail "Parameters must be a keyword map" {}))
    (doseq [[k v] parameters] (n/nonnegative! [:parameter k] v))
    (counts! initial (set species) :initial)
    (when-not (and (vector? reactions) (<= (count reactions) 1024))
      (n/fail "Reactions must be a vector of at most 1024 entries" {}))
    (when-not (= (count reactions) (count (set (map :id reactions))))
      (n/fail "Duplicate reaction identifiers" {}))
    (doseq [reaction reactions]
      (keys! reaction #{:id :reactants :products :rate :description} :reaction)
      (when-not (keyword? (:id reaction)) (n/fail "Reaction id must be a keyword" {}))
      (doseq [side [:reactants :products]]
        (counts! (get reaction side) (set species) [(:id reaction) side]))
      (when (> (reduce +' 0N (vals (:reactants reaction))) 32)
        (n/fail "Total reaction order exceeds 32" {:reaction (:id reaction)}))
      (let [rate (:rate reaction)]
        (if (keyword? rate)
          (when-not (contains? parameters rate)
            (n/fail "Undeclared rate parameter" {:parameter rate}))
          (n/nonnegative! [(:id reaction) :rate] rate))))
    (assoc model :parameters parameters)))

(defn compile-model [model]
  (let [model (validate model)
        {:keys [species initial reactions parameters]} model
        index (zipmap species (range))
        reactions (mapv (fn [r]
                          (let [reactants (mapv #(get (:reactants r) % 0) species)
                                products (mapv #(get (:products r) % 0) species)]
                            (assoc r :consume reactants :produce products
                                   :delta (mapv - products reactants)
                                   :constant (if (keyword? (:rate r))
                                               (parameters (:rate r)) (:rate r))))) reactions)]
    {:hazel.model/compiled true :source model :hash (edn/sha256 model)
     :name (or (:name model) "unnamed") :species species :index index
     :parameters parameters :initial (mapv #(get initial % 0) species)
     :reactions reactions}))

(defn compiled! [model]
  (when-not (true? (:hazel.model/compiled model))
    (n/fail "Expected compiled network" {}))
  model)

(defn state!
  "State vectors follow declared species order and contain machine integers."
  [model state]
  (compiled! model)
  (when-not (and (vector? state) (= (count state) (count (:species model))))
    (n/fail "State dimension mismatch" {:state state :species (:species model)}))
  (doseq [x state] (n/natural! :population x))
  state)

(defn state-map [model state]
  (state! model state)
  (zipmap (:species model) state))

(defn with-parameters [model changes]
  (compiled! model)
  (when-not (and (map? changes) (every? #(contains? (:parameters model) %) (keys changes)))
    (n/fail "Parameter override contains an undeclared parameter" {:changes changes}))
  (compile-model (update (:source model) :parameters merge changes)))

(defn from-file [path] (compile-model (edn/read-file path)))

(defn reaction-index [model id]
  (or (first (keep-indexed #(when (= id (:id %2)) %1) (:reactions model)))
      (n/fail "Unknown reaction" {:id id})))
