(ns hazel.model.predicate
  "Inert population predicates for event studies. No eval or reader functions."
  (:require [hazel.math.numeric :as n]
            [hazel.model.network :as net]))

(defn compile-predicate
  ([model expression] (compile-predicate model expression 0))
  ([model expression depth]
   (when (> depth 32) (n/fail "Predicate nesting exceeds 32" {}))
   (cond
     (boolean? expression) (constantly expression)
     (map? expression)
     (do
       (net/keys! expression #{:species :op :value} :predicate)
       (let [index (get (:index model) (:species expression)) threshold (:value expression)
             comparison (case (:op expression) :< < :<= <= := = :>= >= :> > :!= not=
                              (n/fail "Unknown population comparison" {:op (:op expression)}))]
         (when (nil? index) (n/fail "Predicate references undeclared species" {}))
         (n/finite! :threshold threshold)
         (fn [state] (comparison (state index) threshold))))
     (vector? expression)
     (let [children (mapv #(compile-predicate model % (inc depth)) (rest expression))]
       (when (empty? children) (n/fail "Boolean predicate needs operands" {}))
       (case (first expression)
         :and (fn [state] (every? #(% state) children))
         :or (fn [state] (boolean (some #(% state) children)))
         :not (do (when-not (= 1 (count children)) (n/fail "not takes one operand" {}))
                  (fn [state] (not ((first children) state))))
         (n/fail "Unknown Boolean predicate operator" {})))
     :else (n/fail "Invalid population predicate" {:expression expression}))))
