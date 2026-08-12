# ADR-0005: Defer the Karaf-vs-Quarkus runtime decision to the Jakarta migration gate

- **Status:** Proposed
- **Date:** 2026-08-12
- **Deciders:** Mobi build / platform maintainers

## Context

The question was raised whether Mobi should replace Apache Karaf / OSGi with
Quarkus as its runtime. The forces, with measurements taken 2026-08-12 (greps
over non-generated sources on the `java-25-ehcache-modernization` branch):

- **Karaf's maintenance drag is real and recurring.** The pain Mobi keeps
  hitting is runtime-induced: the ehcache version was locked at 3.3.1 for years
  because 3.10.0 "causes unsatisfied ehcache services" — an OSGi service-resolution
  failure, only lifted in commit `50d5454615` (now `ehcache.version` 3.10.8,
  `mobi-parent/pom.xml:63`); Mobi must keep a copied `jre.properties` in sync
  with Karaf (`mobi-distribution/src/main/resources/etc/jre.properties`); and
  the Karaf shell/CLI is broken on JDK 24+ with no fix on the 4.4.x line
  ([ADR-0004](0004-karaf-jakarta-coupling-for-java-25.md) validation caveat,
  `plans/java-25-migration/findings.md` F-10/F-12) while Karaf 4.5.0 is not GA.
- **The OSGi coupling is broad but shallow.** Measured surface: 410 files import
  `org.osgi.*`; 228 files use Declarative Services `@Component` and 132 use
  `@Reference` (mechanically mappable to CDI `@ApplicationScoped` / `@Inject`);
  the hand-rework tail is 49 ConfigAdmin/metatype files, 42 files on the raw
  framework API (`BundleContext` / `ServiceReference`), 16 using
  `ServiceTracker`/dynamic lookup, 18 EventAdmin, 14 Karaf shell commands, 12
  HTTP-whiteboard servlets; plus 89 OSGi-packaged Maven modules and the 312-line
  `mobi-karaf-feature/src/main/feature/feature.xml`.
- **Mobi does not use OSGi's headline feature.** No source file calls
  `installBundle` — there is no runtime bundle installation or hot-deploy. The
  dynamism OSGi exists to provide is not load-bearing, so a single-classloader
  runtime is architecturally possible; the cost is mechanical breadth, not a
  design obstacle.
- **The `javax`→`jakarta` migration is a shared prerequisite of both futures.**
  Quarkus is Jakarta-native (`jakarta.ws.rs`, Servlet 6, RESTEasy), and Karaf
  4.5 / pax-web 10 is the same namespace move. The ~110-file surface (88 files
  importing `javax.ws.rs`, 27 `javax.servlet`, CXF 3→4) measured in
  ADR-0004 must be paid on *either* path. ADR-0004 deliberately deferred it,
  gated on Karaf 4.5.0 reaching GA.
- **A Quarkus migration is big-bang.** The reactor cannot run half in Karaf and
  half in CDI; the realistic shape is a long-lived branch touching ~400+ files,
  all 89 poms, and rewriting the config (ConfigAdmin factory configs → 
  MicroProfile Config), security (Karaf JAAS — 2 files), packaging
  (karaf-assembly → fast-jar), and Pax-Exam integration-test layers. Reasoning,
  not measurement: several person-months, competing directly with the in-flight
  Java-25 / RDF4J-5 work ([ADR-0003](0003-rdf4j-4-to-5-upgrade.md),
  `plans/java-25-migration/implementation-plan.md`).
- **An incremental escape hatch already exists.**
  [ADR-0002](0002-out-of-process-prolly-rdf4j-over-a-network-boundary.md)
  established the out-of-process-service-over-a-network-boundary pattern. New
  capabilities can be built as standalone services (Quarkus or otherwise) beside
  Karaf without committing the monolith to anything.

## Options

| Option | Summary | Pros | Cons |
|---|---|---|---|
| **A. Defer the runtime decision to the Jakarta gate** | Finish Java-25/RDF4J-5 work on Karaf 4.4.x; build new services out-of-process (ADR-0002 pattern); decide "Jakarta inside Karaf 4.5" vs "Jakarta onto Quarkus" when Karaf 4.5.0 GA puts the ~110-file Jakarta migration on the table anyway. | No duplicated Jakarta cost; no competition with in-flight work; the decision is made when both options are concrete and the shared prerequisite is priced in; new-service work de-risks Quarkus with zero commitment. | Karaf pain (shell broken on JDK 25, `jre.properties` sync) persists until the gate; risks drifting into "defer forever" without a named trigger. |
| **B. Migrate to Quarkus now** | Big-bang branch: Jakarta-ize, DS→CDI, ConfigAdmin→MicroProfile Config, collapse 89 bundles to plain jars, karaf-assembly→fast-jar, Pax-Exam→`@QuarkusTest`. | Deletes the whole category of OSGi-resolution pain; fixes the JDK-25 CLI break; dev-mode/startup/onboarding wins arrive soonest. | Several person-months on a long-lived branch; forces the Jakarta migration ADR-0004 just deferred; regression risk concentrated in ConfigAdmin-driven repository wiring; stalls Java-25/RDF4J-5 delivery. |
| **C. Commit to Karaf indefinitely** | Treat OSGi as permanent; plan only the Karaf 4.5 upgrade. | No rewrite ever; smallest total diff if Karaf stays healthy. | Accepts the maintenance drag as permanent; measured evidence (no `installBundle` callers) says Mobi pays OSGi's complexity for dynamism it never uses; contributor-onboarding cost persists. |

## Decision

**Adopt Option A.** The Karaf-vs-Quarkus decision is explicitly deferred — not
rejected — to the moment the `javax`→`jakarta` migration becomes unavoidable.
The named trigger: **when Karaf 4.5.0 reaches GA and a Jakarta migration plan is
drafted, that plan must open by re-running this comparison** with the migration
cost shared between both options. Until then, new out-of-process services may
freely use Quarkus (per the ADR-0002 boundary pattern), and no new code should
deepen the hand-rework tail (raw `BundleContext`, `ServiceTracker`, EventAdmin)
without need.

Reasoning, marked as reasoning: given Mobi uses none of OSGi's runtime
dynamism, the expectation is that Quarkus wins the comparison when it is
actually run — but that is a prediction, not a decision.

**Status: Proposed** until the trigger condition is ratified by the platform
maintainers.

## Consequences

**Easier:** Java-25 and RDF4J-5 delivery proceed unblocked; the Jakarta cost is
paid once, inside whichever runtime decision wins; Quarkus can be evaluated
cheaply on real Mobi workloads via out-of-process services before any
commitment.

**Harder / new cost:** the JDK-25 Karaf shell break
(`plans/java-25-migration/upstream-karaf-shell-jdk25.md`) and the
`jre.properties` sync burden persist until the gate; the deferral needs an
active watch on Karaf 4.5.0's release, or Option A silently decays into
Option C.

**Follow-ons:** the future Jakarta migration plan (owner of the re-run
comparison, per ADR-0004's follow-ons); any new out-of-process service plan
citing ADR-0002 may choose Quarkus and should report experience back here.

## Where this lives

- `mobi-parent/pom.xml` — `karaf.version` 4.4.11 (`:73`), `ehcache.version`
  3.10.8 (`:63`).
- `mobi-karaf-feature/src/main/feature/feature.xml` — the 312-line feature
  aggregation a Quarkus move would delete.
- `mobi-distribution/src/main/resources/etc/jre.properties` — the copied file
  that must track Karaf.
- [ADR-0002](0002-out-of-process-prolly-rdf4j-over-a-network-boundary.md) — the
  incremental out-of-process pattern.
- [ADR-0004](0004-karaf-jakarta-coupling-for-java-25.md) — the Jakarta deferral
  this ADR's gate is built on.
- `plans/java-25-migration/findings.md`,
  `plans/java-25-migration/upstream-karaf-shell-jdk25.md` — the JDK-25 shell
  break evidence.
