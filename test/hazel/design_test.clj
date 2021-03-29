(ns hazel.design-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.math.symmetric :as symmetric]
            [hazel.math.linear :as l]
            [hazel.math.numeric :as n]
            [hazel.analysis.design :as design]))

(deftest eigensystem-reconstruction
  (doseq [a [[[2 1] [1 2]] [[4 1 2] [1 3 0] [2 0 5]] [[1]]]]
    (let [{:keys [values vectors]} (symmetric/eigen a)
          diagonal (mapv (fn [i] (assoc (vec (repeat (count values) 0.0)) i (values i))) (range (count values)))
          reconstructed (l/multiply (l/multiply vectors diagonal) (l/transpose vectors))]
      (is (< (n/distance (mapcat identity a) (mapcat identity reconstructed)) 1e-10))
      (is (< (n/distance (mapcat identity (l/identity (count a)))
                        (mapcat identity (l/multiply (l/transpose vectors) vectors))) 1e-10)))))

(deftest complementary-experiments
  (let [candidates [{:id :x :cost 1 :matrix [[10 0] [0 0]]}
                    {:id :y :cost 1 :matrix [[0 0] [0 10]]}
                    {:id :weak :cost 1 :matrix [[1 0] [0 1]]}]
        r (design/greedy candidates 2 {:regularization 1.0})]
    (is (= #{:x :y} (set (:selected r))))
    (is (= 2.0 (:spent r)))
    (is (every? #(pos? (:gain-per-cost %)) (:history r)))))
