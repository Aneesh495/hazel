(ns hazel.math.polynomial
  "Sparse multivariate polynomials with exponent-vector keys and exact coefficients.
  Operations retain rational arithmetic and enforce explicit expansion budgets."
  (:refer-clojure :exclude [constant?])
  (:require [hazel.math.numeric :as n]
            [hazel.math.probability :as probability]))

(def term-limit 100000)

(defn clean [polynomial]
  (when (> (count polynomial) term-limit) (n/fail "Polynomial term budget exceeded" {}))
  (into {} (remove (comp zero? val)) polynomial))

(defn constant [dimensions value]
  (if (zero? value) {} {(vec (repeat dimensions 0)) value}))

(defn variable [dimensions index]
  (when-not (< -1 index dimensions) (n/fail "Polynomial variable out of range" {}))
  {(assoc (vec (repeat dimensions 0)) index 1) 1N})

(defn monomial [exponents]
  (doseq [e exponents] (n/natural! :exponent e))
  {(vec exponents) 1N})

(defn add [a b] (clean (merge-with +' a b)))
(defn scale [value p] (clean (into {} (map (fn [[m c]] [m (*' value c)])) p)))

(defn multiply [a b]
  (when (> (* (count a) (count b)) (* 4 term-limit))
    (n/fail "Polynomial product expansion budget exceeded" {}))
  (clean (reduce (fn [result [[ma ca] [mb cb]]]
                   (n/same-size! ma mb)
                   (update result (mapv + ma mb) (fnil +' 0N) (*' ca cb)))
                 {} (for [a a b b] [a b]))))

(defn power [p exponent dimensions]
  (n/natural! :exponent exponent)
  (when (> exponent 32) (n/fail "Polynomial exponent exceeds 32" {}))
  (loop [exponent exponent base p result (constant dimensions 1N)]
    (if (zero? exponent) result
        (recur (quot exponent 2)
               (if (> exponent 1) (multiply base base) base)
               (if (odd? exponent) (multiply result base) result)))))

(defn- integer-power [x exponent]
  (loop [exponent exponent base x result 1N]
    (if (zero? exponent) result
        (recur (quot exponent 2) (if (> exponent 1) (*' base base) base)
               (if (odd? exponent) (*' result base) result)))))

(defn evaluate [p state]
  (reduce +' 0N
          (for [[exponents coefficient] p]
            (do (n/same-size! exponents state)
                (*' coefficient (reduce *' 1N (map integer-power state exponents)))))))

(defn derivative [p index]
  (clean (reduce-kv (fn [result exponents coefficient]
                      (let [order (get exponents index)]
                        (when (nil? order) (n/fail "Polynomial derivative variable out of range" {}))
                        (if (zero? order) result
                            (assoc result (update exponents index dec) (*' coefficient order))))) {} p)))

(defn shift-monomial [exponents delta]
  (n/same-size! exponents delta)
  (let [dimensions (count exponents)]
    (reduce multiply (constant dimensions 1N)
            (map-indexed
             (fn [i order]
               (into {} (for [k (range (inc order))
                              :let [coefficient (*' (probability/choose order k)
                                                    (integer-power (delta i) (- order k)))]
                              :when (not (zero? coefficient))]
                          [(assoc (vec (repeat dimensions 0)) i k) coefficient]))) exponents))))

(defn shift [p delta]
  (reduce add {} (map (fn [[exponents coefficient]] (scale coefficient (shift-monomial exponents delta))) p)))

(defn degree [p] (reduce max 0 (map #(reduce + %) (keys p))))

(defn basis
  "All monomials up to a total degree, ordered by degree then lexicographically."
  [dimensions maximum-order]
  (n/natural! :dimensions dimensions) (n/natural! :maximum-order maximum-order)
  (when (or (zero? dimensions) (> maximum-order 12)
            (> (probability/choose (+ dimensions maximum-order) maximum-order) 10000))
    (n/fail "Moment basis exceeds supported dimension or size" {}))
  (letfn [(of-degree [n total]
            (if (= n 1) [[total]]
                (for [x (range (inc total)) rest (of-degree (dec n) (- total x))] (into [x] rest))))]
    (vec (mapcat #(of-degree dimensions %) (range (inc maximum-order))))))
