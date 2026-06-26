---
status: proposed
priority: P2
category: persistence
epic: rdf4j-upgrade
---

# RDF4J 4.3.15 → 5.x upgrade

Upgrade the triple-store layer from RDF4J `4.3.15` to `5.x` across the reactor. This is
the dedicated, phased plan [ADR-0003](../docs/adr/0003-rdf4j-4-to-5-upgrade.md) calls for
under its **Option C** (decouple the upgrade from the prolly decision and justify it on
its own modernization merits). RDF4J is woven through the platform — **~454 Java files
import `org.eclipse.rdf4j` across 76 of the 88 modules** (ADR-0003, 2026-06-04) — and
RDF4J types appear in Mobi's *own* public API, so this is a large, API-visible change,
not a contained bump. **Proposed, not started:** per ADR-0003 the gate to begin is a
concrete driver (a needed 5.x feature, a security advisory, or 4.x end-of-life) or the
*embedded*-prolly choice (ADR-0001) making it a hard prerequisite. **Decoupled from Java
25** — 4.3.15 builds and unit-tests clean on JDK 25 (java-25 findings.md F-1), so this is
not on the Java-25 critical path.

> **Status note (2026-06-26):** still `proposed` / gated on a driver — the upgrade is
> NOT started. But the **Phase-0 spike (Step 2) was completed** as de-risking
> groundwork (see its wrap-up below). Key result: the real 4→5 break surface is **far
> narrower than the 454-file import count** — it concentrates in a handful of
> rdf4j-*extension* classes (the `CloseableIteration` generics change;
> `ReadOnlyRepositoryConnection` breaks first) plus mechanical exception-handling
> cleanup, while **19 core modules compiled clean against rdf4j 5.1.4**. This lowers the
> estimated cost and should inform the go/no-go.

## Goal

`mobi-parent` pins `rdf4j.version = 5.x`, the full reactor compiles and the unit suite
passes, the OWL API ↔ RDF4J coupling is reconciled (no split RDF4J on the classpath or in
the OSGi wiring), the assembled distribution boots, and the persistence + ontology
integration suites are green — the same baseline that exists on 4.3.15 today, on 5.x.

## Decisions

- **D-1: Standalone modernization, not a prolly line item (ADR-0003 Option C).** Justify
  and schedule the upgrade on its own (security / EOL / a 5.x feature). Do not gate a
  454-file, 76-module change to the experimental storage swap — ADR-0002 (out-of-process
  prolly) shows the swap does not need it; only *embedded* prolly (ADR-0001) does.
- **D-2: OWL API reconciliation rides along, and is the trap.** OWL API **5.5.0** declares
  `owlapi.rdf4j.version = 4.2.0` (`mobi-parent/pom.xml:90`), an already-skewed transitive
  RDF4J. Moving Mobi to 5.x without reconciling OWL API yields **two RDF4J majors on the
  classpath**. Either bump OWL API to a release aligned with RDF4J 5, or override its
  transitive RDF4J — decided in Phase 2, not assumed here.
- **D-3: Phased, in ADR-0003's order:** property bump → fix compilation across the
  affected modules (the 4→5 API breaks, notably the **Sail SPI** change) → reconcile OWL
  API → adjust OSGi bundle versions/ranges → green the test + boot suites. Each phase
  ends green before the next starts.
- **D-4: This changes Mobi's public API.** `OsgiRepository extends
  org.eclipse.rdf4j.repository.Repository`
  (`com.mobi.persistence.api/.../repository/api/OsgiRepository.java:26`), so RDF4J 5 types
  surface in Mobi's API — downstream consumers see the change; call it out in the release
  notes, do not treat it as internal-only.

## What's there already (anchoring)

Snapshot from ADR-0003 (2026-06-04) + a 2026-06 re-grep; re-anchor before resuming.

- **The pin:** `mobi-parent/pom.xml:97` — `<rdf4j.version>4.3.15</rdf4j.version>`. Roughly
  a dozen RDF4J artifacts are pinned off it (`rdf4j-model`/`-model-api`,
  `-repository-api`/`-http`/`-sail`, `-sail-memory`, `-runtime-osgi`, … — `:107`+).
- **The OWL API coupling:** `owlapi.version 5.5.0` (`:89`) with
  `owlapi.rdf4j.version 4.2.0` (`:90`) — the skew D-2 must reconcile.
- **Blast radius — import count ≠ break surface (Step-2 spike):** ~454 files *import*
  `org.eclipse.rdf4j` across **76 / 88 modules** (ADR-0003; ~269 in `com.mobi.*` main
  sources, 2026-06). But the spike showed the *break* surface is much smaller: 19 core
  modules compiled clean against 5.1.4, and breaks concentrate in rdf4j-extension classes
  + exception handling (see Step 2). Re-measure both at the start.
- **API-visible:** `com.mobi.persistence.api/src/main/java/com/mobi/repository/api/OsgiRepository.java:26`
  — `OsgiRepository extends org.eclipse.rdf4j.repository.Repository` (D-4).
- **OSGi wiring:** the `rdf4j-deps` feature
  (`mobi-karaf-feature/src/main/feature/feature.xml:165`) and
  `rdf4j-runtime-osgi/${rdf4j.version}` (`:182`) — bundle versions + Import-Package ranges
  to bump in Phase 3.
- **Decision record:** [ADR-0003](../docs/adr/0003-rdf4j-4-to-5-upgrade.md) (Proposed) —
  this plan is its "if pursued, a dedicated phased plan" follow-on.

## Phase 0 — Gate & spike

- [ ] **Step 1.** Record the **driver** that moves ADR-0003 to *Accepted* (the 5.x
  feature / security advisory / EOL, or the embedded-prolly choice) in this plan's lead,
  and re-measure the blast radius (`grep -rl org.eclipse.rdf4j` across modules). **Gate:**
  a named driver + a current file/module count.
- [x] **Step 2.** Spike the 4→5 breaking surface on a throwaway branch: enumerate the
  removed/renamed APIs Mobi actually uses (especially the **Sail SPI** change called out
  in the embedded-prolly draft) and the OWL API options for D-2. **Gate:** a written list
  of break categories with a rough per-category fix count.

  *(Done 2026-06-26 — spike only, no upgrade committed; the version flip was reverted, the
  branch is clean. Done ahead of the gate as de-risking groundwork; the upgrade proper
  remains gated on Step 1's driver.)*

  > **Spike findings — the headline 5.0 break, and the blast radius is far smaller than
  > the import count suggests.**
  >
  > **The dominant 4→5 break (verified by `javap`):** `CloseableIteration` lost its
  > checked-exception type parameter and the `Iteration` interface was removed —
  > 4.3.15 `CloseableIteration<E, X extends Exception> extends Iteration<E, X>` (with
  > `close() throws X`) → 5.1.4 `CloseableIteration<E> extends java.util.Iterator<E>`
  > (`close()` throws nothing). So every two-type-param `CloseableIteration<…,…>` /
  > `Iteration<…>` reference, and every method **override** whose signature embedded the
  > old form, breaks.
  >
  > **Measured exposure (grep of `com.mobi.*` main sources):** **0** files use
  > `CloseableIteration` directly, **1** uses the removed bare `Iteration<…>`,
  > `Iterations.` helper 1, `RepositoryResult` 25, `TupleQueryResult`/`GraphQueryResult`/
  > `QueryResults` 58 — but those result types stayed single-parameter, so most *usage*
  > survives; only now-unchecked exception handling and overrides break. ~**269** main
  > files import `org.eclipse.rdf4j` (vs ADR-0003's 454 incl. tests).
  >
  > **Scratch compile against rdf4j 5.1.4** (`mvn clean compile -fae`, reverted):
  > **19 modules compiled CLEAN** — including the core `com.mobi.api`,
  > `com.mobi.persistence.api`, `com.mobi.cache*`, `com.mobi.security*`,
  > `com.mobi.semantic.service`, `com.mobi.server*`, `com.mobi.owlapi.utils`. The first
  > break is **`com.mobi.persistence.utils/src/main/java/com/mobi/persistence/utils/ReadOnlyRepositoryConnection.java`** (extends
  > rdf4j's `RepositoryConnectionWrapper`): `cannot find symbol` + **"name clash … same
  > erasure … neither overrides"** on its `add`/`remove`/`getStatements` overrides — i.e.
  > exactly the `CloseableIteration` signature change. (`-fae` then skipped 87 dependents
  > of this foundational module, so this is a *first-wave* count, not the total.)
  >
  > **Break categories + rough sizing:**
  > 1. **rdf4j-extension classes** (the real work): types that *extend* rdf4j base
  >    classes — `RepositoryConnectionWrapper` (`ReadOnlyRepositoryConnection`), any
  >    `*SailWrapper`/`NotifyingSail`, custom iterations — and override changed-signature
  >    methods. Small set (single digits to low tens), but foundational (they gate
  >    dependents). **This is where the effort is.**
  > 2. **Checked-exception handling** around `RepositoryResult`/query-result iteration
  >    (`catch (RepositoryException|QueryEvaluationException)` that no longer apply) — a
  >    subset of the 25 + 58 files; mechanical fixes.
  > 3. **The vast majority** of the 269 rdf4j files use stable model/value/consumption
  >    APIs (`IRI`, `Statement`, `ValueFactory`, `Model`, `RepositoryConnection` calls) —
  >    **no break**, confirmed by the 19 clean modules.
  > 4. **OWL API (D-2):** unchanged by this spike — still needs reconciling (owlapi 5.5.0
  >    pulls rdf4j 4.2.0); a runtime/resolution concern, not a Mobi-code compile break.
  >
  > **Conclusion:** the 4→5 upgrade is **narrower than the 454-file import count implies**
  > — concentrated in a handful of rdf4j-*extension* classes (`ReadOnlyRepositoryConnection`
  > first) plus mechanical exception-handling cleanup, not a pervasive rewrite. The full
  > count needs the iterative Phase-1 fix (fix the foundational extension classes →
  > recompile → next wave), which is the upgrade itself. **Still gated on Step 1's driver.**

## Phase 1 — Property bump + compilation

- [ ] **Step 3.** Bump `rdf4j.version` → **5.x** (`mobi-parent/pom.xml:97`) and drive the
  reactor to compile, module by module, fixing the 4→5 API breaks from the Step-2 list.
  **Gate:** `mvn clean install -DskipTests` compiles the full reactor on the current JDK.

## Phase 2 — OWL API reconciliation

- [ ] **Step 4.** Resolve the OWL API ↔ RDF4J coupling (D-2): bump `owlapi.version` to a
  release aligned with RDF4J 5, or override `owlapi.rdf4j.version`, so a single RDF4J
  major is on the classpath. **Gate:** dependency tree shows one RDF4J 5.x; ontology
  module unit tests compile + pass.

## Phase 3 — OSGi bundle versions & ranges

- [ ] **Step 5.** Update `rdf4j-deps` and `rdf4j-runtime-osgi` to 5.x and fix any
  Import-Package/Export-Package version ranges the major bump shifts
  (`mobi-karaf-feature/.../feature.xml`). **Gate:** the distribution assembles and the
  RDF4J bundles resolve at boot.

## Phase 4 — Test & boot green

- [ ] **Step 6.** Run the full unit suite and the persistence + ontology Pax-Exam
  integration suites; boot the distribution and exercise repository/SPARQL paths.
  **Gate:** units + persistence/ontology ITs green and the distribution boots on the
  target JDK — matching the 4.3.15 baseline, on 5.x.

## Out of scope

- The **prolly** storage decision (ADR-0001 / ADR-0002) — this upgrade is deliberately
  decoupled from it (D-1).
- The **Java 25** move — decoupled (4.3.15 works on 25); see
  [java-25-migration](java-25-migration/README.md).
- Adopting **new RDF4J 5 features** (GeoSPARQL, new Sail implementations, etc.) beyond
  what the version move requires — a separate follow-on once the baseline is on 5.x.

## Where this lives

- `mobi-parent/pom.xml` — `rdf4j.version` (`:97`), `owlapi.version` (`:89`),
  `owlapi.rdf4j.version` (`:90`), and the dozen pinned RDF4J artifacts.
- `mobi-karaf-feature/src/main/feature/feature.xml` — `rdf4j-deps` (`:165`),
  `rdf4j-runtime-osgi` (`:182`).
- `com.mobi.persistence.api/.../repository/api/OsgiRepository.java:26` — the API-visible
  RDF4J type.
- Serves: [ADR-0003](../docs/adr/0003-rdf4j-4-to-5-upgrade.md); relates to
  [ADR-0001](../docs/adr/0001-evaluate-prolly-tree-storage-backend.md) /
  [ADR-0002](../docs/adr/0002-out-of-process-prolly-rdf4j-over-a-network-boundary.md).
