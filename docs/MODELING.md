# Model authoring

## Complete study shape

Studies are one EDN map with optional title and description, a model, and one
experiment. The reader rejects executable reader forms, tagged literals,
trailing values, and inputs over its byte limit. Unknown fields are rejected
where the schema is defined, which catches misspellings early.

```clojure
{:title "A birth and decay experiment"
 :model {:name "Immigration and decay"
         :species [:rna]
         :initial {:rna 0}
         :parameters {:birth 2 :death 1/2}
         :reactions [{:id :birth :reactants {} :products {:rna 1} :rate :birth}
                     {:id :death :reactants {:rna 1} :products {} :rate :death}]}
 :experiment {:kind :transient :time 3 :epsilon 1e-9}}
```

Species are a vector of distinct keywords. This order determines every state
vector. The initial state and reaction sides are maps from those keywords to
nonnegative counts. Missing counts are zero. Reactions have distinct keyword
identifiers. A rate is a nonnegative constant or a declared parameter keyword.

The model supports at most 64 species, 1,024 reactions, and total reactant order
32. Population counts must fit nonnegative signed 64-bit integers. Products may
not produce negative populations through an invalid firing.

## Units and combinatorics

Choose one time unit for rates, observations, schedules, and horizons. Hazel
does not infer or convert units. An arrival rate of `2` means two events per
chosen time unit in a zero-order arrival channel.

For `2 A -> B`, the stochastic hazard is `k * choose(A,2)`. The consumed
population controls availability. Catalysts belong on both sides, for example
`{:reactants {:on 1} :products {:on 1 :rna 1}}`.

The deterministic macroscopic route uses powers divided by factorials. It is
documented separately because it is not the same nonlinear propensity on a
small integer population.

Use integer or ratio rates when exact certificates are needed. Ordinary decimal
literals are binary64 inputs. Model names and descriptions are metadata, not
rate expressions. Arbitrary kinetic functions are not part of the language.

## Projections

`:projection` accepts `:bounds`, a vector in species order, and `:max-states`.
Bounds limit individual populations. The state budget limits total discovered
states. A reaction crossing either boundary contributes escape mass.

The transient experiment adapts bounds automatically and records each attempt.
Other projection-based studies use the supplied fixed projection. Their
`:complete` status means the requested calculation completed, not that all
projection error is negligible. Inspect escape and compare larger bounds.

A zero rate can hide states from base-model reachability. If a fit will turn
that rate on, construct a fixed state set through the API using a model that
contains the intended transitions. The CLI enumerates the base model's state
set and does not automatically explore a parameter box.

## Experiment kinds

| Kind | Principal fields | Result |
|---|---|---|
| `:transient` | `:time`, `:epsilon`, optional refinement limits | Adaptive population law and history |
| `:trajectory` | `:times` | Incremental transient series |
| `:certify` | Exact `:time`, optional `:target` and series orders | Rational lower distribution and independent check |
| `:first-passage` | `:times`, `:target` | First-passage probability interval and density |
| `:hitting` | `:target` | Eventual hit probability and mean times |
| `:stationary` | Projection only | Closed-class stationary laws |
| `:sensitivity` | `:parameters`, `:time` | Fixed-projection probability derivatives |
| `:filter` | `:sensors`, `:observations`, optional smoothing and bridge seed | Retained-path posterior |
| `:score` | Sensor data and selected `:parameters` | Projected likelihood gradient |
| `:fit` | Sensor data, `:parameters`, `:bounds` | Bounded maximum likelihood |
| `:particle` | Sensor data, `:particles`, `:seed` | Particle likelihood and weighted populations |
| `:mcmc` | Sensor data, parameters, priors, iterations, seed | Pseudo-marginal parameter chain |
| `:design` | Parameters, times, observable species, budget | Greedy independent-snapshot design |
| `:schedule` | Piecewise `:segments` | Propagated rate interventions |
| `:reward` | Time and reaction or observable species | Event-count or occupation moments |
| `:moments` | `:order`, `:time` | Exactly closed polynomial moment system |
| `:bridge` | Start/end state vectors, time, seed | Continuous population bridge |
| `:importance` | Proposal parameters, target, time, samples, seed | Weighted rare-event estimate |
| `:simulate` | Time, seed, optional algorithm and event limit | Recorded direct or next-reaction path |
| `:ensemble` | Time, seed, samples, optional workers | Deterministic parallel sample population |

`:solver` carries method options such as tolerance, chunk budget, and optimizer
limits. Options only affect methods that consume them. The API provides lower
level control when a study needs a common manually assembled state set.

## Event predicates

An atomic predicate names a species, comparison, and threshold:

```clojure
{:species :rna :op :>= :value 10}
```

Comparisons are `:<`, `:<=`, `:=`, `:>=`, `:>`, and `:!=`. Boolean vectors use
`:and`, `:or`, and unary `:not`. `true` and `false` are valid predicates.
Nesting is capped at 32. Thresholds are finite numbers; no code is evaluated.

```clojure
[:and {:species :rna :op :>= :value 10}
      [:not {:species :on :op := :value 0}]]
```

## Sensors and schedules

An exact count sensor is `{:species :rna :kind :exact}`. A binomial detection
sensor adds `:probability`; a Gaussian sensor requires positive `:sigma`.
See [inference](INFERENCE.md) for all channels and posterior semantics.

Schedule segments use `:until` and an optional parameter patch. The first patch
applies at time zero; each later patch applies at the previous boundary. Patches
are cumulative. Boundaries increase strictly. No population jumps are inserted
by changing a rate.

```clojure
[{:until 1 :parameters {:birth 1}}
 {:until 3 :parameters {:birth 6}}
 {:until 8 :parameters {:birth 0}}]
```

## Reproducibility

Every stochastic experiment requires an explicit seed. The same study and
runtime reproduce the same stream choices. Ensemble worker counts preserve
trajectory indexes and reduction order.

The saved request includes the complete model and experiment. `inspect` verifies
its hash; `replay` recomputes the result. `verify` applies the separate rational
checker to a certificate study. None of these commands fetch a remote dataset.
