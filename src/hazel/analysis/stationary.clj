(ns hazel.analysis.stationary
  "Closed-class stationary laws. A truncated chain is never silently reflected."
  (:require [hazel.math.graph :as graph]
            [hazel.math.linear :as l]
            [hazel.math.numeric :as n]
            [hazel.ctmc.generator :as g]
            [hazel.ctmc.sparse :as sparse]
            [hazel.analysis.backward :as backward]))

(defn classes [q]
  (let [graph (backward/adjacency q)
        real (dissoc graph (:sink q))]
    {:strong (graph/strongly-connected real)
     :closed (filterv #(not (contains? % (:sink q))) (graph/closed-components graph))}))

(defn solve
  ([q] (solve q {}))
  ([q {:keys [max-class-size] :or {max-class-size 400}}]
   (g/validate q)
   (when (some (fn [row] (some #(= (:sink q) (first %)) row)) (take (:sink q) (:rows q)))
     (n/fail "Stationary analysis requires a closed state space; projection escape is present" {}))
   (let [components (:closed (classes q)) kernel (sparse/compile-kernel q)]
     {:unique? (= 1 (count components))
      :classes
      (mapv (fn [component]
              (let [indexes (vec (sort component)) size (count indexes)]
                (when (> size max-class-size)
                  (n/fail "Stationary class exceeds solve budget" {:size size}))
                (let [a (mapv (fn [j] (mapv #(double (g/rate q % j)) indexes)) indexes)
                      a (assoc a (dec size) (vec (repeat size 1.0)))
                      b (assoc (vec (repeat size 0.0)) (dec size) 1.0)
                      pi (mapv n/clamp-probability (l/solve-scaled a b))
                      full (reduce (fn [v [i x]] (assoc v i x))
                                   (vec (repeat (:size q) 0.0)) (map vector indexes pi))]
                  {:states indexes :probabilities full
                   :stationarity-residual (n/l1 (sparse/generator-product kernel full))
                   :normalization-residual (abs (- 1.0 (n/sum full)))}))) components)})))

(defn detailed-balance-residual [q pi]
  (n/probability-vector! pi) (n/same-size! (:rows q) pi)
  (reduce max 0.0
          (for [[i row] (map-indexed vector (:rows q)) [j rate] row]
            (abs (- (* (pi i) rate) (* (pi j) (g/rate q j i)))))))

(defn entropy-production
  "Stationary entropy production. Returns :irreversible for one-way positive flux."
  [q pi]
  (let [terms (for [[i row] (map-indexed vector (:rows q)) [j rate] row
                    :let [forward (* (pi i) rate) reverse (* (pi j) (g/rate q j i))]
                    :when (pos? forward)]
                (if (zero? reverse) :irreversible (* forward (Math/log (/ forward reverse)))))]
    (if (some #{:irreversible} terms) {:status :irreversible :value nil}
        {:status :finite :value (n/sum terms)})))
