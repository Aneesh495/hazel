(ns hazel.math.exponential
  "Independent dense scaling-and-squaring Taylor exponential for small oracles.
  This implementation uses matrix-matrix products, never uniformization weights."
  (:require [hazel.math.linear :as l]
            [hazel.math.numeric :as n]))

(defn expm
  ([a] (expm a {}))
  ([a {:keys [tolerance max-terms max-size] :or {tolerance 1e-15 max-terms 100 max-size 256}}]
   (l/matrix! a)
   (let [[rows cols] (l/shape a)]
     (when-not (and (= rows cols) (pos? rows) (<= rows max-size))
       (n/fail "Dense exponential requires a small square matrix" {:shape [rows cols] :limit max-size}))
     (let [norm (l/norm-one a)
           squarings (if (<= norm 0.5) 0
                         (long (Math/ceil (/ (Math/log (/ norm 0.5)) (Math/log 2.0)))))
           scaled (l/scale (/ 1.0 (Math/pow 2.0 squarings)) a)
           base (loop [k 1 term (l/identity rows) result (l/identity rows)]
                  (when (> k max-terms) (n/fail "Dense exponential series did not converge" {}))
                  (let [term (l/scale (/ 1.0 k) (l/multiply term scaled))
                        result (l/add result term)]
                    (if (< (l/norm-one term) (* tolerance (max 1.0 (l/norm-one result)))) result
                        (recur (inc k) term result))))]
       (loop [k 0 result base]
         (if (= k squarings) result
             (recur (inc k) (l/multiply result result))))))))

(defn action [a initial time]
  (n/nonnegative! :time time)
  (l/rowvec initial (expm (l/scale time a))))
