---
status: proposed
priority: P3
category: operations
epic: storage-backend
---

# Production notes — running Mobi + prolly-rdf4j as two processes

The operational half of the out-of-process prolly integration: what production
has to solve when Mobi (Karaf, the catalog registry) and prolly-rdf4j (its own
JVM, RocksDB, gRPC/REST) run as two cooperating processes. Both prolly plans
name "productionising the two-process deployment" as a separate future plan —
this is that plan. **Proposed and deliberately dormant: do not start until the
[out-of-process evaluation](out-of-process-prolly-service.md) Phases 1–2
succeed** (the ADR-0002 gate). Until then this file is the parking lot for
production decisions so they are designed once, not discovered in an outage.

## Goal

An operator can install, start, supervise, secure, back up, and upgrade a
Mobi + prolly deployment from documented artifacts alone: one supervised prolly
process beside the Karaf distribution, reached only by Mobi's service identity,
with a tested backup/restore runbook covering both stores and a stated
compatible-upgrade order — and a prolly outage degrades only prolly-backed
records, verifiably leaving classic records untouched.

## Decisions

- **D-1: Default topology is a same-host sidecar over loopback.** The hop is
  tens of microseconds; the measured advantage on time-travel reads (~1 ms
  snapshot vs multi-second replay, out-of-process plan Step 0 wrap-up) absorbs
  even millisecond-order wire overhead. Remote placement stays possible — the
  wire contract doesn't care — but starts unjustified: it adds real latency and
  a distributed-systems operations bill where a sidecar suffices.
- **D-2: Prolly runs on its own JDK, never Karaf's.** The sidecar needs Java 25
  and `--enable-native-access=ALL-UNNAMED` (RocksDB JNI); Mobi's JDK choice
  stays governed by [ADR-0004](../docs/adr/0004-karaf-jakarta-coupling-for-java-25.md).
  The two processes must never be forced onto one runtime — that would
  reintroduce the coupling the topology exists to remove.
- **D-3: Single permission authority — Mobi's.** Prolly is reached only by
  Mobi's *service* identity; all user-level enforcement stays at Mobi's REST
  layer (inherits [grpc-prolly-versioning-client](grpc-prolly-versioning-client.md)
  D-8). Concretely: bind prolly to loopback by default; if it must leave the
  host, mTLS or a shared token between the two processes — never prolly's
  per-repo roles, which would create a second authority to keep in sync.
- **D-4: Version-match by pinned contract, upgrade server-first.** Mobi vendors
  `versioning.proto` pinned (grpc plan D-2). Upgrades roll the prolly server
  first (new server must serve the old contract), then Mobi. A contract-version
  handshake at channel-open turns a mismatch into a clear startup error instead
  of a runtime decode failure.
- **D-5: Failure isolation is the contract — and must be tested, not assumed.**
  Prolly down ⇒ prolly-backed records return a clear unavailable error; classic
  records, login, and the catalog UI are unaffected (the registry never leaves
  Mobi). No startup ordering requirement: Mobi connects lazily and health-checks
  the channel. The crash-consistency window is covered by write ordering
  ([prolly-versioned-rdf-record](prolly-versioned-rdf-record.md) D-4): prolly
  commit first, catalog stub second — a crash strands an orphaned prolly commit
  (reclaimed by GC), never a stub pointing at nothing.
- **D-6: Backup is two-store coordination, catalog first.** A consistent backup
  captures Mobi's repositories *and* prolly's RocksDB directory. Reasoning,
  not yet tested: back up the **catalog first, prolly second** — stubs only
  reference already-committed prolly hashes, so a prolly image at-or-after the
  catalog image satisfies every stub (extra unreferenced commits are harmless,
  GC-reclaimable); the reverse order can leave stubs dangling. The existing
  backup/restore CLI (`com.mobi.utils.cli`) is the integration point.

## What's there already (anchoring)

- The topology decision and its cost list (two processes to deploy, supervise,
  secure, version-match): [ADR-0002](../docs/adr/0002-out-of-process-prolly-rdf4j-over-a-network-boundary.md)
  Consequences.
- The measured justification: out-of-process plan Step 0 wrap-up + addendum
  (replay seconds-to-minutes behind head; prolly snapshot ≈ 1 ms flat, embedded
  bound).
- Mobi's operational surface today: the Karaf assembly
  (`mobi-distribution/src/main/resources/etc/`), the backup/restore CLI
  (`com.mobi.utils.cli`) — note it is broken on JDK 24+ (ADR-0004 validation
  caveat), so the JDK-≤21 admin-CLI constraint applies to backup runbooks too.
- The prolly server artifact: `prolly-rdf4j-rest` / the gRPC service (prolly-port
  repository); boot flags per its docs (Java 25,
  `--enable-native-access=ALL-UNNAMED`).

## Phase 1 — Packaging & supervision (open decision: who owns the process)

- [ ] **Step 1.** Decide the supervisor: (a) the Mobi distribution launches the
  sidecar (extend `bin/start`), (b) systemd units, or (c) a container pair
  (compose file). Record the choice + rationale here; ship the chosen artifact
  with restart-on-crash and a documented health check.
- [ ] **Step 2.** Add the prolly endpoint + credentials to Mobi's configuration
  surface (an `etc/*.cfg` for the client), defaulting to loopback.

## Phase 2 — Security

- [ ] **Step 3.** Loopback bind by default; implement and document the
  off-host option (mTLS or token) and verify prolly rejects unauthenticated
  calls in that mode (D-3).

## Phase 3 — Backup / restore

- [ ] **Step 4.** Extend the backup runbook (and `com.mobi.utils.cli` if the
  evaluation graduates) to capture both stores in D-6's order; test a restore
  that includes a stub whose prolly commit arrived between the two captures
  (must be satisfied) and document the JDK-≤21 CLI constraint.

## Phase 4 — Failure drills & upgrade

- [ ] **Step 5.** Kill-the-sidecar drill in an integration test: prolly-backed
  record returns a clear error, classic record flows stay green, recovery needs
  no Mobi restart (D-5).
- [ ] **Step 6.** Contract-version handshake at channel-open + a documented
  server-first upgrade runbook (D-4).

## Out of scope

- Whether the prolly integration ships at all — owned by the
  [out-of-process evaluation](out-of-process-prolly-service.md) and its gates.
- Multi-host / clustered prolly, replication, HA — nothing in the record
  justifies it; revisit only with a measured need.
- Delegating any permission model to prolly (D-3 forbids it).

## Where this lives

- `mobi-distribution/src/main/resources/etc/` — the configuration surface
  Steps 1–2 extend
- `com.mobi.utils.cli` — the backup/restore CLI Step 4 touches
- Serves: [ADR-0002](../docs/adr/0002-out-of-process-prolly-rdf4j-over-a-network-boundary.md);
  parked by [out-of-process-prolly-service](out-of-process-prolly-service.md) and
  [grpc-prolly-versioning-client](grpc-prolly-versioning-client.md) Out-of-scope;
  relates to [prolly-versioned-rdf-record](prolly-versioned-rdf-record.md)
