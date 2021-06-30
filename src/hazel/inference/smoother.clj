(ns hazel.inference.smoother
  "Sparse backward messages and posterior state bridges on observation grids."
  (:require [hazel.ctmc.sparse :as sparse]
            [hazel.math.probability :as probability]
            [hazel.math.numeric :as n]
            [hazel.simulation.random :as random]))

(defn backward-propagate
  "Column semigroup action exp(Q t) values. Values need not sum to one."
  [q values time]
  (n/nonnegative! :time time)
  (n/same-size! (:rows q) values)
  (doseq [v values] (n/nonnegative! :value v))
  (let [kernel (sparse/compile-kernel q) lambda (:lambda kernel)
        required (max 1.0 (Math/ceil (/ (* lambda time) 32.0)))]
    (when (> required 100000) (n/fail "Backward propagation chunk budget exceeded" {}))
    (if (zero? lambda) values
        (let [chunks (long required)
              weights (:weights (probability/poisson-window (/ (* lambda time) chunks) (/ 1e-13 chunks)))
              action (fn [v]
                       (mapv (fn [i row]
                               (+ (* (v i) (- 1.0 (/ (get (:exits q) i) lambda)))
                                  (n/sum (map (fn [[j rate]] (* (/ rate lambda) (v j))) row))))
                             (range) (:rows q)))]
          (loop [i 0 v values]
            (if (= i chunks) v
                (recur (inc i)
                       (loop [weights weights power v out (vec (repeat (:size q) 0.0))]
                         (let [out (n/axpy (first weights) power out)]
                           (if (empty? (next weights)) out
                               (recur (next weights) (action power) out)))))))))))

(defn- scaled-emission [log-emission]
  (let [maximum (apply max log-emission)]
    (when (= maximum ##-Inf) (n/fail "Every emission is impossible" {}))
    (mapv #(Math/exp (- % maximum)) log-emission)))

(defn smooth [q forward-result]
  (let [steps (:steps forward-result)]
    (loop [k (dec (count steps)) beta (vec (repeat (:size q) 1.0)) results []]
      (if (neg? k)
        {:steps (vec (reverse results)) :projection-conditioned? true
         :initial-smoothed (:probabilities (n/normalize (mapv * (:initial forward-result) beta)))}
        (let [step (steps k)
              smoothed (:probabilities (n/normalize (mapv * (:filtered step) beta)))
              weighted (mapv * (scaled-emission (:log-emission step)) beta)
              ;; Backward messages are columns: exp(Q*dt) acts from the left.
              ;; A row propagation here would reverse the transition convention.
              beta (backward-propagate q weighted (:dt step))
              beta (n/scale (/ 1.0 (apply max beta)) beta)]
          (recur (dec k) beta (conj results {:time (:time step) :probabilities smoothed})))))))

(defn sample-grid-bridge
  "Sample latent states at observation times, not a continuous reaction path."
  [q forward-result seed]
  (let [steps (:steps forward-result)]
    (when (empty? steps) (n/fail "Bridge requires at least one observation" {}))
    (let [[last-state rng] (random/categorical seed (:filtered (peek steps)))]
      (loop [k (- (count steps) 2) next-state last-state rng rng indexes [last-state]]
        (if (neg? k)
          {:seed seed :rng-state rng :kind :observation-grid-bridge
           :grid (mapv (fn [step i] {:time (:time step) :state (get (:states q) i) :index i})
                       steps (reverse indexes))}
          (let [target (assoc (vec (repeat (:size q) 0.0)) next-state 1.0)
                column (backward-propagate q target (:dt (steps (inc k))))
                ;; Conditioning on the next sampled state removes future data
                ;; from this predecessor draw; filtered mass supplies the past.
                weights (mapv * (:filtered (steps k)) column)
                [state rng] (random/categorical rng weights)]
            (recur (dec k) state rng (conj indexes state))))))))
