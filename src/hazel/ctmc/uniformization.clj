(ns hazel.ctmc.uniformization
  "Transient CTMC propagation by a nonnegative Poisson series.

  Time chunking keeps Poisson means small enough to start at exp(-mean).
  Probability is not renormalized: both escape and omitted series mass remain
  observable. The analytic tail bound excludes binary64 roundoff."
  (:require [hazel.ctmc.sparse :as sparse]
            [hazel.ctmc.generator :as generator]
            [hazel.math.probability :as probability]
            [hazel.math.numeric :as n]))

(defn series
  "Weighted powers of the uniformized chain, including the zeroth power."
  [kernel initial weights lambda]
  (loop [weights weights power initial result (vec (repeat (count initial) 0.0))]
    (if (empty? weights) result
        (let [result (n/axpy (first weights) power result)]
          (if (empty? (next weights)) result
              (recur (next weights) (sparse/stochastic-product kernel power lambda) result))))))

(defn propagate-kernel
  ([kernel initial time] (propagate-kernel kernel initial time {}))
  ([kernel initial time {:keys [tolerance max-chunks max-terms lambda]
                        :or {tolerance 1e-12 max-chunks 100000 max-terms 10000}}]
   (sparse/dimensions! kernel initial)
   (let [initial (n/probability-vector! initial true)
         time (n/nonnegative! :time time) tolerance (n/tolerance! tolerance)
         lambda (n/nonnegative! :lambda (or lambda (:lambda kernel)))
         product (* lambda time)]
     (when (or (< lambda (:lambda kernel)) (not (n/finite? product)))
       (n/fail "Invalid uniformization rate or time product" {}))
     (n/natural! :max-chunks max-chunks)
     (let [required (max 1.0 (Math/ceil (/ product 32.0)))]
       (when (> required max-chunks)
         (n/fail "Uniformization chunk budget exhausted" {:required required :budget max-chunks}))
       (let [chunks (long required) mean (/ product chunks)
             window (probability/poisson-window mean (/ tolerance chunks) max-terms)
             weights (:weights window)
             p (loop [i 0 p initial]
                 (if (= i chunks) p
                     (recur (inc i) (series kernel p weights lambda))))
             mass (n/sum p) initial-mass (n/sum initial)
             expected (* initial-mass (Math/pow (:weight-sum window) chunks))
             tail (* initial-mass chunks (:tail-bound window))]
         (n/checked-result :transient-probability p)
         (when (some #(< % -1e-14) p) (n/fail "Uniformization produced negative mass" {}))
         {:status :ok :probabilities p :time time :chunks chunks
          :terms-per-chunk (:terms window) :matrix-products (* chunks (dec (:terms window)))
          :series-tail-bound (min initial-mass tail)
          :mass mass :mass-defect (- initial-mass mass)
          :floating-point-residual (abs (- mass expected)) :lambda lambda})))))

(defn propagate
  ([q initial time] (propagate q initial time {}))
  ([q initial time options]
   (generator/validate q)
   (let [result (propagate-kernel (sparse/compile-kernel q) initial time options)
         escape (get (:probabilities result) (:sink q))]
     (assoc result :escape-probability escape :retained-probability (- (:mass result) escape)
                   :truncation-bound (min 1.0 (+ escape (:series-tail-bound result)))))))

(defn solve
  ([q state time] (solve q state time {}))
  ([q state time options]
   (propagate q (generator/initial-vector q state) time options)))

(defn trajectory
  "One incremental propagation per observation interval, with cumulative tails."
  ([q initial times] (trajectory q initial times {}))
  ([q initial times options]
   (doseq [t times] (n/nonnegative! :time t))
   (when-not (apply <= 0 times) (n/fail "Times must be nondecreasing" {}))
   (let [tolerance (get options :tolerance 1e-12)
         options (assoc options :tolerance (/ tolerance (max 1 (count times))))]
     (loop [times times previous 0.0 p initial tail 0.0 results []]
       (if (empty? times) results
           (let [t (first times) result (propagate q p (- t previous) options)
                 tail (+ tail (:series-tail-bound result))]
             (recur (rest times) t (:probabilities result) tail
                    (conj results (assoc result :time t :series-tail-bound tail)))))))))
