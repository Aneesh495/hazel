(ns hazel.inference.observation
  "Independent observation channels. Missing species in :values means unobserved."
  (:require [hazel.model.network :as net]
            [hazel.math.numeric :as n]
            [hazel.math.probability :as p]))

(defn compile-sensors [model sensors]
  (net/compiled! model)
  (when-not (and (vector? sensors) (= (count sensors) (count (set (map :species sensors)))))
    (n/fail "Sensors must be a vector with distinct species" {}))
  (mapv (fn [sensor]
          (net/keys! sensor #{:species :kind :probability :efficiency :background :sigma :scale :offset} :sensor)
          (let [index (get (:index model) (:species sensor))]
            (when (nil? index) (n/fail "Sensor references undeclared species" {:sensor sensor}))
            (case (:kind sensor)
              :exact nil
              :binomial (let [p (n/nonnegative! :detection-probability (:probability sensor))]
                          (when (> p 1) (n/fail "Detection probability exceeds one" {})))
              :poisson (do (n/nonnegative! :efficiency (:efficiency sensor 1.0))
                           (n/nonnegative! :background (:background sensor 0.0)))
              :gaussian (do (n/positive! :sigma (:sigma sensor))
                            (n/finite! :scale (:scale sensor 1.0))
                            (n/finite! :offset (:offset sensor 0.0)))
              (n/fail "Unknown sensor kind" {:kind (:kind sensor)}))
            (assoc sensor :index index))) sensors))

(defn validate-observations [sensors observations]
  (let [species (set (map :species sensors))]
    (doseq [observation observations]
      (net/keys! observation #{:time :values} :observation)
      (n/nonnegative! :observation-time (:time observation))
      (when-not (map? (:values observation)) (n/fail "Observation values must be a map" {}))
      (doseq [[k v] (:values observation)]
        (when-not (species k) (n/fail "Observation has no sensor" {:species k}))
        (let [sensor (first (filter #(= k (:species %)) sensors))]
          (if (= :gaussian (:kind sensor)) (n/finite! :measurement v) (n/natural! :measurement v)))))
    (when-not (apply <= 0 (map :time observations))
      (n/fail "Observation times must be nondecreasing" {})))
  observations)

(defn channel-log-likelihood [sensor population value]
  (case (:kind sensor)
    :exact (if (= population value) 0.0 ##-Inf)
    :binomial (p/binomial-log-pmf value population (:probability sensor))
    :poisson (p/poisson-log-pmf value (+ (:background sensor 0.0) (* (:efficiency sensor 1.0) population)))
    :gaussian (p/normal-log-pdf value (+ (:offset sensor 0.0) (* (:scale sensor 1.0) population)) (:sigma sensor))
    (n/fail "Uncompiled sensor kind" {})))

(defn log-likelihood [sensors state observation]
  (reduce (fn [total sensor]
            (if (contains? (:values observation) (:species sensor))
              (+ total (channel-log-likelihood sensor (state (:index sensor))
                                                (get (:values observation) (:species sensor)))) total))
          0.0 sensors))

(defn emission [q sensors observation]
  (conj (mapv #(log-likelihood sensors % observation) (:states q)) ##-Inf))
