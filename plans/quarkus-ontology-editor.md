---
status: proposed
priority: P3
category: ontology
epic: ontology-editor-ng
---

# Quarkus ontology editor — GitHub-style repos over an embedded prolly engine

A greenfield, single-purpose ontology editor: one Quarkus (Java 25) service
embedding prolly-rdf4j as a library, presenting each ontology as a GitHub-style
repo page — browse, history, branches, and **merge requests on the same page** —
with `owl:imports` resolution pushed **below the SPARQL layer** as a composite
sail over pinned, content-addressed member snapshots. This is the
[ADR-0005](../docs/adr/0005-defer-karaf-vs-quarkus-to-jakarta-gate.md) pattern
in action: new capability built out-of-process on Quarkus, deciding nothing
about the Mobi monolith. Proposed; nothing built.

## Goal

A user can create an ontology repo, edit entities on a branch, open a merge
request from the repo page, review an axiom-level diff, merge it, and query or
time-travel any commit — with the full imports closure transparently visible to
every SPARQL query and permalinked by content hash — from a single deployed
Quarkus process backed by RocksDB. Acceptance: the end-to-end
create → branch → edit → MR → merge → time-travel-query flow works in one
booted service, closure queries need no application-level materialization, and
re-opening any historical commit reproduces its exact closure (pinned imports).

## Decisions

- **D-1: Imports move below the SPARQL layer — a composite sail over pinned
  snapshots.** Today Mobi resolves the closure *above* the query layer,
  copying every member into cache-repo graphs keyed `recordId&commitId`
  (`com.mobi.ontology.utils/src/main/java/com/mobi/ontology/utils/cache/repository/OntologyDatasets.java:88`)
  with unpinned resolution (`ImportsResolver` takes bare IRIs — no commit
  argument). Here, a `ClosureSail` (an RDF4J `Sail`) composes the union of N
  immutable prolly snapshots — the ontology at its commit plus each pinned
  import at its hash — as one `SailConnection`. Because all members live in the
  same embedded NodeStore, the union is an iterator-merge over co-located
  sorted indexes: zero copying, no cache repo, dataset semantics done at the
  storage layer where they belong. The closure's identity is the sorted set of
  member hashes — itself hashable, so composite results are cacheable and two
  ontologies sharing a closure share the work. **Explicitly rejected:**
  cross-process federated SPARQL (`SERVICE`/FedX-style) — joins over the wire
  are the classic performance trap; the union must stay co-located.
- **D-2: The prolly engine is embedded, not a sidecar.** ADR-0002 chose
  out-of-process *because of Mobi's constraints* — Java 17, RDF4J 4.3.15, OSGi
  packaging. A greenfield Quarkus service has none of them: Java 25 matches
  prolly's build, RDF4J 5 is just a dependency, plain jars need no bundling. So
  the original embedded path (ADR-0001's `ProllySail`) is finally available:
  in-process reads (~µs, no wire, no decode), and D-1's `ClosureSail` becomes
  an ordinary library concern instead of a server-side feature request.
  prolly-server remains relevant as an optional *sync peer* (prolly ships a
  sync protocol + gRPC surface), not as the storage tier.
- **D-3: GitHub's resource model, literally.** Repo = ontology (one prolly repo);
  branch/tag/commit as in prolly; the repo page is tabbed:
  **Ontology** (entity tree + axioms at any ref), **Changes** (the working
  copy — prolly's staging), **History** (commit log), **Branches**,
  **Merge Requests** (list + detail *on the same page*), **Settings** (import
  pins, visibility). Permalinks are content hashes
  (`/repos/{repo}/at/{commitHash}/...`) — immutable URLs, so HTTP caching is
  trivial and exact (`ETag` = the hash). Per-axiom "blame" (who introduced
  this axiom, in which commit) rides prolly's per-triple provenance index —
  opt-in and marked *implementing* upstream (`prolly-rdf4j/FEATURES.md`), so
  blame ships behind its capability flag, never assumed.
- **D-4: Merge requests reuse prolly's MR storage, surfaced in the repo page.**
  prolly already ships an MR storage backend
  (`prolly.rdf4j.mrs.backend` — RocksDB column-family or
  commits-on-hidden-branches, `prolly-port` configuration reference). The
  editor adds what storage can't: the *ontology-aware* review surface — an
  axiom-level diff (grouped by entity, rendered as OWL changes rather than raw
  triples), inline discussion threads, and merge gating.
- **D-5: Merge is set-union by engine, blank-node-correct by service.** The
  engine's merge is honest about itself: `ServerInfo.set_union_merge_only =
  true` (verified live 2026-08-12) — RDF quad-sets cannot value-conflict, but
  the engine does **not** canonicalize blank nodes. The editor therefore keeps
  a merge service for blank-node-heavy structures (OWL axioms are bnode-heavy:
  restrictions, lists), computing the canonical merge and writing a two-parent
  commit — the same division of labor Mobi's catalog keeps (ADR-0002 D-4),
  relocated into the new service.
- **D-6: Web imports become library repos — fetched once, versioned forever.**
  An `owl:imports` of a web IRI is resolved by ingesting it into an
  auto-managed **library repo** (initial commit = the fetch; re-fetch = a new
  commit, so upstream drift is visible history and diffable). The importing
  ontology's commit records its pins (member repo + hash) in a reserved
  metadata graph. Un-referenced library content is reclaimed by the engine's
  reachability GC once no pin reaches it — no refcounting code.
- **D-7: Scope is the editor, and the editor only.** No catalog-of-everything,
  no datasets, no workflows, no Mobi UI parity. Quarkus stack: RESTEasy
  Reactive + OIDC for auth (not prolly-server's PAT stack), a SPA served via
  Quinoa, SSE for live repo-page updates. Relationship to Mobi: a parallel
  evaluation-track application; if it earns adoption, the Karaf-vs-Quarkus
  comparison ADR-0005 defers gains a working exhibit — but nothing here
  migrates Mobi.

## What's there already (anchoring)

- The engine and its verbs, verified live this branch: repos, branches,
  commits, snapshots (~1 ms flat at any depth, out-of-process plan Step 0
  addendum), diff, merge-base, staging (`/sparql/staging`), MR storage
  backend, GC + snapshot leases, sync — `prolly-port` repo, exercised
  end-to-end 2026-08-12 (`plans/prolly-versioned-rdf-record.md` Step 10
  wrap-up).
- The embedded usage pattern: `ProllySail` + `openSnapshotAt` + `MergeEngine`
  (`prolly-port/prolly-rdf4j/src/main/java/com/earasoft/prolly/rdf4j/examples/VersioningDemo.java`).
- What this design escapes, measured: Mobi's above-the-query-layer closure
  machinery — per-record materialized cache datasets, unpinned import
  resolution, `HttpURLConnection` web fetches with a `lastAccessed` timestamp
  (`com.mobi.ontology.utils/src/main/java/com/mobi/ontology/utils/imports/impl/ImportsResolverImpl.java:145`,
  `OntologyDatasets.java:34-37`).
- The decision framework this executes:
  [ADR-0005](../docs/adr/0005-defer-karaf-vs-quarkus-to-jakarta-gate.md) (new
  services on Quarkus, monolith decision deferred),
  [ADR-0002](../docs/adr/0002-out-of-process-prolly-rdf4j-over-a-network-boundary.md)
  (whose three embedded-path blockers are all Mobi-specific and absent here).

## Repo page (the one screen that matters)

```
┌──────────────────────────────────────────────────────────────────────┐
│ acme/clinical-ontology                    ⑂ branch: feature/labs ▾   │
│ [Ontology] [Changes·3] [History] [Branches·4] [Merge Requests·2] [⚙] │
├──────────────────────────────────────────────────────────────────────┤
│ MR !12  Add specimen hierarchy        feature/labs → main   ✓ no-conf│
│  ├─ Diff (by entity): +Specimen ⊑ Material, +hasSpecimen dom/range…  │
│  ├─ Discussion (3)                                                   │
│  └─ [Merge]  base 2218a09…  →  merge commit (two-parent)             │
│ MR !11  Pin skos to 2026-08 fetch     imports/skos → main   ● open   │
└──────────────────────────────────────────────────────────────────────┘
    permalink: /repos/acme/clinical-ontology/at/63bbfbb9…/class/Specimen
```

## The versioned editor surface (what the Ontology tab contains)

Baseline to beat: Mobi's editor ships Project, Overview, Classes, Properties,
Individuals, Schemes/Concepts (vocabulary-conditional), Search, and
Visualization tabs
(`com.mobi.web/src/main/resources/public/app/ontology-editor/components/ontologyTab/ontologyTab.component.html:27-57`),
with axiom/characteristics/annotation blocks and per-entity-kind create
overlays. The redesign keeps that proven information architecture and makes
**every view a pure function of a ref** (branch, tag, or commit hash) — the
version axis is not a separate tab, it is a dimension of all of them.

| Workspace | What it shows | What versioning adds |
|---|---|---|
| **Overview** | Ontology header (IRI, versionIRI, annotations, license), entity counts, direct imports | Metrics *deltas* between any two refs ("this release: +40 classes, −2 properties"); header history |
| **Classes** | `subClassOf` tree; class page: equivalent/disjoint axioms, restrictions, annotations, instances, usages | Per-class history, per-axiom blame, class-scoped diff vs any ref, hash permalink |
| **Object Properties** | `subPropertyOf` tree; domain/range, characteristics (Functional, InverseFunctional, Transitive, Symmetric, Asymmetric, (Ir)Reflexive), inverses, chains | Same per-entity mechanics; characteristic changes are high-risk edits — blame matters most here |
| **Data Properties** | Hierarchy, domain, range datatypes, Functional | Same |
| **Annotation Properties** | List + usage counts across the closure | Same |
| **Individuals** | Browse by class; type/property assertions, same/differentAs | Same |
| **Datatypes** | Custom datatype definitions | Same |
| **Concepts / Schemes** *(vocabulary mode, shown when SKOS content exists — Mobi's `isVocab` precedent)* | broader/narrower tree, scheme membership | Same |
| **Imports** | The **pinned closure** as a tree: direct + transitive members, each with repo + hash + drift status against its latest library fetch | Update-a-pin is an ordinary change → reviewed as an MR; closure reproducible at every historical commit (D-6) |
| **Search** | Entity finder + text search across the whole closure | Search runs *at the ref* — finding what an entity looked like two releases ago is the same search |
| **Query** | SPARQL editor over the closure sail (D-1); saved queries | Saved queries live in a reserved graph — **versioned with the ontology**, branchable, reviewable in MRs; results are cacheable by (query, closure-hash) |
| **Visualization** | Neighborhood graph view | At-ref rendering; diff overlay (added/removed edges between refs) — late phase |

**Every entity page has the same anatomy**, regardless of kind: header (IRI,
labels, copy-permalink-at-hash) → kind-specific axiom blocks → annotations →
usages across the closure (cheap under D-1: one union-sail lookup) → a
**history strip** of commits touching this entity (the engine's log filtered by
subject — Mobi's `CommitManager.getCommitEntityChain` is the precedent that
this is the right granularity) → a blame toggle per axiom (provenance
capability flag, D-3) → a compare-to-ref selector. Every edit affordance
(create overlays, restriction builder, characteristic toggles) writes a staged
delta grouped by entity — the Changes tab and MR review render the *same*
entity-grouped axiom diff, so "what am I about to commit" and "what am I being
asked to review" are one component.

## Phase 1 — Engine spike (prove D-1/D-2 in a week, not a quarter)

- [ ] **Step 1.** A Quarkus app embedding prolly-rdf4j: create repo, commit,
  snapshot, branch — the `VersioningDemo` flow behind REST, with tests.
- [ ] **Step 2.** `ClosureSail`: union `SailConnection` over N
  `openSnapshotAt` members; SPARQL over a 3-member closure returns the union;
  benchmark vs single-snapshot queries (name the machine, report ratios).

## Phase 2 — Repos, editing, pins

- [ ] **Step 3.** Repo/branch/commit REST + hash permalinks (`ETag`); entity
  tree + axiom views at any ref via the closure sail.
- [ ] **Step 4.** Working copy on prolly staging; entity edits → deltas →
  commits with author/message (signed commits optional, engine supports them).
- [ ] **Step 5.** Import pinning: reserved metadata graph, library-repo
  ingestion for web imports (D-6), closure resolution = read pins, never fetch
  at query time; re-fetch endpoint creates a new library commit + diff view.

## Phase 3 — The entity workspaces

- [ ] **Step 6.** Classes + object/data property workspaces: hierarchy trees,
  entity pages with kind-specific axiom blocks, closure-wide usages; edits as
  staged entity-grouped deltas.
- [ ] **Step 7.** The long tail of kinds: annotation properties, individuals,
  datatypes; vocabulary mode (concepts/schemes) shown when SKOS content
  exists.
- [ ] **Step 8.** The version dimension on every page: entity history strip
  (log filtered by subject), compare-to-ref entity diff, per-axiom blame
  behind the provenance capability flag.
- [ ] **Step 9.** Search at ref across the closure; SPARQL Query tab over the
  closure sail, saved queries in a reserved (versioned) graph.

## Phase 4 — Merge requests on the repo page

- [ ] **Step 10.** MR CRUD over prolly's MR backend; repo-page tab with list +
  detail; axiom-level diff rendering grouped by entity (the same component as
  the Changes tab).
- [ ] **Step 11.** Merge: blank-node-correct merge service (D-5) writing the
  two-parent commit; conflict surface for the structural cases the engine
  reports; discussion threads.

## Phase 5 — Product hardening

- [ ] **Step 12.** OIDC auth + per-repo roles; SSE live updates; visualization
  view with diff overlay; packaging (fast-jar + container) and an operator
  runbook.

## Out of scope

- Migrating Mobi or reusing its UI — this is a parallel evaluation app (D-7).
- Cross-process federated SPARQL (rejected in D-1).
- Delegating blank-node-correct merge to the engine (D-5; revisit if upstream
  ships canonicalizing merge).
- Reasoning/inference services beyond structural OWL views.
- Multi-node HA — single process + RocksDB; the sync protocol is the future
  scale-out story, not this plan's.

## Where this lives

- Greenfield — no Mobi module is touched. The repository exists as of
  2026-08-13: `~/git/quarkus-ontology-editor` (README, a design doc, founding
  ADRs 0001/0002), deliberately self-contained — its docs carry the concepts
  from this plan but reference no Mobi code. The design document in that repo
  is now the canonical copy of this design; this plan remains the Mobi-side
  record of why it exists and what it borrows.
- What it learns from: `com.mobi.ontology.utils/src/main/java/com/mobi/ontology/utils/cache/repository/OntologyDatasets.java`,
  `com.mobi.ontology.utils/src/main/java/com/mobi/ontology/utils/imports/impl/ImportsResolverImpl.java`
  (the closure machinery D-1 replaces), and this branch's prolly integration
  (`plans/prolly-versioned-rdf-record.md`).
- Serves: [ADR-0005](../docs/adr/0005-defer-karaf-vs-quarkus-to-jakarta-gate.md);
  builds on [ADR-0001](../docs/adr/0001-evaluate-prolly-tree-storage-backend.md) /
  [ADR-0002](../docs/adr/0002-out-of-process-prolly-rdf4j-over-a-network-boundary.md).
