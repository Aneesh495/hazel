# ADR 001: inert models and explicit escaped probability

Status: accepted

## Context

Reaction models should be replayable as data and inspectable without executing
arbitrary code. Truncating an unbounded population domain creates an approximation
that must remain visible to later inference and interpretation.

## Decision

Use one bounded EDN value with a strict model schema. Fix species order at
compilation and retain the original validated source plus its canonical hash.
Support combinatorial mass action rather than arbitrary model-supplied closures.

Retain every outgoing edge of a finite projection. Aggregate boundary crossings
into an absorbing escape state. Never reflect those edges or renormalize away
their probability. Adaptive enlargement restarts from the original initial law.

## Consequences

Models can be included directly in result bundles and independently reconstructed.
The implementation has a smaller kinetic language but a clearer trust boundary.
Projection escape can be conservative for endpoint events because it includes
trajectories that would return. Inference must explicitly condition on retained
paths until a richer tail-observation treatment is implemented.

The restart strategy repeats work. That cost is accepted to keep each refinement
attempt's probability meaning simple and auditable.
