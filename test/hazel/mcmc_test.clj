(ns hazel.mcmc-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.inference.observation :as o]
            [hazel.inference.mcmc :as mcmc]
            [hazel.inference.particle :as particle]
            [hazel.inference.diagnostics :as diagnostics]
            [hazel.random-test :as sample]
            [hazel.simulation.random :as random]))

(deftest pseudo-marginal-rejection-retains-estimate
  (let [sensors (o/compile-sensors f/birth [{:species :x :kind :gaussian :sigma 1}])
        observations [{:time 2 :values {:x 5}}]
        run #(mcmc/sample f/birth [:birth] sensors observations
                          {:birth {:kind :gamma :shape 2 :rate 1}} 30 42
                          {:particles 40 :proposal-scales [2.0]})
        r (run)]
    (is (= :complete (:status r)))
    (is (= r (run)))
    (is (< 0 (:acceptance-rate r) 1))
    (doseq [[previous current] (partition 2 1 (:samples r)) :when (not (:accepted? current))]
      (is (= (dissoc previous :accepted?) (dissoc current :accepted?))))))

(deftest chain-diagnostics
  (let [a (sample/samples random/normal 1 500) b (sample/samples random/normal 2 500)
        rhat (diagnostics/split-rhat [a b])]
    (is (< (:value rhat) 1.05))
    (is (> (diagnostics/ess a) 250)))
  (is (> (:value (diagnostics/split-rhat [(vec (range 100)) (vec (range 100 200))])) 1.5)))

(deftest outside-prior-does-not-run-likelihood-regression
  (let [calls (atom 0)]
    (with-redefs [particle/filter (fn [& _]
                                   (swap! calls inc)
                                   {:status :complete :log-likelihood 0.0})]
      (let [r (mcmc/sample f/switch [:activate :deactivate] [] []
                           {:activate {:kind :uniform :lower 0.399 :upper 0.401}
                            :deactivate {:kind :uniform :lower 0.799 :upper 0.801}}
                           5 42 {:proposal-scales [10.0 10.0]})]
        (is (= :complete (:status r)))
        (is (= 1 @calls))
        (is (zero? (:accepted r)))))))
