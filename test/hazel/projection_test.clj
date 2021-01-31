(ns hazel.projection-test
  (:require [clojure.test :refer [deftest is]]
            [hazel.fixtures :as f]
            [hazel.ctmc.projection :as p]))

(deftest reachable-space
  (let [projection (p/enumerate f/death)]
    (is (= 7 (count (:states projection))))
    (is (:closed? projection))
    (is (= [0] (last (:states projection)))))
  (let [projection (p/enumerate f/birth {:bounds [4]})]
    (is (= [[0] [1] [2] [3] [4]] (:states projection)))
    (is (= 1 (count (:frontier projection))))
    (is (= :bounds (-> projection :frontier first :reason)))
    (is (not (:closed? projection)))))

(deftest honest-budgets
  (let [projection (p/enumerate f/birth {:bounds [100] :max-states 3})]
    (is (= 3 (count (:states projection))))
    (is (:budget-exhausted? projection))
    (is (= :budget (-> projection :frontier first :reason))))
  (is (thrown? clojure.lang.ExceptionInfo (p/enumerate f/death {:bounds [2]})))
  (is (thrown? clojure.lang.ExceptionInfo (p/enumerate f/birth {:max-states 0})))
  (is (= [1 6] (p/grow-bounds [0 3] 2))))
