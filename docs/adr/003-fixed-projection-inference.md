# ADR 003: fixed projections during differentiation and fitting

Status: accepted

## Context

Changing a population ceiling while changing a parameter alters the numerical
state representation. Treating that adaptive selection as a smooth likelihood
operation would obscure what the reported derivative means.

## Decision

Differentiate and fit on a fixed explicit state set. Recompute all rates on that
set for each parameter value. Sensitivity entry points require a model hash
matching the generator. Treat projection adequacy as a separate check across
the parameter region of interest.

A base rate of zero may hide future states during reachability enumeration.
Provide a deliberate common state set through the API when parameters enable
previously disabled channels.

## Consequences

Tangent equations have a well-defined finite-generator meaning and can be
checked by centered differences. The optimizer does not automatically validate
full infinite-state likelihood accuracy. Users must compare projections after
fitting and across profiles rather than interpreting optimizer convergence as
a projection convergence result.
