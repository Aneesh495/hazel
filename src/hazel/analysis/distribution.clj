(ns hazel.analysis.distribution
  "Queries preserve the distinction between captured and conditional probability."
  (:require [hazel.math.numeric :as n]))

(defn state-probabilities [q result]
  (n/same-size! (:rows q) (:probabilities result))
  (mapv vector (:states q) (take (:sink q) (:probabilities result))))

(defn event
  "Analytic truncation interval for a population event. Roundoff is separate."
  [q result predicate]
  (let [captured (n/sum (for [[state p] (state-probabilities q result)
                             :when (predicate state)] p))
        omitted (+ (get (:probabilities result) (:sink q)) (:series-tail-bound result 0.0))]
    {:captured captured :lower captured :upper (min 1.0 (+ captured omitted))
     :omitted-mass-bound omitted :floating-point-residual (:floating-point-residual result 0.0)}))

(defn marginal [q result species-index]
  (n/natural! :species-index species-index)
  (when (or (empty? (:states q)) (>= species-index (count (first (:states q)))))
    (n/fail "Marginal species index out of range" {}))
  (reduce (fn [m [state p]] (update m (state species-index) (fnil + 0.0) p))
          (sorted-map) (state-probabilities q result)))

(defn expectation
  "Captured expectation. Tail moments need an independent bound on the observable."
  [q result observable]
  (n/sum (map (fn [[state p]] (* p (n/finite! :observable (observable state))))
              (state-probabilities q result))))

(defn bounded-expectation [q result observable lower upper]
  (n/finite! :lower lower) (n/finite! :upper upper)
  (when (> lower upper) (n/fail "Observable bounds are reversed" {}))
  (let [value (expectation q result
                           (fn [state]
                             (let [x (observable state)]
                               (when-not (<= lower x upper)
                                 (n/fail "Observable violates stated bounds" {:state state :value x})) x)))
        missing (+ (get (:probabilities result) (:sink q)) (:series-tail-bound result 0.0))]
    {:captured value :lower (+ value (* missing (min 0 lower)))
     :upper (+ value (* missing (max 0 upper))) :omitted-mass-bound missing}))

(defn conditional-moments [q result observable]
  (let [pairs (state-probabilities q result)
        retained (n/sum (map second pairs))]
    (when-not (pos? retained) (n/fail "No retained mass for conditional moments" {}))
    (let [mean (/ (expectation q result observable) retained)
          variance (/ (n/sum (map (fn [[state p]]
                                   (let [d (- (observable state) mean)] (* p d d))) pairs)) retained)]
      {:mean mean :variance variance :condition :never-left-projection :retained retained})))

(defn quantile
  "Quantile of a normalized sorted discrete marginal. Refuses hidden missing mass."
  [marginal probability]
  (let [probability (n/clamp-probability probability)
        mass (n/sum (vals marginal))]
    (when (> (abs (- mass 1)) 1e-8) (n/fail "Quantile requires normalized marginal" {:mass mass}))
    (loop [entries (seq marginal) cumulative 0.0]
      (when-not entries (n/fail "Empty or inconsistent marginal" {}))
      (let [[x p] (first entries) cumulative (+ cumulative p)]
        (if (or (>= cumulative probability) (nil? (next entries))) x
            (recur (next entries) cumulative))))))
