---
status: proposed
priority: P2
category: persistence
epic: storage-backend
---

# Prolly-tree storage backend evaluation

Evaluate backing Mobi's RDF storage with the content-addressed `prolly-rdf4j`
Sail, to remove the O(history) compiled-resource replay and lift the heap-bound
storage ceiling. This plan is the implementation track for
[ADR-0001](../docs/adr/0001-evaluate-prolly-tree-storage-backend.md); it is
**proposed**, not started, and gated on a repo-wide RDF4J upgrade.

## Goal

A Mobi build in which a dataset repository can be configured to use the
`ProllySail` backend (Strategy B in ADR-0001), validated to (a) pass Mobi's
existing catalog and ontology test suites and (b) preserve blank-node correctness
on a blank-node-heavy ontology — with a measured comparison of compiled-resource
read time against the RDF4J native store on a deep-history record.

## Decisions

- **D-1: Pursue the shallow Sail swap first (ADR-0001 Option B), not direct
  versioning delegation.** It validates the substrate under real workloads at the
  lowest correctness risk, because Mobi's own merge stays in charge.
- **D-2: Keep Mobi's `deterministicSkolemize` merge; do not delegate merge to the
  store.** prolly-rdf4j's merge is byte-set-union, "correct only for
  blank-node-free RDF"; Mobi's ontologies are blank-node-heavy
  (`SimpleBNodeService.java` exists precisely to handle this). Delegating would
  regress correctness. (ADR-0001 Context.)
- **D-3: The RDF4J 4→5 upgrade is its own prerequisite, tracked separately.** It
  is repo-wide and blocks every later phase; do not fold it into a storage step.

## What's there already (anchoring)

- `com.mobi.repository.impl.prolly/` — a DRAFT binding (`ProllyRepositoryWrapper`,
  `ProllyRepositoryConfig`, pom), **not** in the reactor `<modules>` list. Its pom
  header documents the three prerequisites below.
- `mobi-parent/pom.xml` — `rdf4j.version = 4.3.15`; compiler `<release>17</release>`.
- `com.mobi.repository.impl.sesame/` — the current memory / native / SPARQL /
  HTTP RDF4J backends the prolly backend would join.
- `com.mobi.catalog.impl/.../SimpleCompiledResourceManager.java:197` — the
  O(history) replay this evaluation targets.

## Phase 1 — Clear the prerequisites (each blocks all later phases)

- [ ] **Step 1.** Upgrade RDF4J 4.3.15 → 5.x repo-wide (its own sub-plan and ADR);
  green the full reactor on the new Sail service-provider interface.
- [ ] **Step 2.** Raise the Java baseline 17 → 21 and add the Panama runtime flags
  (`--enable-preview --enable-native-access=ALL-UNNAMED`) to the Karaf runtime.
- [ ] **Step 3.** Package `prolly-rdf4j` / `prolly-rdf` / `prolly-port-core` as
  OSGi bundles with the RocksDB native library on the Karaf native path.

## Phase 2 — Wire the Sail (Strategy A proving ground)

- [ ] **Step 4.** Add `com.mobi.repository.impl.prolly` to the reactor `<modules>`;
  get it compiling.
- [ ] **Step 5.** Register `ProllyRepositoryConfig` as an OSGi repository type
  alongside the sesame backends; configure one dataset repository to use it.
- [ ] **Step 6.** Run the existing catalog + ontology integration suites against a
  prolly-backed dataset repository; record pass/fail counts.

## Phase 3 — Correctness validation (the merge hazard)

- [ ] **Step 7.** Build the falsification test: load `pizza.owl`, create two
  divergent branches that both edit the same `owl:Restriction` blank-node
  structures, and assert Mobi's merge output is unchanged whether the store is
  native RDF4J or prolly — proving merge correctness is unaffected because it runs
  above the Sail.
- [ ] **Step 8.** Verify prolly's commit-time canonicalization does not disturb
  Mobi's stored-blank-node round-trip (`PRESERVE_BNODE_IDS` export at
  `SimpleCompiledResourceManager.java:183`).

## Phase 4 — Measure the read-path win (Strategy B motivation)

- [ ] **Step 9.** On a deep-history record (synthesize ≥ N commits), measure
  `getCompiledResource` wall-clock on native vs. prolly. Report the ratio and the
  machine; do not invent absolute numbers.
- [ ] **Step 10.** If the win holds, draft the follow-on plan to migrate the
  Catalog read path (`getCompiledResource` → prolly snapshot read), keeping merge
  on Mobi's implementation.

## Out of scope

- Delegating merge or conflict detection to prolly (D-2) — blocked until prolly's
  blank-node-correct merge ships.
- The RDF4J 4→5 upgrade *internals* — Step 1 references it; the upgrade is its own
  plan and ADR.
- Removing the RDF4J native/memory backends — prolly is added as an option, not a
  replacement, during evaluation.

## Where this lives

- `com.mobi.repository.impl.prolly/` — the draft binding
- `com.mobi.repository.impl.sesame/` — the backends it joins
- `com.mobi.catalog.impl/src/main/java/com/mobi/catalog/impl/SimpleCompiledResourceManager.java`
  — the read path
- `mobi-parent/pom.xml` — the RDF4J + Java version pins
- Serves: [ADR-0001](../docs/adr/0001-evaluate-prolly-tree-storage-backend.md)
