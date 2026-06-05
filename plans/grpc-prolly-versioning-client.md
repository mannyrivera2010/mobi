---
status: proposed
priority: P2
category: persistence
epic: storage-backend
---

# gRPC prolly versioning client (Mobi side)

The Mobi-side client for delegating versioning reads/writes to an out-of-process
prolly-rdf4j over **gRPC**. This is the client half of a two-repo effort; the
server half is `prolly-port/plans/grpc-versioning-service.md` in the prolly-port repository.
Together they realize the gRPC transport that
[ADR-0002](../docs/adr/0002-out-of-process-prolly-rdf4j-over-a-network-boundary.md)
selects for Strategy B, and the gRPC path of the
[out-of-process plan](out-of-process-prolly-service.md). Proposed, not started;
gated on that plan's Phase-0 measurement showing the replay cost is real.

## Goal

Mobi can read a graph at a commit, create commits, and manage branches against a
remote prolly process over gRPC — replacing the O(history) compiled-resource
replay with an O(1) snapshot-read remote call — behind a config flag, with merge
kept in Mobi's Catalog, and with the end-to-end read measured against both the
current replay and the SPARQL-over-HTTP alternative.

## Decisions

- **D-1: gRPC carries versioning; stock SPARQL cannot.** `SPARQLRepositoryWrapper`
  speaks only SPARQL 1.1 (its config is endpoint URLs only) — no `?commit=`,
  branch, or commit verb (ADR-0002 D-3).
- **D-2: The `.proto` contract is owned by the prolly-rdf4j-grpc module** (single
  source of truth); Mobi vendors a copy and generates client stubs from it. The
  two plans must keep the contract in lockstep.
- **D-3: De-risk gRPC-in-Karaf *first*.** gRPC-java + Netty (grpc-netty-shaded)
  in OSGi is the known hazard (ADR-0002 Consequences). If it cannot be bundled
  cleanly, fall back to prolly's existing REST endpoints (ADR-0002 Option C) and
  stop — do not force it.
- **D-4: Keep merge in Mobi's Catalog** (inherits ADR-0001 D-2). The client
  exposes merge-base + diff; Mobi computes the blank-node-correct merge and writes
  a multi-parent commit.
- **D-5: Ship behind a config flag and measure.** Wire the snapshot read behind a
  per-repository flag; report read time as a ratio versus the replay and versus
  SPARQL-over-HTTP (continues the out-of-process plan's Phase-0 baseline).
- **D-6: Consume the performance contract — batched, interned, cacheable.** The
  shared `versioning.proto` streams `QuadBatch` (not per-quad `Quad`) carrying an
  interned term dictionary (server plan P-1); the client un-interns on decode. **The
  client decode is where Mobi's read latency actually lands** — not the server — so it
  is what Step 6 must measure. Because a compiled resource is immutable
  (content-addressed), the client (a) passes a `have` commit so the server skips
  re-streaming a snapshot Mobi already holds, and (b) **caches decoded compiled
  resources keyed by commit hash** so repeated reads of the same commit never touch the
  wire (server plan P-2). Batch branch-head lookups where Mobi needs several
  (server plan P-3). Avoid materialising a whole compiled resource on heap twice
  (proto objects + Mobi model) — stream batches into the model and drop them.

## What's there already (anchoring)

- `com.mobi.repository.impl.sesame/src/main/java/com/mobi/repository/impl/sesame/sparql/SPARQLRepositoryWrapper.java`
  — the remote-store pattern, and the proof it cannot carry versioning.
- `com.mobi.catalog.impl/src/main/java/com/mobi/catalog/impl/SimpleCompiledResourceManager.java`
  — the replay (`buildCompiledResource`) a snapshot read replaces.
- `com.mobi.repository.impl.prolly/src/main/java/com/mobi/repository/impl/prolly/ProllyRepositoryWrapper.java`
  — the embedded draft this is an alternative to.
- The server side: `prolly-port/plans/grpc-versioning-service.md` in the prolly-port repository,
  which owns `src/main/proto/versioning.proto`.

## Phase 1 — De-risk gRPC inside Karaf (do this before anything else)

- [ ] **Step 1.** Stand up a throwaway gRPC echo service; prove a Mobi OSGi bundle
  can open a `ManagedChannel` and round-trip to it inside the running Karaf
  distribution (grpc-netty-shaded on the bundle classpath, native/`Bundle-NativeCode`
  resolved). Record exactly what bundling was required.
- [ ] **Step 2 (gate).** If gRPC cannot be bundled cleanly, **stop the gRPC path**
  and pursue ADR-0002 Option C (prolly's existing REST) instead; record the
  decision. Otherwise proceed.

## Phase 2 — Generate the client from the shared contract

- [ ] **Step 3.** Add a new module (for example `com.mobi.versioning.grpc.client`),
  vendor `versioning.proto` from the prolly-rdf4j-grpc module, and wire
  `protobuf-maven-plugin` to generate the stubs. Pin the contract version.
- [ ] **Step 4.** Implement a thin Java client over the generated stubs:
  `getCompiledResource(commit)`, `commit(delta, parents, message)`,
  `createBranch`, `getBranchHead`, `diff(a, b)`, `getMergeBase(a, b)`.
  **Decode the performance contract (D-6):** consume `QuadBatch` streams and un-intern
  the term dictionary into Mobi's model; keep a bounded **client-side cache of decoded
  compiled resources keyed by commit hash** (immutable ⇒ a hit never touches the wire),
  and pass the `have` commit on read so the server can skip a snapshot Mobi already
  holds. Do not hold both the proto objects and the Mobi model for the whole resource at
  once — stream batches in and release them.

## Phase 3 — Strategy B read path (the actual win)

- [ ] **Step 5.** Behind a per-repository config flag, route the compiled-resource
  read for a prolly-backed record to the client's `getCompiledResource(commit)`
  server-stream instead of `SimpleCompiledResourceManager.buildCompiledResource`.
- [ ] **Step 6.** Measure: end-to-end read time at increasing chain depth, as a
  ratio versus the replay baseline (the out-of-process plan's Phase-0 harness) and
  versus the SPARQL-over-HTTP path; name the machine. **Measure the boundary honestly
  (D-6):** the timing must include **client-side decode** (un-intern + build the Mobi
  model), since that — not server encode — is where Mobi's latency lands; report a
  cold read (cache miss, full stream + decode) AND a **warm read (client cache hit)**
  separately, so the immutable-resource cache's effect is visible rather than averaged
  away.

## Phase 4 — Versioning writes (keep merge in Mobi)

- [ ] **Step 7.** Route commit creation and branch operations through the client
  (`commit`, `createBranch`, `getBranchHead`).
- [ ] **Step 8.** Merge stays in Mobi: fetch `getMergeBase` + `diff` (or both
  compiled resources), compute the blank-node-correct merge with Mobi's existing
  machinery, and write a multi-parent commit via `commit(...)`.

## Out of scope

- Delegating merge to prolly (D-4) — until prolly's blank-node-correct merge ships.
- The embedded Sail path — that is
  [prolly-tree-storage-backend-evaluation](prolly-tree-storage-backend-evaluation.md).
- Productionising the two-process deployment (supervision, security, the
  distribution launching the sidecar) — a separate plan if the evaluation succeeds.

## Where this lives

- `com.mobi.catalog.impl/src/main/java/com/mobi/catalog/impl/SimpleCompiledResourceManager.java`
  — the read path Phase 3 replaces
- `com.mobi.repository.impl.sesame/src/main/java/com/mobi/repository/impl/sesame/sparql/SPARQLRepositoryWrapper.java`
  — the SPARQL alternative measured against
- Serves: [ADR-0002](../docs/adr/0002-out-of-process-prolly-rdf4j-over-a-network-boundary.md);
  paired with `prolly-port/plans/grpc-versioning-service.md` (prolly-port repository)
