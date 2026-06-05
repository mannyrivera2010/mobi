---
status: proposed        # proposed | active | complete | deferred
priority: P2            # P0 | P1 | P2 | P3
category: <area>        # e.g. catalog, persistence, ontology, build
epic: <epic-name>       # groups related plans; the blast-radius proxy
---

<!-- Copy this file to a descriptive kebab-case name. Module-scoped plans go in
     <module>/plans/; cross-cutting plans go in root plans/. Delete these comments. -->

# <Plan title>

<One lead paragraph: what this plan does and its current status, in a breath.>

## Goal

<The concrete, user-visible acceptance contract. What can a user (or operator,
or test) do when this is done that they cannot do now?>

## Decisions

- **D-1: <decision>.** <Rationale — the "why this approach" over the alternative.>
- **D-2: <decision>.** <Rationale.>

## What's there already (anchoring)

<What the plan builds on, grounded in `file:line`. This is the snapshot that
rots — re-anchor before resuming.>

## Phase 1 — <name>

- [ ] **Step 1.** <Small, verifiable action. One component + a few tests is normal.>
- [ ] **Step 2.** …

## Phase 2 — <name>

- [ ] **Step 3.** …

## Out of scope

- <Explicitly excluded work, to keep scope creep visible.>

## Where this lives

- <real source paths this plan touches>
- Serves: <ADR or issue link>
