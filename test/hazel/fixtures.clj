(ns hazel.fixtures
  (:require [hazel.model.network :as net]))

(def birth-source
  {:name "Immigration" :species [:x] :initial {:x 0}
   :parameters {:birth 2}
   :reactions [{:id :birth :reactants {} :products {:x 1} :rate :birth}]})

(def birth (net/compile-model birth-source))

(def death-source
  {:name "Decay" :species [:x] :initial {:x 6} :parameters {:death 0.7}
   :reactions [{:id :death :reactants {:x 1} :products {} :rate :death}]})

(def death (net/compile-model death-source))

(def switch-source
  {:name "Two-state switch" :species [:off :on] :initial {:off 1}
   :parameters {:activate 0.4 :deactivate 0.8}
   :reactions [{:id :activate :reactants {:off 1} :products {:on 1} :rate :activate}
               {:id :deactivate :reactants {:on 1} :products {:off 1} :rate :deactivate}]})

(def switch (net/compile-model switch-source))

(def immigration-death
  (net/compile-model
   {:name "Immigration and decay" :species [:x] :initial {:x 0}
    :parameters {:birth 2 :death 0.5}
    :reactions [{:id :birth :reactants {} :products {:x 1} :rate :birth}
                {:id :death :reactants {:x 1} :products {} :rate :death}]}))
