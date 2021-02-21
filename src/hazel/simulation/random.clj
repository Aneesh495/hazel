(ns hazel.simulation.random
  "Pure SplitMix64 streams. Samplers return [sample next-state].
  Stream state is a signed 64-bit integer, so experiments can checkpoint it."
  (:require [hazel.math.numeric :as n]
            [hazel.math.probability :as p]))

(def ^:private golden (unchecked-long 0x9e3779b97f4a7c15))
(def ^:private mix-one (unchecked-long 0xbf58476d1ce4e5b9))
(def ^:private mix-two (unchecked-long 0x94d049bb133111eb))

(defn seed [value]
  (when-not (and (integer? value) (<= Long/MIN_VALUE value Long/MAX_VALUE))
    (n/fail "Seed must fit signed 64 bits" {:seed value}))
  (long value))

(defn next-long [state]
  (let [state (unchecked-add (long state) (long golden))
        z (unchecked-multiply (bit-xor state (unsigned-bit-shift-right state 30)) (long mix-one))
        z (unchecked-multiply (bit-xor z (unsigned-bit-shift-right z 27)) (long mix-two))]
    [(bit-xor z (unsigned-bit-shift-right z 31)) state]))

(defn uniform
  "Open interval (0,1), using 52 random bits and representable half-bin centers."
  [state]
  (let [[bits state] (next-long state)]
    [(/ (+ 0.5 (double (unsigned-bit-shift-right bits 12))) 4503599627370496.0) state]))

(defn split [state stream-id]
  (n/natural! :stream-id stream-id)
  (first (next-long (unchecked-add (long state) (unchecked-multiply (long stream-id) (long golden))))))

(defn exponential [state rate]
  (n/positive! :rate rate)
  (let [[u state] (uniform state)] [(/ (- (Math/log u)) rate) state]))

(defn normal
  ([state] (normal state 0.0 1.0))
  ([state mean sigma]
   (n/finite! :mean mean) (n/positive! :sigma sigma)
   (let [[u state] (uniform state) [v state] (uniform state)]
     [(+ mean (* sigma (Math/sqrt (* -2 (Math/log u))) (Math/cos (* 2 Math/PI v)))) state])))

(defn categorical [state weights]
  (let [weights (mapv #(n/nonnegative! :weight %) weights) total (n/sum weights)]
    (when-not (and (seq weights) (pos? total) (n/finite? total))
      (n/fail "Categorical weights need positive finite mass" {}))
    (let [[u state] (uniform state) target (* u total)]
      (loop [i 0 cumulative (weights 0)]
        (if (or (< target cumulative) (= i (dec (count weights)))) [i state]
            (recur (inc i) (+ cumulative (weights (inc i)))))))))

(defn poisson
  "Knuth for small means, Hormann transformed rejection for large means."
  [state mean]
  (let [mean (n/nonnegative! :mean mean)]
    (when (> mean 1e12) (n/fail "Poisson sampler mean exceeds supported range" {}))
    (cond
      (zero? mean) [0 state]
      (< mean 30)
      (let [limit (Math/exp (- mean))]
        (loop [state state product 1.0 k 0]
          (let [[u state] (uniform state) product (* product u)]
            (if (<= product limit) [k state] (recur state product (inc k))))))
      :else
      (let [b (+ 0.931 (* 2.53 (Math/sqrt mean))) a (+ -0.059 (* 0.02483 b))
            inv-alpha (+ 1.1239 (/ 1.1328 (- b 3.4))) vr (- 0.9277 (/ 3.6224 (- b 2.0)))]
        (loop [state state attempts 0]
          (when (> attempts 100000) (n/fail "Poisson rejection budget exhausted" {}))
          (let [[u state] (uniform state) u (- u 0.5)
                [v state] (uniform state) us (- 0.5 (abs u))
                k (long (Math/floor (+ (* (+ (/ (* 2 a) us) b) u) mean 0.43)))]
            (cond
              (or (neg? k) (and (< us 0.013) (> v us))) (recur state (inc attempts))
              (and (>= us 0.07) (<= v vr)) [k state]
              (<= (Math/log (/ (* v inv-alpha) (+ (/ a (* us us)) b)))
                  (p/poisson-log-pmf k mean)) [k state]
              :else (recur state (inc attempts)))))))))

(defn binomial [state trials probability]
  (n/natural! :trials trials)
  (let [probability (n/clamp-probability probability)]
    (when (> trials 10000000) (n/fail "Binomial sampler trial budget exceeded" {}))
    (loop [i 0 count 0 state state]
      (if (= i trials) [count state]
          (let [[u state] (uniform state)]
            (recur (inc i) (if (< u probability) (inc count) count) state))))))
