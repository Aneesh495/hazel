(ns hazel.math.linear
  "Dense reference algebra. Exact rationals are preserved unless tolerance is set.
  Sparse Markov products never route through this namespace."
  (:refer-clojure :exclude [identity])
  (:require [hazel.math.numeric :as n]))

(defn matrix! [a]
  (when-not (and (vector? a) (every? vector? a)
                 (or (empty? a) (apply = (map count a))))
    (n/fail "Expected rectangular vector matrix" {}))
  (doseq [x (mapcat clojure.core/identity a)] (n/finite! :matrix-entry x))
  a)

(defn shape [a] [(count a) (if (seq a) (count (first a)) 0)])
(defn zeros [rows cols] (vec (repeat rows (vec (repeat cols 0)))))
(defn identity [size]
  (mapv (fn [i] (mapv #(if (= i %) 1 0) (range size))) (range size)))

(defn transpose [a]
  (matrix! a)
  (if (seq a) (apply mapv vector a) []))

(defn add [a b]
  (when-not (= (shape a) (shape b)) (n/fail "Matrix dimension mismatch" {}))
  (mapv #(mapv + %1 %2) a b))

(defn scale [c a] (mapv #(mapv (partial * c) %) a))

(defn multiply [a b]
  (matrix! a) (matrix! b)
  (let [[ar ac] (shape a) [br bc] (shape b) bt (transpose b)]
    (when-not (= ac br) (n/fail "Matrix multiplication dimension mismatch" {}))
    (mapv (fn [i]
            (mapv (fn [j] (reduce + 0 (map * (a i) (bt j)))) (range bc)))
          (range ar))))

(defn matvec [a x]
  (matrix! a)
  (mapv (fn [row] (n/same-size! row x) (reduce + 0 (map * row x))) a))

(defn rowvec [x a] (matvec (transpose a) x))

(defn rref
  "Gauss-Jordan with maximum-magnitude pivots. eps=0 is exact arithmetic.
  Optional pivot-columns prevents an augmented RHS becoming a coefficient pivot."
  ([a] (rref a {}))
  ([a {:keys [eps pivot-columns] :or {eps 0}}]
   (matrix! a)
   (let [[rows cols] (shape a) limit (or pivot-columns cols)]
     (loop [a a row 0 col 0 pivots []]
       (if (or (= row rows) (= col limit))
         {:matrix a :pivots pivots :rank (count pivots)}
         (let [candidate (apply max-key #(abs (get-in a [% col])) (range row rows))
               p (get-in a [candidate col])]
           (if (<= (abs p) eps)
             (recur a row (inc col) pivots)
             (let [a (assoc a row (a candidate) candidate (a row))
                   pivot (get-in a [row col])
                   normalized (mapv #(/ % pivot) (a row))
                   a (assoc a row normalized)
                   a (mapv (fn [i]
                             (if (= i row) normalized
                                 (let [f (get-in a [i col])]
                                   (mapv #(- %1 (* f %2)) (a i) normalized))))
                           (range rows))]
               (recur a (inc row) (inc col) (conj pivots col))))))))))

(defn rank [a] (:rank (rref a)))

(defn nullspace
  "Column-vector basis for the kernel. Supply columns for a zero-row matrix."
  ([a] (nullspace a (second (shape a))))
  ([a columns]
   (matrix! a)
   (let [{:keys [matrix pivots]} (rref a)
         free (remove (set pivots) (range columns))]
     (mapv (fn [f]
             (reduce (fn [v [row p]] (assoc v p (- (get-in matrix [row f]))))
                     (assoc (vec (repeat columns 0)) f 1)
                     (map-indexed vector pivots))) free))))

(defn solve
  "Solve a square nonsingular system. Exact by default, numeric eps optional."
  ([a b] (solve a b 0))
  ([a b eps]
   (matrix! a)
   (let [[rows cols] (shape a)]
     (when-not (and (= rows cols) (= rows (count b)))
       (n/fail "solve requires a square matrix and matching RHS" {}))
     (let [{:keys [matrix rank]} (rref (mapv conj a b) {:eps eps :pivot-columns cols})]
       (when-not (= rank cols) (n/fail "Singular linear system" {:rank rank :size cols}))
       (mapv peek matrix)))))

(defn inverse [a]
  (let [size (count a)]
    (transpose (mapv #(solve a %) (identity size)))))

(defn determinant [a]
  (matrix! a)
  (let [[rows cols] (shape a)]
    (when-not (= rows cols) (n/fail "Determinant requires square matrix" {}))
    (loop [a a k 0 product 1]
      (if (= k rows) product
          (let [p (apply max-key #(abs (get-in a [% k])) (range k rows))
                value (get-in a [p k])]
            (if (zero? value) 0
                (let [a (assoc a k (a p) p (a k))
                      a (reduce (fn [a i]
                                  (let [factor (/ (get-in a [i k]) value)]
                                    (assoc a i (mapv #(- %1 (* factor %2)) (a i) (a k)))))
                                a (range (inc k) rows))]
                  (recur a (inc k) (* product value (if (= p k) 1 -1))))))))))

(defn norm-one [a]
  (if (empty? a) 0.0 (apply max 0.0 (map #(n/sum (map abs %)) (transpose a)))))

(defn quadratic [x a] (n/dot x (matvec a x)))

(defn outer [a b] (mapv (fn [x] (mapv #(* x %) b)) a))
