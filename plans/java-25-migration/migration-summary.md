# Java 17 → 25 migration — change summary / PR description

> Consolidates the committed changes and validation for the Java 25 work. Detail lives in
> [implementation-plan.md](implementation-plan.md), measured results in
> [findings.md](findings.md), the dashboard in [README.md](README.md). Decision context:
> [ADR-0004](../../docs/adr/0004-karaf-jakarta-coupling-for-java-25.md).

## What this does

Makes Mobi's build and **REST runtime** Java-25-ready on the existing `javax` Karaf
4.4.x line (no Jakarta migration — see ADR-0004). The reactor compiles to class-file
v69 and unit-tests green on JDK 25; the assembled distribution **boots and serves on
JDK 25** (validated by booting and by the Pax-Exam IT gate). `<release>` stays **17** in
the committed tree — the staged `17→21→25` flip is gated on CI (Java 21 + headless
browser), which this work does not include.

## Validation (all on JDK 25.0.3 unless noted)

- **Build + unit tests:** full reactor compiles to v69 and the unit suite passes
  (matches the Java-17 baseline; the only build failure is the pre-existing
  `com.mobi.web` ChromeHeadless env gap). See findings F-1/F-2/F-10.
- **Boot:** the assembled distribution boots and serves SPA + REST on JDK 25 — **89/90
  bundles Active** (the one Waiting, `utils.cli`, is by design), **0
  `UnsupportedClassVersion` / 0 module-access errors**. Validated for both v52
  (release-17) and v69 (release-25) bundles. F-10.
- **Pax-Exam IT gate** (runs in-sandbox; no browser needed): `itests-orm`,
  `itests-platform`, `itests-vfs` fully green; `itests-rest` green except the
  shell-using `UserRestIT`; `itests-etl` fails only in its `executeCommand` setup.
  **Every IT failure is the F-12 Karaf-shell issue; every non-shell IT passes on JDK 25.**
  F-11.

## Changes (grouped by purpose)

**Build toolchain — JDK-portable, land on Java 17 (Steps 4-6, F-2)**
- `mobi-parent/pom.xml`: maven-compiler 3.8.0→3.13, surefire/failsafe 2.22.2→3.2.5,
  enforcer→3.4.1; **bndlib override → 7.3.0** inside maven-bundle-plugin (parses v69);
  **Mockito 4.6.1→5.23.0**, Groovy 3→4.0.32 / Spock 2.4-groovy-4.0 / JUnit 5.14.1.
  `<release>` kept 17.
- `com.mobi.server.utils/pom.xml`: shade 3.5.0→3.6.0.
- `rdf-orm/rdf-orm-maven-plugin/pom.xml`: maven-plugin-plugin 3.6.4→3.15.1 + ASM 9.10.1
  (the `@Mojo` scanner must read v69).
- Mockito 5: added `mockito-core` (test) to the 10 modules that declared only
  `mockito-inline`; deleted 3 redundant `…/mockito-extensions/org.mockito.plugins.MockMaker`
  files; `initMocks`→`openMocks` in `shacl.form.rest`. (Touches `com.mobi.api`,
  `catalog.api/impl/rest`, `email.impl`, `jaas.rest`, `ontology.impl.repository`,
  `security.impl`, `server.impl`, `utils.cli`, etc.)

**Runtime — Karaf 4.4.11 boot fixes (this work)**
- `mobi-parent/pom.xml`: `karaf.version` 4.4.3→**4.4.11**. **Removed the bnd
  `_noimportjava` instruction** — it stripped the substitutable self-import and made
  Felix return null for bundles' root `*.rq` resources during the boot transient,
  killing ~21 DS services (Defect B, F-8).
- `mobi-distribution/src/main/resources/etc/org.ops4j.pax.logging.cfg`: synced to
  4.4.11's pax-logging 2.3.3 (`log4j2.out.pattern`→`log4j2.outpattern`) so file logging
  works (F-7).
- `mobi-distribution/src/main/resources/etc/jre.properties`: added `jre-19`…`jre-26`
  (from stock 4.4.11; `jre-25 = ${jre-24}, javax.sound`), preserving Mobi's
  customizations — required for the JDK-25 execution environment.
- `mobi-distribution/pom.xml`: build-time `maven-antrun-plugin` step that rezips the
  `geronimo-osgi-locator.jar` embedded in `aries.javax.jax.rs-api` (its 2018 ZIP extra
  field tag `0xbfef` trips hardened JDK `ZipFile`), preserving the bundle's
  `osgi.contract=JavaJAXRS` (Defect A, F-9).
- `mobi-karaf-feature/src/main/feature/feature.xml`: log4j-api/core **2.17.1→2.25.4** to
  match Karaf 4.4.11's pax-logging 2.3.3 (the 2.17.1 jars lacked
  `simple.internal.SimpleProvider`, aborting boot-feature install so `BootFinished` was
  never reached — F-9 follow-through / F-11).

**Integration-test fixes (this work, test-classpath only)**
- `mobi-itests/pom.xml`: commons-io **2.11.0→${commons-io.version}** (2.18.0) and added
  commons-lang3 **3.18.0** (test) — pre-existing skews vs commons-compress 1.27.1 that
  blocked the Pax-Exam containers from extracting/booting (F-11).

**Docs**
- `plans/java-25-migration/` (new): README dashboard, implementation-plan, findings
  (F-1…F-12), this summary, and the upstream Karaf issue draft.
- `docs/adr/0004-…`: added the F-12 validation caveat.

## Known limitation (not fixable in-tree) — F-12

Karaf 4.4.11's **shell/console/CLI** is broken on **JDK 24+**: `Subject.getSubject` was
removed by JEP 486 and it spans ~8 classes across 7 bundles (Karaf's JAAS layer: shell, JMX, jaas commands). This breaks
`bin/client`, the Gogo console, and `com.mobi.utils.cli` (backup/restore/migration) on
JDK 25 — **REST/boot are unaffected**. A carry-patch was validated but rejected as
impractical. **Disposition:** run admin CLI on **JDK ≤ 21** while the REST runtime runs
on JDK 25, until an upstream Karaf fix (`Subject.current()`) lands — a 4.4.x patch or
4.5.x GA. Ready-to-file report: [upstream-karaf-shell-jdk25.md](upstream-karaf-shell-jdk25.md).

## How to verify locally

```bash
# unit build (current JDK 17): matches baseline, web ChromeHeadless gap aside
mvn clean install
# JDK 25: temporarily set <release>25> in mobi-parent, then on JDK 25:
mvn clean install -DskipTests -Denforcer.skip=true     # assembles the v69 distribution
# boot the assembled distribution on JDK 25 → SPA https://localhost:8443/mobi/index.html
# Pax-Exam ITs on JDK 25 (no browser): non-shell modules pass
mvn verify -pl mobi-itests/itests-orm,mobi-itests/itests-platform,mobi-itests/itests-vfs -DskipITs=false
```

## Remaining (out of this change)

- **CI-tier:** the staged `<release>` flip 17→**21**→**25**, each gated on units +
  Pax-Exam ITs + Nightwatch — needs a JDK-21/25 CI image and a headless browser.
- **F-12 decision:** wait for the upstream Karaf shell fix, or adopt the JDK-split
  (CLI ≤21 / REST 25).
- **Deferred, separate plans:** `javax`→`jakarta` / Karaf 4.5 (ADR-0004); ehcache 3.10
  lock-lift; RDF4J 5 (ADR-0003) — all confirmed *not* required for the JDK-25 move.
