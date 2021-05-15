# Regression ledger

Observed defects belong here with the failing test, root cause, and correction.
Routine incomplete development is not a defect claim. Keep the regression after
the correction. Statistical disagreement is investigated before tolerances move.

## HZ-001: compensated addition of impossible prior terms

The multi-parameter PMMH support regression failed with six likelihood calls
instead of one. A compensated finite-number sum received negative infinity from
an out-of-support prior and produced NaN. The sampler then evaluated proposals
that should have been rejected before simulation. Check prior support before
adding finite terms. The regression uses narrow uniform priors and retains the
likelihood-call assertion to verify that the rejected branch stays unreachable.

Reproducer: `outside-prior-does-not-run-likelihood-regression` in
`test/hazel/mcmc_test.clj`. The failing test was committed before the correction.

## HZ-002: moment derivative argument order

All three initial reward tests failed because a thread-first expression passed a
vector as the scalar argument to `axpy`. Write the affine combination explicitly
before adding the second forcing term. Poisson event-count mean and variance,
constant occupation reward, and state-preserving event counts are retained as
regressions. Their failing implementation and tests were committed first.

## HZ-003: exact algebra overflowed machine integers

`large-exact-algebra-regression` produced three arithmetic exceptions at 10^20.
Matrix products, matrix-vector products, and determinant elimination used checked
machine arithmetic before any operand became a bigint. Use promoting arithmetic
throughout the exact algebra path. Numeric algorithms still use binary64.

## HZ-004: absolute pivot thresholds depended on rate units

Rescaling a two-state chain to rates of 10^-18 incorrectly produced a singular
backward system. Scale each coefficient row before numeric pivot tests. The
regression verifies both the 10^18 mean waiting time and the unchanged stationary
law. It does not claim that every ill-conditioned system is accurately solvable.

## HZ-005: cumulative trajectory tail metadata was inconsistent

The trajectory stored a cumulative series tail alongside the latest segment's
truncation bound. Three assertions failed in the four-interval regression. Rebuild
the combined truncation bound using cumulative series tail and current escape.

## HZ-006: a tiny rate difference was accepted as strong lumpability

An absolute tolerance of 10^-12 merged states whose rates differed by a factor
of two at a 10^-20 scale. Strong lumpability now defaults to exact signature
comparison. An explicitly supplied tolerance remains an approximate diagnostic.

## HZ-007: incomplete validation on alternate entry points

The edge-contract tests reproduced acceptance of a mismatched sensitivity model,
a malformed replay censor time, invalid initial probability in an empty score
run, and an unboundedly high reaction order. Validate those entry paths before
returning early. Total reactant order is capped at 32 to bound combinatorial work.

HZ-003 through HZ-007 are reproduced in `test/hazel/edge_contract_test.clj`,
committed together while failing, before these corrections.

## HZ-008: equal source/class timestamps triggered runtime recompilation

The executable JAR displayed help but failed while rendering a real report.
Normalizing every entry to the same timestamp caused Clojure to select source
instead of precompiled namespace initializers. A protocol interface was then
loaded in two class loaders, producing a `PrettyFlush` cast failure. Give class
entries a fixed timestamp two seconds newer than source entries. Keep source in
the JAR for inspection. The package regression runs a real certificate study,
independent verification, and strict replay from outside the checkout.

## HZ-009: a generated closure changed between fresh package builds

The two-build package regression found functionally equivalent class files with
different closure-capture ordering. AOT compilation of all application code was
not byte-reproducible between fresh JVMs. Compile only a minimal Clojure bootstrap
and bundle application namespaces as source, which uses the normal runtime JIT
path. The regression compares fresh build hashes on the same runtime. This does
not promise identical artifacts across different Clojure or JDK releases.
