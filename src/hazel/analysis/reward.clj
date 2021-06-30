(ns hazel.analysis.reward
  "First two moments of additive Markov rewards and reaction event counts.
  Moment equations use adaptive ODE integration, not Poisson error bounds."
  (:require [hazel.ctmc.sparse :as sparse]
            [hazel.math.ode :as ode]
            [hazel.math.numeric :as n]))

(defn- solve-moments [q initial time first-action second-action options interpretation]
  (let [size (:size q) kernel (sparse/compile-kernel q)
        _ (n/probability-vector! initial)
        _ (n/same-size! (:rows q) initial)
        initial (into (vec initial) (repeat (* 2 size) 0.0))
        f (fn [_ state]
            (let [p (subvec state 0 size) m (subvec state size (* 2 size)) s (subvec state (* 2 size))
                  dp (sparse/generator-product kernel p)
                  dm (n/add (sparse/generator-product kernel m) (first-action p))
                  ;; Differentiating a tilted generator twice gives 2*m*B+p*C.
                  ;; Event counts have C=B; continuous occupation rewards have C=0.
                  ds (-> (n/axpy 2.0 (first-action m) (sparse/generator-product kernel s))
                         (n/add (second-action p)))]
              (into (into dp dm) ds)))
        r (ode/integrate f initial time options)
        means (subvec (:state r) size (* 2 size))
        seconds (subvec (:state r) (* 2 size))
        mean (n/sum means) raw-second (n/sum seconds)
        variance (- raw-second (* mean mean))]
    (when (< variance -1e-7) (n/fail "Reward moment variance is negative beyond rounding tolerance" {:variance variance}))
    {:status (:status r) :time (:time r) :mean mean :second-moment raw-second
     :variance (max 0.0 variance) :state-weighted-means means :state-weighted-second-moments seconds
     :steps (:steps r) :approximation :adaptive-rk4 :interpretation interpretation}))

(defn occupancy
  [q initial time observable options]
  (let [rewards (conj (mapv #(n/nonnegative! :reward (observable %)) (:states q)) 0.0)
        action #(mapv * % rewards)
        zero-action (fn [_] (vec (repeat (:size q) 0.0)))]
    (solve-moments q initial time action zero-action options :reward-until-projection-escape)))

(defn reaction-count
  "Counts include state-preserving reactions and the event that exits a projection."
  [q initial time reaction-id options]
  (let [events (:events q)
        ;; Original labeled edges include real self events discarded from Q.
        ;; Their destination still matters for state-weighted count moments.
        action (fn [p]
                 (let [out (double-array (:size q)) sink (:sink q)]
                   (doseq [[i row] (map-indexed vector events)
                           {:keys [id rate destination]} row :when (= id reaction-id)]
                     (let [j (if (= destination :escape) sink destination)]
                       (aset-double out j (+ (aget out j) (* (p i) rate)))))
                   (vec out)))]
    (solve-moments q initial time action action options :count-until-projection-escape)))
