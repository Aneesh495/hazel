(ns hazel.inference.mcmc
  "Particle marginal Metropolis-Hastings in log-parameter coordinates.
  A rejected proposal retains the current likelihood estimate and particle seed."
  (:require [hazel.inference.particle :as particle]
            [hazel.model.network :as net]
            [hazel.simulation.random :as random]
            [hazel.math.probability :as probability]
            [hazel.math.numeric :as n]))

(defn log-prior [value {:keys [kind shape rate lower upper]}]
  (case kind
    :gamma (do (n/positive! :shape shape) (n/positive! :rate rate)
               (if (pos? value)
                 (+ (* shape (Math/log rate)) (- (probability/log-gamma shape))
                    (* (dec shape) (Math/log value)) (- (* rate value))) ##-Inf))
    :uniform (do (n/finite! :lower lower) (n/finite! :upper upper)
                 (when-not (< lower upper) (n/fail "Uniform prior bounds are reversed" {}))
                 (if (<= lower value upper) (- (Math/log (- upper lower))) ##-Inf))
    (n/fail "Unknown prior" {:kind kind})))

(defn sample
  [model parameters sensors observations priors iterations seed
   {:keys [particles proposal-scales max-events]
    :or {particles 200 max-events 1000000} :as options}]
  (n/natural! :iterations iterations) (random/seed seed)
  (let [scales (or proposal-scales (vec (repeat (count parameters) 0.15)))
        initial (mapv #(n/positive! :initial-parameter (get (:parameters model) %)) parameters)
        _ (n/same-size! scales initial)
        _ (doseq [s scales] (n/positive! :proposal-scale s))
        _ (doseq [p parameters] (when-not (contains? priors p) (n/fail "Missing prior" {:parameter p})))
        evaluate (fn [z particle-seed]
                   (let [values (mapv #(Math/exp %) z)
                         prior (if (every? n/finite? values)
                                 (n/sum (map #(log-prior %1 (get priors %2)) values parameters)) ##-Inf)]
                     (if (= prior ##-Inf) {:status :outside-prior :target ##-Inf}
                         (let [candidate (net/with-parameters model (zipmap parameters values))
                               result (particle/filter candidate sensors observations particles particle-seed
                                                       {:max-events max-events})]
                           (case (:status result)
                             :complete {:status :complete :target (+ prior (n/sum z) (:log-likelihood result))
                                        :log-likelihood (:log-likelihood result) :particle-seed particle-seed}
                             :zero-evidence {:status :zero-evidence :target ##-Inf}
                             {:status :incomplete :reason (:status result)})))))
        [particle-seed rng] (random/next-long seed)
        initial-z (mapv #(Math/log %) initial) current (evaluate initial-z particle-seed)]
    (if-not (= :complete (:status current))
      {:status :initial-estimate-failed :reason (:status current) :samples [] :seed seed}
      (loop [i 0 z initial-z current current rng rng samples [] accepted 0]
        (if (= i iterations)
          {:status :complete :seed seed :rng-state rng :samples samples
           :acceptance-rate (if (zero? iterations) 0.0 (/ accepted (double iterations)))
           :accepted accepted :iterations iterations}
          (let [[proposal rng] (reduce (fn [[proposal rng] [value scale]]
                                         (let [[noise rng] (random/normal rng 0 scale)] [(conj proposal (+ value noise)) rng]))
                                       [[] rng] (map vector z scales))
                [particle-seed rng] (random/next-long rng)
                candidate (evaluate proposal particle-seed)]
            (if (= :incomplete (:status candidate))
              {:status :incomplete :reason (:reason candidate) :samples samples :seed seed}
              (let [[u rng] (random/uniform rng)
                    accept? (< (Math/log u) (- (:target candidate) (:target current)))
                    z (if accept? proposal z) current (if accept? candidate current)
                    record {:parameters (zipmap parameters (mapv #(Math/exp %) z))
                            :log-likelihood-estimate (:log-likelihood current)
                            :particle-seed (:particle-seed current) :accepted? accept?}]
                (recur (inc i) z current rng (conj samples record) (if accept? (inc accepted) accepted))))))))))
