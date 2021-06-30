(ns hazel.ctmc.generator
  "Sparse row-oriented CTMC generators. Rows store positive off-diagonal rates.
  Diagonals are implied by exit rates. The final state is absorbing escape."
  (:require [hazel.math.numeric :as n]
            [hazel.math.linear :as l]
            [hazel.ctmc.projection :as projection]))

(defn validate [generator]
  (let [{:keys [size rows exits sink]} generator]
    (n/natural! :size size)
    (when-not (and (pos? size) (= size (count rows) (count exits))
                   (= sink (dec size)))
      (n/fail "Invalid generator dimensions or escape index" {}))
    (doseq [[i row] (map-indexed vector rows)]
      (when-not (= (count row) (count (set (map first row))))
        (n/fail "Generator row contains duplicate destinations" {:row i}))
      (doseq [[j rate] row]
        (when-not (and (integer? j) (<= 0 j) (< j size) (not= i j))
          (n/fail "Invalid off-diagonal destination" {:row i :destination j}))
        (n/positive! :transition-rate rate))
      (n/nonnegative! :exit-rate (exits i))
      (when-not (n/close? (exits i) (n/sum (map second row)) 1e-12 1e-12)
        (n/fail "Exit rate disagrees with sparse row" {:row i})))
    (when-not (and (empty? (rows sink)) (zero? (exits sink)))
      (n/fail "Escape state must be absorbing" {}))
    generator))

(defn build [projection]
  ;; Multiple reaction labels can reach one population. Aggregate their rates
  ;; in Q, while retaining original labeled events for path-count observables.
  (let [states (:states projection) sink (count states)
        rows (mapv (fn [i edges]
                     (->> edges
                          (reduce (fn [rates {:keys [destination rate]}]
                                    (let [j (if (= destination :escape) sink destination)]
                                      ;; A real self event changes reaction counts,
                                      ;; but not the population process's exit rate.
                                      (if (= i j) rates (update rates j (fnil +' 0) rate)))) {})
                          (sort-by key) (mapv vec)))
                   (range sink) (:edges projection))
        rows (conj rows [])
        exits (mapv #(reduce +' 0 (map second %)) rows)]
    (validate {:size (inc sink) :sink sink :states states :rows rows :exits exits
               :events (:edges projection) :model-hash (:model-hash projection)
               :closed? (:closed? projection)})))

(defn for-states [model states]
  (build (projection/fixed model states)))

(defn initial-vector [generator state]
  (let [i (.indexOf ^java.util.List (:states generator) state)]
    (when (neg? i) (n/fail "Initial state outside projection" {:state state}))
    (assoc (vec (repeat (:size generator) 0.0)) i 1.0)))

(defn dense [generator]
  (validate generator)
  (reduce (fn [a [i row]]
            (reduce (fn [a [j rate]] (assoc-in a [i j] rate))
                    (assoc-in a [i i] (- ((:exits generator) i))) row))
          (l/zeros (:size generator) (:size generator))
          (map-indexed vector (:rows generator))))

(defn absorb
  "Kill outgoing events from target states. Escape remains a distinct outcome."
  [generator targets]
  (let [targets (set targets)]
    (doseq [i targets]
      (when-not (and (integer? i) (<= 0 i) (< i (:sink generator)))
        (n/fail "Target index out of range" {:index i})))
    (validate (-> generator
                  (update :rows #(mapv (fn [i row] (if (targets i) [] row)) (range) %))
                  (update :exits #(mapv (fn [i rate] (if (targets i) 0 rate)) (range) %))
                  (assoc :absorbing-targets targets)))))

(defn rate [generator from to]
  (if (= from to) (- (get (:exits generator) from))
      (get (into {} (get (:rows generator) from)) to 0)))
