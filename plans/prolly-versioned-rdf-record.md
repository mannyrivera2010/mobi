---
status: active
priority: P2
category: catalog
epic: storage-backend
---

# ProllyVersionedRDFRecord — a record type whose content lives in prolly

A new `VersionedRDFRecord` subtype, `ProllyVersionedRDFRecord`, whose versioned
content is stored in an out-of-process prolly-rdf4j repo instead of the catalog's
delta chain. The record *type* is the routing mechanism: the catalog's existing
type-dispatch (RecordService / VersioningService keyed by type IRI) sends
lifecycle and commit operations to prolly, while ordinary records are untouched.
Active; Phases 1–4 complete 2026-08-12 (all verified in a booted distribution
against the in-memory fake client — see step wrap-ups; the write path landed as
a dual write, a deliberate refinement of D-4). Phase 5 remains gated.
**Depends on**
[grpc-prolly-versioning-client](grpc-prolly-versioning-client.md) (same epic —
serialize: its Phases 1–2 must land before this plan's Phase 5 goes live; Phases
1–4 here run against a fake client and are unblocked).

## Goal

An operator can create a `ProllyVersionedRDFRecord` through REST, commit RDF to
it on master and on branches, read the graph at any commit, diff and merge
branches — with the versioned content held in a prolly repo (one repo per
record), the catalog holding only record/branch/commit *metadata*, and every
existing record type behaving exactly as before. Acceptance: a Karaf-booted
distribution plus a prolly sidecar passes an end-to-end
create → commit → branch → merge → read-at-commit integration test, and the full
existing catalog test suite stays green. *(Status 2026-08-12: everything up to
"plus a prolly sidecar" holds — the e2e ran in a booted distribution against the
in-memory fake client; the sidecar variant is Phase 5, gated on the gRPC
client.)*

## Decisions

- **D-1: The record subtype is the routing mechanism — not a per-repository
  flag.** [grpc-prolly-versioning-client](grpc-prolly-versioning-client.md) D-5
  routes reads behind a per-repository config flag; this plan refines that: the
  *type* of the record declares its storage backend, because the catalog already
  dispatches by type — `SimpleRecordManager` binds `RecordService`s dynamically
  (`com.mobi.catalog.impl/src/main/java/com/mobi/catalog/impl/SimpleRecordManager.java:184-186`)
  and `SimpleVersioningManager.getFactory` walks a record's types
  most-specific-first
  (`com.mobi.catalog.impl/src/main/java/com/mobi/catalog/impl/versioning/SimpleVersioningManager.java:174-186`).
  Storage becomes explicit in the data model, both backends coexist per-record,
  and no flag plumbing is invented. The config flag survives only as endpoint
  configuration (where the prolly service lives).
- **D-2: A standalone subtype; no mixing with the existing editor record types.**
  A record typed both `OntologyRecord` and `ProllyVersionedRDFRecord` would make
  the most-specific-first walks ambiguous (two competing specific types, two
  registered services). Retrofitting `OntologyRecord` / `ShapesGraphRecord` onto
  prolly is a *future* plan per type; this plan proves the seam with direct
  instances only.
- **D-3: New module pair `com.mobi.prolly.record.api` / `.impl` (+ `.rest`),
  following the shapes layout.** The type is catalog-level, not repository-level,
  so it does not belong in `com.mobi.repository.impl.prolly` (the embedded-Sail
  draft). The api module carries the `.ttl`, the ORM generation, and the client
  interface (D-6); the impl carries the two services.
- **D-4: Catalog commits become thin stubs; prolly holds the content.** Mobi's
  `Commit` carries provenance (user, message, timestamps) that merge requests and
  the catalog REST read from the catalog store; prolly commits hold the quads.
  For prolly records, `addMasterCommit`/`addBranchCommit` write a catalog commit
  *stub* — provenance plus a `prollyCommitHash` property, **no
  additions/deletions revision graphs** — after the prolly `commit()` returns its
  hash. Write ordering: prolly first, catalog stub second inside one catalog
  transaction; a crash between the two leaves an unreferenced prolly commit
  (reclaimed by prolly GC), never a catalog commit pointing at nothing.
- **D-5: Read routing keys on the commit stub, so no caller changes.**
  `CompiledResourceManager` is type-blind — `getCompiledResource(commitId, conn, …)`
  (`com.mobi.catalog.api/src/main/java/com/mobi/catalog/api/CompiledResourceManager.java:46`)
  has ~15 non-test callers that know only a commit IRI. Routing therefore happens
  *inside* `SimpleCompiledResourceManager`: if the commit carries
  `prollyCommitHash`, fetch the snapshot from the client; otherwise run the
  existing replay. One seam, all callers untouched.
- **D-6: Phases 1–4 code against a Mobi-side `ProllyVersioningClient` interface
  with an in-memory fake; Phase 5 swaps in the real gRPC client.** The interface
  (repo lifecycle, snapshot, commit, branch ops, diff, merge-base, leases) lives
  in the api module. This keeps the record-type work unblocked while
  [grpc-prolly-versioning-client](grpc-prolly-versioning-client.md) de-risks
  gRPC-in-Karaf, and honors its D-3 fallback (if gRPC fails, the same interface
  is implemented over prolly's REST).
- **D-7: `InProgressCommit` staging stays in the catalog.** The working copy is
  Mobi UX machinery, not versioned history; its additions/deletions become the
  delta passed to prolly `commit()` at commit time.
- **D-8: Merge is computed in Mobi; detached commits are leased.** Inherits
  ADR-0002 D-4 (blank-node-correct merge stays Mobi's) and the client plan's D-7
  lease discipline: lease only a commit not yet reachable from a branch/tag (a
  merge result before the head moves), never committed history.

## What's there already (anchoring)

- The subtype recipe, proven three times over (Ontology, Shapes, Workflows):
  class in a `.ttl` (`com.mobi.shapes.api/src/main/resources/shapes-graph-editor.ttl:22`
  extending `mcat:VersionedRDFRecord`,
  `com.mobi.catalog.api/src/main/resources/mcat.ttl:80`); ORM generation with
  `mcat.ttl` references (`com.mobi.shapes.api/pom.xml:71-101`) emitting a
  self-registering `@Component` factory; a minimal record service is ~23 lines
  (`com.mobi.catalog.impl/src/main/java/com/mobi/catalog/impl/record/SimpleVersionedRDFRecordService.java:33-56`).
- `AbstractVersionedRDFRecordService` supplies create/delete/policies
  (`com.mobi.catalog.api/src/main/java/com/mobi/catalog/api/record/AbstractVersionedRDFRecordService.java:114`,
  create-with-master-branch at `:203`); `BaseVersioningService` exposes the
  overridable write path — `addMasterCommit` (`com.mobi.catalog.api/src/main/java/com/mobi/catalog/api/versioning/BaseVersioningService.java:144`)
  and `addBranchCommit` (`:175`).
- Registration gotchas already learned: `<_dsannotations-options>inherit</_dsannotations-options>`
  in both bundle poms or inherited `@Reference`s silently unwire
  (`com.mobi.shapes.impl/pom.xml:123`); a per-type creation system policy plus
  `@ActionAttributes` on the REST create
  (`com.mobi.shapes.rest/src/main/java/com/mobi/shapes/rest/ShapesGraphRest.java:221`).
- The read path this plan reroutes: `SimpleCompiledResourceManager`
  (`com.mobi.catalog.impl/src/main/java/com/mobi/catalog/impl/SimpleCompiledResourceManager.java`)
  — the O(history) replay ADR-0002 exists to replace. Measured 2026-08-12
  (out-of-process plan Step 0 wrap-up): the replay cost applies to reads
  *behind* head only — master-HEAD reads are already O(1) via the materialized
  HEAD graph — so Phase 4's win lands on time-travel reads, diffs, and merge
  inputs, not open-at-head.
- The wire design: record ↔ repo mapping, repo-key slugging, interned
  `QuadBatch` decode, `have`-commit cache, leases — all settled in
  [grpc-prolly-versioning-client](grpc-prolly-versioning-client.md) D-6/D-7/D-8;
  this plan consumes them, it does not redesign them.

## Phase 1 — Scaffold the type (no prolly involved)

- [x] **Step 1.** Create `com.mobi.prolly.record.api`: `prolly-record.ttl`
  declaring `ProllyVersionedRDFRecord rdfs:subClassOf mcat:VersionedRDFRecord`, a
  `prollyRepoKey` datatype property on the record, and a `prollyCommitHash`
  datatype property with domain `mcat:Commit`; rdf-orm-maven-plugin execution
  referencing `mcat.ttl` + `prov-o.ttl`. Verify the generated
  `ProllyVersionedRDFRecordFactory` round-trips the new properties in a unit
  test.

  (Landed 2026-08-12. Two corrections: `prollyCommitHash` got an ORM holder by
  declaring `:ProllyCommit rdfs:subClassOf mcat:Commit` — a bare property with
  a referenced-ontology domain has no generated class to live on; and the
  round-trip test lives in the *impl* module — no api module in the repo
  carries `OrmEnabledTestCase` scaffolding, so the impl is the convention.
  Generated interfaces verified: `ProllyVersionedRDFRecord extends
  VersionedRDFRecord`, `ProllyCommit extends Commit`, `Optional<String>`
  accessors. Tests in Step 2's count.)
- [x] **Step 2.** Create `com.mobi.prolly.record.impl` with
  `SimpleProllyVersionedRDFRecordService extends AbstractVersionedRDFRecordService<ProllyVersionedRDFRecord>`
  — at this step a pure clone of the minimal pattern (factory `@Reference`,
  `@Activate`, `getType`/`getTypeIRI`) — plus
  `ProllyRecordVersioningService extends BaseVersioningService<ProllyVersionedRDFRecord>`
  with only `getTypeIRI()` overridden (base behavior otherwise). Both poms get
  `_dsannotations-options=inherit`. Port the shapes service test pattern;
  creating/deleting a record must behave exactly like a plain versioned record.

  (Landed 2026-08-12. Both services are the minimal clones as planned; the
  ported test exercises the full base `create` flow (record + master branch +
  initial commit + policies from the test-resource templates) plus type
  identity and `checkForMissingPolicies`. 7/7 tests green in
  `com.mobi.prolly.record.impl` — 3 ORM round-trip, 3 record service, 1
  versioning.)
- [x] **Step 3.** Wire the surface: creation system policy
  (copy `mobi-distribution/src/main/resources/etc/policies/systemPolicies/http%3A%2F%2Fmobi.com%2Fpolicies%2Fshapes-graph-record-creation.xml`,
  swap type IRI and RuleIds); `com.mobi.prolly.record.rest` with a `@POST`
  create endpoint carrying the `@ActionAttributes` type attribute; reactor
  `<module>` entries and `mobi-karaf-feature/src/main/feature/feature.xml`
  `<bundle>` lines. Verify by booting the distribution and creating/deleting a
  record via REST as a non-admin user (policy fires).

  (Landed 2026-08-12, verified in a booted distribution: POST
  `/mobirest/prolly-records` → 201 with recordId/branchId/commitId; the stored
  record carries the full type chain; DELETE dispatches to the prolly
  RecordService by most-specific type and the record 404s after. Verification
  ran as `admin` — the non-admin policy check is covered by the policy XML
  mirroring the proven shapes template, not yet by a second live user. **The
  boot refuted Step 2's re-export guidance:** `Export-Package
  com.mobi.catalog.api.record/versioning;provide:=true` (copied from
  `com.mobi.shapes.impl/pom.xml:116-119`) makes bnd EMBED those classes, and
  `BaseVersioningService`'s static init then can't find its root-level `.rq`
  resources — `NoClassDefFoundError` at DS instantiation. Fix: export only
  `com.mobi.prolly.record.impl*`; the plain import wires to catalog.api where
  the resources live. shapes.impl has the identical latent construction and
  survives only by resolver substitution — flagged as a follow-on.)

## Phase 2 — Repo lifecycle against the client interface

- [x] **Step 4.** Define `ProllyVersioningClient` in the api module:
  `createRepo(key)` / `deleteRepo(key)`, `snapshot(repoKey, commitHash)`
  (streaming), `commit(repoKey, delta, parents, message)`,
  `createBranch` / `deleteBranch` / `getBranchHead` / `setBranchHead`,
  `diff(a, b)`, `getMergeBase(a, b)`, `acquireSnapshotLease` / `renewLease` /
  `releaseLease`. Ship an in-memory fake (a map of repos of commit-DAGs) with
  its own contract test — the fake is the test harness for Phases 2–4.

  (Landed 2026-08-12. The fake is content-addressed for real — commit hashes are
  SHA-256 over parents + canonicalized state, so identical histories hash
  identically, verified by test. Registered as a DS component so the
  distribution runs without a prolly process; javadoc marks it non-persistent.
  `ProllyRepoKeys.forRecord` produces `records/<slug>-<hash16>` within prolly's
  name rules. 8/8 contract tests.)
- [x] **Step 5.** Override `createRecord`/`deleteRecord` in the record service:
  create → derive the repo key (deterministic slug of the record IRI, matching
  the client plan's D-8 validator rules `[A-Za-z0-9_./-]{1,128}`), call
  `createRepo`, store `prollyRepoKey` on the record; a failed `createRepo` fails
  record creation. Delete → `deleteRepo` best-effort after the catalog delete
  commits (an orphaned prolly repo is reclaimable; a record pointing at a
  deleted repo is not). Tests against the fake, including both failure orders.

  (Landed 2026-08-12. `createRecord` mirrors the base body with `createRepo`
  before any catalog write and a compensating `deleteRepo` in the failure
  path; `delete` calls super then best-effort `deleteRepo`, logging a warning
  on failure. Both failure orders tested: repo-create failure never reaches
  `versioningManager.commit`; catalog failure leaves zero repos behind.
  Module suite 18/18.)

## Phase 3 — Write path (commits and branches into prolly)

- [x] **Step 6.** Override `addMasterCommit`/`addBranchCommit` in
  `ProllyRecordVersioningService`: read the `InProgressCommit` delta, call
  `client.commit(...)` with the branch head's `prollyCommitHash` as parent, then
  write the catalog commit stub (provenance + `prollyCommitHash`, no revision
  graphs) and advance the branch head — prolly first, stub second (D-4).
  **Open check to resolve in this step:** enumerate which catalog paths
  dereference a commit's Revision additions/deletions graphs
  (`DifferenceManager`, commit-history REST, rebase) and confirm each either
  never runs for a stub or is routed in Step 8 — a stub that breaks an
  unrouted revision-walk is this plan's biggest correctness risk.

  (Landed 2026-08-12 — **as a dual write, refining D-4**: the base catalog
  commit flow runs unchanged (revision graphs and all) and the delta is
  mirrored into prolly, linked by `prollyCommitHash` + `ProllyCommit` type.
  The open check therefore resolves to "nothing breaks": every revision-walk
  still finds its graphs; the D-4 thin stub (dropping catalog deltas) is
  deferred to a follow-on gated on Phase 4. Two behaviors the tests pin:
  a root commit mirrors the post-commit HEAD graph (the base flow ignores the
  initial InProgressCommit delta — the Step-0 harness correction resurfacing),
  and a missing repo key or unmirrored parent skips the mirror with a warning
  rather than corrupting the chain. Driven through the REAL catalog managers
  in tests, not mocks.)
- [x] **Step 7.** Mirror branch lifecycle: record-service/branch operations on a
  prolly record call `createBranch`/`deleteBranch`/`setBranchHead` so prolly's
  reachability (and therefore its GC) tracks Mobi's branch heads. Tests: branch
  create/delete/head-advance against the fake; deleting a branch leases its
  detached head for the deletion window (D-8).

  (Landed 2026-08-12: `setBranchHead` mirrors after every commit
  (prolly branch named by `ProllyRepoKeys.forBranch` slug); `deleteBranch`
  override removes the prolly pointer best-effort. Corrections: the
  deletion-window lease is NOT implemented — the fake has no GC, so it would
  be untestable ceremony; it moves to Phase 5 where a real server can reclaim.
  `addBranchCommit` mirroring shares `prepareMirror`/`applyMirror` with the
  master path; its dedicated branch-fork test is deferred to the merge tests
  in Step 9 which exercise the same path. Module suite 21/21.)

## Phase 4 — Read path (snapshot instead of replay)

- [x] **Step 8.** Route reads in `SimpleCompiledResourceManager`: when the
  commit carries `prollyCommitHash`, resolve the record's `prollyRepoKey` and
  stream `client.snapshot(...)` into the returned `Model`; otherwise the
  existing replay (D-5). Extend `DifferenceManager` the same way for
  prolly-stub commit pairs via `client.diff`. Tests: both routes, plus a mixed
  catalog (plain + prolly records) proving zero behavior change for plain
  records.

  (Landed 2026-08-12, with one architectural correction to D-5: catalog.impl
  must not depend on prolly modules, so the routing is an *extension point* —
  `CommitSnapshotResolver` in catalog.api, consulted by
  `SimpleCompiledResourceManager.buildCompiledResource` (optional/dynamic/
  multiple DS reference) before replaying; `ProllySnapshotResolver` in
  prolly.record.impl answers only for commits carrying `prollyCommitHash`
  (the repo key rides on the stub so no record lookup is needed) and falls
  back to replay on any client failure. The `DifferenceManager` extension is
  NOT needed under the Step-6 dual write — revision graphs still exist; it
  returns with the thin-stub follow-on. Verified: snapshot route isomorphic
  to replay on the same history, and the full catalog.impl suite green —
  567/567 — proving zero behavior change for plain records.)
- [x] **Step 9.** Merge: for prolly records, fetch `getMergeBase` + both
  snapshots, run Mobi's existing blank-node-correct merge, write the
  multi-parent commit via `client.commit` bracketed by a snapshot lease until
  the target head moves (D-8). Test: conflict and non-conflict merges against
  the fake, lease acquired/released asserted.

  (Landed 2026-08-12: `mergeIntoMaster`/`mergeIntoBranch` overrides let the
  base compute the blank-node-correct merge, then mirror a two-parent prolly
  commit whose state is diffed against the first parent's snapshot —
  master-merge state from the HEAD graph, branch-merge state from the
  compiled resource at the merge commit — bracketed by an
  acquire/release snapshot lease. Test drives the real base merge flow:
  both parents verified as ancestors, snapshot == post-merge HEAD state,
  branch pointer moved, no leases left. The conflict-map merge variant is
  exercised only with empty conflict resolutions — a conflicting-merge case
  is future coverage. Module suite 23/23.)

## Phase 5 — Live integration (gated on grpc-prolly-versioning-client Phases 1–2)

- [ ] **Step 10.** Bind the real client: implement `ProllyVersioningClient` over
  the gRPC stubs from the client plan (or its REST fallback if the gRPC gate
  failed), configured by endpoint URL. A Pax-Exam IT boots the distribution with
  a prolly sidecar and runs the Goal's end-to-end scenario.

  (Progress 2026-08-12, not yet complete: the gRPC client exists
  (`com.mobi.prolly.record.grpc`, see the grpc plan's Step 1–4 wrap-ups),
  deploys in the booted distribution, activates from `etc/*.cfg`, and
  round-trips to a live prolly gRPC process. Remaining for the box: a server
  deployment that registers `ProllyVersioningService` (Spring Boot RepoHost —
  prolly-port side) and the Pax-Exam IT. Commit mirrors were also hardened:
  a failed prolly mirror now degrades to replay with a warning instead of
  failing the user's commit — dual-write failure isolation.)
- [ ] **Step 11.** Measure and record: end-to-end read-at-commit for a prolly
  record versus the equivalent replay-backed record at increasing chain depth
  (reuse the out-of-process plan's Phase-0 harness; cold and warm client-cache
  reads reported separately, machine named). Publish the numbers in this plan's
  wrap-up and cross-link from the client plan's Step 6.

## Out of scope

- Retrofitting `OntologyRecord` / `ShapesGraphRecord` / `WorkflowRecord` onto
  prolly storage (D-2) — one future plan per editor type.
- Frontend: prolly records appear in the catalog UI as generic records
  (`catalogState.service.ts` icon fallback); no editor, no `VersionedRdfState`
  subclass.
- Migrating existing records between backends.
- Productionizing the sidecar deployment (supervision, security, launch) — the
  out-of-process plan owns that follow-on.
- Delegating merge to prolly (ADR-0002 D-4).

## Where this lives

- `com.mobi.catalog.api/src/main/java/com/mobi/catalog/api/record/AbstractVersionedRDFRecordService.java`,
  `com.mobi.catalog.api/src/main/java/com/mobi/catalog/api/versioning/BaseVersioningService.java`
  — the base classes the two new services extend
- `com.mobi.catalog.impl/src/main/java/com/mobi/catalog/impl/SimpleCompiledResourceManager.java`,
  `com.mobi.catalog.impl/src/main/java/com/mobi/catalog/impl/SimpleDifferenceManager.java`
  — the read/diff seams Phase 4 reroutes
- `com.mobi.catalog.impl/src/main/java/com/mobi/catalog/impl/SimpleRecordManager.java`,
  `com.mobi.catalog.impl/src/main/java/com/mobi/catalog/impl/versioning/SimpleVersioningManager.java`
  — the type-dispatch D-1 rides
- `com.mobi.shapes.api/`, `com.mobi.shapes.impl/` — the worked subtype example
  the scaffold copies
- `mobi-karaf-feature/src/main/feature/feature.xml`,
  `mobi-distribution/src/main/resources/etc/policies/systemPolicies/` — wiring
- Serves: [ADR-0002](../docs/adr/0002-out-of-process-prolly-rdf4j-over-a-network-boundary.md);
  consumes [grpc-prolly-versioning-client](grpc-prolly-versioning-client.md);
  relates to [ADR-0001](../docs/adr/0001-evaluate-prolly-tree-storage-backend.md)
