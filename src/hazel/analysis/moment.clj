(ns hazel.analysis.moment
  "Symbolic chemical-master-equation moment hierarchies.
  A hierarchy is integrated only if polynomial closure is exact."
  (:require [hazel.math.polynomial :as polynomial]
            [hazel.math.linear :as l]
            [hazel.math.numeric :as n]
            [hazel.math.ode :as ode]))

(defn propensity-polynomial [reaction]
  (let [dimensions (count (:consume reaction))]
    (reduce polynomial/multiply (polynomial/constant dimensions (:constant reaction))
            (map-indexed
             (fn [i order]
               (let [x (polynomial/variable dimensions i)
                     falling (reduce (fn [p j]
                                       (polynomial/multiply p (polynomial/add x (polynomial/constant dimensions (- j)))))
                                     (polynomial/constant dimensions 1N) (range order))
                     factorial (reduce *' 1N (range 1 (inc order)))]
                 (polynomial/scale (/ 1N factorial) falling))) (:consume reaction)))))

(defn generator-action [model observable]
  (reduce polynomial/add {}
          (for [reaction (:reactions model)]
            (polynomial/multiply
             (propensity-polynomial reaction)
             (polynomial/add (polynomial/shift observable (:delta reaction))
                             (polynomial/scale -1 observable))))))

(defn compile-hierarchy [model maximum-order]
  (let [basis (polynomial/basis (count (:species model)) maximum-order)
        index (zipmap basis (range))
        equations (mapv #(generator-action model (polynomial/monomial %)) basis)
        unclosed (set (remove #(contains? index %) (mapcat keys equations)))]
    ;; The degree cutoff is a closure question, not a truncation instruction.
    ;; Missing monomials make the hierarchy unclosed and prevent integration.
    {:basis basis :equations equations :maximum-order maximum-order
     :closed? (empty? unclosed) :unclosed-monomials unclosed
     :matrix (when (empty? unclosed)
               (mapv (fn [equation] (mapv #(get equation % 0N) basis)) equations))}))

(defn solve [model maximum-order time options]
  (let [hierarchy (compile-hierarchy model maximum-order)]
    (when-not (:closed? hierarchy)
      (n/fail "Moment hierarchy is not closed; no closure approximation is applied"
              {:unclosed-monomials (:unclosed-monomials hierarchy)}))
    (let [initial (mapv #(double (polynomial/evaluate (polynomial/monomial %) (:initial model))) (:basis hierarchy))
          result (ode/integrate (fn [_ moments] (l/matvec (:matrix hierarchy) moments)) initial time options)]
      {:status (:status result) :moments (zipmap (:basis hierarchy) (:state result))
       :basis (:basis hierarchy) :time (:time result) :steps (:steps result)
       :closure :exact-polynomial :integration :adaptive-rk4})))
