(ns hazel.generator-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.ctmc.projection :as p]
            [hazel.ctmc.generator :as g]))

(deftest escape-is-absorbing
  (let [q (g/build (p/enumerate f/birth {:bounds [2]}))]
    (is (= [[-2N 2N 0 0] [0 -2N 2N 0] [0 0 -2N 2N] [0 0 0 0]] (g/dense q)))
    (is (= [1.0 0.0 0.0 0.0] (g/initial-vector q [0])))
    (is (= [0 0 0 0] (nth (g/dense (g/absorb q #{1})) 1)))
    (is (thrown? clojure.lang.ExceptionInfo (g/validate (assoc-in q [:exits 0] 0))))
    (is (thrown? clojure.lang.ExceptionInfo (g/validate (assoc-in q [:rows 3] [[0 1]]))))))

(deftest self-events-do-not-change-populations
  (let [q (g/build {:states [[1]] :model-hash "self" :closed? true
                    :edges [[{:destination 0 :rate 5 :reaction 0}]]})]
    (is (= [0 0] (:exits q)))
    (is (= 1 (count (first (:events q)))))))
