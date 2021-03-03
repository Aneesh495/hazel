(ns hazel.math.ode
  "Adaptive RK4 step doubling for independent deterministic reference dynamics."
  (:require [hazel.math.numeric :as n]))

(defn rk4 [f time state h]
  (let [k1 (f time state)
        k2 (f (+ time (* 0.5 h)) (n/axpy (* 0.5 h) k1 state))
        k3 (f (+ time (* 0.5 h)) (n/axpy (* 0.5 h) k2 state))
        k4 (f (+ time h) (n/axpy h k3 state))]
    (n/checked-result :ode-state
                      (mapv #(+ %1 (* (/ h 6.0) (+ %2 (* 2 %3) (* 2 %4) %5))) state k1 k2 k3 k4))))

(defn integrate
  ([f state time] (integrate f state time {}))
  ([f state time {:keys [atol rtol max-steps initial-step]
                  :or {atol 1e-10 rtol 1e-8 max-steps 100000}}]
   (n/nonnegative! :time time) (n/positive! :atol atol) (n/positive! :rtol rtol)
   (n/natural! :max-steps max-steps)
   (let [state (n/checked-result :initial-state state)]
     (loop [t 0.0 state state h (or initial-step (max 1e-8 (/ time 10))) steps 0 rejected 0]
       (cond
         (>= t time) {:status :complete :state state :time time :steps steps :rejected rejected}
         (= steps max-steps) {:status :step-budget-exhausted :state state :time t :steps steps :rejected rejected}
         :else
         (let [h (min h (- time t))]
           (when-not (> (+ t h) t) (n/fail "ODE step cannot advance time" {:time t :step h}))
           (let [coarse (rk4 f t state h)
                 half (rk4 f t state (* 0.5 h))
                 fine (rk4 f (+ t (* 0.5 h)) half (* 0.5 h))
                 correction (n/scale (/ 1.0 15) (mapv - fine coarse))
                 error (reduce max 0.0 (map #(/ (abs %1) (+ atol (* rtol (max (abs %2) (abs %3)))))
                                             correction state fine))
                 factor (if (zero? error) 4.0 (min 4.0 (max 0.1 (* 0.9 (Math/pow error -0.2)))))
                 next-h (* h factor)]
             (if (<= error 1.0)
               (recur (+ t h) (n/add fine correction) next-h (inc steps) rejected)
               (recur t state next-h (inc steps) (inc rejected))))))))))
