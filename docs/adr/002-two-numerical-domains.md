# ADR 002: binary64 execution and a bounded rational checker

Status: accepted

## Context

Sparse transient analysis benefits from primitive arrays and binary64 arithmetic.
Numerical diagnostics alone do not justify formal roundoff claims. Fully rational
calculation can make exact inequalities explicit but grows expensive quickly.

## Decision

Use binary64 for general sparse execution, with separate escape, Poisson-tail,
mass-defect, and floating-point residual fields. Use a small rational reference
path for integer/ratio rate models. Its certificate is a lower distribution plus
an exact deficit, checked through a separate source-level reconstruction.

Do not label ordinary solver results as certified. Do not silently convert
binary64 constants into exact decimal rationals for the certificate path.

## Consequences

The general engine remains usable on much larger state spaces. The rational
path supplies an inspectable arithmetic check where its explicit budgets permit.
The two modes have different contracts and must remain visually distinct in
reports and documentation.

The checker is independent in implementation, not a formal proof-assistant
kernel. Its trust base is documented and included in code review scope.
