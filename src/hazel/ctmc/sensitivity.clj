(ns hazel.ctmc.sensitivity
  "Forward tangent equations on a fixed projection.
  Differentiate p'=pQ as s'=sQ+pQ_theta while holding the uniformization rate fixed."
  (:require [hazel.model.network :as net]
            [hazel.ctmc.generator :as g]
            [hazel.ctmc.sparse :as sparse]
            [hazel.math.probability :as probability]
            [hazel.math.numeric :as n]))

(defn derivative-generator [model states parameter]
  (when-not (contains? (:parameters model) parameter)
    (n/fail "Unknown sensitivity parameter" {:parameter parameter}))
  (let [source (-> (:source model)
                   (assoc :parameters {})
                   (update :reactions #(mapv (fn [r] (assoc r :rate (if (= parameter (:rate r)) 1 0))) %)))]
    (g/for-states (net/compile-model source) states)))

(defn- tangent-series [kernel derivatives p tangents weights lambda]
  (loop [weights weights p p tangents tangents
         out-p (vec (repeat (count p) 0.0))
         out-s (mapv #(vec (repeat (count %) 0.0)) tangents)]
    (let [weight (first weights)
          out-p (n/axpy weight p out-p)
          out-s (mapv #(n/axpy weight %1 %2) tangents out-s)]
      (if (empty? (next weights)) [out-p out-s]
          (let [next-s (mapv (fn [s derivative]
                               (n/axpy (/ 1.0 lambda)
                                       (sparse/generator-product derivative p)
                                       (sparse/stochastic-product kernel s lambda))) tangents derivatives)]
            (recur (next weights) (sparse/stochastic-product kernel p lambda) next-s out-p out-s))))))

(defn propagate
  ([model q initial parameters time] (propagate model q initial parameters time {}))
  ([model q initial parameters time {:keys [tolerance initial-sensitivities max-chunks]
                                    :or {tolerance 1e-13 max-chunks 100000}}]
   (n/nonnegative! :time time) (n/tolerance! tolerance)
   (let [initial (n/probability-vector! initial true)
         _ (n/same-size! (:rows q) initial)
         _ (when-not (= (count parameters) (count (set parameters)))
             (n/fail "Sensitivity parameters must be distinct" {}))
         kernel (sparse/compile-kernel q) lambda (:lambda kernel)
         derivatives (mapv #(sparse/compile-kernel (derivative-generator model (:states q) %)) parameters)
         norms (mapv #(* 2 (:lambda %)) derivatives)
         tangents (or initial-sensitivities (vec (repeat (count parameters) (vec (repeat (:size q) 0.0)))))]
     (when-not (= (count parameters) (count tangents)) (n/fail "Sensitivity dimension mismatch" {}))
     (doseq [s tangents] (n/same-size! initial s) (n/checked-result :sensitivity s))
     (if (zero? lambda)
       {:probabilities initial
        :sensitivities (zipmap parameters (mapv #(n/axpy time (sparse/generator-product %1 initial) %2)
                                               derivatives tangents))
        :probability-tail-bound 0.0 :sensitivity-tail-bounds (zipmap parameters (repeat 0.0))}
       (let [required (max 1.0 (Math/ceil (/ (* lambda time) 32.0)))]
         (when (> required max-chunks) (n/fail "Sensitivity chunk budget exceeded" {}))
         (let [chunks (long required) dt (/ time chunks) mean (* lambda dt)
               window (probability/poisson-window mean (/ tolerance chunks))
               weights (:weights window) tail (:tail-bound window)
               count-tail (* mean (+ (peek weights) tail))]
           (loop [i 0 p initial tangents tangents probability-error 0.0
                  errors (vec (repeat (count parameters) 0.0))]
             (if (= i chunks)
               {:probabilities p :sensitivities (zipmap parameters tangents)
                :probability-tail-bound probability-error
                :sensitivity-tail-bounds (zipmap parameters errors)}
               (let [next-errors (mapv (fn [error s norm]
                                         (+ error (* dt norm probability-error)
                                            (* (n/l1 s) tail) (* (/ norm lambda) count-tail))) errors tangents norms)
                     [p tangents] (tangent-series kernel derivatives p tangents weights lambda)]
                 (recur (inc i) p tangents (+ probability-error tail) next-errors))))))))))
