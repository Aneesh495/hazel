(ns hazel.ctmc.projection
  "Reachable finite projections. Frontier edges are retained, never reflected."
  (:require [hazel.model.network :as net]
            [hazel.model.kinetics :as k]
            [hazel.model.structure :as structure]
            [hazel.math.numeric :as n]))

(defn bounds! [model bounds]
  (when-not (and (vector? bounds) (= (count bounds) (count (:species model))))
    (n/fail "Projection bounds must follow species order" {:bounds bounds}))
  (doseq [b bounds] (n/natural! :bound b))
  bounds)

(defn inside? [bounds state] (every? true? (map <= state bounds)))

(defn default-bounds [model]
  (mapv #(or %1 (max 8 %2)) (structure/nonnegative-bounds model) (:initial model)))

(defn enumerate
  ([model] (enumerate model {}))
  ([model {:keys [bounds max-states seeds]
           :or {max-states 10000}}]
   (net/compiled! model)
   (n/natural! :max-states max-states)
   (when (zero? max-states) (n/fail "Projection state budget must be positive" {}))
   (let [bounds (bounds! model (or bounds (default-bounds model)))
         seeds (vec (distinct (or seeds [(:initial model)])))]
     (when (or (empty? seeds) (> (count seeds) max-states))
       (n/fail "Invalid projection seeds" {:count (count seeds) :budget max-states}))
     (doseq [s seeds]
       (net/state! model s)
       (when-not (inside? bounds s) (n/fail "Seed outside projection bounds" {:seed s})))
     (loop [states seeds index (zipmap seeds (range)) cursor 0 edges [] frontier []]
       (if (= cursor (count states))
         {:states states :index index :edges edges :frontier frontier :bounds bounds
          :model-hash (:hash model) :max-states max-states
          :closed? (empty? frontier)
          :budget-exhausted? (boolean (some #(= :budget (:reason %)) frontier))}
         (let [transitions (k/transitions model (states cursor))
               [states index row frontier]
               (reduce (fn [[states index row frontier] {:keys [target] :as edge}]
                         (let [known (get index target)
                               reason (cond (not (inside? bounds target)) :bounds
                                            (and (nil? known) (= (count states) max-states)) :budget)]
                           (if reason
                             [states index (conj row (assoc edge :destination :escape))
                              (conj frontier (assoc edge :source cursor :reason reason))]
                             (let [destination (or known (count states))]
                               ;; Every newly admitted state receives a stable BFS
                               ;; index. Boundary edges stay in both row and frontier.
                               [(if known states (conj states target))
                                (if known index (assoc index target destination))
                                (conj row (assoc edge :destination destination)) frontier]))))
                       [states index [] frontier] transitions)]
           (recur states index (inc cursor) (conj edges row) frontier)))))))

(defn grow-bounds [bounds factor]
  (n/positive! :growth factor)
  (when (<= factor 1) (n/fail "Projection growth factor must exceed one" {}))
  (mapv #(max (inc %) (long (Math/ceil (* factor %)))) bounds))

(defn fixed
  "Recompute edges on a fixed state set, including states reached at zero base rate.
  Used for parameter derivatives, schedules, and likelihood comparisons."
  [model states]
  ;; Fixed means fixed populations, not frozen hazards. Re-evaluate every
  ;; channel so a parameter change can enable edges missing at the base rate.
  (net/compiled! model)
  (when-not (and (vector? states) (seq states) (= (count states) (count (set states))))
    (n/fail "Fixed projection requires distinct states" {}))
  (doseq [state states] (net/state! model state))
  (let [index (zipmap states (range))
        edges (mapv (fn [state]
                      (mapv #(assoc % :destination (get index (:target %) :escape))
                            (k/transitions model state))) states)
        frontier (vec (for [[i row] (map-indexed vector edges)
                            edge row :when (= :escape (:destination edge))]
                        (assoc edge :source i :reason :fixed)))]
    {:states states :index index :edges edges :frontier frontier
     :model-hash (:hash model) :closed? (empty? frontier) :budget-exhausted? false}))
