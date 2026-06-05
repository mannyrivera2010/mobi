---
status: active
priority: P2
category: persistence
epic: storage-backend
---

# Out-of-process prolly-rdf4j service evaluation

Evaluate integrating `prolly-rdf4j` as a **separate process** behind a network
boundary, rather than embedded in Mobi's Java Virtual Machine. This is the
implementation track for
[ADR-0002](../docs/adr/0002-out-of-process-prolly-rdf4j-over-a-network-boundary.md);
it is an **alternative** to the embedded
[prolly-tree storage backend evaluation](prolly-tree-storage-backend-evaluation.md),
not pursued alongside it. Proposed, not started.

## Goal

A measured, evidence-based answer to: does an out-of-process prolly integration
(a) eliminate the embedded path's three prerequisites — especially the repo-wide
RDF4J 4→5 upgrade — and (b) deliver acceptable read performance for a
coarse-grained snapshot-read contract? With a working Strategy A proof of concept
that needs no RDF4J upgrade, no Java 21 in Mobi, and no OSGi packaging of prolly.

## Decisions

- **D-1: The integration boundary is the network, not the in-Java-Virtual-Machine
  Sail.** That is what removes the three prerequisites (ADR-0002 D-1). The decisive
  one is the RDF4J 4→5 upgrade, which an embedded Sail cannot avoid.
- **D-2: Reuse prolly's existing `prolly-rdf4j-rest` endpoints before writing any
  new server surface.** It already exposes `/sparql` and versioning
  (`/sparql/commits`, `?commit=`). gRPC is introduced only if Phase 0 shows its
  constant-factor advantage justifies a new contract plus the gRPC-client OSGi cost.
- **D-3: Versioning never rides the stock `SPARQLRepositoryWrapper`** — it cannot
  express it (`SPARQLRepositoryConfig` is endpoint URLs only). Versioning is
  custom-REST or gRPC.
- **D-4: Keep merge in Mobi's Catalog** (inherits ADR-0001 D-2).

## What's there already (anchoring)

- `com.mobi.repository.impl.sesame/src/main/java/com/mobi/repository/impl/sesame/sparql/SPARQLRepositoryWrapper.java`
  — wraps a stock RDF4J `SPARQLRepository(endpointUrl)`; the zero-new-code Strategy
  A path.
- `prolly-rdf4j-rest` (in the prolly-rdf4j project, not this repository) — the
  Spring Boot server with `/sparql` + versioning endpoints.
- `com.mobi.catalog.impl/src/main/java/com/mobi/catalog/impl/SimpleCompiledResourceManager.java`
  — the O(history) replay a coarse snapshot-read RPC would replace.

## Phase 0 — Measure (turn the performance intuition into data)

- [ ] **Step 0 — Confirm the problem exists, in Mobi alone (no prolly needed).**
  Measure whether `getCompiledResource` cost grows with commit-chain depth. This is
  the gate behind *all three* ADRs: if the replay is not actually hurting at
  realistic depths, stop — neither prolly path is justified yet. Uses only Mobi's
  existing catalog + in-memory store; needs no prolly, no RDF4J upgrade, no Java 21.
  *(Started 2026-06-04 — runbook below authored; execution pending a Java 17 build
  environment, which the authoring session lacked.)*

  ### Runbook for Step 0

  The harness already exists: `SimpleCompiledResourceManagerTest`
  (`com.mobi.catalog.impl/src/test/java/com/mobi/catalog/impl/SimpleCompiledResourceManagerTest.java`)
  wires a real `SimpleCompiledResourceManager` over a `MemoryRepositoryWrapper` and
  calls `manager.getCompiledResource(commitId, conn)` after loading a commit-chain
  fixture with `addData(repo, "/testCatalogData/.../X.trig", RDFFormat.TRIG)`. To
  turn it into a depth measurement, copy that harness into a sibling class (named
  without the `Test` suffix so surefire does not auto-run it) and wrap the verified
  call in a timing loop:

  ```java
  // mirror SimpleCompiledResourceManagerTest setUp (lines 84-110), then:
  long timeReadMillis(Resource headCommit, int reps) {
      long best = Long.MAX_VALUE;
      try (RepositoryConnection conn = repo.getConnection()) {
          for (int i = 0; i < reps; i++) {
              long t0 = System.nanoTime();
              Model m = manager.getCompiledResource(headCommit, conn);   // the verified call
              long ms = (System.nanoTime() - t0) / 1_000_000;
              best = Math.min(best, ms);
          }
      }
      return best;   // report the min (least-noise) per depth
  }
  ```

  Drive it across **increasing chain depth** and record a depth→time table:
  - Quick start: time the read at the initial commit versus a head commit of an
    existing chain fixture (a 2-3 point sweep, enough to see direction).
  - Real curve: build (or export from a real Mobi) records at depths ~10 / 100 /
    1000 commits and read the head at each. Report **time as a ratio across
    depths**, and name the machine — no invented absolute numbers
    (`newcomer-docs/contributing/measuring-the-real-thing.md`).

  Run: `mvn -pl com.mobi.catalog.impl -am test -Dtest=<your-benchmark-class>`
  (Java 17, per `CLAUDE.md`).

  **Decision rule.** Time roughly flat across depth → the replay is not the
  bottleneck; **stop**, neither prolly path is justified yet. Time grows ~linearly
  (or worse) with depth → the problem is real; proceed to Step 1 and to the
  Strategy-B snapshot-read evaluation.

- [ ] **Step 1.** Stand up `prolly-rdf4j-rest` out of band (its own Java 21 + Panama
  process); load a representative ontology (for example `pizza.owl` scaled up, or a
  deep-history synthetic record).
- [ ] **Step 2.** Measure a coarse **compiled-resource read at a commit** over (a)
  SPARQL-over-HTTP with RDF4J's binary results format and (b) a gRPC server-stream,
  on the same data and host. Report payload size and wall-clock as a **ratio**, and
  name the machine — no invented absolute numbers (see
  `newcomer-docs/contributing/measuring-the-real-thing.md`).
- [ ] **Step 3.** Measure a **fine-grained Strategy A** sequence (many `getStatements`/
  `add` calls) over the wire, to confirm the reasoning that chatty access is
  latency-bound out-of-process and Strategy B coarse reads are required.

## Phase 1 — Strategy A proof of concept (no prerequisites)

- [ ] **Step 4.** Configure a `SPARQLRepositoryConfig` pointing at the running
  `prolly-rdf4j-rest` `/sparql`; register it as a dataset repository.
- [ ] **Step 5.** Run the catalog + ontology suites against it; confirm functional
  correctness **and** that it required no RDF4J upgrade, no Java 21 in Mobi, and no
  OSGi packaging of prolly. Record the result.

## Phase 2 — Strategy B read path (the actual win)

- [ ] **Step 6.** Build a thin client for the snapshot read — `?commit=<hash>` over
  HTTP (a customised `SPARQLProtocolSession`) or the gRPC `CompiledResource` stream,
  whichever Phase 0 favoured.
- [ ] **Step 7.** Wire it into the compiled-resource read path and measure against
  the embedded native store; report the ratio on a deep-history record.

## Phase 3 — Versioning mutations

- [ ] **Step 8.** Over the chosen transport (custom-REST or gRPC), exercise
  branch/commit-with-message against prolly; keep merge in Mobi's Catalog.

## Out of scope

- The embedded Sail path — that is
  [prolly-tree-storage-backend-evaluation](prolly-tree-storage-backend-evaluation.md);
  the two are alternatives.
- Delegating merge to prolly (D-4) — blocked until prolly's blank-node-correct merge
  ships.
- Productionising the two-process deployment (supervision, security, the
  distribution launching the sidecar) — a separate plan if the evaluation succeeds.

## Where this lives

- `com.mobi.repository.impl.sesame/src/main/java/com/mobi/repository/impl/sesame/sparql/SPARQLRepositoryWrapper.java`
  — the remote-store entry point
- `com.mobi.catalog.impl/src/main/java/com/mobi/catalog/impl/SimpleCompiledResourceManager.java`
  — the read path Strategy B replaces
- Serves: [ADR-0002](../docs/adr/0002-out-of-process-prolly-rdf4j-over-a-network-boundary.md)
