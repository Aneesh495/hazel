(ns hazel.importance-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.simulation.importance :as importance]
            [hazel.math.probability :as p]
            [hazel.math.numeric :as n]))

(deftest tilted-poisson-tail
  (let [r (importance/estimate f/birth {:birth 8} 1 #(>= (first %) 10) 4000 123 {})
        truth (n/sum (map #(p/poisson-pmf % 2) (range 10 60)))]
    (is (= :complete (:status r)))
    (is (< (abs (- truth (:estimate r))) (* 6 (:standard-error r))))
    (is (> (:proposal-hits r) 500))
    (is (> (:event-ess r) 100))))

(deftest support-and-pilot-contracts
  (is (thrown? clojure.lang.ExceptionInfo (importance/proposal f/birth {:birth 0})))
  (let [r (importance/fit-tilt f/birth {:birth 6} [:birth] 1 #(>= (first %) 8) 200 2 42 {})]
    (is (= :complete (:status r)))
    (is (> (get (:parameters r) :birth) 6))))
