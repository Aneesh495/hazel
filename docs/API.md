# API walkthrough

The functions below run in a REPL from the repository root. Start one with
`bin/clj`, `bin/clj.ps1`, or `clojure -M:dev`. All namespace aliases are local
to the examples. The API returns immutable data except for private compiled
sparse arrays, which callers should treat as opaque.

## Compile and propagate a network

```clojure
(require '[hazel.io.edn :as edn]
         '[hazel.model.network :as net]
         '[hazel.ctmc.projection :as projection]
         '[hazel.ctmc.generator :as g]
         '[hazel.ctmc.uniformization :as u]
         '[hazel.analysis.distribution :as distribution])

(def study (edn/read-file "examples/telegraph.edn"))
(def model (net/compile-model (:model study)))
(def projected (projection/enumerate model {:bounds [1 1 24]}))
(def q (g/build projected))
(def initial (g/initial-vector q (:initial model)))
(def result (u/propagate q initial 5 {:tolerance 1e-12}))

(select-keys result [:escape-probability :series-tail-bound
                     :floating-point-residual])
(distribution/marginal q result 2)
(distribution/event q result #(>= (nth % 2) 10))
```

The event query returns captured probability and an analytic truncation interval.
It does not normalize the finite projection. `conditional-moments` explicitly
conditions on retained paths; `expectation` is a captured expectation.
An unbounded moment needs additional tail control to become a full-model bound.

Use `hazel.ctmc.adaptive/solve` when automatic restart and growth is appropriate.
It returns its projection and generator as well as the numerical result, so an
API caller can continue querying the exact state set that was used.

## Inspect structural invariants

```clojure
(require '[hazel.model.structure :as structure])

(structure/conservation-laws model)
(structure/topology model)
```

The promoter has a conserved total of one across `:off` and `:on`. Conservation
bases need not be unique or nonnegative. Deficiency and weak reversibility are
structural descriptors. The code does not infer a general equilibrium theorem
from them automatically.

## Fixed-projection sensitivities

```clojure
(require '[hazel.ctmc.sensitivity :as sensitivity])

(def tangent
  (sensitivity/propagate model q initial [:activate :transcribe] 5))
(get (:sensitivities tangent) :activate)
```

The returned vector includes escape sensitivity. Its total is approximately
zero for a normalized initial law. The state set stays fixed. When evaluating
other parameter values, use `net/with-parameters` and `g/for-states` to recompute
rates on that same set.

## Filtering and smoothing

```clojure
(require '[hazel.inference.observation :as observation]
         '[hazel.inference.filter :as filtering]
         '[hazel.inference.smoother :as smoothing])

(def sensors
  (observation/compile-sensors
   model [{:species :rna :kind :binomial :probability 7/10}]))
(def measurements
  [{:time 1 :values {:rna 0}}
   {:time 2 :values {}}
   {:time 3 :values {:rna 4}}])
(def filtered (filtering/forward q initial sensors measurements))
(def smoothed (smoothing/smooth q filtered))
(smoothing/sample-grid-bridge q filtered 1729)
```

Missing measurements and observed zeros have different likelihoods. These
posteriors condition on retained paths. Increasing the state set is a separate
model approximation check.

## Likelihood scores and fitting

```clojure
(require '[hazel.inference.score :as score]
         '[hazel.inference.fit :as fit])

(score/forward model q initial [:activate] sensors measurements {})
(fit/fit model (:states q) [:activate] sensors measurements
         {:activate [1/100 3]} {:max-iterations 100})
```

The score includes normalization derivatives. A fit's status must be inspected.
Convergence means the bounded local optimizer met its projected-gradient test.
It does not establish a unique or globally optimal model.

`fit/profile` fixes one parameter at supplied grid points and refits nuisance
parameters. An example call using the same state set is:

```clojure
(fit/profile model (:states q) [:activate] sensors measurements
             {:activate [1/100 3]} :activate [1/10 1/4 1/2 1] {})
```

## Particle methods

```clojure
(require '[hazel.inference.particle :as particle]
         '[hazel.inference.mcmc :as mcmc])

(def particles (particle/filter model sensors measurements 300 1729))
(particle/expectation particles #(nth % 2))

(mcmc/sample model [:activate] sensors measurements
             {:activate {:kind :gamma :shape 2 :rate 4}}
             20 1729 {:particles 100 :proposal-scales [0.2]})
```

The short chain above demonstrates the interface, not posterior convergence.
Run independent chains, discard a justified warmup, and inspect ESS and Rhat
before making a scientific interpretation. Particle filtering can return zero
evidence at finite sample size.

## Exact rational certificate

```clojure
(require '[hazel.ctmc.certificate :as certificate]
         '[hazel.analysis.certificate-check :as checker])

(def arrival-model
  (net/compile-model (:model (edn/read-file "examples/certificate.edn"))))
(def arrival-q
  (g/build (projection/enumerate arrival-model {:bounds [18]})))
(def checked-result (certificate/create arrival-model arrival-q 1))
(checker/check arrival-model checked-result)
(certificate/event-interval checked-result #(>= (first %) 3))
```

All bound arithmetic in this path is rational. The separate checker reconstructs
the model's generator independently. A decimal rate literal is rejected; use
ratios for exact noninteger constants.

## Continuous population bridge

```clojure
(require '[hazel.inference.bridge :as bridge])

(def decay-model
  (net/compile-model (:model (edn/read-file "examples/extinction-bridge.edn"))))
(def decay-q (g/build (projection/enumerate decay-model)))
(def population-path (bridge/sample decay-q [6] [0] 2 1729))
(bridge/validate decay-q population-path)
```

The bridge's `:jumps` are population changes. It does not claim a complete
reaction-labeled chemical history.

## Symbolic moment equations

```clojure
(require '[hazel.analysis.moment :as moment])

(moment/compile-hierarchy model 3)
(moment/solve model 3 5 {})
```

This promoter model closes exactly in a finite degree-three polynomial basis.
A nonlinear reaction may produce higher-degree monomials. In that case the
compiler reports them and `solve` refuses to integrate an unclosed system.

## Save and verify artifacts

For complete studies, prefer the orchestration layer so the bundle request and
result stay aligned:

```clojure
(require '[hazel.experiment :as experiment]
         '[hazel.io.bundle :as bundle])

(def completed (experiment/run-study study))
(def artifact
  (bundle/create! "out/api" (:model completed)
                  (:request completed) (:result completed)))
(bundle/read! (:path artifact))
```

The CLI `replay` command recomputes the entire result. A bundle created from
arbitrary low-level data can still be hash-checked, but it is not automatically
a replayable complete study.
