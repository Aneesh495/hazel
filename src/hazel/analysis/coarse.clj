(ns hazel.analysis.coarse
  "Strong CTMC lumpability and exact rate-signature partition refinement."
  (:require [hazel.ctmc.generator :as g]
            [hazel.math.numeric :as n]))

(defn partition!
  [q blocks]
  (let [flat (vec (mapcat seq blocks))]
    (when-not (and (every? seq blocks) (= (count flat) (count (set flat)))
                   (= (set flat) (set (range (:size q))))
                   (some #{#{(:sink q)}} (map set blocks)))
      (n/fail "Partition must cover states once and isolate escape" {})))
  (mapv #(vec (sort %)) blocks))

(defn by-observable [q observable]
  (conj (->> (map-indexed vector (:states q))
             (group-by #(observable (second %)))
             (sort-by (comp pr-str key))
             (mapv #(mapv first (val %)))) [(:sink q)]))

(defn- signature [q index block-of block-count]
  ;; Include the negative diagonal when accumulating the source block. Summing
  ;; positive outgoing edges alone is not a generator block-rate signature.
  (let [rates (reduce (fn [v [j rate]] (update v (block-of j) +' rate))
                      (vec (repeat block-count 0)) (get (:rows q) index))]
    (update rates (block-of index) - (get (:exits q) index))))

(defn check
  ([q blocks] (check q blocks 0.0))
  ([q blocks tolerance]
   (let [blocks (partition! q blocks)
         block-of (into {} (mapcat (fn [b indexes] (map #(vector % b) indexes)) (range) blocks))
         differences (for [indexes blocks i (rest indexes)
                           :let [reference (signature q (first indexes) block-of (count blocks))
                                 actual (signature q i block-of (count blocks))
                                 residual (n/distance reference actual)
                                 equal? (every? true? (map (fn [a b]
                                                           (if (and (or (integer? a) (ratio? a))
                                                                    (or (integer? b) (ratio? b))) (= a b) (== a b))) reference actual))]]
                       {:reference (first indexes) :state i :residual residual :equal? equal? :expected reference :actual actual})
         worst (reduce (fn [a b] (if (> (:residual b) (:residual a)) b a)) {:residual 0.0} differences)
         bad (if (zero? tolerance) (first (remove :equal? differences))
                 (first (filter #(> (:residual %) tolerance) differences)))]
       {:lumpable? (nil? bad) :maximum-residual (:residual worst)
        :counterexample bad :blocks blocks})))

(defn refine
  "Split exact rate signatures until a fixed point. No numeric rounding in keys."
  [q initial-blocks]
  (loop [blocks (partition! q initial-blocks) iterations 0]
    (let [block-of (into {} (mapcat (fn [b indexes] (map #(vector % b) indexes)) (range) blocks))
          refined (vec (mapcat (fn [indexes]
                                (->> indexes (group-by #(signature q % block-of (count blocks)))
                                     (sort-by (comp pr-str key)) (mapv val))) blocks))]
      (if (= (count blocks) (count refined))
        {:blocks blocks :iterations iterations :reduction (/ (:size q) (double (count blocks)))}
        (recur refined (inc iterations))))))

(defn lump [q blocks]
  (let [{:keys [lumpable?] :as checked} (check q blocks)]
    (when-not lumpable? (n/fail "Partition is not strongly lumpable" checked))
    (let [blocks (:blocks checked)
          ;; Keep escape at the final block regardless of caller ordering.
          blocks (conj (filterv #(not= [(:sink q)] %) blocks) [(:sink q)])
          block-of (into {} (mapcat (fn [b indexes] (map #(vector % b) indexes)) (range) blocks))
          rows (mapv (fn [b indexes]
                       (let [rates (signature q (first indexes) block-of (count blocks))]
                         (vec (for [[j rate] (map-indexed vector rates) :when (and (not= j b) (pos? rate))] [j rate]))))
                     (range) blocks)
          result (g/validate {:size (count blocks) :sink (dec (count blocks)) :rows rows
                              :exits (mapv #(reduce +' 0 (map second %)) rows)
                              :states (mapv vector (range (dec (count blocks))))
                              :model-hash (:model-hash q) :closed? (:closed? q)})]
      (assoc result :blocks blocks :block-of block-of))))
