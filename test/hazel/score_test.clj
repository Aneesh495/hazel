(ns hazel.score-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.model.network :as net]
            [hazel.ctmc.projection :as p]
            [hazel.ctmc.generator :as g]
            [hazel.ctmc.sensitivity :as sensitivity]
            [hazel.inference.observation :as o]
            [hazel.inference.filter :as filter]
            [hazel.inference.score :as score]
            [hazel.math.numeric :as n]))

(deftest normalized-score-finite-differences
  (let [model f/switch q (g/build (p/enumerate model)) initial (g/initial-vector q [1 0])
        sensors (o/compile-sensors model [{:species :on :kind :gaussian :sigma 0.4}])
        observations [{:time 1 :values {:on 0.8}} {:time 2 :values {:on 0.2}}]
        r (score/forward model q initial [:activate :deactivate] sensors observations {})]
    (doseq [parameter [:activate :deactivate]]
      (let [base (get (:parameters model) parameter) h 1e-5
            evaluate (fn [value]
                       (:log-likelihood (filter/forward
                                         (g/for-states (net/with-parameters model {parameter value}) (:states q))
                                         initial sensors observations)))
            fd (/ (- (evaluate (+ base h)) (evaluate (- base h))) (* 2 h))]
        (is (n/close? fd (get (:score r) parameter) 1e-8 1e-7))))))

(deftest poisson-snapshot-information
  (let [q (g/build (p/enumerate f/birth {:bounds [35]}))
        r (sensitivity/propagate f/birth q (g/initial-vector q [0]) [:birth] 3)
        information (score/fisher-exact q r [:birth] first)]
    (is (n/close? 1.5 (get-in information [:matrix 0 0]) 1e-10 1e-8))))
