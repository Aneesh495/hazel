(ns hazel.edn-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.io.edn :as e]))

(deftest inert-single-value
  (is (= {:x 1/3} (e/read-string "; comment\n{:x 1/3}")))
  (doseq [bad ["" "{} {}" "[1" "#=(System/exit 0)" "#foo 1"
               "#inst \"2026-01-01\"" "#uuid \"00000000-0000-0000-0000-000000000000\""]]
    (is (thrown? clojure.lang.ExceptionInfo (e/read-string bad))))
  (is (thrown? clojure.lang.ExceptionInfo (e/read-string "\"é\"" 3))))

(deftest content-hashing
  (is (= (e/sha256 {:x 1 :y #{3 2}}) (e/sha256 (array-map :y #{2 3} :x 1))))
  (is (not= (e/sha256 {:x 1}) (e/sha256 {:x 2})))
  (is (= {:x [1 2] :set #{:a :b}} (e/read-string (e/canonical {:set #{:b :a} :x [1 2]}))))
  (is (thrown? clojure.lang.ExceptionInfo (e/canonical (fn [] 1))))
  (is (thrown? clojure.lang.ExceptionInfo (e/canonical ##NaN))))
