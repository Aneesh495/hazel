(ns hazel.math.rational
  "Exact nonnegative rational bounds used by the small-model certificate path."
  (:require [hazel.math.numeric :as n]))

(defn exact! [label x]
  (when-not (or (integer? x) (ratio? x))
    (n/fail "Certificate arithmetic requires integers or ratios, not floating decimals" {:field label :value x}))
  x)

(defn nonnegative! [label x]
  (exact! label x)
  (when (neg? x) (n/fail "Expected nonnegative rational" {:field label :value x})) x)

(defn sum [xs] (reduce +' 0N xs))
(defn dot [xs ys] (n/same-size! xs ys) (sum (map *' xs ys)))

(defn ceiling [x]
  (nonnegative! :ceiling x)
  (if (ratio? x) (quot (+' (numerator x) (dec (denominator x))) (denominator x)) x))

(defn exp-enclosure
  "Enclose exp(x) for rational 0<=x<=1. After term K, ratios are <=x/(K+2).
  The omitted positive series is at most next-term/(1-ratio)."
  [x order]
  (nonnegative! :exponent x)
  (n/natural! :order order)
  (when-not (and (<= x 1) (<= 2 order 100))
    (n/fail "Rational exponential requires x<=1 and order 2..100" {}))
  (loop [k 0 term 1N total 1N]
    (if (= k order)
      (let [next (/ (*' term x) (inc k)) ratio (/ x (+ k 2)) tail (/ next (- 1 ratio))]
        {:lower total :upper (+' total tail) :tail tail})
      (let [next (/ (*' term x) (inc k))]
        (recur (inc k) next (+' total next))))))

(defn exp-negative-lower [x order]
  (/ 1 (:upper (exp-enclosure x order))))

(defn coefficients [x order]
  (loop [k 0 term 1N terms [1N]]
    (if (= k order) terms
        (let [next (/ (*' term x) (inc k))]
          (recur (inc k) next (conj terms next))))))
