(ns hazel.certificate-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.model.network :as net]
            [hazel.ctmc.projection :as p]
            [hazel.ctmc.generator :as g]
            [hazel.ctmc.certificate :as certificate]
            [hazel.analysis.certificate-check :as checker]
            [hazel.math.rational :as rational]))

(deftest exact-exponential-enclosures
  (doseq [x [0 1/3 1] order [4 8 16]]
    (let [{:keys [lower upper]} (rational/exp-enclosure x order)]
      (is (<= (double lower) (+ 1e-15 (Math/exp (double x)))))
      (is (>= (+ 1e-15 (double upper)) (Math/exp (double x)))))))

(deftest independent-rational-certificate
  (let [q (g/build (p/enumerate f/birth {:bounds [18]}))
        c (certificate/create f/birth q 1)
        interval (certificate/event-interval c #(>= (first %) 3))
        exponential (rational/exp-enclosure 1 40)
        truth-lower (- 1 (/ 5 (*' (:lower exponential) (:lower exponential))))
        truth-upper (- 1 (/ 5 (*' (:upper exponential) (:upper exponential))))]
    (is (= :verified (:status (checker/check f/birth c))))
    (is (<= (:lower interval) truth-lower truth-upper (:upper interval)))
    (is (< (double (:truncation-upper c)) 1e-11))
    (doseq [bad [(update c :mass-deficit + 1/100)
                 (update-in c [:lower-probabilities 0] + 1/100)
                 (assoc-in c [:rows 0 0 1] 3)
                 (assoc c :model-hash "wrong")]]
      (is (thrown? clojure.lang.ExceptionInfo (checker/check f/birth bad))))))

(deftest reject-inexact-rate-inputs
  (let [q (g/build (p/enumerate f/death))]
    (is (thrown? clojure.lang.ExceptionInfo (certificate/create f/death q 1))))
  (let [model (net/with-parameters f/death {:death 7/10}) q (g/build (p/enumerate model))]
    (is (= :verified (:status (checker/check model (certificate/create model q 1/2)))))))

(deftest disabled-floating-rate-is-still-inexact-regression
  (let [model (net/with-parameters f/birth {:birth 0.0})
        q (g/for-states model [[0] [1]])]
    (is (thrown? clojure.lang.ExceptionInfo (certificate/create model q 1)))))
