(ns hazel.math.symmetric
  "Jacobi diagonalization for small real symmetric matrices."
  (:require [hazel.math.linear :as l]
            [hazel.math.numeric :as n]))

(defn eigen
  ([a] (eigen a {}))
  ([a {:keys [tolerance max-rotations] :or {tolerance 1e-13}}]
   (l/matrix! a)
   (let [[size cols] (l/shape a)]
     (when-not (and (pos? size) (= size cols) (<= size 128))
       (n/fail "Symmetric eigensolver requires square size 1..128" {}))
     (doseq [i (range size) j (range size)]
       (when-not (n/close? (get-in a [i j]) (get-in a [j i]) 1e-12 1e-12)
         (n/fail "Matrix is not symmetric" {})))
     (let [budget (or max-rotations (* 100 size size)) threshold (* tolerance (max 1.0 (l/norm-one a)))]
       (loop [a (mapv #(mapv double %) a) v (l/identity size) count 0]
         (let [[p q magnitude] (reduce (fn [best [i j]]
                                         (let [m (abs (get-in a [i j]))] (if (> m (nth best 2)) [i j m] best)))
                                       [0 0 0.0] (for [i (range size) j (range (inc i) size)] [i j]))]
           (cond
             (<= magnitude threshold)
             (let [order (sort-by #(get-in a [% %]) (range size))]
               {:values (mapv #(get-in a [% %]) order)
                :vectors (mapv (fn [row] (mapv row order)) v) :rotations count :residual magnitude})
             (= count budget) (n/fail "Symmetric eigensolver rotation budget exhausted" {})
             :else
             (let [app (get-in a [p p]) aqq (get-in a [q q]) apq (get-in a [p q])
                   tau (/ (- aqq app) (* 2 apq))
                   t (/ (if (neg? tau) -1.0 1.0) (+ (abs tau) (Math/hypot 1.0 tau)))
                   c (/ 1.0 (Math/sqrt (+ 1 (* t t)))) s (* t c)
                   a (reduce (fn [a k]
                               (if (or (= k p) (= k q)) a
                                   (let [akp (get-in a [k p]) akq (get-in a [k q])
                                         vp (- (* c akp) (* s akq)) vq (+ (* s akp) (* c akq))]
                                     (-> a (assoc-in [k p] vp) (assoc-in [p k] vp)
                                         (assoc-in [k q] vq) (assoc-in [q k] vq))))) a (range size))
                   a (-> a (assoc-in [p p] (- app (* t apq))) (assoc-in [q q] (+ aqq (* t apq)))
                         (assoc-in [p q] 0.0) (assoc-in [q p] 0.0))
                   v (mapv (fn [row]
                             (let [vp (row p) vq (row q)]
                               (assoc row p (- (* c vp) (* s vq)) q (+ (* s vp) (* c vq))))) v)]
               (recur a v (inc count))))))))))
