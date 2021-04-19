(ns hazel.cli-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.cli :as cli]
            [hazel.fixtures :as f]
            [hazel.bundle-test :as temp]
            [hazel.io.report :as report]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(deftest end-to-end-artifact-replay
  (temp/with-directory
    (fn [dir]
      (let [study (io/file dir "study.edn") output (io/file dir "result")]
        (spit study (pr-str {:title "<escaped & safe>" :model f/birth-source :experiment {:kind :transient :time 1}}))
        (is (= 0 (:exit (cli/invoke ["check" (str study)]))))
        (let [r (cli/invoke ["run" (str study) "--out" (str output)])
              bundle (get-in r [:data :bundle]) html (slurp (io/file output "index.html"))]
          (is (= 0 (:exit r)))
          (is (= 0 (:exit (cli/invoke ["replay" bundle]))))
          (is (str/includes? html "&lt;escaped &amp; safe&gt;"))
          (is (not (str/includes? html "<script")))
          (is (str/includes? html "Content-Security-Policy")))))))

(deftest stable-errors
  (is (= 0 (:exit (cli/invoke ["help"]))))
  (is (= 1 (:exit (cli/invoke ["nope"]))))
  (is (= 1 (:exit (cli/invoke ["run"]))))
  (is (= 1 (:exit (cli/invoke ["check" "does-not-exist.edn"]))))
  (is (= "&lt;&gt;&amp;&quot;&#39;" (report/escape "<>&\"'"))))
