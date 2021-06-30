(ns hazel.analysis.certificate-check
  "Independent small-model checker. Does not call the projection, generator,
  propensity, sparse-product, Poisson, or certificate construction code."
  (:require [hazel.model.network :as net]
            [hazel.math.rational :as r]
            [hazel.math.numeric :as n]))

(defn- combinations [x s]
  ;; Falling factorial divided by factorial, independent of probability/choose.
  (if (< x s) 0N
      (/ (reduce *' 1N (range (inc (- x s)) (inc x)))
         (reduce *' 1N (range 1 (inc s))))))

(defn- reconstruct [model states]
  ;; The supplied state set need not be the producer's reachable BFS order.
  ;; Any distinct finite set containing the initial state defines a valid FSP;
  ;; all missing destinations must still enter the absorbing escape index.
  (let [species (:species model) source (:source model)
        sink (count states) index (zipmap states (range))]
    (conj
     (mapv (fn [i state]
             (->> (:reactions source)
                  (reduce (fn [row reaction]
                            (let [orders (mapv #(get (:reactants reaction) % 0) species)
                                  constant (if (keyword? (:rate reaction))
                                             (get (:parameters source) (:rate reaction)) (:rate reaction))
                                  _ (r/nonnegative! :reaction-constant constant)
                                  rate (*' constant (reduce *' 1N (map combinations state orders)))]
                              (if (zero? rate) row
                                  (let [target (mapv (fn [x species order]
                                                       (+' (- x order) (get (:products reaction) species 0))) state species orders)
                                        destination (get index target sink)]
                                    (if (= i destination) row (update row destination (fnil +' 0N) rate)))))) {})
                  (sort-by key) (mapv vec))) (range) states) [])))

(defn check
  [model certificate]
  (net/compiled! model)
  (when-not (and (= :hazel/certificate-v1 (:format certificate))
                 (= (:hash model) (:model-hash certificate)))
    (n/fail "Certificate format or model hash mismatch" {}))
  (let [{:keys [states time lambda chunks poisson-order exponential-order initial lower-probabilities mass-deficit]} certificate
        size (inc (count states))]
    (when-not (and (vector? states) (<= 1 (count states) 64) (= (count states) (count (set states))))
      (n/fail "Invalid certified state set" {}))
    (doseq [state states] (net/state! model state))
    (r/nonnegative! :time time)
    (when-not (and (integer? chunks) (<= 1 chunks 64)) (n/fail "Invalid certificate chunks" {}))
    (doseq [order [poisson-order exponential-order]]
      (when-not (and (integer? order) (<= 2 order 100)) (n/fail "Invalid certificate series order" {})))
    (let [rows (reconstruct model states)
          exits (mapv #(r/sum (map second %)) rows)
          expected-lambda (reduce max 0N exits)
          expected-chunks (max 1N (r/ceiling (*' expected-lambda time)))
          index (.indexOf ^java.util.List states (:initial model))
          _ (when (neg? index) (n/fail "Certified state set omits initial state" {}))
          expected-initial (assoc (vec (repeat size 0N)) index 1N)]
      (when-not (and (= rows (:rows certificate)) (= exits (:exits certificate))
                     (= lambda expected-lambda) (= chunks expected-chunks) (= initial expected-initial))
        (n/fail "Certificate generator reconstruction mismatch" {}))
      (let [mean (/ (*' lambda time) chunks)
            ;; Independent explicit power-series summation and dense transition matrix.
            denominator-upper
            (let [[sum term] (reduce (fn [[sum term] k]
                                      (let [term (/ (*' term mean) k)] [(+' sum term) term]))
                                    [1N 1N] (range 1 (inc exponential-order)))
                  next (/ (*' term mean) (inc exponential-order))]
              (+' sum (/ next (- 1 (/ mean (+ exponential-order 2))))))
            transition (when (pos? lambda)
                         (mapv (fn [i]
                                 (reduce (fn [row [j rate]] (assoc row j (/ rate lambda)))
                                         (assoc (vec (repeat size 0N)) i (- 1 (/ (exits i) lambda))) (rows i))) (range size)))
            step (fn [p]
                   (loop [k 0 coefficient 1N power p result (vec (repeat size 0N))]
                     (let [result (mapv (fn [a b] (+' a (*' coefficient b))) result power)]
                       (if (= k poisson-order) (mapv #(/ % denominator-upper) result)
                           (let [next-power (mapv (fn [j]
                                                    (r/sum (map-indexed (fn [i value] (*' value (get-in transition [i j]))) power)))
                                                  (range size))]
                             (recur (inc k) (/ (*' coefficient mean) (inc k)) next-power result))))))
            computed (if (zero? lambda) initial (nth (iterate step initial) chunks))
            ;; Equality checks below are exact ratio comparisons. No decimal
            ;; tolerance can turn a modified component into an accepted bound.
            deficit (- 1N (r/sum computed))
            escape (peek computed)]
        (when-not (and (= computed lower-probabilities) (= deficit mass-deficit)
                       (= escape (:escape-lower certificate))
                       (= (min 1N (+' escape deficit)) (:truncation-upper certificate))
                       (not (neg? deficit)) (every? #(<= 0 %) computed))
          (n/fail "Certificate lower-bound recurrence mismatch" {}))
        {:status :verified :arithmetic :exact-rational :states (count states)
         :mass-deficit deficit :truncation-upper (:truncation-upper certificate)}))))
