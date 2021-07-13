# Observed results

This file is generated from a source-bound local acceptance campaign. It reports executed work on the environment below. Hosted matrix status is reported by the workflow; this local record does not stand in for those jobs.

| Environment | Observed value |
|---|---|
| JDK | `23.0.1+11-39` |
| OS / architecture | Mac OS X / aarch64 |
| Campaign elapsed time | 13.8659 seconds |
| Source fingerprint | `ee6ec2ba8ee9d7490e1076b20028c414c4847522e8a2d0fe54be08ae0070d5c8` |

## Executed acceptance

| Check | Observed result |
|---|---|
| Test cases | 115 |
| Assertions | 3138 passed, 0 failed, 0 errors |
| Mutation controls | 24 / 24 detected |
| Complete supplied studies | 13 executed and strictly replayed |
| Rational certificate | Independently checked from source reactions |
| Fresh executable builds | 2 identical on this runtime |
| Package outside checkout | Study, report, certificate verification, and replay passed |
| Tested package bytes | 5090845 |
| Tested package SHA-256 | `c608b4974197a67c36b6c3ff78cc7bff9811bad3f6947ab3626c2ea06466a1c1` |

## Numerical fixtures

The arrival certificate encloses `P(X(1)>=3)` for rate two. Its exact fractions are retained in the bundle. Decimal presentation below is only a display.

| Fixture | Observed value |
|---|---|
| Certificate lower display | 0.323324 |
| Certificate upper display | 0.323324 |
| Exact interval width, decimal display | 6.47730e-13 |
| Promoter final projected populations | 70 |
| Promoter refinement attempts | 4 |
| Promoter escaped probability | 2.65623e-18 |
| Promoter series tail allowance | 1.84577e-10 |
| Arrival-rate maximum likelihood estimate | 3.50000 |
| Tilted rare-arrival probability estimate | 4.65231e-05 |
| Tilted estimate empirical standard error | 1.82827e-06 |

The fit's analytic maximum is 3.5. The statistical estimate is not an exact probability or a deterministic confidence interval. Binary64 projection diagnostics do not inherit rational certificate status.

## Descriptive measurements

Three warmup invocations precede five measured invocations. All raw timings and result hashes are retained. Representations are prepared outside each product loop. These measurements are not isolated-process JMH results or comparisons with optimized numerical libraries.

| Workload | Iterations per invocation | Median seconds | Median ns per iteration |
|---|---:|---:|---:|
| sparse-stochastic-product-64 | 200 | 0.00246858 | 12342.9 |
| dense-stochastic-product-64 | 200 | 0.135499 | 677495 |
| uniformization-1024 | 1 | 0.363065 | 3.63065e+08 |

The dense case is the included persistent-data reference. The sparse case uses CSR arrays. Their difference describes these implementations and this small workload, not a general hardware or language speed claim.

## Reproduce

```sh
make acceptance verify
bin/clj -m hazel.results
```

Raw evidence is in `out/acceptance/`, which is intentionally not tracked. CI retains environment-specific bundles as workflow artifacts. See [verification](VERIFICATION.md) and [limits](LIMITS.md) for the acceptance boundary.
