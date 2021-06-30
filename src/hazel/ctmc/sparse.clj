(ns hazel.ctmc.sparse
  "Private primitive arrays accelerate products; public results remain persistent.
  Kernels are read-only after construction and can be shared across threads."
  (:require [hazel.ctmc.generator :as generator]
            [hazel.math.numeric :as n]))

(defn compile-kernel [q]
  (generator/validate q)
  (let [rows (:rows q) size (:size q)
        offsets (int-array (reductions + 0 (map count rows)))]
    {:size size :offsets offsets
     :targets (int-array (map first (mapcat identity rows)))
     :rates (double-array (map second (mapcat identity rows)))
     :exits (double-array (:exits q))
     :lambda (apply max 0.0 (map double (:exits q)))}))

(defn dimensions! [kernel vector]
  (when-not (= (:size kernel) (count vector))
    (n/fail "Sparse product dimension mismatch" {:expected (:size kernel) :actual (count vector)})))

(defn generator-product
  "Row vector times Q. Signed vectors are supported for tangent equations."
  [kernel p]
  ;; All contributions to a destination add into one invocation-local buffer.
  ;; Shared kernel arrays are never changed by a forward or backward action.
  (dimensions! kernel p)
  (let [size (int (:size kernel))
        ^ints offsets (:offsets kernel) ^ints targets (:targets kernel)
        ^doubles rates (:rates kernel) ^doubles exits (:exits kernel)
        ^doubles input (double-array p) output (double-array size)]
    (dotimes [i size]
      (let [value (aget input i)]
        (aset-double output i (- (aget output i) (* value (aget exits i))))
        (loop [edge (aget offsets i)]
          (when (< edge (aget offsets (inc i)))
            (let [j (aget targets edge)]
              (aset-double output j (+ (aget output j) (* value (aget rates edge)))))
            (recur (inc edge))))))
    (vec output)))

(defn stochastic-product
  "Row vector times I+Q/lambda, without subtractive cancellation on the diagonal."
  ([kernel p] (stochastic-product kernel p (:lambda kernel)))
  ([kernel p lambda]
   (dimensions! kernel p)
   (n/nonnegative! :uniformization-rate lambda)
   (when (< lambda (:lambda kernel))
     (n/fail "Uniformization rate is below a generator exit rate" {}))
   (if (zero? lambda) (mapv double p)
       ;; Evaluate the stochastic diagonal directly. Forming p+pQ/lambda
       ;; instead can cancel most of a dominant component before adding flux.
       (let [size (int (:size kernel))
             ^ints offsets (:offsets kernel) ^ints targets (:targets kernel)
             ^doubles rates (:rates kernel) ^doubles exits (:exits kernel)
             ^doubles input (double-array p) output (double-array size)
             inv (/ 1.0 (double lambda))]
         (dotimes [i size]
           (let [value (aget input i)]
             (aset-double output i (+ (aget output i) (* value (- 1.0 (* inv (aget exits i))))))
             (loop [edge (aget offsets i)]
               (when (< edge (aget offsets (inc i)))
                 (let [j (aget targets edge)]
                   (aset-double output j (+ (aget output j) (* value inv (aget rates edge)))))
                 (recur (inc edge))))))
         (vec output)))))

(defn backward-product
  "Q times a column value function."
  [kernel values]
  (dimensions! kernel values)
  (let [^ints offsets (:offsets kernel) ^ints targets (:targets kernel)
        ^doubles rates (:rates kernel) ^doubles exits (:exits kernel)
        ^doubles input (double-array values)]
    (mapv (fn [i]
            (+ (- (* (aget exits i) (aget input i)))
               (loop [edge (aget offsets i) value 0.0]
                 (if (= edge (aget offsets (inc i))) value
                     (recur (inc edge) (+ value (* (aget rates edge) (aget input (aget targets edge)))))))))
          (range (:size kernel)))))
