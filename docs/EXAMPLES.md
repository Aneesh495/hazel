# Example studies

All rates and measurement values in these files are illustrative. The repository
does not ship an experimental dataset. Each study isolates a numerical or
modeling question and is executed by the full acceptance campaign.

## Arrival process

`immigration.edn` starts at zero and adds one particle at rate two. At time four,
the exact endpoint law is Poisson with mean eight. The initial population bound
is deliberately small, forcing adaptive refinement. The report shows every
attempt and its escaped probability.

This case checks that projection growth improves a calculation without
renormalizing away the missing mass. The analytic reference also detects time
unit mutations and reflected-boundary mistakes.

## Rational arrival certificate

`certificate.edn` asks for the probability of at least three arrivals by time
one. The exact analytic expression is `1 - 5 exp(-2)`. The supplied projection
contains populations zero through eighteen.

The result includes exact rational lower and upper bounds. The test reference
uses another rational exponential enclosure, rather than assuming printed
floating-point digits are exact. Run the independent `verify` command on the
printed bundle path.

## Switching promoter

`telegraph.edn` has one promoter in either `:off` or `:on`, with transcription
while active and first-order RNA degradation. Promoter total is conserved;
RNA count is unbounded. Adaptive projection must therefore control an infinite
population domain while preserving a finite promoter state.

The model's hazards are linear in populations. This makes its mean and higher
moment equations a useful independent route to selected transient statistics.

`telegraph-moments.edn` constructs the polynomial generator and integrates an
exactly closed degree-three hierarchy. The ODE integration is approximate; the
polynomial closure itself is exact.

## Partially observed promoter

`telegraph-filter.edn` uses a binomial RNA detector with probability 0.7. A
measurement at time two is missing. Later detected counts update both RNA
belief and the hidden promoter state.

The study saves filtered distributions, smoothed distributions, likelihood
increments, interval escape diagnostics, and a sampled observation-time latent
grid. The fixed RNA ceiling is 28. Compare a larger ceiling before interpreting
the posterior as a full infinite-state result.

## Enzyme completion time

`enzyme.edn` starts with one enzyme and six substrate molecules. Binding,
unbinding, and catalysis conserve enzyme total and substrate units. The resulting
reachable population space is finite.

The target is six product molecules. Target absorption computes the first time
that complete conversion occurs. This is a finite phase-type completion law,
not a deterministic time obtained from a mean concentration trajectory.

## Reversible dimerization

`dimer.edn` starts with twenty monomer units and permits reversible
`2 monomer <-> dimer`. It has eleven reachable population states. The
stoichiometric invariant is monomer plus twice dimer.

Stationary analysis operates on this closed state space. The association
hazard uses `choose(monomer,2)`, so the example also makes the combinatorial
rate convention explicit. It can be checked through adjacent-state detailed
balance ratios.

## Finite SIR outbreak

`epidemic.edn` is a schematic population of twelve individuals, initially one
infected. The event is ever reaching six simultaneous infections. Absorbing the
threshold states gives a first-passage probability, while recovered individuals
make the underlying population chain eventually absorbing.

This is a mechanism study with illustrative rates. It is not a forecast or a
calibrated epidemiological model.

## Kinetic proofreading and information

`proofreading.edn` uses a conserved receptor moving through free, bound,
checked, and signaling states. Unbinding competes with progression. Candidate
times are scored for information about unbinding and checking rates, observing
only whether the receptor signals.

The design adds information from independent snapshot experiments and selects
three candidate times with a regularized D objective. It does not treat repeated
observations of one receptor trajectory as independent measurements.

## Pulse and washout

`pulse.edn` changes RNA birth rates at times one and three, then turns birth
off. Decay continues. The state set stays fixed across segments, and escaped
probability is carried forward without being recycled.

The terminal distribution is still Poisson for this linear model. Its mean can
also be obtained by integrating the piecewise affine mean equation. This is an
independent check of intervention boundaries and rate-patch semantics.

## Rare arrival event

`rare-arrivals.edn` estimates the event of ten or more arrivals by time one under
rate two. A rate-eight proposal generates more relevant paths. Every sample is
corrected with the full reaction-path likelihood ratio, including waiting-time
exposure.

The estimate is compared with an analytic Poisson tail in tests. The report's
standard error is empirical. Zero observed weighted hits would be a diagnostic,
not a deterministic zero-probability conclusion.

## Conditioned extinction

`extinction-bridge.edn` conditions a decay process on going from six particles
to zero in two time units. Uniformization samples the virtual-event count and
embedded bridge, then assigns continuous event times.

Exactly six population-decreasing jumps remain after virtual transitions are
removed. This differs from rejecting unconditioned SSA paths until an endpoint
happens to match.

## Analytic parameter fit

`rate-fit.edn` observes seven arrivals after two time units. Its likelihood is
maximized at rate `7/2`. The example tests the complete pipeline from propensity
derivatives through observation normalization to a bounded optimizer.

The known answer makes this a verification fixture. It is not evidence that
every nonlinear, partially observed kinetic model is identifiable or easy to fit.
