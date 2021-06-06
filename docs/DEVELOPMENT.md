# Development

## Local setup

Use Java 17 or newer. No global Clojure installation is required for the source
launcher. `bin/clj` downloads three pinned runtime JARs to `.cache/runtime/` and
checks SHA-256 hashes before launching Java. `bin/clj.ps1` provides the Windows
equivalent. A standard Clojure CLI can use `deps.edn` directly.

```sh
bin/clj -e '(println (clojure-version))'
make test
make acceptance verify
make jar
java -jar target/hazel.jar run examples/certificate.edn
```

Run these commands from the repository root. Generated outputs live in `out/`
and `target/`; runtime downloads live in `.cache/`. All are ignored by Git.
`make clean` removes only `out/` and `target/`.

## Repository map

```text
src/hazel/
  model/                  Inert schema, reactions, invariants, predicates
  math/                   Algebra, probabilities, ODEs, polynomials, optimization
  ctmc/                   Projections, generators, sparse products, propagation
  simulation/             Streams, event paths, ensembles, importance sampling
  inference/              Observation models, filtering, scores, particles, MCMC
  analysis/               Passage, equilibrium, rewards, aggregation, certificates
  io/                     EDN, bundles, reports
  experiment.clj          Complete study orchestration
  cli.clj                 Process boundary
  main.clj                Minimal executable-JAR bootstrap
test/hazel/               Analytic, generated, integration, and regression tests
dev/hazel/                Build, source manifests, controls, measurements, acceptance
examples/                 Complete inert studies
resources/hazel/          Offline report styling
docs/                    Contracts, derivations, walkthroughs, results, decisions
bin/                     Unix and Windows runtime launchers
.github/workflows/        Full cross-platform acceptance
```

## Changing behavior

Start with the invariant being changed. Keep reaction semantics, probability
conventions, and projection interpretation explicit. A numerical improvement
must preserve its analytic fixtures and independent route comparisons.

For an observed bug:

1. Create a minimal reproducer and run it against the failing code
2. Preserve the failing observation and a regression test
3. Correct the implementation without weakening unrelated acceptance
4. Run the regression and relevant independent checks
5. Update `docs/BUGS.md` with cause and correction
6. Commit the checkpoint and run full acceptance before publication

A statistical failure is not permission to search for a passing seed or enlarge
its tolerance without an explanation. A budget failure is not success. An
unclosed moment hierarchy must not be made to pass by silently discarding terms.

## Numeric representation

Use exact integers and ratios in structural or certificate algorithms. Use
promoting arithmetic when machine-range operands can produce larger exact
products. Exact matrix operations must not overflow before a bigint appears.

Use binary64 deliberately in large numerical kernels. Validate finite model
inputs and dimensions at boundaries, preserve nonnegative probability flow,
and report omitted mass. Signed tangent vectors are not probability vectors.

Do not infer a rigorous error bound from a tiny residual or from agreement with
one other implementation. Keep analytic truncation, numerical diagnostics,
projection adequacy, and statistical uncertainty separate in both data and prose.

## Source manifests

`hazel.source` hashes executable and test inputs in deterministic path order.
Acceptance captures the full manifest before execution and compares it after
all numerical and packaging work. A changed file invalidates the run.

This prevents source changes during expensive acceptance from being mislabeled
as validation of one frozen revision. It does not authenticate an author or
replace a fresh run. Narrative documentation is excluded from this fingerprint.

## Deterministic streams and concurrency

Random functions return a value and the next signed 64-bit stream state. Avoid
global mutable RNGs. Derive independent trajectory streams from explicit indexes.
Preserve output and reduction order across worker counts.

Particle resampling changes ancestry but must not duplicate future random
streams. PMMH proposals must not refresh the current likelihood estimate when
rejected. These are semantic contracts, not implementation details to optimize
away.

The ensemble executor has an explicit worker limit and shuts down on both
success and failure. Algorithms themselves do not create unbounded background
threads. CLI shutdown terminates Clojure agent pools before process exit.

## Build strategy

`hazel.build` uses JDK JAR APIs. The package contains the three pinned runtime
artifacts, source namespaces, resources, and a tiny AOT-compiled Clojure bootstrap.
It does not invoke Maven, Leiningen, Gradle, or a native compiler.

Full-application AOT output showed nondeterministic closure-capture ordering
between fresh JVMs. Bundling application source keeps the normal Clojure JIT
path and makes the tested package reproducible on the same runtime.

Dependency class entries receive fixed timestamps newer than their source
entries. Equal timestamps caused Clojure to recompile a namespace at runtime and
load a protocol interface in two class loaders. Preserve this ordering if the
packager changes.

The package check deletes generated classes before building, compares two fresh
package hashes, then executes a study from a temporary unrelated directory.
It checks reports, independent certificates, and replay. Byte identity across
different runtime releases is not promised.

## Dependencies and licenses

The only non-JDK runtime dependency is Clojure and its standard spec libraries.
The source launchers pin their bytes; `deps.edn` pins Clojure's release. Review
both when updating a runtime. Update hashes only after verifying the intended
artifact from its official registry.

The JAR preserves dependency license and notice entries under unique paths.
See [third-party notices](../THIRD_PARTY.md). Hazel's own source is MIT licensed.

## Measurements

Benchmarks report five measured invocations after three warmups. They retain
every timing and result digest, use a median summary, and record runtime and
machine context. Dense and sparse product cases precompute their representations
outside the measured iteration loop.

The dense path is an internal persistent-data reference, not an optimized BLAS
implementation. A comparison measures these implementations and workloads. It
must not be generalized into a platform-wide performance claim.

## CI and publication

The workflow pins action revisions and runs full acceptance across three
operating systems and three JDK versions. Each job uploads its evidence even
when a later step fails, where artifacts exist.

Before publishing a revision, run the full campaign from a fresh checkout,
inspect its reports, and verify the remote head after pushing. Report hosted
CI status from actual completed jobs. A queued or pending check is not a pass.

Keep public claims tied to mechanisms and observed evidence. The codebase does
not promise universal correctness, arbitrary model scalability, or scientific
calibration from illustrative examples.
