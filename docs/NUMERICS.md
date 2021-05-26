# Numerical contracts

## State and rate convention

For species populations `x` and reaction `r`, let `s_r` be the consumed counts,
`v_r` the population change, and `k_r` the rate constant. Hazel uses

\[
a_r(x)=k_r\prod_i {x_i\choose s_{ri}}.
\]

An unavailable reactant makes the corresponding factor zero. For `2 A -> B`,
the hazard is `k A(A-1)/2`. Parameters imported from a convention using
`k A(A-1)` must be converted explicitly. No volume scaling is implicit.

States and stoichiometry use machine-range nonnegative integer populations.
Combinatorial factors and structural algebra promote to arbitrary precision.
Binary64 routines reject nonfinite model inputs and invalid probability mass.
The total reactant order is capped at 32.

## Sparse row generator

The forward equation is

\[
\dot p(t)=p(t)Q.
\]

For distinct population states, `Q_ij` sums hazards of reactions from `i` to `j`.
The diagonal is `Q_ii = -sum_{j != i} Q_ij`. State-preserving events are excluded
from this population generator and retained separately as reaction events.

Every row sums to zero. Off-diagonal rates are nonnegative. The escape state
has an empty row and zero exit rate. Validation rejects duplicate destinations,
diagonal entries masquerading as edges, negative rates, and inconsistent exits.

## Finite-state projection

Let `J` be a finite set containing the initial state. Preserve every reaction
inside `J`; send each transition leaving `J` into one absorbing escape state.
The population components describe paths that have never left `J`.

Consequently, a retained component is a lower approximation to the corresponding
full-model population probability. The escaped probability includes paths that
would later return. It is intentionally conservative for endpoint events.

For an event `A` and an exact projected solution,

\[
\sum_{x\in J\cap A}p_J(x,t)
\leq P(X(t)\in A)
\leq \sum_{x\in J\cap A}p_J(x,t)+p_{escape}(t).
\]

The binary64 solver adds its analytic series-tail allowance to the upper side.
Floating-point residuals are reported separately. They are not a rigorous
roundoff enclosure. Use rational certificates when that distinction matters
and the small-model limits are acceptable.

## Uniformization

Choose `lambda >= max_i(-Q_ii)` and form `P = I + Q/lambda`. Then

\[
p(t)=e^{-\lambda t}\sum_{k=0}^{\infty}
       { (\lambda t)^k\over k!}p(0)P^k.
\]

`P` is stochastic. The solver evaluates the diagonal as `1-exit/lambda`
directly and accumulates off-diagonal contributions with positive rates.
It never renormalizes the computed probability vector to conceal omitted mass.

If `lambda=0`, the chain does not change populations. If `t=0`, the zeroth
power returns the initial vector. A supplied rate below the maximum exit rate
is invalid, even if one particular initial distribution avoids the bad row.

## Poisson truncation

Let `w_k = exp(-mu) mu^k/k!`, with `mu=lambda*t`. After keeping terms through
`K`, the first omitted term is `w_(K+1)`. If `r=mu/(K+2)<1`, each later ratio
is at most `r`, and

\[
\sum_{k>K}w_k \leq {w_{K+1}\over 1-r}.
\]

The stopping test uses this positive geometric bound rather than subtracting a
cumulative weight from one. The latter loses information when the tail becomes
small. The bound is an analytic statement evaluated in binary64, not a directed
interval calculation.

Large `mu` is split into equal time chunks with `mu/chunks <= 32`. Each chunk
receives an equal share of the requested tail tolerance. A stochastic semigroup
is an L1 contraction, so omitted mass from earlier chunks is not amplified by
later probability propagation. The reported tail allowance sums chunk bounds.

Chunk and term budgets are hard limits. Exceeding them raises a diagnostic.
Adaptive state-budget or refinement-budget exhaustion returns an incomplete
status. Neither path reports convergence merely because a budget was consumed.

## Numerical diagnostics

The transient result exposes:

| Field | Meaning |
|---|---|
| `:escape-probability` | Computed mass in the absorbing escape state |
| `:series-tail-bound` | Analytic Poisson-series omission allowance |
| `:truncation-bound` | Escape plus series allowance, clipped at one |
| `:mass-defect` | Initial total mass minus computed total mass |
| `:floating-point-residual` | Difference from mass predicted by the computed weight sums |
| `:matrix-products` | Actual sparse products implied by the evaluated series |

The floating-point residual is a diagnostic for accumulation drift. It does
not bound every component's numerical error. The independent dense solver and
analytic laws provide additional tests, not a replacement for interval analysis.

## Adaptive projection

Each attempt enumerates a larger state set and starts again from the original
initial distribution. Conservation-derived bounds cap growth where available.
An attempt is converged only if the truncation allowance meets epsilon and the
state budget was not exhausted.

The history retains bounds, state count, escape probability, series tail, and
budget outcome for every attempt. The default geometric growth is a heuristic;
it is not a minimal-state projection optimizer.

## Sensitivity equations

For a fixed state set and parameter theta,

\[
\dot s=sQ+pQ_\theta,\qquad s={\partial p\over\partial\theta}.
\]

The uniformization rate is held fixed during differentiation. This is valid
because the semigroup identity holds for any admissible fixed rate near the
evaluation point. Differentiating the maximum-rate selection itself would add
unnecessary nonsmooth bookkeeping.

Embedded-chain tangent powers satisfy

\[
p_{k+1}=p_kP,\qquad
s_{k+1}=s_kP+p_kQ_\theta/\lambda.
\]

Mass-action rate derivatives use the combinatorial factor directly. They remain
defined at a zero parameter. When the whole base generator is zero, the tangent
is `s(0)+t p(0) Q_theta`.

Sensitivity-tail accounting uses a row L1 operator bound on `Q_theta` and the
first moment of the omitted Poisson count. It excludes binary64 roundoff and
does not account for changing the projection with theta.

## First passage and backward equations

For a target set, kill outgoing rows from target states. Their accumulated
probability is the projected first-passage CDF. Incoming target flux gives its
density. Competing target sets must be disjoint.

Eventual hitting probabilities solve `-Q_UU h = Q_UT 1` on states that can
reach the target. States outside that graph have zero hitting probability.
For the time weighted by successful hitting, solve `-Q_UU g = h`, then divide
`g_i/h_i` for conditional mean times. An unconditional mean is `nil` when the
target is not reached with probability one.

Rows are scaled before numeric pivot tests. Changing time units must not create
a false singularity. Size limits protect the dense backward solve; there is no
claim of a large-scale sparse factorization backend.

## Stationary analysis and aggregation

Stationary analysis requires a genuinely closed population state space. It
finds closed communicating classes and solves one normalized stationary system
per class. Multiple classes produce multiple extremal stationary laws.

Strong lumpability requires identical total generator rates from any two states
in a block into every block. The default check uses exact stored signatures.
An explicit nonzero tolerance is an approximate diagnostic. A lumped generator
is built only after the default strong check passes.

## Moment and deterministic approximations

The polynomial generator acts as

\[
L f(x)=\sum_r a_r(x)\{f(x+v_r)-f(x)\}.
\]

Expanding this expression gives exact moment equations. A hierarchy is
integrated only if every resulting monomial belongs to the finite requested
basis. Nonlinear unclosed hierarchies fail explicitly.

The separate macroscopic approximation replaces falling factorials by powers
divided by factorials. Linear-noise covariance satisfies
`C' = J C + C J^T + D`. These are approximations for nonlinear networks. For
linear networks, the mean and covariance equations agree with exact moments.

Moment and reward equations use adaptive RK4 step doubling. Their tolerances
describe local integration error estimates. They do not inherit uniformization
tail bounds or rational certificate status.

## Independent routes

The dense exponential uses scaling and squaring with a matrix Taylor series.
It performs matrix-matrix products and does not use Poisson weights. Small
random generators, analytic distributions, and semigroup identities check the
sparse route against it.

The direct and next-reaction simulators operate on reaction populations without
the finite generator. Their statistical checks use fixed seeds and stated
sampling tolerances. The rational producer and checker use exact arithmetic
and different generator/product implementations. Agreement across these routes
is stronger evidence than repeating one implementation with the same inputs.
