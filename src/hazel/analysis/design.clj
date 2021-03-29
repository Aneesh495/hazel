(ns hazel.analysis.design
  "Independent-snapshot information design. Correlated repeated observations
  require the full filtering likelihood and cannot simply add snapshot matrices."
  (:require [hazel.math.symmetric :as symmetric]
            [hazel.math.linear :as l]
            [hazel.math.numeric :as n]
            [hazel.ctmc.sensitivity :as sensitivity]
            [hazel.inference.score :as score]))

(defn information-summary [matrix regularization]
  (n/positive! :regularization regularization)
  (let [values (:values (symmetric/eigen matrix))]
    (when (some #(< % -1e-9) values) (n/fail "Information matrix is not positive semidefinite" {}))
    (let [values (mapv #(max 0.0 %) values) shifted (mapv #(+ regularization %) values)]
      {:eigenvalues values :rank (count (filter #(> % 1e-9) values))
       :log-determinant (n/sum (map #(Math/log %) shifted))
       :negative-inverse-trace (- (n/sum (map #(/ 1.0 %) shifted)))
       :minimum-eigenvalue (apply min shifted)
       :condition-number (/ (apply max shifted) (apply min shifted))})))

(defn objective [matrix criterion regularization]
  (get (information-summary matrix regularization)
       (case criterion :d :log-determinant :a :negative-inverse-trace :e :minimum-eigenvalue
             (n/fail "Unknown design criterion" {:criterion criterion}))))

(defn snapshot-candidates [model q initial parameters times observable options]
  (mapv (fn [time]
          (let [r (sensitivity/propagate model q initial parameters time options)
                info (score/fisher-exact q r parameters observable)]
            {:id time :time time :cost 1.0 :matrix (:matrix info) :observable-mass (:observable-mass info)})) times))

(defn greedy
  [candidates budget {:keys [criterion regularization] :or {criterion :d regularization 1e-6}}]
  (n/nonnegative! :budget budget)
  (when (empty? candidates) (n/fail "Design requires candidates" {}))
  (when-not (= (count candidates) (count (set (map :id candidates))))
    (n/fail "Design candidate ids must be unique" {}))
  (let [size (count (:matrix (first candidates)))]
    (doseq [candidate candidates]
      (n/positive! :cost (:cost candidate))
      (when-not (= [size size] (l/shape (:matrix candidate))) (n/fail "Design matrix dimension mismatch" {}))
      (information-summary (:matrix candidate) regularization))
    (loop [remaining (vec candidates) selected [] matrix (l/zeros size size) spent 0.0 history []]
      (let [current (objective matrix criterion regularization)
            eligible (filter #(<= (+ spent (:cost %)) (+ budget 1e-12)) remaining)]
        (if (empty? eligible)
          {:selected (mapv :id selected) :matrix matrix :spent spent :budget budget :history history
           :objective current :criterion criterion :regularization regularization}
          (let [ranked (sort-by (fn [c] [(- (:gain-per-cost c)) (pr-str (:id c))])
                                (map #(let [gain (- (objective (l/add matrix (:matrix %)) criterion regularization) current)]
                                        (assoc % :gain-per-cost (/ gain (:cost %)))) eligible))
                choice (first ranked)]
            (recur (filterv #(not= (:id %) (:id choice)) remaining) (conj selected choice)
                   (l/add matrix (:matrix choice)) (+ spent (:cost choice))
                   (conj history {:id (:id choice) :gain-per-cost (:gain-per-cost choice)}))))))))
