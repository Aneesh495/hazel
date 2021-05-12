(ns hazel.property-test
  "Deterministic generated algebra and semantic differential tests.
  Generators construct invariants independently of the operations under test."
  (:require [clojure.test :refer [deftest is testing]]
            [hazel.math.linear :as linear]
            [hazel.math.exponential :as exponential]
            [hazel.math.numeric :as numeric]
            [hazel.math.polynomial :as polynomial]
            [hazel.simulation.random :as random]
            [hazel.ctmc.generator :as generator]
            [hazel.ctmc.projection :as projection]
            [hazel.ctmc.sparse :as sparse]
            [hazel.ctmc.uniformization :as uniform]
            [hazel.ctmc.sensitivity :as sensitivity]
            [hazel.model.network :as net]
            [hazel.model.kinetics :as kinetics]
            [hazel.model.structure :as structure]
            [hazel.analysis.moment :as moment]))

(defn draw [state limit]
  (let [[bits state] (random/next-long state)] [(mod bits limit) state]))

(defn random-generator
  "Independent random sparse rows, not reaction-network compilation."
  [seed size]
  (loop [i 0 state seed rows []]
    (if (= i size)
      {:size size :sink (dec size) :states (mapv vector (range (dec size)))
       :model-hash "generated" :rows rows :exits (mapv #(reduce + 0 (map second %)) rows)}
      (if (= i (dec size))
        (recur (inc i) state (conj rows []))
        (let [[row state] (loop [j 0 state state row []]
                            (if (= j size) [row state]
                                (let [[value state] (draw state 7)]
                                  (recur (inc j) state
                                         (if (or (= i j) (< value 3)) row (conj row [j (/ value 7)]))))))]
          (recur (inc i) state (conj rows row)))))))

(deftest generated-sparse-semigroups
  (doseq [seed (range 80)]
    (let [size (+ 2 (mod seed 6)) q (generator/validate (random-generator seed size))
          initial (assoc (vec (repeat size 0.0)) 0 1.0)
          dense (generator/dense q) kernel (sparse/compile-kernel q)
          time (/ (inc (mod seed 17)) 5.0)
          actual (uniform/propagate q initial time {:tolerance 1e-13})
          reference (exponential/action dense initial time)
          first-half (:probabilities (uniform/propagate q initial (* 0.5 time)))
          two-halves (:probabilities (uniform/propagate q first-half (* 0.5 time)))]
      (testing (str "seed=" seed)
        (is (< (numeric/distance reference (:probabilities actual)) 1e-10))
        (is (< (numeric/distance (:probabilities actual) two-halves) 1e-10))
        (is (every? #(>= % 0) (:probabilities actual)))
        (is (<= (:mass actual) (+ 1.0 1e-12)))
        (is (< (abs (numeric/sum (sparse/generator-product kernel initial))) 1e-12))
        (is (< (numeric/distance (sparse/backward-product kernel (vec (repeat size 1.0)))
                                (vec (repeat size 0.0))) 1e-12))
        (is (every? #(zero? (reduce + %)) dense))))))

(defn unimodular [seed size]
  (loop [i 0 state seed a (linear/identity size)]
    (if (= i 12) a
        (let [[from state] (draw state size) [offset state] (draw state (dec size))
              to (mod (+ from offset 1) size) [coefficient state] (draw state 11)
              coefficient (- coefficient 5)]
          (recur (inc i) state
                 (assoc a to (mapv #(+' (*' coefficient %1) %2) (a from) (a to))))))))

(deftest generated-exact-elimination
  (doseq [seed (range 100)]
    (let [size (+ 2 (mod seed 3)) a (unimodular seed size)
          expected (mapv #(- (* % 10000000000N) seed) (range size))
          b (linear/matvec a expected)]
      (is (= 1 (linear/determinant a)))
      (is (= expected (linear/solve a b)))
      (is (= (linear/identity size) (linear/multiply a (linear/inverse a)))))))

(deftest generated-nullspaces
  (doseq [rows (range 1 5) free (range 1 5) seed (range 10)]
    (let [a (mapv (fn [i]
                    (into (get (linear/identity rows) i)
                          (map (fn [j] (- (mod (+ (* (inc i) (inc j)) seed) 7) 3)) (range free)))) (range rows))
          basis (linear/nullspace a)]
      (is (= free (count basis)))
      (is (= rows (linear/rank a)))
      (doseq [v basis] (is (= (vec (repeat rows 0N)) (linear/matvec a v)))))))

(defn generated-polynomial [seed]
  (into {} (for [a (range 4) b (range (- 4 a))
                 :let [coefficient (- (mod (+ (* 31 seed) (* 7 a) (* 13 b)) 11) 5)]
                 :when (not (zero? coefficient))]
             [[a b] coefficient])))

(deftest generated-polynomial-laws
  (doseq [seed (range 100)]
    (let [a (generated-polynomial seed) b (generated-polynomial (inc seed))
          state [(mod seed 5) (mod (* seed 3) 7)] delta [-2 1]
          product (polynomial/multiply a b)]
      (is (= (*' (polynomial/evaluate a state) (polynomial/evaluate b state))
             (polynomial/evaluate product state)))
      (is (= (polynomial/evaluate a (mapv + state delta))
             (polynomial/evaluate (polynomial/shift a delta) state)))
      (doseq [index [0 1]]
        (is (= (polynomial/derivative product index)
               (polynomial/add (polynomial/multiply (polynomial/derivative a index) b)
                               (polynomial/multiply a (polynomial/derivative b index)))))))))

(deftest generated-reaction-operator-semantics
  (let [model (net/compile-model
               {:species [:a :b] :initial {:a 4 :b 3} :parameters {:forward 2/7 :reverse 3/5}
                :reactions [{:id :forward :reactants {:a 2 :b 1} :products {:a 1 :b 2} :rate :forward}
                            {:id :reverse :reactants {:b 2} :products {:a 1} :rate :reverse}]})]
    (doseq [observable [{[2 1] 1N} {[0 3] 1N [1 0] -2N} {[0 0] 1N}]
            a (range 6) b (range 6)]
      (let [state [a b]
            expected (reduce +' 0N (for [{:keys [target rate]} (kinetics/transitions model state)]
                                     (*' rate (-' (polynomial/evaluate observable target)
                                                   (polynomial/evaluate observable state)))))
            actual (polynomial/evaluate (moment/generator-action model observable) state)]
        (is (= expected actual))))))

(deftest generated-network-conservation-and-sensitivities
  (doseq [seed (range 20)]
    (let [total (inc (mod seed 5))
          model (net/compile-model
                 {:species [:a :b] :initial {:a total} :parameters {:forward (/ (inc seed) 7) :reverse 2/3}
                  :reactions [{:id :forward :reactants {:a 1} :products {:b 1} :rate :forward}
                              {:id :reverse :reactants {:b 1} :products {:a 1} :rate :reverse}]})
          laws (structure/conservation-laws model)
          projection (projection/enumerate model) q (generator/build projection)
          initial (generator/initial-vector q (:initial model))
          time (/ (inc seed) 10)
          r (sensitivity/propagate model q initial [:forward] time)
          h 1e-5 base (double (get (:parameters model) :forward))
          evaluate (fn [value]
                     (:probabilities (uniform/propagate
                                      (generator/for-states (net/with-parameters model {:forward value}) (:states q)) initial time)))
          difference (numeric/scale (/ 1.0 (* 2 h)) (mapv - (evaluate (+ base h)) (evaluate (- base h))))]
      (is (= (inc total) (count (:states q))))
      (is (:closed? projection))
      (is (every? #(structure/conserved? laws %) (:states q)))
      (is (< (numeric/distance difference (get (:sensitivities r) :forward)) 1e-7)))))
