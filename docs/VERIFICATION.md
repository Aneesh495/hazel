# Verification

## Commands and acceptance meaning

```sh
make test
make acceptance
make verify
```

The first command runs unit, generated, analytic, integration, and regression
tests. Acceptance freezes source bytes, runs those tests, executes mutation
controls, runs every supplied study, strictly replays each result, measures
real workloads, and tests the standalone package from outside the checkout.

`verify` checks a previously generated campaign against current source and
artifact bytes, then recomputes saved example results. It does not silently
regenerate missing or stale evidence. To execute the tests again, run acceptance.

A passing campaign establishes the tested contracts on its recorded environment.
It does not establish absence of all defects, scientific validity of schematic
models, formal proof of the implementation, or convergence of arbitrary MCMC
chains. The [limits](LIMITS.md) describe those boundaries explicitly.

## Independent checks

| Subject | Independent evidence |
|---|---|
| Exact linear algebra | Reconstruct solutions and inverses; generated unimodular matrices; kernel reconstruction |
| Sparse forward products | Dense row products built from the same inspectable rates |
| Sparse backward products | Independent dense column products |
| Transient probabilities | Poisson, binomial, two-state laws; independent dense scaling-and-squaring exponential |
| Time composition | Whole interval versus successive half intervals |
| Projection semantics | Escape retained; growing bounds reduce leakage; deliberate reflection fails an analytic law |
| Tangent probabilities | Centered finite differences on fixed state sets; zero-rate boundary |
| Likelihood score | Centered differences of complete normalized observation likelihoods |
| Optimizer | Analytic arrival-rate maximum and a constrained quadratic |
| Simulation | Conservation, path replay, analytic population moments, independent scheduling algorithm |
| Parallel execution | Worker-count invariance of indexed streams and histograms |
| Particles | Likelihood agreement in expectation with a finite hidden-state reference |
| PMMH | Rejected proposals retain the likelihood estimate; impossible priors skip likelihood work |
| Bridges | Endpoint and jump validation; extinction jump count; switch parity |
| Importance sampling | Known Poisson tail with fixed seeds and empirical uncertainty |
| Stationarity | Generator residuals, normalization, detailed balance, time reversal |
| Rewards | Poisson count mean/variance, deterministic constant reward, self-event counts |
| Aggregation | Lumped and full transients; asymmetric rates reject strong lumpability |
| Symbolic moments | Polynomial identities and direct reaction-generator semantics |
| Rational certificates | Independent source-rate reconstruction and dense exact recurrence |
| Artifacts | Hash mismatch, model mismatch, strict numerical replay, portable nonfinite log values |
| Presentation | Escaping, script-free CSP, SVG axis fill contract, manual browser inspection |
| Packaging | Two fresh builds, unrelated working directory, full study, certificate check, strict replay |

The dense reference and sparse implementation share the model's rates. They
can agree while a rate compiler is wrong. Analytic models, direct sample-path
semantics, symbolic generator tests, and the independent certificate checker
address that separate risk.

## Generated populations

Generated tests use explicit reproducible streams and bounded problem sizes.
They construct invariants rather than merely repeating an implementation's
intermediate representation.

* Eighty small generators test nonnegativity, conservation, dense agreement,
  backward constants, and semigroup composition
* One hundred unimodular systems have known determinant one and independently
  chosen exact solutions, including values beyond machine integer products
* Rectangular systems contain an identity block, fixing their rank before
  nullspace computation
* Sparse polynomials test evaluation, shifts, products, and Leibniz derivatives
* Nonlinear reaction operators are compared against direct integer-state jumps
* Generated conserved networks test reachable populations and parameter tangents

The tests do not sample every possible model. Their seeds and sizes are part of
source and therefore part of the acceptance fingerprint.

## Statistical checks

Statistical tests use fixed seeds, so regressions are reproducible. Mean checks
use analytic moments or an explicit multiple of the estimated standard error.
Variance checks have separately stated tolerances. A source change that fails a
statistical check is investigated before changing a seed, population, or bound.

The importance-sampling fixture deliberately chooses a proposal with many more
target hits than ordinary sampling. It corrects the complete path density.
The particle fixture averages likelihood estimates across runs and compares
with an exact finite-state filter. Averaging log likelihoods would test a
different, biased quantity.

Short MCMC tests verify mechanics. They are not used to publish posterior
accuracy, mixing, or effective-sample-size claims for a scientific model.

## Mutation controls

Acceptance executes 24 negative controls. They cover executable reader syntax,
trailing EDN, misspelled fields, negative and infinite rates, duplicate species,
invalid generator edges/exits, nonabsorbing escape, inadequate uniformization
rates, invalid mass and time, altered artifacts, corrupted certificates, changed
models, invalid replay transitions, and support-removing importance proposals.

Two semantic controls change time units and reflect a projection boundary. They
must disagree with the independent analytic law. Budget controls must return
their incomplete statuses. Each observation is recorded, including the actual
exception message or measured disagreement. The campaign does not initialize
their detected flags to success.

## Evidence layout

```text
out/acceptance/
  acceptance.edn          Source manifest, environment, results, artifact hashes
  tests.log               Raw clojure.test output
  benchmarks.edn          Timings, workload sizes, result hashes
  controls/
    controls.edn          Executed controls and observations
    corrupt.edn           Deliberately damaged fixture
  examples/
    <study>/
      <digest>.edn        Model, request, and complete result
      index.html          Offline SVG report
  package/
    hazel.jar             The tested standalone package
    build-first.log       Fresh build evidence
    build-second.log      Repeated build evidence
    jar-run.log           Study execution outside the checkout
    jar-verify.log        Independent rational check
    jar-replay.log        Strict numerical replay
```

The source manifest covers implementation, tests, development tooling, resources,
examples, launchers, dependency and build files, and CI. Narrative docs and
outputs are excluded so the evidence does not depend on its own hash.

Hashes detect changes. They are not signatures and cannot authenticate a claimed
past execution against an adversary who can rewrite all files. Rerun acceptance
to obtain direct execution evidence in your own environment.

## Platform and packaging checks

CI runs the full campaign on Linux, macOS, and Windows with JDK 17, 21, and 25.
Each matrix job retains its own raw evidence. A local pass is not reported as a
pass on an unexecuted platform. Hosted status is visible in the workflow.

Package regression uses a child JVM with an unrelated working directory. It
runs a real certificate study, then its independent verification and replay.
This catches missing report resources and class-loading failures that `help`
alone cannot reveal.

## Defect handling

An observed defect receives a reproducing test before its correction. The
regression stays in the repository. The [bug ledger](BUGS.md) records root cause
and fix, including numerical magnitude, rate-unit scaling, source validation,
packaging, and visual defects found during development.

There is no invented line-coverage percentage. The mapping above identifies
the checks that actually run and their practical limits.
