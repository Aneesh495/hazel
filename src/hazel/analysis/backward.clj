(ns hazel.analysis.backward
  "Backward equations for finite projections. Escape acts as a failure boundary."
  (:require [hazel.math.graph :as graph]
            [hazel.math.linear :as l]
            [hazel.math.numeric :as n]
            [hazel.ctmc.generator :as g]
            [hazel.analysis.first-passage :as fp]))

(defn adjacency [q]
  (into {} (map-indexed (fn [i row] [i (set (map first row))]) (:rows q))))

(defn hitting
  "Return eventual target probabilities and conditional mean hitting times.
  A nil unconditional mean denotes a positive probability of never hitting."
  ([q predicate] (hitting q predicate {}))
  ([q predicate {:keys [max-size] :or {max-size 400}}]
   (g/validate q)
   (when (> (:size q) max-size)
     (n/fail "Backward dense solve exceeds size budget" {:size (:size q) :limit max-size}))
   (let [targets (fp/target-indexes q predicate)
         reverse (graph/reverse-graph (adjacency q))
         can-hit (reduce into #{} (map #(graph/reachable reverse %) targets))
         unknown (vec (sort (remove targets can-hit)))
         ;; States unable to reach the target are failure boundaries, including
         ;; escape and closed non-target classes. Removing them avoids a singular
         ;; all-state system without pretending their hit probability is one.
         a (mapv (fn [i] (mapv #(- (double (g/rate q i %))) unknown)) unknown)
         b (mapv (fn [i] (n/sum (map #(g/rate q i %) targets))) unknown)
         solution (if (empty? unknown) [] (l/solve-scaled a b))
         h (reduce (fn [h [i p]] (assoc h i (n/clamp-probability p)))
                   (reduce #(assoc %1 %2 1.0) (vec (repeat (:size q) 0.0)) targets)
                   (map vector unknown solution))
         weighted-times (if (empty? unknown) [] (l/solve-scaled a (mapv h unknown)))
         ;; g=E[T*1_hit] solves -Q*g=h. Dividing by h gives conditional time;
         ;; an unconditional finite mean is justified only when h is one.
         conditional (reduce (fn [t [i value]]
                               (assoc t i (when (pos? (h i)) (/ value (h i)))))
                             (reduce #(assoc %1 %2 0.0) (vec (repeat (:size q) nil)) targets)
                             (map vector unknown weighted-times))]
     {:hit-probabilities h :conditional-mean-times conditional
      :mean-times (mapv #(when (n/close? %1 1.0 1e-10 0) %2) h conditional)
      :targets targets :unknown-count (count unknown)
      :equation-residual (if (empty? unknown) 0.0 (n/distance (l/matvec a solution) b))})))
