---
status: active
priority: P2
category: build
epic: jdk-modernization
---

# Java 17 → 25 upgrade (via the Java 21 LTS checkpoint)

> **For the current status, read [README.md](README.md) (the dashboard) first.**
> This file is the detailed step-by-step plan with per-step wrap-ups. Execution
> diverged from the original linear Steps 7–12: a diagnostic pass proved the JDK-25
> *build* directly (see [findings.md](findings.md) F-1/F-2) and jumped ahead to the
> Karaf 4.4.11 boot (F-4). The dashboard reconciles plan-vs-reality; the
> measured results live in findings.md.

Move Mobi from Java 17 to Java 25 (LTS). A Phase-0 compatibility spike (Step 1,
done) refuted the original premise that Java 25 forces a Karaf 4.5 / Jakarta
migration: the `javax` Karaf **4.4.x** line already supports modern JDKs at
runtime (4.4.8 "Java26 support"), and Karaf **4.4.11's** `jre.properties` defines
the `JavaSE-25` profile Mobi lacks. So Java 25 is reached by a Karaf *maintenance
bump* (4.4.3 → 4.4.11) plus the JDK-portable toolchain/library work, staying on
`javax` — routed through Java 21 as a proven intermediate LTS. The
`javax`→`jakarta` migration is decoupled and deferred to its own plan
([ADR-0004](../../docs/adr/0004-karaf-jakarta-coupling-for-java-25.md)). Both Phase-0
gates are now answered: the second (Step 2) found the ehcache lock is liftable but
**also decoupled from Java 25** — the locked 3.3.1 has no JDK-25 hazard, so it
rides the upgrade unchanged and the lock-lift is a separate, optional
modernization. Proposed; ready to execute.

## Session findings (2026-06-19) — build + unit tests PROVEN on Java 25

Driving Phase 1 surfaced that **the Java-25 work is far smaller than the plan
assumed.** Measured on a box with JDK 17/21/25 (boot/IT/functional gates excluded —
no Karaf boot or headless browser here):

- **Mobi's full production reactor + its entire unit suite compile to class-file
  v69 and pass on JDK 25.** `mvn clean install` (release 25, JDK 25): 96 SUCCESS /
  1 FAILURE / 10 SKIPPED with **3270 / 0 / 0 / 15 — identical to the Java-17
  baseline**; the only failure is the `com.mobi.web` ChromeHeadless env gap. The
  same toolchain reverted to release 17 also stays **3270 / 0 / 0 / 15** on JDK 17
  (no regression) — so the changes land incrementally on the current JDK.
- **Runtime dependencies are JDK-25-compatible UNCHANGED** — RDF4J 4.3.15, OWLAPI
  5.5.0, ehcache 3.3.1 all build + unit-test clean on 25. So **Step 8 (RDF4J 5 /
  OWLAPI) is NOT required for the JDK move** (stays valid as separate hardening per
  [ADR-0003](../../docs/adr/0003-rdf4j-4-to-5-upgrade.md)), and **Step 7's ehcache
  concern is build-clean on 25** (only its boot-time `javax.xml.bind` resolution
  remains, tied to Step 9 + boot).
- **Every JDK-25 blocker was an old build-toolchain bytecode library** (not code or
  runtime deps) — same class as Step 5's bndlib. Found by a flip-to-25-and-measure
  diagnostic, and fixed:
  - `maven-plugin-plugin` 3.6.4 → **3.15.1** + an **ASM 9.10.1** override
    (`rdf-orm/rdf-orm-maven-plugin/pom.xml`) — the `@Mojo` descriptor scanner could
    not read the rdf-orm plugin's v69 classes.
  - the Groovy/Spock/JUnit test cluster (`mobi-parent/pom.xml`): `gmavenplus`
    1.4 → **3.0.2** (goal `testCompile`→`compileTests`), Groovy 3.0 → **4.0.32**
    (groovy-bom), `spock-core` 2.1-groovy-3.0 → **2.4-groovy-4.0**, `junit` 5.9.1 →
    **5.14.1** (junit-platform alignment with Spock 2.4). The 12 Spock specs compile
    + pass on Groovy 4 with **no spec-code changes**.

**What remains for true completion** (needs CI / a bootable environment, not this
sandbox): the Karaf bump + `jre.properties` sync (Step 9), distribution assembly +
Pax-Exam boot (Step 10), the functional/Karma suites, and the staged release flip
17→21→25 with those gates green.

## Goal

`mvn clean install` builds the 88-module reactor under JDK 25 with
`<release>25</release>`; the assembled `mobi-distribution` boots in Karaf 4.4.11
on a JDK 25 runtime and serves the SPA + REST endpoints; and the Pax-Exam
integration suite (`-DskipITs=false`) and the Nightwatch functional suite
(`-DskipFunctional=false`) pass — the same green baseline that exists on Java 17
today, on Java 25. Java 21 is a fully-green, shippable checkpoint along the way.

## Decisions

- **D-1: Java 25 is gated by the runtime, not the compiler.** The OSGi system
  bundle needs a JavaSE execution-environment profile for the JDK it runs on.
  Mobi's `jre.properties` (`mobi-distribution/src/main/resources/etc/jre.properties`)
  chains aliases that stop at `jre-18` (`:517-525`) — which is why Mobi cannot run
  on JDK 25 today. The `<release>` flag (`mobi-parent/pom.xml:643`) is the last
  step, not the first.
- **D-2: Java 25 is reached on the `javax` Karaf 4.4.x line — NOT via Karaf 4.5 /
  Jakarta (spike result).** Karaf release notes show the 4.4.x (pax-web 8,
  `javax`) line tracking JDKs: 4.4.5 "JDK21 support (ASM 9.6)", 4.4.7 "JDK23 at
  runtime", **4.4.8 "Java26 support"**; latest is **4.4.11**. Verified at the
  source: Karaf 4.4.11's `jre.properties` defines `jre-19`…`jre-26` (incl.
  `jre-25 = ${jre-24}, javax.sound`). Karaf 4.5.0 (the Jakarta / pax-web 10 line)
  is **not yet GA**. So the upgrade is a maintenance bump 4.4.3 → 4.4.11 plus
  syncing Mobi's `jre.properties`, keeping `javax.servlet`. See
  [ADR-0004](../../docs/adr/0004-karaf-jakarta-coupling-for-java-25.md).
- **D-3: Route through Java 21, change one axis at a time.** Java 21 (prior LTS)
  has broader certification than Java 25. Land the Karaf bump and toolchain/library
  work, reach a green Java 21 checkpoint, then flip to 25.
- **D-4: Do the JDK-portable axes first on Java 17.** The toolchain, test/library,
  and CI changes run on the *current* JDK and shrink the eventual diff; only the
  `jre.properties` sync and the `<release>` flips need the new JDK.
- **D-5: Both gates answered; ehcache is decoupled too.** Step 1 (Karaf↔JDK-25)
  and Step 2 (ehcache) are both done. The ehcache spike found the lock is real (a
  3.3.1 plain-bundle → 3.10 Declarative-Services-bundle change, not a version
  bump) but **liftable, and not a Java-25 blocker**: the locked 3.3.1 uses no
  `Unsafe`/`jdk.internal`/`sun.reflect` (jar-scanned) and resolves on JDK 25 via
  the faked `javax.xml.bind` export, so it rides the upgrade unchanged. The
  remaining *validation* risk is booting the pax-web 8 / Jetty 9.4 stack on JDK 25
  (Step 12), far cheaper than a namespace migration.
- **D-6: The `javax`→`jakarta` migration is out of scope here.** It is real future
  work (~110 EE files, the OSGi HTTP-whiteboard rewrite, CXF 3→4) but is gated on
  Karaf 4.5.0 GA and is not a Java-25 prerequisite — it belongs to a separate plan.

## What's there already (anchoring)

Snapshot from a June 2026 scan; re-anchor before resuming.

- **Java-17 enforcement (single source of truth):** `mobi-parent/pom.xml:643`
  (`<release>17</release>`) and `:790-792` (enforcer `requireJavaVersion 17`). No
  `maven.compiler.*` properties, no bare `<source>/<target>`, no
  `maven-toolchains-plugin`.
- **Runtime stack:** Karaf **4.4.3** (`mobi-parent/pom.xml:73`), OSGi **R8**
  (`osgi.core 8.0.0`, `:88`), servlet **3.1.0 `javax`** (`:98`). pax-web 8.0.15;
  pax-exam/pax-logging/pax-url/Felix unpinned (Karaf BOM). Mobi's `jre.properties`
  profiles stop at `jre-18` (`mobi-distribution/.../etc/jre.properties:517-525`)
  — Karaf 4.4.11 ships `jre-19`…`jre-26`, the file to sync from. **No
  SecurityManager / policy / `karaf.secured`** anywhere — JEP 486 is a non-issue.
- **Build toolchain (all old enough to block):** maven-compiler-plugin **3.8.0**
  (`:640`, below the ≥3.13 that knows `<release>25`); surefire/failsafe **2.22.2**
  (`:668`, `:686`) with `forkCount 5` and **no `<argLine>` anywhere** in the
  reactor; maven-bundle-plugin **5.1.4** (`:76`, `:595-597`, `<extensions>true`) —
  the sole OSGi manifest + DS generator, BND-backed; maven-shade-plugin **3.5.0**
  (`com.mobi.server.utils/pom.xml:60`); license-maven-plugin **1.20** (`:722`);
  rdf-orm-maven-plugin built with maven-plugin-plugin **3.6.4** and
  `com.sun.codemodel:codemodel:2.6`.
- **JDK-sensitive libraries:** Mockito **4.6.1** (`:77`, `:541-549`) — 4.x does
  not support Java 21+; 112 test files import `org.mockito`, `mockito-inline` in
  11 modules, 3 `mockito-extensions/...MockMaker` files, 28
  `mockStatic`/`mockConstruction` sites. ehcache **3.3.1 LOCKED** (`:63`; comment
  "3.10.0 ... causes unsatisfied ehcache services") using
  `org.ehcache.expiry.Expirations`
  (`com.mobi.security.policy.api/.../PolicyCacheConfiguration.java:34`, removed
  later in 3.x), bundled at `mobi-karaf-feature/.../feature.xml:108`. RDF4J
  **4.3.15** (`:97`, see [ADR-0003](../../docs/adr/0003-rdf4j-4-to-5-upgrade.md)),
  OWLAPI **5.5.0** (`:89`, skewed `owlapi.rdf4j.version 4.2.0`). Netty
  **4.1.119.Final** (runtime bundles, `feature.xml:183-190`). **Mobi main source
  is clean** of `sun.misc.Unsafe`, `doPrivileged`/`SecurityManager`, `finalize()`,
  `setAccessible`, JDK-internal `Class.forName`.
- **Frontend (JDK-independent):** prod SPA built by exec-maven-plugin calling host
  `npm` (`com.mobi.web/pom.xml:106-135`); itests-web uses frontend-maven-plugin
  **1.15.1** with Node **v18.19.0** (`mobi-itests/itests-web/pom.xml:153-162`).
- **CI:** image `…/mobi-ci-container:feature-java-17`; SPA build needs
  `NODE_OPTIONS=--max_old_space_size=4096` (`CLAUDE.md:35`).
- **(For the deferred jakarta plan, not this one):** the EE surface is 83
  `javax.ws.rs` files, 27 `javax.servlet`, 27 `@Path`, 27
  `javax.annotation.security.RolesAllowed`, 18 `javax.xml.bind`, 13
  `org.osgi.service.http` whiteboard files; `jakarta.ws.rs-api 2.1.6` already
  declared (`:368-371`). None of this is touched by the Java-25 path.

## Phase 0 — Gating spikes

- [x] **Step 1.** Determine which Karaf release supports JDK 25 and what it drags
  with it. *(Done 2026-06-18. Result reshaped the plan: the `javax` Karaf 4.4.x
  line supports JDK 25 at runtime — 4.4.8 "Java26 support" — and Karaf 4.4.11's
  `jre.properties` defines `jre-19`…`jre-26` (`jre-25 = ${jre-24}, javax.sound`),
  verified at tag `karaf-4.4.11`. Karaf 4.5.0 / pax-web 10 / Jakarta is not yet
  GA. Conclusion: Java 25 needs only a maintenance bump to 4.4.11, NOT the Jakarta
  migration — see [ADR-0004](../../docs/adr/0004-karaf-jakarta-coupling-for-java-25.md).
  Residual risk relocated to Step 12: pax-web 8 / Jetty 9.4 must be proven to boot
  on JDK 25.)*
- [x] **Step 2.** Diagnose the ehcache lock and decide if it is liftable. *(Done
  2026-06-18. Diagnosed from the bundle manifests rather than a full build/boot.
  Root cause: ehcache went from a **plain SPI bundle (3.3.1)** to a **Declarative
  Services bundle (3.10.0)** — 3.10 adds `Service-Component: OSGI-INF/*.xml` and
  `Require-Capability: osgi.extender=osgi.component [1.3.0,2.0.0)`, registering
  `EhcacheCachingProvider` + ~30 factories as DS components. `SimpleCacheManager`
  (`com.mobi.cache.impl.core/.../SimpleCacheManager.java:96`) still does the
  3.3-era SPI lookup `Caching.getCachingProvider(fqcn, cl)` and the feature wires
  ehcache as a plain bundle (`feature.xml:108-109`) — that mismatch is the
  "unsatisfied services". **Liftable**, cost: 1-file compile fix (Step 7's residual
  note), `scr` feature dependency + likely JAXB API/runtime bundles (3.10 imports
  `javax.xml.bind` 2.2+ non-optional), a `SimpleCacheManager` rework to consume the
  DS-registered provider, and a boot validation. **Surprise:** the lock is NOT a
  Java-25 blocker — the locked 3.3.1 has 0 `sun.misc.Unsafe`/`jdk.internal`/
  `sun.reflect` refs (jar-scanned) and resolves on JDK 25 via the faked
  `javax.xml.bind` export, so it rides the upgrade unchanged. Lock-lift moved to a
  separate optional follow-on; only a residual check remains here.)*
- [x] **Step 3.** Capture the green baseline on Java 17: archive
  `mvn clean install`, `-DskipITs=false`, `-DskipFunctional=false` results (test
  counts) as the diff target every later gate is measured against. *(Done
  2026-06-19 on JDK 17.0.19. **Java unit baseline: 3256 tests, 0 failures, 0
  errors, 1 skipped** across 59 test-bearing modules; reactor 96 SUCCESS / 1
  FAILURE / 10 SKIPPED, cold `mvn clean install` 34 min. The single FAILURE is
  `com.mobi.web`'s `npm run tests` (`ng test`) — **"No binary for ChromeHeadless"**
  on this sandbox, an environment gap, not a red test; `mobi-karaf-feature` +
  `mobi-distribution` SKIPPED downstream of it. **Surprise:** the box had no JDK 17
  (only 21/25/26) — installed Temurin 17.0.19 user-locally to `~/.local/jdks/` to
  unblock; 21 and 25 are already present for Phases 3–4. **Scope correction:** only
  the `mvn clean install` *unit* baseline was capturable here — the integration
  tests (`-DskipITs=false`, which need the assembled distribution, itself skipped
  downstream of the web test gate) and the functional/Karma suites are CI-tier
  (`mobi-ci-container`) and deferred there. The same Chrome gate will block
  distribution assembly for the Step-10/12 boot validation unless Chrome is
  installed or the web test step is skipped — noted for those steps. The diff
  target for the Phase 1–4 unit gates is **3256 / 0 / 0 / 1 on Java 17**.)*

## Phase 1 — Modernize the JDK-portable axes (stays on Java 17)

- [x] **Step 4.** Bump the build toolchain in `mobi-parent/pom.xml`:
  maven-compiler-plugin 3.8.0 → 3.13+ (`:640`), surefire/failsafe 2.22.2 → 3.2.5+
  (`:668`, `:686`), shade 3.5.0 → 3.6+, license 1.20 → 2.x (`:722`), enforcer
  3.1.0 → 3.4+ (`:780`). Keep `<release>17`. **Gate:** full units green on 17.
  *(Done 2026-06-19. Bumped compiler 3.8.0→**3.13.0**, surefire 2.22.2→**3.2.5**,
  failsafe 2.22.2→**3.2.5**, enforcer 3.1.0→**3.4.1** (`mobi-parent/pom.xml`), shade
  3.5.0→**3.6.0** (`com.mobi.server.utils/pom.xml`). **Gate GREEN:** full
  `mvn clean install` on JDK 17.0.19 — the per-module test summaries are
  **byte-identical to the Step-3 baseline** for every `[INFO]` module (a `diff` of
  the per-module counts is empty), 0 failures / 0 errors; the only build FAILURE is
  the same `com.mobi.web` ChromeHeadless env gap (not a regression). **Surefire
  major-bump risk retired:** the provider auto-selects `JUnitPlatformProvider` and
  all 12 Spock specs across 6 modules still run (verified `ModelsSpec` 20/0,
  `SimpleMappingManagerSpec` 4/0). **Reporting nuance (benign):** the aggregate
  reads 3270/15-skip vs the baseline's 3256/1-skip, entirely from
  `com.mobi.workflows.impl.dagu` (33/1 → 47/15) — junit-platform now surfaces 14
  previously-uncounted `@Ignore`/`@Disabled` tests as skipped; **executed-and-passing
  is unchanged** (32 in that module, ~3255 reactor-wide), no running test lost.
  **Plan correction:** the **license-maven-plugin 1.20→2.x bump was deferred** — it
  runs `update-file-header`, which *rewrites* source headers, so a major bump risks
  reformatting every file's header (a huge spurious diff) for zero JDK-25 benefit
  (not a bytecode plugin); revisit only if 1.20 fails to run on JDK 25 itself, with
  a header-diff check.)*
- [x] **Step 5.** Migrate OSGi manifest + DS generation from maven-bundle-plugin
  5.1.4 to **bnd-maven-plugin 7.x** across the reactor — required so bnd can parse
  Java 25 class files (major 69); maven-bundle-plugin's embedded bnd lags. (This
  is for class-file v69, independent of the Karaf/namespace question.) Pilot on one
  leaf module, then roll out. **Gate:** generated `MANIFEST.MF` (Import/Export,
  DS) equivalent to the 5.1.4 output; units + ITs green. *(Done 2026-06-19.
  **Plan correction — re-scoped from a full plugin migration to a one-file bndlib
  override.** The diagnosis: maven-bundle-plugin 5.1.4 embeds **bndlib 5.1.1**
  (2020), which cannot parse class-file v69 (confirmed); the requirement is just a
  v69-capable bnd. The full bnd-maven-plugin migration would rewrite **~89 modules'**
  `<instructions>` — huge blast radius for that narrow need. Instead, **overrode the
  embedded bndlib → 7.3.0 inside the existing maven-bundle-plugin** plus a global
  `<_noimportjava>true>` — a single `mobi-parent/pom.xml` `pluginManagement` change,
  **zero module edits**.

  > **⚠ Correction (2026-06-24, findings.md F-8): `<_noimportjava>true>` was a mistake
  > and has been removed; only the bndlib-7.3.0 override is kept.** It did not merely
  > suppress `java.*` imports — in bndlib 7.3.0 it also stripped the **substitutable
  > self-import** of exported packages (e.g.
  > `Import-Package: …,com.mobi.setting.api.ontologies.setting`). Proof (3) below is
  > therefore wrong: the normalized diff masked that dropped self-import, so the bump
  > was **not** manifest-neutral. At runtime this makes Felix's bundle classloader
  > return null for the bundle's root `*.rq` resources during the boot/activation
  > transient, so the `static{}` `requireNonNull(getResourceAsStream(".rq"))` blocks
  > throw `ExceptionInInitializerError` and ~21 backend DS services die (unseen at the
  > time because the distribution boot was never validated in this sandbox). **Fix:**
  > keep the bndlib-7.3.0 override, drop `_noimportjava` — importing `java.*` is
  > harmless (the system bundle provides it by boot delegation). See findings.md F-8.

  **Proofs (as originally recorded — see correction above re: #3):** (1) mbp 5.1.4 + bndlib 7.3.0 build-compatible;
  (2) the global `-noimportjava` merges into a module's own `<instructions>`
  (Maven DOM config-merge) — verified on `persistence.utils` (java.* imports = 0,
  custom exports kept); (3) generated manifest **semantically identical** to the
  bndlib-5.1.1 baseline (normalized diff empty) — bndlib 7.3.0's only behavioral
  change, adding `java.*` imports, is suppressed so the bump stays neutral and
  decoupled from the jre.properties/Step-9 question; (4) **bndlib 7.3.0 parses a
  real JDK-25 v69 class → `format=Java_25`** (direct `aQute.bnd.osgi.Clazz` test);
  (5) DS generation intact — the `SimpleCacheManager` descriptor (DS v1.3.0,
  service + dynamic `0..n` reference) is correct under 7.3.0. **Gate GREEN:** full
  `mvn clean install` on JDK 17.0.19 — **96 SUCCESS / 1 FAILURE / 10 SKIPPED**
  (identical to Step 4; only `com.mobi.web` fails on the ChromeHeadless env gap),
  test count **3270 / 0 / 0 / 15** (identical to Step 4 — bnd runs post-`test`), no
  bnd/manifest errors anywhere. **Flaky-test finding (pre-existing, not Step 5):**
  the full build first tripped `com.mobi.vfs` `DefaultVirtualFilesystemTest.testProcessAllFiles`
  (`expected:<2> but was:<1>`) — a data race (a non-thread-safe `HashSet` mutated
  across `ForkJoinPool` threads); exonerated four ways (bnd runs after `test`; the
  test is independently racy; passed in baseline + Step 4; passed on isolated
  rerun 28/0) and absorbed by `-Dsurefire.rerunFailingTestsCount=2` (CI parity). A
  1-line `ConcurrentHashMap.newKeySet()` fix would kill the flake — tracked as an
  optional follow-up, out of this step's scope. **bnd-maven-plugin 7.x is NOT used**
  — the override achieves the same v69 outcome at ~1/30th the blast radius.)*
- [x] **Step 6.** Mockito 4.6.1 → 5.x (`:77`, `:541-549`). Drop the now-default
  `mockito-inline` from its 11 modules; reconcile the 3
  `mockito-extensions/...MockMaker` files; revalidate the 28 `mockStatic` sites;
  add a surefire `<argLine>` carrying `-XX:+EnableDynamicAgentLoading` (none exists
  today — JEP 451). **Gate:** all 112 Mockito test files green on 17. *(Done
  2026-06-19. Bumped to **5.23.0**. **`mockito-inline` → `mockito-core`** across the
  10 consumer modules — the pre-check caught that **9 of them declared *only*
  `mockito-inline`**, which in Mockito 4 transitively pulled `mockito-core`; bare
  removal dropped Mockito entirely (`utils.cli` failed with
  `NoClassDefFoundError: InvocationOnMock` at test discovery), so the correct move
  was add `mockito-core` (test scope) to those 9 (scripted). Removed the
  `mockito-inline` dependencyManagement entry; **deleted the 3 redundant
  `MockMaker` files** (inline is the 5.x default — verified `dataset.impl` 176/0);
  fixed the one removed-API break **`MockitoAnnotations.initMocks` → `openMocks`**
  (`shacl.form.rest` 4/0). **Plan correction:** did **not** add
  `-XX:+EnableDynamicAgentLoading` — that flag is **JDK 21+**, so on Java 17 the
  fork JVM rejects it and all tests fail to start; deferred to Step 11. Mockito 5's
  inline self-attach works on 17 without it. **Gate GREEN:** full
  `mvn clean install` (CI rerun flag for the vfs flake) — 96 SUCCESS / 1 FAILURE
  (only `com.mobi.web` ChromeHeadless) / 10 SKIPPED, test count **3270 / 0 / 0 / 15
  identical to the Step 4/5 baseline** — all 112 Mockito test files pass, including
  the 28 `mockStatic` sites.)*
- [ ] **Step 7.** Keep the locked ehcache 3.3.1 (decoupled from Java 25 per Step
  2) but confirm it still resolves after the JDK move: verify its non-optional
  `javax.xml.bind` import stays satisfied once Mobi's `jre.properties` is synced
  from Karaf 4.4.11 (Step 9) — if Karaf 4.4.11 dropped the faked `javax.xml.bind`
  export, add an explicit JAXB-API bundle to the feature. **Gate:** the
  security/policy cache works in the distribution booted on the target JDK.
  *(Lifting the lock to ehcache 3.10 — the DS-bundle migration from Step 2 — is a
  separate optional follow-on, out of scope here.)*

  > **Update (2026-06-26): the lock was lifted — the working tree is now on ehcache
  > 3.10.8, not 3.3.1.** The follow-on
  > [`plans/ehcache-3.10-lock-lift.md`](../ehcache-3.10-lock-lift.md) is **complete**:
  > `ExpiryPolicyBuilder` + a `SimpleCacheManager` `@Reference CachingProvider` rework,
  > no feature.xml change (scr + JAXB already present). This **subsumes** Step 7 — the
  > JDK-25 boot now runs ehcache 3.10.8 and the cache layer is Active (89/90 bundles,
  > unchanged), so "the security/policy cache works on the target JDK" is satisfied by
  > 3.10.8 rather than by confirming 3.3.1. The "ehcache 3.3.1 LOCKED" anchoring
  > elsewhere in this plan is now stale.
- [ ] **Step 8.** Bump and validate the JDK-sensitive runtime libs on 17: RDF4J
  4.3.15 → 5.x ([ADR-0003](../../docs/adr/0003-rdf4j-4-to-5-upgrade.md)), OWLAPI 5.5.0
  → latest 5.5.x (resolve the `owlapi.rdf4j` 4.2.0 skew). **Gate:**
  persistence/ontology ITs green.

## Phase 2 — Karaf maintenance bump (stays javax, stays Java 17)

- [ ] **Step 9.** Bump `karaf.version` 4.4.3 → **4.4.11** (`:73`); take pax-web
  8.x / pax-logging / ASM updates the BOM brings; **sync Mobi's `jre.properties`**
  from Karaf 4.4.11 so `jre-19`…`jre-25` profiles exist
  (`mobi-distribution/.../etc/jre.properties`, currently stops at `jre-18`).
  **Gate:** the distribution assembles.
- [ ] **Step 10.** Validate the Karaf bump in isolation — still on Java 17, no
  `<release>` change — by booting the assembled distribution: full Pax-Exam ITs
  (`-DskipITs=false`) and Nightwatch (`-DskipFunctional=false`). Mind the
  reactor-ordering gotcha: `com.mobi.web` (the SPA bundle) must build before
  `mobi-distribution` (`CLAUDE.md`). **Gate:** ITs + functional green on Java 17
  with Karaf 4.4.11 (pure Karaf regression isolated from the JDK change).

## Phase 3 — Java 21 checkpoint

- [ ] **Step 11.** Flip `<release>` to 21 (`:643`) and `requireJavaVersion` to 21
  (`:790-792`); confirm the synced `jre.properties` carries `jre-21`; add any
  `--add-opens`/`--add-exports` to the surefire `<argLine>` and Karaf
  `config.properties`; provision a JDK-21 CI image; **boot the distribution on JDK
  21** (pax-web 8 / Jetty 9.4 validation begins here). **Gate:** full units + ITs +
  functional green on **Java 21** — a shippable checkpoint.

## Phase 4 — Java 25

- [ ] **Step 12.** Flip `<release>` to 25 and `requireJavaVersion` to 25; confirm
  the `jre-25` profile is present in Mobi's `jre.properties`; resolve any remaining
  module-system opens; **boot the distribution on JDK 25 — the load-bearing
  pax-web 8 / Jetty 9.4-on-25 gate**; final library validation (Netty 4.1.119
  `Unsafe` warnings per JEP 471/498 — warnings, not errors; RDF4J/OWLAPI; the
  rdf-orm-maven-plugin/codemodel 2.6 under JDK 25; the SonarQube scanner);
  provision the JDK-25 CI image (replacing `mobi-ci-container:feature-java-17`).
  **Gate:** full units + ITs + functional green on **Java 25** — the acceptance
  contract.

## Out of scope

- **The `javax`→`jakarta` migration and Karaf 4.5 / pax-web 10** — decoupled from
  Java 25 by the Step-1 spike
  ([ADR-0004](../../docs/adr/0004-karaf-jakarta-coupling-for-java-25.md)); gated on
  Karaf 4.5.0 GA; belongs to a separate future plan. The EE surface (~110 files,
  the OSGi HTTP-whiteboard rewrite, CXF 3→4) is not touched here.
- Migrating off OSGi/Karaf.
- The Angular SPA framework/Node upgrade — JDK-independent; bump Node only if a
  toolchain step needs it.
- **Lifting the ehcache lock (3.3.1 → 3.10).** Decoupled from Java 25 by the Step-2
  spike — the locked 3.3.1 rides the upgrade unchanged. The lift is a real but
  optional OSGi-modernization (plain bundle → Declarative Services: `scr` feature
  dependency, JAXB bundles, a `SimpleCacheManager` rework, the one-file
  `Expirations`→`ExpiryPolicyBuilder` compile fix) and belongs to its own follow-on.

## Where this lives

- `mobi-parent/pom.xml` — `<release>` (`:643`), enforcer (`:790-792`), Karaf
  (`:73`), and the JDK-sensitive plugin/library versions; the single source of
  truth for the build.
- `mobi-distribution/src/main/resources/etc/jre.properties` — the JavaSE
  execution-environment profiles to sync from Karaf 4.4.11 (`:517-525`).
- `mobi-karaf-feature/src/main/feature/feature.xml` — ehcache and Netty runtime
  bundles.
- `mobi-itests/` — the Pax-Exam + Nightwatch gates that validate the runtime on
  the new JDK.
- Serves: [ADR-0004](../../docs/adr/0004-karaf-jakarta-coupling-for-java-25.md);
  depends on [ADR-0003](../../docs/adr/0003-rdf4j-4-to-5-upgrade.md).
