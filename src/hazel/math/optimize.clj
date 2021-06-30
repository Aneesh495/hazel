(ns hazel.math.optimize
  "Box-constrained inverse-BFGS with projected gradients and Armijo backtracking."
  (:require [hazel.math.numeric :as n]
            [hazel.math.linear :as l]))

(defn project [x bounds]
  (mapv (fn [x [lo hi]] (max lo (min hi x))) x bounds))

(defn projected-gradient [x gradient bounds]
  (mapv (fn [x g [lo hi]] (if (or (and (<= x lo) (pos? g)) (and (>= x hi) (neg? g))) 0.0 g))
        x gradient bounds))

(defn- evaluate [f x]
  (let [r (f x)]
    (n/finite! :objective (:value r))
    (n/same-size! x (:gradient r))
    (n/checked-result :objective-gradient (:gradient r)) r))

(defn minimize
  ([f initial bounds] (minimize f initial bounds {}))
  ([f initial bounds {:keys [max-iterations gradient-tolerance max-backtracks]
                      :or {max-iterations 200 gradient-tolerance 1e-7 max-backtracks 40}}]
   (n/same-size! initial bounds)
   (when (empty? initial) (n/fail "Optimizer needs at least one coordinate" {}))
   (doseq [[lo hi] bounds]
     (n/finite! :lower-bound lo) (n/finite! :upper-bound hi)
     (when-not (< lo hi) (n/fail "Optimizer bounds must have nonzero width" {})))
   (when-not (= (mapv double initial) (mapv double (project initial bounds)))
     (n/fail "Initial point outside optimizer bounds" {}))
   (n/natural! :max-iterations max-iterations) (n/positive! :gradient-tolerance gradient-tolerance)
   (let [size (count initial) eye (l/identity size)]
     (loop [x (mapv double initial) current (evaluate f initial) inverse eye iteration 0 history []]
       (let [gradient (:gradient current) pg (projected-gradient x gradient bounds)
             norm (n/l2 pg) history (conj history {:iteration iteration :value (:value current) :gradient-norm norm})
             result {:point x :objective (:value current) :gradient gradient :iterations iteration :history history}]
         (cond
           (<= norm gradient-tolerance) (assoc result :status :converged)
           (= iteration max-iterations) (assoc result :status :iteration-budget-exhausted)
           :else
           (let [proposal (n/scale -1.0 (l/matvec inverse pg))
                 direction (if (< (n/dot proposal gradient) 0) proposal (n/scale -1.0 pg))
                 line (loop [alpha 1.0 attempt 0]
                        (if (= attempt max-backtracks) nil
                            (let [candidate (project (n/axpy alpha direction x) bounds)
                                  delta (mapv - candidate x)
                                  ;; Armijo must use the actual projected step,
                                  ;; not alpha*direction before clipping at bounds.
                                  slope (n/dot gradient delta)]
                              (if (>= slope 0) (recur (* 0.5 alpha) (inc attempt))
                                  (let [next (evaluate f candidate)]
                                    (if (<= (:value next) (+ (:value current) (* 1e-4 slope)))
                                      {:point candidate :result next :delta delta}
                                      (recur (* 0.5 alpha) (inc attempt))))))))]
             (if-not line (assoc result :status :line-search-failed)
                 (let [s (:delta line) next (:result line) y (mapv - (:gradient next) gradient)
                       curvature (n/dot y s)
                       ;; Positive curvature preserves a positive inverse metric.
                       ;; Reset rather than amplify a nearly singular update.
                       inverse (if (<= curvature (* 1e-12 (n/l2 y) (n/l2 s))) eye
                                   (let [rho (/ 1.0 curvature)
                                         left (l/add eye (l/scale (- rho) (l/outer s y)))
                                         right (l/add eye (l/scale (- rho) (l/outer y s)))]
                                     (l/add (l/multiply (l/multiply left inverse) right)
                                            (l/scale rho (l/outer s s)))))]
                   (recur (:point line) next inverse (inc iteration) history))))))))))
