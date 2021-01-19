(ns hazel.linear-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.math.linear :as l]))

(deftest exact-reconstruction
  (let [a [[2 1 -1] [-3 -1 2] [-2 1 2]] b [8 -11 -3] x (l/solve a b)]
    (is (= [2 3 -1] x))
    (is (= b (l/matvec a x)))
    (is (= (l/identity 3) (l/multiply a (l/inverse a))))
    (is (= -1 (l/determinant a))))
  (is (= [1/3 1/3] (l/solve [[2 1] [1 2]] [1 1])))
  (is (= 1 (l/determinant []))))

(deftest singular-and-nullspace
  (let [a [[1 2 3] [2 4 6]] basis (l/nullspace a)]
    (is (= 1 (l/rank a)))
    (is (= 2 (count basis)))
    (doseq [v basis] (is (= [0 0] (l/matvec a v)))))
  (is (= [[1 0] [0 1]] (l/nullspace [] 2)))
  (is (thrown? clojure.lang.ExceptionInfo (l/solve [[1 2] [2 4]] [1 2])))
  (is (thrown? clojure.lang.ExceptionInfo (l/solve [[1 2]] [1])))
  (is (thrown? clojure.lang.ExceptionInfo (l/matrix! [[1] [2 3]]))))

(deftest pivot-swap-and-multiply
  (is (= -1 (l/determinant [[0 1] [1 0]])))
  (is (= [2 1] (l/solve [[0 1] [1 0]] [1 2])))
  (is (= [[14 32] [32 77]] (l/multiply [[1 2 3] [4 5 6]] [[1 4] [2 5] [3 6]]))))
