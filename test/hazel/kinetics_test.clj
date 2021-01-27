(ns hazel.kinetics-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.model.network :as net]
            [hazel.model.kinetics :as k]))

(deftest elementary-kinetics
  (is (= [2N] (k/hazards f/birth [100])))
  (is (= [101] (k/fire f/birth [100] :birth)))
  (is (= [] (k/transitions f/death [0])))
  (is (thrown? clojure.lang.ExceptionInfo (k/fire f/death [0] :death)))
  (is (= [#{1} #{1}] (k/dependency-graph
                          (net/compile-model
                           {:species [:x] :initial {:x 2} :parameters {}
                            :reactions [{:id :birth :reactants {} :products {:x 1} :rate 1}
                                        {:id :death :reactants {:x 1} :products {} :rate 2}]})))))

(deftest combinatorial-convention
  (let [m (net/compile-model
           {:species [:a :b] :initial {:a 4} :parameters {:k 3}
            :reactions [{:id :dimer :reactants {:a 2} :products {:b 1} :rate :k}]})]
    (is (= [18N] (k/hazards m [4 0])))
    (is (= [2 1] (k/fire m [4 0] :dimer)))
    (is (= 6N (k/parameter-derivative (first (:reactions m)) [4 0] :k)))
    (is (= [0N] (k/hazards (net/with-parameters m {:k 0}) [4 0])))))
