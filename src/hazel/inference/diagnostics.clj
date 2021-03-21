(ns hazel.inference.diagnostics
  "Basic split-Rhat and initial-positive-sequence ESS for scalar chains.
  These diagnostics do not establish convergence or replace multiple chains."
  (:require [hazel.math.numeric :as n]))

(defn mean [xs] (/ (n/sum xs) (count xs)))
(defn variance [xs]
  (when (< (count xs) 2) (n/fail "Variance requires at least two samples" {}))
  (let [m (mean xs)] (/ (n/sum (map #(let [d (- % m)] (* d d)) xs)) (dec (count xs)))))

(defn autocorrelation [xs lag]
  (n/natural! :lag lag)
  (when (>= lag (count xs)) (n/fail "Autocorrelation lag exceeds sample count" {}))
  (let [m (mean xs) centered (mapv #(- % m) xs) denominator (n/dot centered centered)]
    (if (zero? denominator) (if (zero? lag) 1.0 0.0)
        (/ (n/dot (subvec centered lag) (subvec centered 0 (- (count xs) lag))) denominator))))

(defn ess [xs]
  (when (< (count xs) 4) (n/fail "ESS requires at least four samples" {}))
  (let [count (count xs)
        pairs (map (fn [k] (+ (autocorrelation xs k) (autocorrelation xs (inc k))))
                   (range 0 (dec count) 2))
        positive (take-while pos? pairs)
        monotone (rest (reductions min ##Inf positive))
        tau (max 1.0 (- (* 2.0 (n/sum monotone)) 1.0))]
    (max 1.0 (min (double count) (/ count tau)))))

(defn split-rhat [chains]
  (when-not (and (<= 2 (count chains)) (apply = (map count chains)) (<= 8 (count (first chains))))
    (n/fail "Rhat needs at least two equal-length chains with eight samples" {}))
  (let [half (quot (count (first chains)) 2)
        splits (vec (mapcat #(vector (subvec (vec %) 0 half) (subvec (vec %) (- (count %) half))) chains))
        means (mapv mean splits) within (mean (mapv variance splits))
        between (* half (variance means))]
    (if (zero? within)
      {:status (if (zero? between) :constant :separated-constants) :value nil}
      {:status :finite :value (Math/sqrt (/ (+ (* (/ (dec half) (double half)) within)
                                               (/ between half)) within))})))

(defn summarize [xs]
  (doseq [x xs] (n/finite! :sample x))
  (let [effective (ess xs) var (variance xs)]
    {:count (count xs) :mean (mean xs) :variance var :ess effective
     :monte-carlo-standard-error (Math/sqrt (/ var effective))}))
