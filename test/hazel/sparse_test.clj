(ns hazel.sparse-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.ctmc.projection :as p]
            [hazel.ctmc.generator :as g]
            [hazel.ctmc.sparse :as s]
            [hazel.math.linear :as l]
            [hazel.math.numeric :as n]))

(deftest sparse-dense-products
  (doseq [model [f/birth f/death f/switch]]
    (let [q (g/build (p/enumerate model)) kernel (s/compile-kernel q)
          p (mapv #(/ (inc %) (double (reduce + (range 1 (inc (:size q)))))) (range (:size q)))
          dense (g/dense q)]
      (is (< (n/distance (l/rowvec p dense) (s/generator-product kernel p)) 1e-12))
      (is (< (n/distance (l/matvec dense p) (s/backward-product kernel p)) 1e-12))
      (is (n/close? 1 (n/sum (s/stochastic-product kernel p))))
      (is (every? #(>= % 0) (s/stochastic-product kernel p)))
      (is (thrown? clojure.lang.ExceptionInfo (s/stochastic-product kernel p 0))))))
