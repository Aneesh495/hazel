(ns hazel.bridge-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.ctmc.generator :as g]
            [hazel.ctmc.projection :as p]
            [hazel.inference.bridge :as bridge]))

(deftest conditioned-extinction-path
  (let [q (g/build (p/enumerate f/death)) path (bridge/sample q [6] [0] 2 42)]
    (is (= :valid (:status (bridge/validate q path))))
    (is (= 6 (count (:jumps path))))
    (is (< (:conditional-tail-bound path) 1e-9))
    (is (= path (bridge/sample q [6] [0] 2 42)))))

(deftest conditioned-switch-parity
  (let [q (g/build (p/enumerate f/switch)) paths (mapv #(bridge/sample q [1 0] [0 1] 3 %) (range 20))]
    (is (every? #(odd? (count (:jumps %))) paths))
    (is (every? #(= :valid (:status (bridge/validate q %))) paths)))
  (is (thrown? clojure.lang.ExceptionInfo
               (bridge/sample (g/build (p/enumerate f/switch)) [1 0] [0 1] 0 42))))
