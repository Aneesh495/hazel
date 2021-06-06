# Scope and known limits

These boundaries are part of the implementation contract. They are not hidden
failures or targets presented as completed work.

## Model class

Hazel models well-mixed, discrete, mass-action reaction networks with fixed
stoichiometry and nonnegative rate constants. There are no spatial compartments,
delayed reactions, arbitrary kinetic functions, non-Markov waiting-time laws,
or automatic physical-unit conversions.

Population counts fit nonnegative signed 64-bit integers. The schema limits
species, reaction count, and total reactant order. It does not prove
nonexplosion of every unbounded network. Event and projection budgets provide
explicit computational stopping conditions.

Structural conservation detection uses a nullspace basis. Its nonnegative-bound
detector is sufficient but incomplete. Deficiency and reversibility are
descriptors; the implementation does not claim a general symbolic classification
of all reaction-network equilibria.

## Numerical scope

The main solver is binary64. Its Poisson truncation bound excludes roundoff.
Its mass residual is a diagnostic, not a formal componentwise error enclosure.
Very large rate-time products can exceed chunk budgets. Extremely small
endpoint probabilities can make conditional sampling numerically unsuitable.

The rational reference path has strict small-model limits. It supports only
integer and ratio rate/time inputs. It is independently checked by executable
code, not a proof assistant. It relies on the language and bigint runtime.

Backward and stationary systems use small dense linear solves. There is no
sparse direct factorization, Krylov exponential backend, tensor compression,
GPU kernel, or distributed state-space exploration.

Sparse products use primitive arrays internally but materialize persistent
vectors between products. They are not allocation-free. Benchmarks describe
the included implementation and recorded environment.

## Inference scope

Finite-projection posteriors condition on retained paths. An unconditioned
escape probability does not automatically bound posterior error after unlikely
observations. Parameter fits need projection checks across the fitted range.

Sensitivity propagation holds the state set fixed. The CLI enumerates the base
model's reachable states; a rate initially equal to zero can hide states that a
later parameter value would enable. Use a deliberate common state set through
the API for such comparisons.

Sensor parameters are fixed during kinetic differentiation. Observation channels
are conditionally independent given populations. There is no automatic
inference over model topology, stoichiometry, or observation-channel parameters.

The optimizer is local. Greedy information design is heuristic and assumes
independent snapshot experiments. It does not solve a global design problem or
model correlated repeated measurements by adding snapshot information.

Particle likelihoods can be zero at finite particle count. PMMH mechanics are
tested, but no supplied short chain establishes posterior convergence for a
scientific application. The diagnostic Rhat is basic split-Rhat rather than the
rank-normalized variant.

Importance-sampling errors are empirical. Rare weight degeneracy can escape
finite-sample diagnostics. Cross-entropy pilot paths must not be reused as a
fixed-proposal production sample without a different estimator.

## Path and moment scope

Continuous bridges are population paths. They omit virtual transitions and
state-preserving chemical events. Observation-grid bridges contain only latent
states at measurement times.

Reward moments are accumulated until projection escape and use an ODE solver.
They do not automatically bound rewards accumulated after escape. Macroscopic
and linear-noise approximations can differ from nonlinear discrete dynamics.
Moment integration refuses unclosed polynomial hierarchies; it does not apply
an undocumented closure approximation.

## Artifacts and portability

Hashes detect byte changes but do not authenticate evidence against an actor
who can rewrite the entire bundle. Run acceptance locally to observe execution.
Atomic replacement requires filesystem support. Directory-entry durability
through every possible power failure is not claimed.

Strict replay compares complete numerical results. Last-bit floating-point
differences across runtime/platform combinations can require regenerating
evidence. Exact rational checking has no floating-point approximation in its
bound computation.

Runtime downloads require first-use network access. An executable JAR or a
populated pinned cache supports offline use. Source launchers and the JAR are
tested on the environments recorded by acceptance and hosted CI; no claim is
made for every historical JVM or operating system.
