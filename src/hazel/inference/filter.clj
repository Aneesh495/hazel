(ns hazel.inference.filter
  "Finite-state forward filtering. Escaped paths have unknown observations and
  are discarded explicitly. Reported posteriors condition on retained paths."
  (:require [hazel.ctmc.uniformization :as u]
            [hazel.inference.observation :as observation]
            [hazel.math.numeric :as n]))

(defn condition [predicted log-emission]
  (n/same-size! predicted log-emission)
  (let [log-weights (mapv #(if (zero? %1) ##-Inf (+ (Math/log %1) %2)) predicted log-emission)
        {:keys [probabilities log-normalizer]} (n/log-normalize log-weights)]
    {:probabilities probabilities :log-normalizer log-normalizer}))

(defn forward
  ([q initial sensors observations] (forward q initial sensors observations {}))
  ([q initial sensors observations options]
   (observation/validate-observations sensors observations)
   (n/probability-vector! initial)
   (n/same-size! (:rows q) initial)
   (when (pos? (get initial (:sink q))) (n/fail "Filtering initial mass cannot be escaped" {}))
   (loop [observations observations p initial time 0.0 log-evidence 0.0 steps []]
     (if (empty? observations)
       {:status :complete :probabilities p :log-likelihood log-evidence
        :projection-conditioned? true :steps steps :initial initial}
       (let [measurement (first observations) dt (- (:time measurement) time)
             prediction (u/propagate q p dt options)
             emission (observation/emission q sensors measurement)
             conditioned (condition (:probabilities prediction) emission)
             increment (:log-normalizer conditioned)
             step {:time (:time measurement) :dt dt :observation measurement
                   :predicted (:probabilities prediction) :filtered (:probabilities conditioned)
                   :log-emission emission :log-increment increment
                   :interval-escape (:escape-probability prediction)
                   :series-tail (:series-tail-bound prediction)}]
         (recur (rest observations) (:probabilities conditioned) (:time measurement)
                (+ log-evidence increment) (conj steps step)))))))
