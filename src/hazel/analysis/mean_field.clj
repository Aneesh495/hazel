(ns hazel.analysis.mean-field
  "Macroscopic mass-action approximation and linear-noise covariance equations.
  Nonlinear macroscopic hazards use x^s/s!, not discrete falling factorials."
  (:require [hazel.math.numeric :as n]
            [hazel.math.linear :as l]
            [hazel.math.ode :as ode]
            [hazel.math.probability :as probability]))

(defn- factorial [k] (reduce *' 1N (range 1 (inc k))))

(defn macroscopic-rate [reaction state]
  (* (double (:constant reaction))
     (reduce * 1.0 (map #(/ (Math/pow (double %1) %2) (double (factorial %2))) state (:consume reaction)))))

(defn rate-gradient [reaction state]
  (mapv (fn [j]
          (let [order (get (:consume reaction) j)]
            (if (zero? order) 0.0
                (* (double (:constant reaction)) order
                   (reduce * 1.0
                           (map-indexed (fn [i x]
                                          (let [s (get (:consume reaction) i)]
                                            (/ (Math/pow (double x) (if (= i j) (dec s) s))
                                               (double (factorial s))))) state))))))
        (range (count state))))

(defn drift [model state]
  (reduce n/add (vec (repeat (count state) 0.0))
          (map #(n/scale (macroscopic-rate % state) (:delta %)) (:reactions model))))

(defn jacobian [model state]
  (reduce l/add (l/zeros (count state) (count state))
          (map #(l/outer (:delta %) (rate-gradient % state)) (:reactions model))))

(defn diffusion [model state]
  (reduce l/add (l/zeros (count state) (count state))
          (map #(l/scale (macroscopic-rate % state) (l/outer (:delta %) (:delta %))) (:reactions model))))

(defn solve
  ([model time] (solve model time {}))
  ([model time options]
   (let [initial (mapv double (:initial model))
         r (ode/integrate (fn [_ state] (drift model state)) initial time options)]
     (assoc r :mean (:state r) :approximation :macroscopic))))

(defn linear-noise
  ([model time] (linear-noise model time {}))
  ([model time options]
   (let [size (count (:species model))
         initial (into (mapv double (:initial model)) (repeat (* size size) 0.0))
         f (fn [_ state]
             (let [mean (subvec state 0 size) covariance (mapv vec (partition size (subvec state size)))
                   j (jacobian model mean)
                   dc (-> (l/multiply j covariance)
                          (l/add (l/multiply covariance (l/transpose j)))
                          (l/add (diffusion model mean)))]
               (into (drift model mean) (mapcat identity dc))))
         r (ode/integrate f initial time options)]
     (assoc r :mean (subvec (:state r) 0 size)
              :covariance (mapv vec (partition size (subvec (:state r) size)))
              :approximation :linear-noise))))
