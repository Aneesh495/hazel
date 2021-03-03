(ns hazel.mean-field-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.math.ode :as ode]
            [hazel.math.numeric :as n]
            [hazel.analysis.mean-field :as mf]))

(deftest adaptive-integrator
  (let [r (ode/integrate (fn [_ [x]] [x]) [1.0] 3)]
    (is (= :complete (:status r)))
    (is (n/close? (Math/exp 3) (first (:state r)) 1e-7 1e-8)))
  (is (= :step-budget-exhausted (:status (ode/integrate (fn [_ [x]] [x]) [1.0] 3 {:max-steps 0})))))

(deftest linear-noise-exact-for-linear-networks
  (let [r (mf/linear-noise f/immigration-death 2) truth (* 4 (- 1 (Math/exp -1)))]
    (is (n/close? truth (first (:mean r))))
    (is (n/close? truth (get-in r [:covariance 0 0]))))
  (let [r (mf/linear-noise f/death 2) survival (Math/exp -1.4)]
    (is (n/close? (* 6 survival) (first (:mean r))))
    (is (n/close? (* 6 survival (- 1 survival)) (get-in r [:covariance 0 0])))))
