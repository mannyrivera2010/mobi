# ADR-0004: The Java 25 upgrade is decoupled from the javax→jakarta migration — Karaf stays on the 4.4.x line

- **Status:** Proposed
- **Date:** 2026-06-18
- **Deciders:** Mobi build / platform maintainers

> **Note on revision:** an initial draft of this ADR asserted the opposite — that
> reaching Java 25 *forced* a Karaf 4.5 major upgrade and a `javax`→`jakarta`
> migration. The Phase-0 compatibility spike in
> [`plans/java-25-migration/implementation-plan.md`](../../plans/java-25-migration/implementation-plan.md) refuted that premise
> (evidence below). This ADR records the corrected decision; it was never
> `Accepted` under the old framing.

## Context

We want Mobi on Java 25 (the September 2025 LTS), currently pinned to Java 17
(`mobi-parent/pom.xml:643` `<release>17</release>`; enforcer `requireJavaVersion 17`
at `:790-792`). For an OSGi / Apache Karaf application the runtime gates the JDK,
so the question is which Karaf line supports JDK 25 and what it drags with it.

The forces, with the spike's findings:

- **The OSGi runtime needs a JavaSE execution-environment profile for the JDK it
  runs on.** Karaf ships a `jre.properties` enumerating the packages the system
  bundle exports per environment. Mobi's copy
  (`mobi-distribution/src/main/resources/etc/jre.properties`) chains aliases that
  **stop at `jre-18`** (`:517-525`) — which is *why* Mobi can't run on JDK 25
  today, but says nothing about which Karaf provides the fix.
- **The `javax` Karaf 4.4.x line already supports modern JDKs at runtime
  (measured).** Karaf release notes: 4.4.5 "better JDK21 support with new ASM
  (9.6)"; 4.4.7 "improvements to support JDK23 at runtime"; **4.4.8 "Java26
  support"** ([karaf.apache.org/news](https://karaf.apache.org/news)). The latest
  is **4.4.11** (Apr 2026). Verified at the source: Karaf **4.4.11's**
  `jre.properties` defines `jre-19` … `jre-26`, including
  `jre-25 = ${jre-24}, javax.sound` and `jre-21 = ${jre-17}`
  (`apache/karaf` tag `karaf-4.4.11`,
  `assemblies/features/base/.../etc/jre.properties`). So the `JavaSE-25` profile
  Mobi lacks **already exists in Karaf 4.4.11** — a maintenance bump, not a major
  upgrade.
- **Karaf 4.4.x is and stays `javax` / pax-web 8.** The 4.4.x line is OSGi R8 with
  pax-web 8.0.x / pax-logging 2.3.x. The Jakarta move (pax-web 10 / Jetty 12 /
  Jakarta Servlet 6) is the **future Karaf 4.5.0, which is not yet GA** — still
  being prepared for a vote
  ([dev@karaf [HEADS UP] 4.5.0](https://www.mail-archive.com/dev@karaf.apache.org/msg16420.html));
  pax-web 8/9 are in maintenance, pax-web 10 in development.
- **Therefore Java 25 and the Jakarta migration are independent.** Java 25 is
  reachable by bumping Karaf 4.4.3 → 4.4.11 and syncing Mobi's `jre.properties`,
  staying entirely on `javax`. The ~110-file `javax`→`jakarta` surface (83
  `javax.ws.rs`, 27 `javax.servlet`, the 13 `org.osgi.service.http` whiteboard
  files, CXF 3→4) measured in the upgrade plan is **not** on the Java-25 path.
- **The residual risk shifts to one concrete check.** pax-web 8 runs Jetty 9.4
  (near-EOL). Karaf 4.4.8's "Java26 support" implies the shipped stack was
  validated, but the load-bearing question becomes: does Mobi's *assembled
  distribution* boot and serve on JDK 25? That is far cheaper to answer than a
  namespace migration.
- **Unchanged regardless of Karaf line:** the JDK-25 toolchain and library work —
  Mockito 4.6.1→5.x (`mobi-parent/pom.xml:77`), the ehcache 3.3.1 lock (`:63`),
  maven-compiler-plugin 3.8.0→3.13+ (`:640`), surefire 2.22.2→3.2.5+ (`:668`),
  bnd/maven-bundle-plugin 5.1.4 for class-file v69 (`:76`),
  [ADR-0003](0003-rdf4j-4-to-5-upgrade.md) RDF4J — is required either way.

## Options

| Option | Summary | Pros | Cons |
|---|---|---|---|
| **A. Bump within the `javax` 4.4.x line (→ 4.4.11) for Java 25; defer Jakarta** | Upgrade Karaf 4.4.3 → 4.4.11, sync `jre.properties`, keep `javax`/pax-web 8, do the toolchain/library work, route 17→21→25. | Officially-supported path (4.4.8+ "Java26 support"); ~110-file Jakarta migration off the critical path; small, supported diff; 4.5.0 isn't even GA. | Stays on pax-web 8 / Jetty 9.4 (must validate boot on JDK 25); the Jakarta modernization still looms as future work. |
| **B. Couple Java 25 with a Karaf 4.5 + Jakarta migration** | Jump to the 4.5 line and migrate `javax`→`jakarta` as one effort. | Lands on the long-term Jakarta runtime in one move. | Karaf 4.5.0 not yet released; couples two large changes; Java 25 demonstrably does not require it — needless scope and risk. |
| **C. Stay on Java 17** | Defer the JDK upgrade. | Zero cost now. | Falls off the LTS curve; library/runtime support for 17 erodes. |

## Decision

**Adopt Option A** — reach Java 25 on the `javax` Karaf 4.4.x line by bumping to
4.4.11 (and syncing Mobi's `jre.properties` from it), keeping pax-web 8 /
`javax.servlet`, and routing 17 → 21 → 25. **The `javax`→`jakarta` migration is
explicitly decoupled and deferred** to its own plan, gated on Karaf 4.5.0 reaching
GA — it is a long-term modernization, not a Java-25 prerequisite.

**Status: Proposed.** Two gates commit it: (1) the assembled `mobi-distribution`
boots and serves on a JDK 25 runtime with Karaf 4.4.11 (the Jetty 9.4 residual
risk); (2) the ehcache 3.3.1 lock is liftable. This ADR moves to `Accepted` once
both report.

## Consequences

> **Validation caveat (2026-06, `plans/java-25-migration/findings.md` F-10/F-12):** the
> decision holds for the **REST runtime** — the distribution boots and serves the SPA +
> REST on JDK 25 on the javax Karaf 4.4.11 line (pax-web 8 / Jetty 9.4), and the
> Pax-Exam REST IT gate is 7/8 green. **But the Karaf shell/console/CLI is broken on
> JDK 24+:** Karaf 4.4.11's `ShellUtil` calls `Subject.getSubject`, which JEP 486
> removed (throws `UnsupportedOperationException` on JDK 24+, no JVM-flag workaround).
> This breaks `bin/client`, the Gogo console, and Mobi's `com.mobi.utils.cli`
> (backup/restore/migration) on JDK 25. No newer Karaf 4.4.x exists and 4.5.0 isn't GA,
> so the fix is upstream (`Subject.current()`) — until then, admin CLI must run on a
> JDK ≤21 even though the REST runtime is on JDK 25. The staged Java-21 checkpoint is
> unaffected. This is a residual JDK-25 gap on the javax line this ADR did not foresee.

**Easier:** the upgrade collapses to a Karaf maintenance bump plus the
JDK-portable toolchain/library work — no namespace rewrite, no whiteboard
migration, no CXF 4, no urgency on the bnd-plugin swap beyond class-file v69.
Java 21 remains a clean shippable checkpoint. The change stays on a supported,
maintained Karaf line.

**Harder / new cost:** Mobi must keep its copied `jre.properties` in sync with the
upgraded Karaf (it diverged at `jre-18`); the pax-web 8 / Jetty 9.4 stack must be
proven on JDK 25 by actually booting the distribution; and the Jakarta debt is
deferred, not retired — Mobi stays on pax-web 8 until a separate plan takes it to
Karaf 4.5.

**Follow-ons:** [`plans/java-25-migration/implementation-plan.md`](../../plans/java-25-migration/implementation-plan.md)
carries the (now smaller) execution. A *separate, future* plan should own the
Karaf 4.5 / `javax`→`jakarta` migration once 4.5.0 is GA. Depends on
[ADR-0003](0003-rdf4j-4-to-5-upgrade.md).

## Where this lives

- `mobi-parent/pom.xml` — `<release>` (`:670`), enforcer (`:817-819`), Karaf
  (`:73`), the JDK-sensitive plugin/library versions. (The `<release>`/enforcer line
  numbers shifted from the originally-cited `:643`/`:790-792` when the bndlib
  `<dependencies>` override block was inserted above them.)
- `mobi-distribution/src/main/resources/etc/jre.properties` — the JavaSE profiles,
  now synced from Karaf 4.4.11: `jre-19`…`jre-26` were added (`:526-540`), so the
  file no longer stops at `jre-18` (`:525`) as it did when this ADR was first written.
- `mobi-itests/` — the Pax-Exam boot gate that validates JDK 25 on pax-web 8.
- Serves: [`plans/java-25-migration/implementation-plan.md`](../../plans/java-25-migration/implementation-plan.md); relates to
  [ADR-0003](0003-rdf4j-4-to-5-upgrade.md).
