(ns hazel.sensitivity-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.model.network :as net]
            [hazel.ctmc.projection :as p]
            [hazel.ctmc.generator :as g]
            [hazel.ctmc.uniformization :as u]
            [hazel.ctmc.sensitivity :as s]
            [hazel.math.numeric :as n]))

(deftest tangent-finite-differences
  (doseq [[model parameter] [[f/birth :birth] [f/death :death] [f/switch :activate]]]
    (let [q (g/build (p/enumerate model)) initial (g/initial-vector q (:initial model))
          base (get (:parameters model) parameter) h 1e-5
          evaluate (fn [value] (:probabilities (u/propagate
                                               (g/for-states (net/with-parameters model {parameter value}) (:states q))
                                               initial 2)))
          finite (n/scale (/ 1.0 (* 2 h)) (mapv - (evaluate (+ base h)) (evaluate (- base h))))
          result (s/propagate model q initial [parameter] 2)]
      (is (< (n/distance finite (get (:sensitivities result) parameter)) 2e-8))
      (is (< (abs (n/sum (get (:sensitivities result) parameter))) 1e-10)))))

(deftest zero-rate-boundary
  (let [model (net/with-parameters f/birth {:birth 0})
        q (g/for-states model [[0] [1] [2]])
        r (s/propagate model q (g/initial-vector q [0]) [:birth] 3)]
    (is (= [-3.0 3.0 0.0 0.0] (get (:sensitivities r) :birth)))))
