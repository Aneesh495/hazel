(ns hazel.smoother-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.ctmc.projection :as p]
            [hazel.ctmc.generator :as g]
            [hazel.inference.observation :as o]
            [hazel.inference.filter :as filter]
            [hazel.inference.smoother :as smoother]
            [hazel.math.exponential :as exp]
            [hazel.math.linear :as l]
            [hazel.math.numeric :as n]))

(deftest backward-dual-reference
  (let [q (g/build (p/enumerate f/switch)) values [0.1 0.9 0.0]
        reference (l/matvec (exp/expm (l/scale 2 (g/dense q))) values)]
    (is (< (n/distance reference (smoother/backward-propagate q values 2)) 1e-12))))

(deftest future-data-changes-past-belief
  (let [q (g/build (p/enumerate f/switch))
        sensors (o/compile-sensors f/switch [{:species :on :kind :exact}])
        r (filter/forward q (g/initial-vector q [1 0]) sensors
                          [{:time 1 :values {}} {:time 1.1 :values {:on 1}}])
        smoothed (smoother/smooth q r)
        bridge (smoother/sample-grid-bridge q r 42)]
    (is (> (second (:probabilities (first (:steps smoothed))))
           (second (:filtered (first (:steps r))))))
    (is (= [0 1] (:state (last (:grid bridge)))))
    (is (= bridge (smoother/sample-grid-bridge q r 42)))
    (is (n/close? 1 (n/sum (:probabilities (first (:steps smoothed))))))))
