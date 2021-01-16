(ns hazel.numeric-test
  (:require [clojure.test :refer [deftest is testing]]
            [hazel.math.numeric :as n]))

(deftest compensated-sums
  (is (= 1.0 (n/sum [1.0e16 1.0 -1.0e16])))
  (is (= 0.0 (n/sum [])))
  (is (= 32.0 (n/dot [1 2 3] [4 5 6])))
  (is (thrown? clojure.lang.ExceptionInfo (n/dot [1] [1 2])))
  (is (= [5 8] (n/axpy 2 [1 2] [3 4]))))

(deftest validation
  (doseq [bad [nil "1" ##NaN ##Inf ##-Inf]]
    (is (thrown? clojure.lang.ExceptionInfo (n/finite! :x bad))))
  (doseq [bad [-1 1/2 1.2 nil]]
    (is (thrown? clojure.lang.ExceptionInfo (n/natural! :x bad))))
  (is (= 2 (n/natural! :x 2)))
  (is (thrown? clojure.lang.ExceptionInfo (n/positive! :x 0)))
  (is (thrown? clojure.lang.ExceptionInfo (n/tolerance! 1))))

(deftest probability-contracts
  (is (= [0.2 0.8] (n/probability-vector! [0.2 0.8])))
  (is (= [0.2 0.3] (n/probability-vector! [0.2 0.3] true)))
  (doseq [p [[] [0.2] [-0.1 1.1] [##NaN] [2]]]
    (is (thrown? clojure.lang.ExceptionInfo (n/probability-vector! p))))
  (is (= {:probabilities [0.25 0.75] :normalizer 4.0} (n/normalize [1 3])))
  (is (thrown? clojure.lang.ExceptionInfo (n/normalize [0 0])))
  (is (n/close? 1.0 (n/sum (:probabilities (n/log-normalize [-1000 -1001])))))
  (is (= ##-Inf (n/log-sum-exp [##-Inf ##-Inf])))
  (is (thrown? clojure.lang.ExceptionInfo (n/log-normalize [##-Inf]))))
