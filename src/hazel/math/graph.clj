(ns hazel.math.graph
  "Deterministic graph traversal without call-stack depth proportional to graph size."
  (:require [clojure.set :as set]))

(defn vertices [graph]
  (set (concat (keys graph) (mapcat seq (vals graph)))))

(defn reverse-graph [graph]
  (reduce-kv (fn [r u vs]
               (reduce #(update %1 %2 (fnil conj #{}) u) r vs))
             (zipmap (vertices graph) (repeat #{})) graph))

(defn reachable [graph start]
  (loop [queue (conj clojure.lang.PersistentQueue/EMPTY start) seen #{start}]
    (if (empty? queue) seen
        (let [v (peek queue) fresh (set/difference (set (get graph v #{})) seen)]
          (recur (into (pop queue) (sort-by pr-str fresh)) (into seen fresh))))))

(defn finishing-order [graph]
  (loop [roots (sort-by pr-str (vertices graph)) seen #{} order []]
    (if (empty? roots) order
        (let [root (first roots)]
          (if (seen root)
            (recur (rest roots) seen order)
            (let [[seen order]
                  (loop [stack [[root false]] seen seen order order]
                    (if (empty? stack) [seen order]
                        (let [[v expanded?] (peek stack) stack (pop stack)]
                          (cond expanded? (recur stack seen (conj order v))
                                (seen v) (recur stack seen order)
                                :else (recur (into (conj stack [v true])
                                                   (map #(vector % false)
                                                        (reverse (sort-by pr-str (get graph v #{})))))
                                             (conj seen v) order)))))]
              (recur (rest roots) seen order)))))))

(defn strongly-connected [graph]
  (let [reverse (reverse-graph graph)]
    (loop [order (rseq (finishing-order graph)) assigned #{} components []]
      (if (empty? order) components
          (let [v (first order)]
            (if (assigned v) (recur (rest order) assigned components)
                ;; Assigned vertices must not be crossed in the reversed search.
                (let [component (loop [stack [v] found #{}]
                                  (if (empty? stack) found
                                      (let [x (peek stack) stack (pop stack)]
                                        (if (or (assigned x) (found x))
                                          (recur stack found)
                                          (recur (into stack (get reverse x #{})) (conj found x))))))]
                  (recur (rest order) (into assigned component) (conj components component)))))))))

(defn weak-components [graph]
  (strongly-connected (merge-with set/union graph (reverse-graph graph))))

(defn closed-components [graph]
  (filterv (fn [component]
             (every? #(set/subset? (set (get graph % #{})) component) component))
           (strongly-connected graph)))
