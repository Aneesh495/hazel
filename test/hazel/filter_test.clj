(ns hazel.filter-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.ctmc.projection :as p]
            [hazel.ctmc.generator :as g]
            [hazel.inference.observation :as o]
            [hazel.inference.filter :as filter]
            [hazel.math.numeric :as n]))

(deftest exact-hidden-chain-likelihood
  (let [q (g/build (p/enumerate f/switch)) initial (g/initial-vector q [1 0])
        sensors (o/compile-sensors f/switch [{:species :on :kind :exact}])
        observations [{:time 1 :values {:on 1}} {:time 2 :values {:on 0}}]
        r (filter/forward q initial sensors observations)
        p01 (* 1/3 (- 1 (Math/exp -1.2)))
        p10 (* 2/3 (- 1 (Math/exp -1.2)))]
    (is (n/close? (Math/log (* p01 p10)) (:log-likelihood r)))
    (is (= [1.0 0.0 0.0] (:probabilities r)))))

(deftest missing-and-noisy-observations
  (let [q (g/build (p/enumerate f/switch)) initial (g/initial-vector q [1 0])
        sensors (o/compile-sensors f/switch [{:species :on :kind :gaussian :sigma 0.3}])
        r (filter/forward q initial sensors [{:time 1 :values {}} {:time 2 :values {:on 0.9}}])]
    (is (> (second (:probabilities r)) 0.9))
    (is (n/close? 1 (n/sum (:probabilities r)))))
  (is (thrown? clojure.lang.ExceptionInfo (o/compile-sensors f/switch [{:species :missing :kind :exact}]))))
