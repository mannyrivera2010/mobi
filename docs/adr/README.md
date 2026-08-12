# Architecture Decision Records

An **Architecture Decision Record** (ADR) captures a single architectural
decision: the context that forced it, the options weighed, the choice made, and
the consequences accepted. ADRs are immutable once accepted — a later decision
that reverses an earlier one is a *new* ADR that supersedes it, never an edit.

## When to write one

Write an ADR when a decision:

- Touches a storage format, wire protocol, or public API surface.
- Picks between viable approaches with materially different trade-offs.
- Has high revisitation cost (schema, security boundary, dependency direction).

Do **not** ADR routine bug fixes or refactors — those belong in the pull-request
description.

## The strict 4-question template

Every ADR answers exactly four questions, in order. Copy
[`_TEMPLATE.md`](_TEMPLATE.md):

1. **Context** — the forces in tension. What makes this a decision and not an
   obvious call? Ground the forces in `file:line` or measured facts.
2. **Options** — at least two, each in a comparison table with its trade-offs.
   An option list of one is a decision already made; surface the alternatives you
   actually rejected.
3. **Decision** — the choice, stated plainly, with the status (`Proposed` /
   `Accepted` / `Superseded by NNNN`).
4. **Consequences** — what becomes easier, what becomes harder, and what new work
   or risk the decision creates. Name the costs, not just the wins.

## Conventions

- **Filename:** `NNNN-kebab-case-title.md`, zero-padded four-digit number,
  allocated in sequence. `0000` is the meta-ADR adopting the practice.
- **Status** is a line near the top: `Proposed` until a decision is committed,
  then `Accepted`, then `Superseded by NNNN-...` if reversed.
- **Calibrated honesty:** an ADR that overstates its evidence is worse than none
  — every factual force in *Context* names where it came from.

## Index

| ADR | Title | Status |
|---|---|---|
| [0000](0000-record-architecture-decisions.md) | Record architecture decisions | Accepted |
| [0001](0001-evaluate-prolly-tree-storage-backend.md) | Evaluate a prolly-tree storage backend for the Catalog | Proposed |
| [0002](0002-out-of-process-prolly-rdf4j-over-a-network-boundary.md) | Out-of-process prolly-rdf4j over a network boundary | Proposed |
| [0003](0003-rdf4j-4-to-5-upgrade.md) | RDF4J 4.3.15 → 5.x upgrade | Proposed |
| [0004](0004-karaf-jakarta-coupling-for-java-25.md) | Java 25 decoupled from javax→jakarta (Karaf stays on 4.4.x) | Proposed |
| [0005](0005-defer-karaf-vs-quarkus-to-jakarta-gate.md) | Defer the Karaf-vs-Quarkus runtime decision to the Jakarta migration gate | Proposed |

## Where this lives

- This convention adapts the prolly-port ADR practice (`prolly-rdf4j/docs/adr/`)
  and Michael Nygard's original ADR pattern.
- The plan practice that ADRs hand work to: [`../../plans/README.md`](../../plans/README.md)
