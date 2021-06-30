(ns hazel.math.numeric
  "Small numerical contracts shared by probability algorithms.

  Every approximation takes finite binary64 inputs. Exact integer and rational
  algebra lives in hazel.math.linear and is deliberately kept separate.")

(defn fail
  ([message] (fail message {}))
  ([message data] (throw (ex-info message (assoc data :hazel/error true)))))

(defn finite? [x]
  (and (number? x) (Double/isFinite (double x))))

(defn finite! [label x]
  (when-not (finite? x) (fail "Expected finite number" {:field label :value x}))
  (double x))

(defn nonnegative! [label x]
  (let [v (finite! label x)]
    (when (neg? v) (fail "Expected nonnegative number" {:field label :value x}))
    v))

(defn positive! [label x]
  (let [v (finite! label x)]
    (when-not (pos? v) (fail "Expected positive number" {:field label :value x}))
    v))

(defn natural! [label x]
  (when-not (and (integer? x) (<= 0 x Long/MAX_VALUE))
    (fail "Expected nonnegative machine integer" {:field label :value x}))
  (long x))

(defn tolerance! [x]
  (let [x (positive! :tolerance x)]
    (when-not (< x 1) (fail "Tolerance must be below one" {:value x}))
    x))

(defn same-size! [xs ys]
  (when-not (= (count xs) (count ys))
    (fail "Dimension mismatch" {:left (count xs) :right (count ys)})))

(defn sum
  "Neumaier compensated sum, including mixed-magnitude and signed inputs."
  [xs]
  ;; Recover the low-order part lost by the addition. Neumaier's magnitude
  ;; branch also handles a new summand larger than the running total.
  (let [[s c] (reduce (fn [[s c] x]
                        (let [x (double x) t (+ s x)]
                          [t (+ c (if (>= (abs s) (abs x))
                                    (+ (- s t) x)
                                    (+ (- x t) s)))]))
                      [0.0 0.0] xs)]
    (+ s c)))

(defn dot [xs ys]
  (same-size! xs ys)
  (sum (map * xs ys)))

(defn l1 [xs] (sum (map abs xs)))
(defn l2 [xs] (Math/sqrt (dot xs xs)))
(defn distance [xs ys] (same-size! xs ys) (l1 (map - xs ys)))

(defn scale [a xs] (mapv #(* (double a) (double %)) xs))
(defn add [xs ys] (same-size! xs ys) (mapv + xs ys))
(defn axpy [a xs ys] (same-size! xs ys) (mapv #(+ (* a %1) %2) xs ys))

(defn probability-vector!
  "Allows substochastic vectors only when requested. Does not normalize data."
  ([p] (probability-vector! p false))
  ([p substochastic?]
   (when-not (and (sequential? p) (seq p))
     (fail "Probability vector must be nonempty" {}))
   (let [p (mapv #(nonnegative! :probability %) p) mass (sum p)]
     (when (or (> mass (+ 1.0 1.0e-10))
               (and (not substochastic?) (> (abs (- mass 1.0)) 1.0e-10)))
       (fail "Invalid probability mass" {:mass mass :substochastic? substochastic?}))
     p)))

(defn normalize
  "Return probabilities and the normalizer. Zero evidence is an explicit error."
  [weights]
  (let [weights (mapv #(nonnegative! :weight %) weights)
        z (sum weights)]
    (when-not (and (finite? z) (pos? z))
      (fail "Cannot normalize zero or nonfinite mass" {:mass z}))
    {:probabilities (scale (/ 1.0 z) weights) :normalizer z}))

(defn log-sum-exp [xs]
  (when (empty? xs) (fail "log-sum-exp needs at least one value" {}))
  (when (some #(or (Double/isNaN (double %)) (= ##Inf %)) xs)
    (fail "Invalid log weight" {}))
  (let [m (apply max xs)]
    (if (= m ##-Inf) ##-Inf
        (+ m (Math/log (sum (map #(Math/exp (- (double %) m)) xs)))))))

(defn log-normalize [log-weights]
  (let [z (log-sum-exp log-weights)]
    (when (= z ##-Inf) (fail "Every log weight is negative infinity" {}))
    {:probabilities (mapv #(Math/exp (- % z)) log-weights) :log-normalizer z}))

(defn close?
  ([x y] (close? x y 1.0e-10 1.0e-8))
  ([x y atol rtol]
   (and (finite? x) (finite? y)
        (<= (abs (- (double x) (double y)))
            (+ atol (* rtol (max (abs (double x)) (abs (double y)))))))))

(defn clamp-probability [x]
  (when-not (and (finite? x) (<= -1.0e-10 x (+ 1.0 1.0e-10)))
    (fail "Probability outside rounding allowance" {:value x}))
  (max 0.0 (min 1.0 (double x))))

(defn linspace [start end n]
  (finite! :start start) (finite! :end end) (natural! :n n)
  (when (< n 2) (fail "linspace requires at least two points" {:n n}))
  (mapv #(+ start (* (- end start) (/ % (double (dec n))))) (range n)))

(defn checked-result [label xs]
  (let [xs (vec xs)]
    (doseq [x xs] (finite! label x))
    xs))
