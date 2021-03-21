(ns hazel.inference.particle
  "Bootstrap particle filtering with adaptive systematic resampling.
  Each propagation draws new particle-indexed streams after resampling, preventing
  cloned ancestors from sharing identical future random numbers."
  (:refer-clojure :exclude [filter])
  (:require [hazel.inference.observation :as observation]
            [hazel.simulation.direct :as direct]
            [hazel.simulation.random :as random]
            [hazel.math.numeric :as n]
            [hazel.math.probability :as probability]))

(defn systematic-resample [rng weights]
  (let [weights (n/probability-vector! weights)
        count (count weights) [u rng] (random/uniform rng)]
    [(loop [i 0 ancestor 0 cumulative (weights 0) indexes []]
       (if (= i count) indexes
           (let [target (/ (+ i u) count)
                 [ancestor cumulative]
                 (loop [j ancestor c cumulative]
                   (if (or (< target c) (= j (dec count))) [j c]
                       (recur (inc j) (+ c (weights (inc j))))))]
             (recur (inc i) ancestor cumulative (conj indexes ancestor))))) rng]))

(defn filter
  ([model sensors observations count seed] (filter model sensors observations count seed {}))
  ([model sensors observations count seed {:keys [resample-threshold max-events]
                                          :or {resample-threshold 0.5 max-events 1000000}}]
   (n/natural! :particle-count count) (random/seed seed)
   (when-not (<= 2 count 100000) (n/fail "Particle count must be in 2..100000" {}))
   (when-not (<= 0 (n/finite! :resample-threshold resample-threshold) 1)
     (n/fail "Resampling threshold must be in [0,1]" {}))
   (observation/validate-observations sensors observations)
   (let [uniform (vec (repeat count (/ 1.0 count)))]
     (loop [observations observations time 0.0 particles (vec (repeat count (:initial model)))
            weights uniform rng seed log-likelihood 0.0 steps []]
       (if (empty? observations)
         {:status :complete :particles particles :weights weights :seed seed :rng-state rng
          :log-likelihood log-likelihood :steps steps :particle-count count}
         (let [measurement (first observations) dt (- (:time measurement) time)
               [step-seed rng] (random/next-long rng)
               paths (mapv (fn [i state]
                             (direct/simulate model dt (random/split step-seed i)
                                              {:initial state :record? false :max-events max-events}))
                           (range count) particles)]
           (if (some #(not= :complete (:status %)) paths)
             {:status :event-budget-exhausted :log-likelihood nil :steps steps :seed seed}
             (let [predicted (mapv :final-state paths)
                   log-weights (mapv (fn [state weight]
                                       (if (zero? weight) ##-Inf
                                           (+ (Math/log weight) (observation/log-likelihood sensors state measurement))))
                                     predicted weights)
                   log-z (n/log-sum-exp log-weights)]
               (if (= log-z ##-Inf)
                 {:status :zero-evidence :log-likelihood nil :steps steps :seed seed}
                 (let [weights (mapv #(Math/exp (- % log-z)) log-weights)
                       ess (probability/effective-sample-size weights)
                       resample? (< ess (* resample-threshold count))
                       [ancestors rng] (if resample? (systematic-resample rng weights) [(vec (range count)) rng])
                       step {:time (:time measurement) :ess ess :resampled? resample?
                             :log-increment log-z :ancestors ancestors
                             :particles predicted :weights weights}]
                   (recur (rest observations) (:time measurement) (mapv predicted ancestors)
                          (if resample? uniform weights) rng (+ log-likelihood log-z) (conj steps step))))))))))))

(defn expectation [result observable]
  (when-not (= :complete (:status result)) (n/fail "Particle filter is incomplete" {}))
  (n/sum (map #(* %2 (observable %1)) (:particles result) (:weights result))))
