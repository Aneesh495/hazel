(ns hazel.network-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.model.network :as net]))

(deftest compiled-data
  (is (= [0] (:initial f/birth)))
  (is (= [1] (get-in f/birth [:reactions 0 :delta])))
  (is (= {:x 3} (net/state-map f/birth [3])))
  (is (= 0 (net/reaction-index f/birth :birth)))
  (is (= 3 (get-in (net/with-parameters f/birth {:birth 3}) [:reactions 0 :constant])))
  (is (not= (:hash f/birth) (:hash (net/with-parameters f/birth {:birth 3})))))

(deftest invalid-models
  (doseq [bad [(assoc f/birth-source :speceis [:x])
               (assoc f/birth-source :species [:x :x])
               (assoc f/birth-source :initial {:y 1})
               (assoc-in f/birth-source [:parameters :birth] -1)
               (assoc-in f/birth-source [:reactions 0 :rate] :missing)
               (assoc-in f/birth-source [:reactions 0 :reactants] {:x 1.5})
               (update f/birth-source :reactions #(into % %))]]
    (is (thrown? clojure.lang.ExceptionInfo (net/compile-model bad))))
  (doseq [state [[-1] [0 1] [1.5] nil]]
    (is (thrown? clojure.lang.ExceptionInfo (net/state! f/birth state))))
  (is (thrown? clojure.lang.ExceptionInfo (net/with-parameters f/birth {:typo 1}))))
