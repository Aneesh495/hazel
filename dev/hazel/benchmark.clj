(ns hazel.benchmark
  "Descriptive microbenchmarks. Every measurement performs and consumes real work."
  (:refer-clojure :exclude [run!])
  (:require [hazel.model.network :as net]
            [hazel.ctmc.generator :as generator]
            [hazel.ctmc.projection :as projection]
            [hazel.ctmc.sparse :as sparse]
            [hazel.ctmc.uniformization :as uniform]
            [hazel.math.linear :as linear]
            [hazel.io.edn :as edn]))

(defn measure [name iterations f]
  (dotimes [_ 3] (f))
  (let [samples (mapv (fn [_]
                         (let [start (System/nanoTime) result (f) elapsed (- (System/nanoTime) start)]
                           {:seconds (/ elapsed 1e9) :result-sha256 (edn/sha256 result)})) (range 5))
        median (nth (sort (map :seconds samples)) 2)]
    {:name name :iterations iterations :median-seconds median
     :median-nanoseconds-per-iteration (/ (* median 1e9) iterations) :samples samples}))

(defn run! []
  (let [model (net/compile-model
               {:species [:x] :initial {:x 0} :parameters {:birth 4 :death 1/2}
                :reactions [{:id :birth :reactants {} :products {:x 1} :rate :birth}
                            {:id :death :reactants {:x 1} :products {} :rate :death}]})
        small (generator/build (projection/enumerate model {:bounds [62]}))
        large (generator/build (projection/enumerate model {:bounds [1022]}))
        kernel (sparse/compile-kernel small) dense (generator/dense small)
        stochastic (linear/add (linear/identity 64) (linear/scale (/ 1.0 (:lambda kernel)) dense))
        initial (generator/initial-vector small [0]) iterations 200]
    {:java-version (System/getProperty "java.runtime.version")
     :os (System/getProperty "os.name") :architecture (System/getProperty "os.arch")
     :processors (.availableProcessors (Runtime/getRuntime))
     :method {:warmup-invocations 3 :measured-invocations 5 :summary :median :isolated-process? false}
     :cases [(measure :sparse-stochastic-product-64 iterations
                      #(loop [i 0 p initial]
                         (if (= i iterations) p (recur (inc i) (sparse/stochastic-product kernel p)))))
             (measure :dense-stochastic-product-64 iterations
                      #(loop [i 0 p initial]
                         (if (= i iterations) p
                             (recur (inc i) (linear/rowvec p stochastic)))))
             (measure :uniformization-1024 1
                      #(select-keys (uniform/solve large [0] 1) [:probabilities :matrix-products :series-tail-bound]))]}))
