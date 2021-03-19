(ns hazel.particle-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.inference.observation :as o]
            [hazel.inference.particle :as particle]
            [hazel.inference.filter :as filter]
            [hazel.ctmc.projection :as p]
            [hazel.ctmc.generator :as g]
            [hazel.math.numeric :as n]))

(deftest systematic-resampling
  (let [[indexes _] (particle/systematic-resample 42 [0 1 0 0])]
    (is (= [1 1 1 1] indexes)))
  (let [[indexes _] (particle/systematic-resample 42 [0.25 0.25 0.25 0.25])]
    (is (= [0 1 2 3] indexes))))

(deftest particle-likelihood-agrees-in-expectation
  (let [sensors (o/compile-sensors f/switch [{:species :on :kind :gaussian :sigma 0.5}])
        observations [{:time 1 :values {:on 0.9}} {:time 2 :values {:on 0.1}}]
        q (g/build (p/enumerate f/switch))
        reference (Math/exp (:log-likelihood (filter/forward q (g/initial-vector q [1 0]) sensors observations)))
        results (mapv #(particle/filter f/switch sensors observations 300 %)
                      (range 100 120))
        values (mapv #(Math/exp (:log-likelihood %)) results)
        mean (/ (n/sum values) (count values))
        sd (Math/sqrt (/ (n/sum (map #(let [d (- % mean)] (* d d)) values)) (dec (count values))))]
    (is (every? #(= :complete (:status %)) results))
    (is (< (abs (- reference mean)) (* 6 (/ sd (Math/sqrt (count values))))))
    (is (= (first results) (particle/filter f/switch sensors observations 300 100)))))
