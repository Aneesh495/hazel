(ns hazel.edge-contract-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.math.linear :as linear]
            [hazel.math.numeric :as numeric]
            [hazel.model.network :as net]
            [hazel.ctmc.projection :as projection]
            [hazel.ctmc.generator :as generator]
            [hazel.ctmc.uniformization :as uniform]
            [hazel.ctmc.sensitivity :as sensitivity]
            [hazel.analysis.backward :as backward]
            [hazel.analysis.stationary :as stationary]
            [hazel.analysis.coarse :as coarse]
            [hazel.inference.score :as score]
            [hazel.simulation.direct :as direct]))

(deftest large-exact-algebra-regression
  (let [large 10000000000
        a [[large 0] [0 large]]]
    (is (= [[100000000000000000000N 0N] [0N 100000000000000000000N]] (linear/multiply a a)))
    (is (= 100000000000000000000N (linear/determinant a)))
    (is (= [100000000000000000000N 100000000000000000000N] (linear/matvec a [large large])))))

(deftest rate-rescaling-does-not-create-singularity-regression
  (let [model (net/with-parameters f/switch {:activate 1e-18 :deactivate 2e-18})
        q (generator/build (projection/enumerate model))
        hit (backward/hitting q #(= [0 1] %))
        pi (-> (stationary/solve q) :classes first :probabilities)]
    (is (numeric/close? 1e18 (first (:mean-times hit)) 1e4 1e-10))
    (is (< (numeric/distance pi [2/3 1/3 0]) 1e-12))))

(deftest sensitivity-rejects-mismatched-generator-regression
  (let [q (generator/build (projection/enumerate f/birth))
        changed (net/with-parameters f/birth {:birth 3})]
    (is (thrown? clojure.lang.ExceptionInfo
                 (sensitivity/propagate changed q (generator/initial-vector q [0]) [:birth] 1)))))

(deftest trajectory-accumulates-every-tail-regression
  (let [q (generator/build (projection/enumerate f/switch))
        results (uniform/trajectory q (generator/initial-vector q [1 0]) [1 2 3 4] {:tolerance 1e-3})]
    (doseq [r results]
      (is (= (min 1.0 (+ (:escape-probability r) (:series-tail-bound r))) (:truncation-bound r))))))

(deftest exact-lumpability-does-not-use-an-absolute-rate-cutoff-regression
  (let [model (net/compile-model
               {:species [:a :b] :initial {:a 1 :b 1} :parameters {}
                :reactions [{:id :a :reactants {:a 1} :products {} :rate 1/100000000000000000000}
                            {:id :b :reactants {:b 1} :products {} :rate 2/100000000000000000000}]})
        q (generator/build (projection/enumerate model))
        blocks (coarse/by-observable q #(reduce + %))]
    (is (not (:lumpable? (coarse/check q blocks))))))

(deftest replay-validates-censor-time-regression
  (let [trace (direct/simulate f/birth 2 42)]
    (is (thrown? clojure.lang.ExceptionInfo (direct/replay f/birth (assoc trace :final-time -1))))))

(deftest bounded-reaction-orders-regression
  (is (thrown? clojure.lang.ExceptionInfo
               (net/compile-model (assoc-in f/birth-source [:reactions 0 :reactants] {:x 1000000000000})))))

(deftest empty-score-still-validates-probability-input-regression
  (let [q (generator/build (projection/enumerate f/switch))]
    (is (thrown? clojure.lang.ExceptionInfo (score/forward f/switch q [2 0 0] [:activate] [] [] {})))))
