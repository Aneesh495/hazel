# Build plan

Hazel studies discrete stochastic reaction networks. The central object is a
continuous-time Markov chain, not a deterministic concentration trajectory.
The motivating question is how much of a probability calculation survives
state-space truncation, numerical approximation, and partial observation.

## Scope and contracts

The implementation language is Clojure. Models are inert EDN with mass-action
kinetics, named parameters, integer populations, and explicit initial states.
Reaction constants use the combinatorial convention `k * product choose(x,s)`.
No arbitrary code is evaluated from a model. Exact stoichiometry and rational
linear algebra support structural checks. Numerical probability calculations
use binary64 and report truncation separately from floating-point diagnostics.
They must not be presented as machine-checked real-arithmetic certificates.

The numerical core is a sparse row-generator and a substochastic finite-state
projection with an absorbing escape state. Uniformization computes transient
probabilities using a Poisson series. State-space growth restarts from the
original initial distribution, so escaped mass is never silently recycled.

Inference builds on the same propagator. Observations condition distributions;
sensitivities differentiate the forward equations on a fixed projection.
Rare-event analysis makes target states absorbing. Simulation is an independent
sample-path route to the model, not an implementation of the matrix solver.

## Checkpoints

Each numbered checkpoint is a meaningful commit, with tests as its behavior
becomes executable. Additional regression commits are expected.

1. Record scope, semantics, acceptance gates, and development sequence
2. Bootstrap a pinned Clojure runtime and dependency-free test runner
3. Add validated numerical primitives and compensated accumulation
4. Implement exact row reduction, rank, solve, and nullspaces
5. Implement stable probability functions and Poisson truncation bounds
6. Read one bounded EDN value and canonicalize content hashes
7. Validate and compile inert reaction models
8. Implement propensities, firing, and reaction dependencies
9. Analyze conservation laws and reaction-complex structure
10. Enumerate bounded reachable projections with explicit frontier metadata
11. Build and validate sparse generators with an absorbing escape state
12. Implement sparse stochastic and generator vector products
13. Implement chunked uniformization and error accounting
14. Add an independent dense matrix-exponential reference solver
15. Implement adaptive projection with honest budget outcomes
16. Add distribution queries and bounded event probabilities
17. Solve first-passage distributions through target absorption
18. Solve backward hitting probabilities and mean hitting times
19. Add communicating classes and stationary finite-chain analysis
20. Implement seeded random streams and distribution samplers
21. Implement direct stochastic simulation with replayable event histories
22. Add observation grids, ensembles, and confidence diagnostics
23. Implement a next-reaction simulator with independent scheduling
24. Add deterministic drift integration and linear-noise covariance
25. Implement fixed-projection parameter sensitivity propagation
26. Add observation likelihoods and forward filtering
27. Add backward smoothing and observation-conditioned bridges
28. Add likelihood scores and Fisher information
29. Fit parameters with bounded optimization and profile likelihoods
30. Implement bootstrap particle filtering
31. Add pseudo-marginal parameter sampling and chain diagnostics
32. Implement experimental-design objectives and greedy selection
33. Implement time-dependent piecewise-constant intervention schedules
34. Add reward accumulation and reaction-count statistics
35. Add coarse-graining and strong-lumpability validation
36. Implement path likelihoods and time-reversal diagnostics
37. Add content-addressed atomic result bundles and strict replay
38. Add a command-line interface with stable exit codes
39. Render offline HTML/SVG experiment reports in Clojure
40. Add complete example studies with analytic reference cases
41. Add randomized algebraic and semantic differential checks
42. Add deliberate corruption and numerical negative controls
43. Add reproducible benchmarks and source-bound acceptance artifacts
44. Document architecture, numerical derivations, and trust boundaries
45. Document model authoring, API usage, and contributor workflows
46. Build an executable standalone JAR and verify packaging
47. Add Linux/macOS and supported-JDK continuous integration
48. Run a fresh-clone verification campaign and fix every observed failure
49. Record measured results, known limits, and final acceptance evidence
50. Publish all commits, technical topics, and verify the remote revision

## Acceptance

* Every public numerical operation validates dimensions and finite inputs
* Exact algebra is checked by reconstruction, not just expected constants
* Birth, death, immigration-death, and two-state chains agree with analytic laws
* Sparse transients agree with an independently implemented dense exponential
* Projection escape is retained and tighter projections improve convergence
* Budget exhaustion returns an incomplete status, never success
* Simulation conserves populations and matches distribution-level predictions
  within stated statistical tests with fixed seeds
* Forward sensitivities agree with centered finite differences
* Filtering, smoothing, and likelihoods agree with small exact hidden chains
* Tampered artifacts, changed models, malformed EDN, and invalid generators fail
* Examples, CLI, offline report, source launcher, and packaged JAR run end to end
* A fresh checkout reproduces acceptance on the supported local JDK; CI covers
  additional operating systems and JDKs and is reported only after completion

Zero defects is an aspiration, not a claim that testing can establish. Document
observed defects with a reproducing regression and its fix. Do not manufacture
source volume, benchmark results, coverage percentages, or acceptance evidence.
