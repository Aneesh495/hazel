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
