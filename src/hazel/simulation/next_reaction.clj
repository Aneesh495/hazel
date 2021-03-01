(ns hazel.simulation.next-reaction
  "Gibson-Bruck next-reaction scheduling with persistent indexed clocks.
  Unaffected hazards retain their clocks. Changed hazards rescale residual time."
  (:require [hazel.simulation.random :as random]
            [hazel.model.kinetics :as k]
            [hazel.model.network :as net]
            [hazel.math.numeric :as n]))

(defn- schedule [rng time rate]
  (if (zero? rate) [##Inf rng]
      (let [[wait rng] (random/exponential rng rate)] [(+ time wait) rng])))

(defn simulate
  ([model horizon seed] (simulate model horizon seed {}))
  ([model horizon seed {:keys [initial max-events record?]
                        :or {max-events 1000000 record? true}}]
   (n/nonnegative! :horizon horizon) (random/seed seed) (n/natural! :max-events max-events)
   (let [initial (net/state! model (or initial (:initial model)))
         dependencies (k/dependency-graph model) initial-rates (k/hazards model initial)
         [clocks rng] (reduce (fn [[clocks rng] rate]
                               (let [[t rng] (schedule rng 0 rate)] [(conj clocks t) rng]))
                             [[] seed] initial-rates)
         queue (into (sorted-set) (map-indexed #(vector %2 %1) clocks))]
     (loop [state initial time 0.0 rates initial-rates clocks clocks queue queue
            rng rng events [] count 0]
       (let [[next-time reaction] (first queue)
             base {:model-hash (:hash model) :algorithm :next-reaction :seed seed
                   :initial-state initial :horizon horizon :events events :event-count count
                   :rng-state rng :final-state state :final-time time :recorded? record?}]
         (cond
           (or (nil? next-time) (> next-time horizon)) (assoc base :status :complete :final-time (double horizon))
           (= count max-events) (assoc base :status :event-budget-exhausted)
           :else
           (let [_ (when-not (> next-time time)
                     (n/fail "Next-reaction time cannot advance" {:time time :next-time next-time}))
                 next-state (k/fire model state reaction)
                 affected (sort (conj (dependencies reaction) reaction))
                 [rates clocks queue rng]
                 (reduce
                  (fn [[rates clocks queue rng] j]
                    (let [old-rate (rates j)
                          new-rate (k/propensity (get (:reactions model) j) next-state)
                          old-time (clocks j)
                          [new-time rng]
                          (if (or (= j reaction) (zero? old-rate))
                            (schedule rng next-time new-rate)
                            [(if (zero? new-rate) ##Inf
                                 (+ next-time (* (/ old-rate new-rate) (- old-time next-time)))) rng])]
                      [(assoc rates j new-rate) (assoc clocks j new-time)
                       (conj (disj queue [old-time j]) [new-time j]) rng]))
                  [rates clocks queue rng] affected)
                 event {:time next-time :reaction (:id (get (:reactions model) reaction)) :state next-state}]
             (recur next-state next-time rates clocks queue rng
                    (if record? (conj events event) events) (inc count)))))))))
