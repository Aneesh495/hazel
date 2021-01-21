(ns hazel.math.probability
  "Probability laws and explicit upper bounds for truncated Poisson sums."
  (:require [hazel.math.numeric :as n]))

(defn choose
  "Exact combinatorial factor. Counts below the reaction order have zero hazard."
  [population order]
  (n/natural! :population population) (n/natural! :order order)
  (if (> order population) 0N
      (let [k (min order (- population order))]
        (loop [i 1 result 1N]
          (if (> i k) result
              (recur (inc i) (quot (*' result (+ (- population k) i)) i)))))))

(def ^:private lanczos
  [676.5203681218851 -1259.1392167224028 771.32342877765313
   -176.61502916214059 12.507343278686905 -0.13857109526572012
   9.9843695780195716e-6 1.5056327351493116e-7])

(defn log-gamma [z]
  (let [z (n/positive! :gamma-argument z)]
    (if (< z 0.5)
      (- (Math/log Math/PI) (Math/log (Math/sin (* Math/PI z))) (log-gamma (- 1 z)))
      (let [z (- z 1.0)
            x (+ 0.99999999999980993
                 (n/sum (map-indexed (fn [i c] (/ c (+ z i 1.0))) lanczos)))
            t (+ z 7.5)]
        (+ (* 0.5 (Math/log (* 2.0 Math/PI)))
           (* (+ z 0.5) (Math/log t)) (- t) (Math/log x))))))

(defn log-factorial [k]
  (n/natural! :count k)
  (log-gamma (inc k)))

(defn poisson-log-pmf [k mean]
  (n/natural! :count k)
  (let [mean (n/nonnegative! :mean mean)]
    (cond (zero? mean) (if (zero? k) 0.0 ##-Inf)
          :else (- (* k (Math/log mean)) mean (log-factorial k)))))

(defn poisson-pmf [k mean] (Math/exp (poisson-log-pmf k mean)))

(defn binomial-log-pmf [k trials probability]
  (n/natural! :trials trials) (n/natural! :count k)
  (let [p (n/clamp-probability probability)]
    (cond (> k trials) ##-Inf
          (zero? p) (if (zero? k) 0.0 ##-Inf)
          (= p 1.0) (if (= k trials) 0.0 ##-Inf)
          :else (+ (- (log-factorial trials) (log-factorial k) (log-factorial (- trials k)))
                   (* k (Math/log p)) (* (- trials k) (Math/log1p (- p)))))))

(defn binomial-pmf [k trials probability]
  (Math/exp (binomial-log-pmf k trials probability)))

(defn normal-log-pdf [x mean sigma]
  (n/finite! :observation x) (n/finite! :mean mean)
  (let [sigma (n/positive! :sigma sigma) z (/ (- x mean) sigma)]
    (- (* -0.5 (+ (* z z) (Math/log (* 2.0 Math/PI)))) (Math/log sigma))))

(defn poisson-window
  "Weights P(N=k), 0 <= k <= K and a geometric upper bound on P(N>K).

  With next weight w and r=lambda/(K+2)<1, all following ratios are <=r,
  so the tail is bounded by w/(1-r). This bounds analytic series truncation;
  it is not an outward-rounded interval proof. Large means must be chunked."
  ([mean tolerance] (poisson-window mean tolerance 10000))
  ([mean tolerance max-terms]
   (let [mean (n/nonnegative! :poisson-mean mean)
         tolerance (n/tolerance! tolerance)]
     (n/natural! :max-terms max-terms)
     (when (> mean 64.0)
       (n/fail "Poisson window requires mean <=64; split the time interval" {:mean mean}))
     (loop [k 0 w (Math/exp (- mean)) weights []]
       (when (>= k max-terms)
         (n/fail "Poisson term budget exhausted" {:max-terms max-terms :mean mean}))
       (let [weights (conj weights w)
             next-weight (* w (/ mean (inc k)))
             ratio (/ mean (+ k 2.0))
             tail (if (< ratio 1.0) (/ next-weight (- 1.0 ratio)) ##Inf)]
         (if (<= tail tolerance)
           {:weights weights :tail-bound tail :terms (count weights)
            :weight-sum (n/sum weights) :mean mean}
           (recur (inc k) next-weight weights)))))))

(defn entropy [p]
  (n/probability-vector! p true)
  (- (n/sum (for [x p :when (pos? x)] (* x (Math/log x))))))

(defn kl-divergence [p q]
  (n/same-size! p q) (n/probability-vector! p) (n/probability-vector! q)
  (if (some true? (map #(and (pos? %1) (zero? %2)) p q)) ##Inf
      (n/sum (map #(if (zero? %1) 0.0 (* %1 (Math/log (/ %1 %2)))) p q))))

(defn total-variation [p q] (* 0.5 (n/distance p q)))

(defn effective-sample-size [weights]
  (let [p (:probabilities (n/normalize weights))]
    (/ 1.0 (n/dot p p))))

(defn wilson-interval
  "Binomial interval, default 95 percent. Descriptive, not a deterministic bound."
  ([successes trials] (wilson-interval successes trials 1.959963984540054))
  ([successes trials z]
   (n/natural! :successes successes) (n/natural! :trials trials)
   (n/positive! :z z)
   (when (or (zero? trials) (> successes trials))
     (n/fail "Invalid binomial observations" {:successes successes :trials trials}))
   (let [p (/ successes (double trials)) z2 (* z z) d (+ 1 (/ z2 trials))
         center (/ (+ p (/ z2 (* 2 trials))) d)
         radius (/ (* z (Math/sqrt (+ (/ (* p (- 1 p)) trials)
                                     (/ z2 (* 4 trials trials))))) d)]
     [(max 0.0 (- center radius)) (min 1.0 (+ center radius))])))
