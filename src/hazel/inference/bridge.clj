(ns hazel.inference.bridge
  "Continuous-time population bridges by endpoint-conditioned uniformization.

  Draw the number of virtual Poisson events, sample the embedded-chain bridge,
  then assign sorted uniform event times. Virtual self transitions are discarded.
  State-preserving chemical reaction events are not reconstructed by this API."
  (:require [hazel.ctmc.generator :as g]
            [hazel.ctmc.sparse :as sparse]
            [hazel.ctmc.uniformization :as u]
            [hazel.math.probability :as probability]
            [hazel.math.numeric :as n]
            [hazel.simulation.random :as random]))

(defn- embedded-probability [q lambda from to]
  (if (= from to) (- 1.0 (/ (get (:exits q) from) lambda))
      (/ (g/rate q from to) lambda)))

(defn sample
  ([q start end time seed] (sample q start end time seed {}))
  ([q start end time seed {:keys [relative-tail max-virtual-events]
                           :or {relative-tail 1e-10 max-virtual-events 10000}}]
   (n/nonnegative! :time time) (n/tolerance! relative-tail) (random/seed seed)
   (let [from (.indexOf ^java.util.List (:states q) start)
         to (.indexOf ^java.util.List (:states q) end)
         _ (when (or (neg? from) (neg? to)) (n/fail "Bridge endpoint outside projection" {}))
         kernel (sparse/compile-kernel q) lambda (:lambda kernel) mean (* lambda time)]
     (when (> mean 64) (n/fail "Bridge Poisson mean exceeds 64; insert intermediate endpoints" {}))
     (if (or (zero? time) (zero? lambda))
       (if (= from to)
         {:status :complete :kind :population-bridge :start start :end end :time time
          :seed seed :jumps [] :virtual-events 0 :endpoint-probability 1.0 :conditional-tail-bound 0.0}
         (n/fail "Bridge endpoints cannot connect at zero event intensity" {}))
       (let [initial (g/initial-vector q start)
             endpoint (get (:probabilities (u/propagate q initial time {:tolerance 1e-14})) to)
             tolerance (* relative-tail endpoint)
             _ (when-not (> tolerance Double/MIN_NORMAL)
                 (n/fail "Bridge endpoint probability is too small for reliable conditioning" {}))
             window (probability/poisson-window mean tolerance max-virtual-events)
             weights (:weights window)
             powers (vec (take (count weights) (iterate #(sparse/stochastic-product kernel %) initial)))
             conditional (mapv #(* %1 (get %2 to)) weights powers)
             ;; Endpoint weighting changes the Poisson event-count law. Drawing
             ;; an unconditioned count and then forcing its endpoint is biased.
             [count rng] (random/categorical seed conditional)
             [indexes rng]
             (loop [k count current to rng rng reversed [to]]
               (if (zero? k) [(vec (reverse reversed)) rng]
                   (let [weights (mapv (fn [i] (* (get (powers (dec k)) i)
                                                   (embedded-probability q lambda i current))) (range (:size q)))
                         [previous rng] (random/categorical rng weights)]
                     (recur (dec k) previous rng (conj reversed previous)))))
             [times rng] (loop [i 0 rng rng times []]
                           ;; Conditional on the count, Poisson event locations
                           ;; are ordered independent uniform times in the window.
                           (if (= i count) [(vec (sort times)) rng]
                               (let [[u rng] (random/uniform rng)]
                                 (recur (inc i) rng (conj times (* time u))))))
             jumps (vec (for [[t i j] (map vector times indexes (rest indexes)) :when (not= i j)]
                          {:time t :from (get (:states q) i) :to (get (:states q) j)
                           :from-index i :to-index j}))]
         {:status :complete :kind :population-bridge :start start :end end :time time :seed seed
          :rng-state rng :jumps jumps :virtual-events count :endpoint-probability endpoint
          :conditional-tail-bound (min 1.0 (/ (:tail-bound window) (n/sum conditional)))})))))

(defn validate [q bridge]
  (when-not (= :population-bridge (:kind bridge)) (n/fail "Expected population bridge" {}))
  (loop [state (:start bridge) time 0.0 jumps (:jumps bridge)]
    (if (empty? jumps)
      (do (when-not (= state (:end bridge)) (n/fail "Bridge final state mismatch" {})) {:status :valid})
      (let [{:keys [from to from-index to-index] :as jump} (first jumps)]
        (when-not (and (= state from) (= from (get (:states q) from-index))
                       (= to (get (:states q) to-index)) (< time (:time jump) (:time bridge))
                       (pos? (g/rate q from-index to-index)))
          (n/fail "Invalid population bridge jump" {:jump jump}))
        (recur to (:time jump) (rest jumps))))))
