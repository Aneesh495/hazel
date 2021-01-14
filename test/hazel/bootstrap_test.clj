(ns hazel.bootstrap-test
  (:require [clojure.test :refer [deftest is]]))

(deftest runtime-contract
  (is (= "1.12.6" (clojure-version)))
  (is (<= 17 (.feature (Runtime/version))))
  (is (= 1/3 (+ 1/6 1/6))))
