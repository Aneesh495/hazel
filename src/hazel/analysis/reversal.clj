(ns hazel.analysis.reversal
  "Stationary time reversal on the positive-probability support."
  (:require [hazel.ctmc.generator :as g]
            [hazel.ctmc.sparse :as sparse]
            [hazel.math.numeric :as n]))

(defn reverse-generator [q pi]
  (n/probability-vector! pi) (n/same-size! (:rows q) pi)
  (when (or (pos? (get pi (:sink q)))
            (> (n/l1 (sparse/generator-product (sparse/compile-kernel q) pi)) 1e-9))
    (n/fail "Time reversal requires a stationary population distribution" {}))
  (let [rows (reduce (fn [rows [i row]]
                       ;; Reversal is only defined on stationary positive support.
                       ;; Keep other indexes visible as undefined, not inferred
                       ;; transient reverse dynamics.
                       (reduce (fn [rows [j rate]]
                                 (if (and (pos? (pi i)) (pos? (pi j)))
                                   (update rows j conj [i (/ (* (pi i) rate) (pi j))]) rows)) rows row))
                     (vec (repeat (:size q) [])) (map-indexed vector (:rows q)))
        reversed (g/validate (assoc q :rows (mapv #(vec (sort-by first %)) rows)
                                   :exits (mapv #(n/sum (map second %)) rows))) ]
    (assoc reversed :undefined-state-indexes (vec (keep-indexed #(when (zero? %2) %1) pi))
                    :events nil :time-reversed? true)))
