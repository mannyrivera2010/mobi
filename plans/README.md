# Implementation plans

A **plan** sequences multi-step work into small, verifiable increments before any
code is written. It is the unit of "what we are going to build," distinct from an
[ADR](../docs/adr/README.md) ("why we decided") and a
[newcomer doc](../newcomer-docs/index.md) ("how it works today").

## Where plans live

- **Module-scoped (preferred):** `<module>/plans/<name>.md`, in the Maven module
  the plan primarily affects.
- **Cross-cutting:** `plans/<name>.md` at the repository root, for work that
  touches many modules (for example, an RDF4J version upgrade or the storage-
  backend evaluation).

## Lifecycle

A plan moves through states, recorded in its frontmatter `status`:

`proposed` → `active` → `complete` (or `deferred`)

- **proposed** — written, not started. Writes no code, so it drifts no branches.
- **active** — some steps checked, some not. This is *started work carrying live
  assumptions* — keep the count of active plans low, because two active plans
  over the same code cross-invalidate each other.
- **complete** — every step done and verified by running the tests, not by
  trusting the prose.

> **Re-anchor before resuming.** A plan is a snapshot of the codebase as it was
> when written. Before driving a not-just-created plan, re-read each unchecked
> step against the *current* code and confirm it is still needed, still accurate
> (paths/classes it names still exist), and unblocked.

## Plan shape

Copy [`_TEMPLATE.md`](_TEMPLATE.md). Every plan has:

1. **YAML frontmatter** — `status`, `priority`, `category`, `epic`.
2. **Lead paragraph** — what the plan does, in one breath.
3. **Goal** — the concrete, user-visible acceptance contract.
4. **Decisions (D-1, D-2, …)** — each with its rationale (the "why this
   approach").
5. **What's there already** — the anchoring: what the plan builds on, with
   `file:line`.
6. **Phases of numbered `- [ ]` steps** — each step small enough that one
   component plus a few tests is normal.
7. **Out of scope** — to keep scope creep visible.

After each step is implemented, check the box and append a wrap-up paragraph in
parentheses: what landed, what surprised (a refuted assumption is the best
entry), any plan correction, and the **test count** (`42/42` beats "tests pass").
Do not rewrite the original step text when it turns out wrong — document the
correction in the wrap-up so the plan's evolution stays visible.

## Index

| Plan | Status | Scope |
|---|---|---|
| [prolly-tree-storage-backend-evaluation](prolly-tree-storage-backend-evaluation.md) | proposed | cross-cutting |
| [out-of-process-prolly-service](out-of-process-prolly-service.md) | active | cross-cutting |
| [grpc-prolly-versioning-client](grpc-prolly-versioning-client.md) | proposed | cross-cutting |

## Where this lives

- The decision this plan serves: [ADR-0001](../docs/adr/0001-evaluate-prolly-tree-storage-backend.md)
- This convention adapts the prolly-port plan practice (`CLAUDE.md` "Plan format").
