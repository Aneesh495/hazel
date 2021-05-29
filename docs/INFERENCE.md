# Inference and experimental design

## Observation channels

A sensor associates one declared species with an observation law. Population
counts remain discrete even when a measurement is continuous.

| Kind | Observation law |
|---|---|
| `:exact` | The measured value equals the population |
| `:binomial` | Detected count given population and fixed detection probability |
| `:poisson` | Mean is background plus efficiency times population |
| `:gaussian` | Mean is offset plus scale times population, with fixed sigma |

Sensors are conditionally independent given the population state. Their log
likelihoods add. Sensor parameters are fixed during kinetic differentiation;
the current score API does not differentiate detection probabilities or noise.

An observation is `{:time t :values {:rna 3}}`. Omitting `:rna` means no
measurement. Supplying `:rna 0` is an observed zero. Unknown measured species,
negative count observations, nonfinite values, and unsorted times are invalid.

## Forward filtering

Let `alpha_(k-1)` be the normalized retained posterior. Predict over the time
interval, then multiply by an emission vector `b_k`:

\[
\tilde\alpha_k=\alpha_{k-1}e^{Q\Delta t_k},\quad
z_k=\sum_i\tilde\alpha_k(i)b_k(i),\quad
\alpha_k(i)={\tilde\alpha_k(i)b_k(i)\over z_k}.
\]

Emissions are evaluated in log space. The filter scales by a common maximum
before normalization and retains the corresponding log-evidence increment.
An observation with zero retained evidence raises an explicit error.

The escape state's emission is zero because its population is unknown. The
result is therefore conditioned on paths that stayed inside the projection.
The product of evidence increments is the projected path likelihood. It omits
escaped paths rather than inventing a measurement for them.

Interval escape diagnostics help assess projection adequacy, but conditioning
can amplify omitted probability. The filter does not claim a posterior error
bound equal to the unconditioned escape probability. Compare larger projections
for inference, especially when observations are unlikely.

## Backward smoothing

Backward messages satisfy

\[
\beta_{k-1}=e^{Q\Delta t_k}(b_k\odot\beta_k).
\]

The smoothed marginal is proportional to `alpha_k * beta_k`. Messages can be
rescaled without changing that marginal. The implementation uses sparse column
semigroup actions and checks them against a separate dense exponential.

Observation-grid sampling proceeds backward from the final filtered state. The
conditional predecessor weight is `alpha_k(i) P_(dt)(i,j)`. It samples only
measurement-time states. A sampled grid is not a complete reaction history.

## Continuous population bridges

`hazel.inference.bridge` conditions a population chain on two endpoints. With
uniformization, the conditional virtual-event count has weights

\[
P(N=n\mid X(0)=a,X(t)=b)\propto
 e^{-\lambda t}{(\lambda t)^n\over n!}(P^n)_{ab}.
\]

After sampling this count, the embedded chain is sampled backward using stored
forward powers. Sorted uniform times provide event locations. Virtual self
transitions are removed.

The result is a continuous-time population path retained by the projection.
It does not reconstruct state-preserving chemical reactions or resolve which
reaction label caused an aggregated population jump. Endpoints must belong to
the projection. The Poisson mean is limited to 64; use intermediate endpoints
for longer intervals. Tiny endpoint probabilities can be rejected when binary64
conditioning is unreliable.

## Likelihood gradients

Forward tangent propagation gives the derivatives of predicted probabilities.
The observation channel then differentiates its normalizer:

\[
\partial_\theta z_k=\sum_i s_k^-(i)b_k(i),\qquad
\partial_\theta\log z_k={\partial_\theta z_k\over z_k},
\]

\[
s_k(i)={s_k^-(i)b_k(i)-\alpha_k(i)\partial_\theta z_k\over z_k}.
\]

The score is the sum of log-evidence derivatives. Omitting the normalizer term
would differentiate an unnormalized vector and give the wrong likelihood score.
Centered finite differences test this complete recurrence.

State sets remain fixed throughout a fit or sensitivity calculation. Changing
the projection as a parameter moves would introduce a separate discontinuous
approximation. Recheck projection adequacy after fitting and across profiles.

## Bounded fitting and profiles

The optimizer minimizes negative projected log likelihood using inverse-BFGS,
projected gradients, and Armijo backtracking. Bounds are finite and explicit.
A direction whose gradient points outside an active bound is suppressed in the
projected convergence test.

The result retains objective history, gradient, iteration count, parameters, and
termination status. An exhausted iteration budget or failed line search is not
convergence. A profile fixes one parameter on a supplied grid and refits the
remaining selected parameters.

There is no automatic claim of a global optimum, posterior confidence region,
or identifiability merely because an optimizer converges. The analytic arrival
example checks a case whose maximum is independently known.

## Bootstrap particles

Each particle propagates with the direct reaction simulator. Observation
likelihoods update normalized weights and the marginal likelihood estimate.
Systematic resampling is triggered by an effective sample size threshold.

Resampling preserves populations and ancestry, then assigns independent
particle-indexed future streams. Cloned ancestors do not receive identical
future random numbers. A new global step seed makes this rule explicit.

For a standard bootstrap filter, the likelihood estimator is nonnegative and
unbiased under the stated simulation and observation model. Its logarithm is
not unbiased. The tests compare likelihoods in expectation rather than averaging
log likelihoods and treating the result as exact.

A finite particle population can assign zero evidence to an exact observation
even when the model likelihood is positive. That is a `:zero-evidence` particle
outcome, not proof that the observation is impossible. Increase particles or
use an observation model appropriate to the measurement process.

Event-budget exhaustion aborts likelihood estimation. Treating truncated paths
as zero-weight samples would change the estimator and invalidate its intended
use in pseudo-marginal inference.

## Particle marginal Metropolis-Hastings

Parameters are positive and proposals operate in log coordinates `z=log theta`.
A symmetric Gaussian proposal in `z` targets

\[
\widehat L(\theta)\pi(\theta)\prod_j\theta_j.
\]

The final product is the Jacobian of the coordinate transformation. It appears
as `sum(z)` in the log target. Omitting it would sample a different prior measure.

A proposal includes a fresh particle likelihood estimate. On rejection, the
current parameter value, likelihood estimate, and particle seed are retained.
Recomputing the current likelihood after rejection is a different algorithm.

Gamma and bounded uniform priors are supported. Out-of-support proposals are
rejected before particle simulation. A failed event budget aborts the chain;
a zero likelihood estimate gives a rejected proposal. Failure to obtain a valid
initial estimate returns an explicit initialization status.

Basic split-Rhat, scalar initial-positive-sequence ESS, and Monte Carlo standard
errors are available as diagnostics. Rhat here is not rank-normalized. Constant
chains receive an explicit nonfinite-Rhat classification. These diagnostics do
not establish convergence, and the supplied short mechanics tests are not a
scientific posterior study.

## Path-space importance sampling

A proposal may change kinetic parameters while preserving every positive
target reaction rate. For a complete reaction path with jumps `r_j` at `t_j`,

\[
\log w=\sum_j\log{a_{r_j}(x_{j-1})\over\tilde a_{r_j}(x_{j-1})}
       -\int_0^T\{a_0(X_s)-\tilde a_0(X_s)\}\,ds.
\]

The estimator averages `w * indicator(event)`. The time integral is exact for
the piecewise-constant population path. Proposal paths that are impossible under
the target receive zero weight. The reverse support failure is rejected.

Cross-entropy pilot updates fit proposal rates using weighted reaction counts
and exposure. The final estimator must use an independent seed and fresh paths.
Reusing adaptively selected pilot paths as ordinary fixed-proposal samples is
not supported. Empirical standard errors and event ESS can reveal poor weights;
they are not deterministic confidence bounds.

## Information design

For an exact observable with outcome probabilities `p_y` and derivatives `d_y`,
the retained contribution to Fisher information is

\[
I=\sum_{y:p_y>0}d_y d_y^T/p_y.
\]

The design engine adds information from independent snapshot experiments.
Repeated measurements on one correlated trajectory cannot be scored by simply
adding these snapshot matrices. Use the full filtering likelihood for that case.

Greedy selection supports regularized D, A, and E criteria and explicit costs.
It is deterministic for ties and honors the budget. It is a greedy heuristic,
not a global combinatorial design optimizer. The regularization changes the
objective and can change which experiment is selected first.
