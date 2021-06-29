(ns hazel.ctmc.certificate
  "Exact lower distributions for small rational-rate finite-state projections.

  The lower Poisson series uses a rational lower bound on exp(-lambda*t).
  All omitted probability, including exponential enclosure error, is retained as
  one exact mass deficit. No binary64 arithmetic enters the bound calculation."
  (:require [hazel.math.rational :as r]
            [hazel.math.numeric :as n]
            [hazel.ctmc.generator :as g]))

(defn- stochastic-step [q p lambda]
  (reduce (fn [out i]
            (let [value (p i)
                  out (update out i +' (*' value (- 1 (/ (get (:exits q) i) lambda))))]
              (reduce (fn [out [j rate]] (update out j +' (*' value (/ rate lambda)))) out (get (:rows q) i))))
          (vec (repeat (:size q) 0N)) (range (:size q))))

(defn- lower-step [q p lambda mean order exp-order]
  ;; A positive polynomial in a stochastic matrix preserves componentwise
  ;; lower bounds. The rational exponential factor is lower as well.
  (let [coefficients (r/coefficients mean order)
        factor (r/exp-negative-lower mean exp-order)]
    (loop [coefficients coefficients power p total (vec (repeat (:size q) 0N))]
      (let [total (mapv +' total (mapv #(*' (first coefficients) %) power))]
        (if (empty? (next coefficients)) (mapv #(*' factor %) total)
            (recur (next coefficients) (stochastic-step q power lambda) total))))))

(defn create
  ([model q time] (create model q time {}))
  ([model q time {:keys [poisson-order exponential-order max-chunks]
                  :or {poisson-order 20 exponential-order 20 max-chunks 64}}]
   (g/validate q)
   (when-not (= (:model-hash q) (:hash model)) (n/fail "Certificate generator/model mismatch" {}))
   (r/nonnegative! :time time)
   ;; Validate source rates as well as populated edges. A disabled floating
   ;; channel has no edge, but it still violates the exact-input contract.
   (doseq [reaction (:reactions model)] (r/nonnegative! :reaction-constant (:constant reaction)))
   (when (> (:size q) 65) (n/fail "Rational certificate supports at most 64 population states" {}))
   (doseq [rate (:exits q)] (r/nonnegative! :exit-rate rate))
   (doseq [rate (map second (mapcat identity (:rows q)))] (r/nonnegative! :transition-rate rate))
   (doseq [order [poisson-order exponential-order]]
     (when-not (and (integer? order) (<= 2 order 100)) (n/fail "Certificate order must be in 2..100" {})))
   (let [lambda (reduce max 0N (:exits q))
         chunks (max 1N (r/ceiling (*' lambda time)))
         _ (when (or (> chunks max-chunks) (> chunks 64))
             (n/fail "Rational certificate chunk budget exceeded" {:required chunks :limit max-chunks}))
         initial-index (.indexOf ^java.util.List (:states q) (:initial model))
         _ (when (neg? initial-index) (n/fail "Initial state outside certified projection" {}))
         initial (assoc (vec (repeat (:size q) 0N)) initial-index 1N)
         mean (/ (*' lambda time) chunks)
         lower (if (zero? lambda) initial
                   (loop [i 0 p initial]
                     (if (= i chunks) p
                         (recur (inc i) (lower-step q p lambda mean poisson-order exponential-order)))))
         deficit (- 1N (r/sum lower))
         ;; The deficit combines every omitted series term and enclosure loss.
         ;; Retaining it once avoids assuming where uncomputed mass would land.
         escape (peek lower)]
     (when (or (neg? deficit) (some neg? lower)) (n/fail "Exact lower-distribution invariant failed" {}))
     {:format :hazel/certificate-v1 :status :bounded
      :model-hash (:hash model) :states (:states q) :rows (:rows q) :exits (:exits q)
      :time time :lambda lambda :chunks chunks :poisson-order poisson-order :exponential-order exponential-order
      :initial initial :lower-probabilities lower :mass-deficit deficit
      :escape-lower escape :truncation-upper (min 1N (+' escape deficit))
      :arithmetic :exact-rational})))

(defn event-interval [certificate predicate]
  (let [lower (r/sum (for [[state probability] (map vector (:states certificate) (:lower-probabilities certificate))
                           :when (predicate state)] probability))]
    {:lower lower :upper (min 1N (+' lower (:truncation-upper certificate)))
     :arithmetic :exact-rational}))
