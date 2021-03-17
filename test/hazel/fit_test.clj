(ns hazel.fit-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.math.optimize :as opt]
            [hazel.math.numeric :as n]
            [hazel.ctmc.projection :as p]
            [hazel.inference.observation :as o]
            [hazel.inference.fit :as fit]))

(deftest constrained-quadratic
  (let [f (fn [[x y]] {:value (+ (* (- x 3) (- x 3)) (* (+ y 2) (+ y 2)))
                       :gradient [(* 2 (- x 3)) (* 2 (+ y 2))]})
        r (opt/minimize f [0 0] [[0 2] [-5 5]])]
    (is (= :converged (:status r)))
    (is (< (n/distance [2 -2] (:point r)) 1e-6))
    (is (apply >= (map :value (:history r))))))

(deftest poisson-rate-mle
  (let [states (:states (p/enumerate f/birth {:bounds [30]}))
        sensors (o/compile-sensors f/birth [{:species :x :kind :exact}])
        observations [{:time 2 :values {:x 7}}]
        r (fit/fit f/birth states [:birth] sensors observations {:birth [0.01 8]} {})]
    (is (= :converged (:status r)))
    (is (n/close? 3.5 (get (:parameters r) :birth) 1e-5 1e-6))
    (is (apply >= (map :value (:history r))))))
