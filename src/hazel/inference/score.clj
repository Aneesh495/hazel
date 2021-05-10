(ns hazel.inference.score
  "Likelihood gradients include the derivative of every filtering normalizer."
  (:require [hazel.ctmc.sensitivity :as sensitivity]
            [hazel.inference.observation :as observation]
            [hazel.math.numeric :as n]
            [hazel.math.linear :as l]))

(defn forward
  [model q initial parameters sensors observations options]
  (observation/validate-observations sensors observations)
  (n/probability-vector! initial)
  (n/same-size! (:rows q) initial)
  (when-not (= (:hash model) (:model-hash q)) (n/fail "Score generator/model mismatch" {}))
  (when (pos? (get initial (:sink q))) (n/fail "Score initial mass cannot be escaped" {}))
  (loop [measurements observations time 0.0 p initial
         tangents (zipmap parameters (repeat (vec (repeat (:size q) 0.0))))
         score (zipmap parameters (repeat 0.0)) log-likelihood 0.0 steps []]
    (if (empty? measurements)
      {:status :complete :log-likelihood log-likelihood :score score
       :probabilities p :sensitivities tangents :steps steps :projection-conditioned? true}
      (let [measurement (first measurements)
            predicted (sensitivity/propagate model q p parameters (- (:time measurement) time)
                                              (assoc options :initial-sensitivities (mapv tangents parameters)))
            logs (observation/emission q sensors measurement)
            maximum (apply max logs)
            _ (when (= maximum ##-Inf) (n/fail "Impossible observation" {}))
            emission (mapv #(Math/exp (- % maximum)) logs)
            weighted (mapv * (:probabilities predicted) emission)
            z (n/positive! :observation-evidence (n/sum weighted))
            filtered (n/scale (/ 1.0 z) weighted)
            weighted-s (mapv #(mapv * (get (:sensitivities predicted) %) emission) parameters)
            dz (mapv n/sum weighted-s)
            next-tangents (zipmap parameters
                                 (mapv (fn [s derivative]
                                         (n/scale (/ 1.0 z) (mapv #(- %1 (* %2 derivative)) s filtered)))
                                       weighted-s dz))
            increment (zipmap parameters (mapv #(/ % z) dz))]
        (recur (rest measurements) (:time measurement) filtered next-tangents
               (merge-with + score increment) (+ log-likelihood (Math/log z) maximum)
               (conj steps {:time (:time measurement) :score-increment increment
                            :probabilities filtered}))))))

(defn fisher-exact
  "Fisher information in an exact observable at a single snapshot.
  Escaped outcomes are excluded and no tail information is imputed."
  [q transient-result parameters observable]
  (let [groups (reduce (fn [groups [i state]]
                         (let [value (observable state)
                               p (get (:probabilities transient-result) i)
                               derivatives (mapv #(get (get (:sensitivities transient-result) %) i) parameters)]
                           (update groups value
                                   (fn [old]
                                     {:probability (+ (:probability old 0.0) p)
                                      :derivatives (n/add (:derivatives old (vec (repeat (count parameters) 0.0))) derivatives)}))))
                       {} (map-indexed vector (:states q)))
        information (reduce l/add (l/zeros (count parameters) (count parameters))
                            (for [{:keys [probability derivatives]} (vals groups) :when (pos? probability)]
                              (l/scale (/ 1.0 probability) (l/outer derivatives derivatives))))]
    {:parameters parameters :matrix information :observable-mass (n/sum (map :probability (vals groups)))
     :outcomes groups :interpretation :retained-outcome-contribution}))
