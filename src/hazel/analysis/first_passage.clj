(ns hazel.analysis.first-passage
  "Finite-horizon rare events by target absorption. Escape is never success."
  (:require [hazel.ctmc.generator :as g]
            [hazel.ctmc.uniformization :as u]
            [hazel.math.numeric :as n]))

(defn target-indexes [q predicate]
  (into #{} (keep-indexed #(when (predicate %2) %1)) (:states q)))

(defn cdf
  ([q initial predicate times] (cdf q initial predicate times {}))
  ([q initial predicate times options]
   (let [targets (target-indexes q predicate)
         absorbed (g/absorb q targets)
         trajectory (u/trajectory absorbed initial times options)]
     (mapv (fn [r]
             (let [hit (n/sum (map #(get (:probabilities r) %) targets))
                   escape (get (:probabilities r) (:sink q))
                   tail (:series-tail-bound r)]
               {:time (:time r) :lower hit :upper (min 1.0 (+ hit escape tail))
                :escape-probability escape :series-tail-bound tail
                :density (n/sum (for [[i row] (map-indexed vector (:rows q))
                                      :when (not (targets i))
                                      [j rate] row :when (targets j)]
                                  (* (get (:probabilities r) i) rate)))})) trajectory))))

(defn competing
  "Competing disjoint absorbing targets. Ambiguous classification is rejected."
  [q initial predicates time options]
  (let [groups (mapv #(target-indexes q %) predicates)
        all (mapcat seq groups)]
    (when-not (= (count all) (count (set all)))
      (n/fail "Competing target sets overlap" {}))
    (let [r (u/propagate (g/absorb q (set all)) initial time options)]
      {:time time
       :outcomes (mapv #(n/sum (map (fn [i] (get (:probabilities r) i)) %)) groups)
       :escape-probability (:escape-probability r)
       :series-tail-bound (:series-tail-bound r)})))
