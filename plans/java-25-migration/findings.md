# Java 25 Migration — findings (the measured record)

What was actually measured, what surprised, and what got refuted. Grounded in real
runs on a box with JDK 17.0.19 / 21 / 25 installed. Calibrated-honesty register:
proven means measured; a refuted hypothesis is recorded *as refuted*, not erased.

## F-1 — Mobi's code + full unit suite pass on Java 25, deps unchanged ✅

`mvn clean install` with `<release>25</release>` on JDK 25: **96 SUCCESS / 1 FAILURE
/ 10 SKIPPED**, test count **3270 / 0 / 0 / 15 — identical to the Java-17 baseline**.
The only failure is `com.mobi.web`'s `npm run tests` (no headless Chrome here — an
environment gap, not a code failure). The same toolchain reverted to `<release>17`
also stays 3270/0/0/15 on JDK 17 (no regression). **RDF4J 4.3.15, OWLAPI 5.5.0, and
ehcache 3.3.1 build and unit-test clean on JDK 25 with no version change** — so the
plan's Step 8 (RDF4J 5) and Step 7 (ehcache) are **not required for the JDK move**.

## F-2 — Every JDK-25 *build* blocker was an old bytecode-reading build tool ✅

Found by flipping `<release>` to 25 and measuring what broke, one at a time. None
were Mobi's code or its runtime deps:

| Blocker | Symptom | Fix |
|---|---|---|
| maven-bundle-plugin's embedded **bndlib 5.1.1** | can't parse class-file v69 | override embedded bndlib → **7.3.0** + `<_noimportjava>true>` (one-file change, no module edits) |
| **ASM** inside maven-plugin-plugin 3.6.4 | `@Mojo` descriptor scan fails on v69 | maven-plugin-plugin → **3.15.1** + **ASM 9.10.1** override |
| **Groovy 3.0** (Spock specs) | `Unsupported class file major version 69` | Groovy → **4.0.32**, Spock → **2.4-groovy-4.0**, gmavenplus → **3.0.2** (`testCompile`→`compileTests`), JUnit → **5.14.1** |
| Mockito 4.6.1 | no Java 21+ support | Mockito → **5.23.0** (`mockito-inline`→`mockito-core`, `initMocks`→`openMocks`) |

Directly verified: `bndlib 7.3.0` parses a real JDK-25 class → reports
`format=Java_25`. The 12 Spock specs compile + pass on Groovy 4 with no spec-code
changes.

## F-3 — Old Karaf 4.4.3 fails to boot on a modern JDK ❌ (the runtime problem)

Booting the assembled distribution on **Karaf 4.4.3 + JDK 17.0.19** (a late-2026 17
patch with hardened ZIP validation): **108 ERROR lines, 21 DS services dead**
("Service factory returned null"), rooted in `AbstractSettingService.<clinit>` NPE —
`getResourceAsStream(".rq")` returning null — plus a pax-logging "Invalid CEN
header… Unable to extract embedded JAR."

**Correction (2026-06-24, F-8):** this F-3 entry conflated *two independent*
defects under one "Felix×JDK" banner, and its conclusion that the **Karaf version**
fixes them was premature (it was never log-verified — see F-4/F-5). With logging
restored, the two separate:
- **Defect A — the `Invalid CEN header (tag 0xbfef)` / "Unable to extract embedded
  JAR".** A *single* event, root-caused (F-8) to one embedded jar
  (`org.apache.aries.javax.jax.rs-api-1.0.1` → `lib/geronimo-osgi-locator.jar`)
  whose ZIP central directory carries a non-standard extra field the hardened JDK
  rejects. **Fixed by rezipping that one jar** (F-8) — not by the Karaf bump.
- **Defect B — `getResourceAsStream("/*.rq")` returns null in `<clinit>`.** The real
  cascade driver (21 dead services). **Independent of Defect A** (still present
  after A is fixed) and **not** a JDK-ZIP failure: all 310 distribution jars open
  cleanly with JDK 17.0.19's `ZipFile`, and the `.rq` loads fine via a plain
  `URLClassLoader` over the same jar. It is a Felix bundle-classloader
  resource-resolution failure in the OSGi runtime — still **open** (F-8).

**Refuted hypothesis:** `-Djdk.util.zip.disableZip64ExtraFieldValidation=true` does
**not** fix it (error counts unchanged) — correct, because that flag only governs
the zip64 tag (0x0001), not the 0xbfef extra field of Defect A.

## F-4 — Karaf 4.4.11 fixes the boot cascade ❌ REFUTED (was a false positive)

The original F-4 claim — "the ERROR cascade is gone on 4.4.11" — rested entirely on
an **empty log** (F-5: 4.4.11 file logging was broken) plus `/mobi`=200 and
`/mobirest/users`=200. Both are necessary-not-sufficient: the SPA (pax-web) and
`UserRest` come up *early* and do **not** depend on the failing services. Once
logging was repaired (F-7) and the boot re-read, **the cascade is NOT gone** (F-8):
on Karaf 4.4.11 + JDK 17.0.19 the boot still produces **182 ERROR lines and 33 dead
DS services** — the *same* `AbstractSettingService`/`BaseVersioningService` `.rq`
`<clinit>` family as 4.4.3. The Karaf bump silenced the log, not the bug.

**Confirmed by live probe (F-8), real `@Path` bases this time:** `/mobi`=200,
`/mobirest/users`=401 (alive), but `/mobirest/{catalogs,ontologies,provenance-data,
sparql,shapes-graphs,workflows}`=**404** — the previously-failing services are
**confirmed dead**, not "wrong path." The Karaf 4.4.11 bump remains a wanted
maintenance step, but it does **not** by itself make the distribution boot.

## F-5 — Karaf 4.4.11 broke Mobi's file logging ✅ FIXED (root cause was one renamed property)

On 4.4.11, `karaf.log` contained only the **10 bootstrap lines** (vs 23,716 on
4.4.3) — even with `rootLogger.level = INFO`. The cause was **not** the log level and
**not** a missing appender block: it was a single renamed property. pax-logging
2.3.3 (shipped in Karaf 4.4.11) expects the console-pattern variable to be
`log4j2.outpattern`; Mobi's config (written for 4.4.3's pax-logging) defined and
referenced `log4j2.out.pattern`. In Log4j2's **properties** config format a dotted
key declares component hierarchy, so `out.pattern` made the parser try to build a
phantom `out` component and **abort the whole configuration build** — leaving the
RollingFile appender uninitialized while the JUL bootstrap lines still flushed.
Apache renamed the variable in the stock template for exactly this reason.

**Fix (F-7):** synced `mobi-distribution/.../etc/org.ops4j.pax.logging.cfg` from
stock Karaf 4.4.11 — `log4j2.out.pattern` → `log4j2.outpattern`, `rootLogger.level`
back to stock `INFO` — preserving Mobi's four custom loggers (hazelcast, karaf, rest,
utils). Verified: reboot grew `karaf.log` from 10 → **29,009 lines**. `bin/client`
(the SSH-shell alternative) still hangs, so the file log is the boot-readout channel.

## F-6 — The whole upgrade is much smaller than the original plan feared

Two assumed-large efforts were refuted as **decoupled from Java 25**: the
`javax`→`jakarta` migration (ADR-0004 — Karaf's `javax` 4.4.x line runs on modern
JDKs) and the ehcache lock-lift (F-1 — 3.3.1 works on 25 unchanged). What remains is
a Karaf maintenance bump + config syncs + boot validation — not a code migration.

**Amended (F-8):** the *build* is small, but the **boot/runtime is a real, open
problem** (Defect B), not just "validation." F-6 holds for everything except the
backend-service boot.

## F-7 — The pax-logging sync fixed file logging ✅ (verified)

Did the F-5 fix and verified it on the live extract (Karaf 4.4.11 + JDK 17.0.19):
synced `org.ops4j.pax.logging.cfg` from stock 4.4.11 (`log4j2.out.pattern` →
`log4j2.outpattern`), rebooted, and `karaf.log` went **10 → 29,009 lines**. This is a
committed source change (`mobi-distribution/src/main/resources/etc/`), and it is the
enabling step: it made the boot readable, which is what exposed F-8.

## F-8 — With logs restored, the 4.4.11 boot is NOT clean — two independent defects 🟡/❌

Reading the now-populated log (and reproducing each mechanism directly) refutes F-4
and splits F-3's "Felix×JDK" banner into two unrelated defects:

- **Defect A — embedded-jar `Invalid CEN header (tag 0xbfef)`. ✅ root-caused & fixed.**
  A full scan of all 310 distribution jars + their 62 embedded jars with JDK
  17.0.19's `ZipFile` found **exactly one** rejected jar:
  `org.apache.aries.javax.jax.rs-api-1.0.1` embeds `lib/geronimo-osgi-locator.jar`,
  whose central directory carries a non-standard extra field (tag `0xbfef`) the
  hardened JDK rejects → Felix `JarContent.<init>` throws during the servicemix-specs
  SPI scan. **Rezipping that one embedded jar** (strip the extra field; manifest +
  `Bundle-ClassPath: .,lib/geronimo-osgi-locator.jar` preserved) and rebooting
  eliminated the CEN error (1 → 0) and recovered ~12 services (distinct dead
  **33 → 21**). *(The fix was applied to the live extract; the repo-level fix is TBD —
  rezip at build, swap the artifact, or upgrade the aries bundle.)*
- **Defect B — `getResourceAsStream("/*.rq")` returns null in `<clinit>`. ✅ ROOT-CAUSED
  & FIXED — it was Step 5's `_noimportjava`.** `AbstractSettingService.<clinit>` (`:81`)
  and `BaseVersioningService.<clinit>` (`:124`) do
  `Objects.requireNonNull(getResourceAsStream("/x.rq"))`; the stream is null → NPE →
  `NoClassDefFoundError` cascades to **21 services** (setting ×3, prov, catalog
  versioning, and the ontology/shapes/workflows record-versioning + REST layers that
  extend `BaseVersioningService`). Pinned down by progressive isolation, all on Karaf
  4.4.11 + JDK 17.0.19:
  1. *Not the jar/JDK/packaging.* The `.rq` is at the jar root; the jar opens cleanly
     with JDK 17.0.19's `ZipFile`; the resource loads non-null via a plain
     `URLClassLoader`; a diagnostic OSGi bundle showed setting.api's **own classloader
     returns the resource non-null once the bundle is settled/Active** — so the
     failure is a *transient* during the boot/activation window, made permanent
     because it happens in a `static{}` initializer (clinit runs once;
     `ExceptionInInitializerError` is cached).
  2. *It is the build, not the runtime.* Rebuilding **setting.api with the original
     bndlib 5.1.1** (override removed) and cold-rebooting → setting clinit NPE **0**,
     while catalog.api (still 7.3.0) kept failing — a clean A/B with the runtime held
     constant.
  3. *The exact trigger is `_noimportjava`.* The 7.3.0 vs 5.1.1 jars are
     **byte-identical at the ZIP level** (46 entries, all deflate, same `0xcafe`
     extra-field, same order); the **only** functional manifest difference is a
     dropped **substitutable self-import** — 5.1.1 emits
     `Import-Package: …,com.mobi.setting.api.ontologies.setting` (a package it also
     exports), 7.3.0 + `_noimportjava` omits it. Rebuilding with bndlib 7.3.0 **without**
     `_noimportjava` restores the self-import (and re-adds the harmless `java.*`
     imports), and cold-rebooting → setting clinit NPE **0**. So `_noimportjava` —
     added in Step 5 believing it only suppressed `java.*` — *also* strips the
     self-import, and without it Felix's bundle classloader returns null for root
     resources during the activation transient.
  - **Fix:** drop `_noimportjava` from the global maven-bundle-plugin config in
    `mobi-parent/pom.xml`, keeping the bndlib-7.3.0 override. Importing `java.*` is
    harmless (the system bundle provides it via boot delegation).
  - **Corrects Step 5:** its claim that the bndlib bump left manifests "semantically
    identical (normalized diff empty)" was wrong — the normalization masked the
    dropped self-import. The `_noimportjava` knob was not behavior-neutral.
  - Also answers the open question in the prior revision: Defect B is **migration-
    induced** (a Step-5 build-config side effect), not a pre-existing or JDK-25 fault.
  - **VALIDATED end-to-end (2026-06-24).** Full `mvn clean install -DskipTests` rebuild
    (BUILD SUCCESS, 9:40) with `_noimportjava` removed, then a clean cold boot of the
    reassembled distribution on Karaf 4.4.11 + JDK 17.0.19: **setting & versioning
    `<clinit>` NPEs = 0, `Service factory returned null` = 0** (was 70), ERROR lines
    **182 → 7**, and **89/90 `com.mobi` bundles Active**. The previously-404 REST
    endpoints now respond registered: `/mobirest/{catalogs,provenance-data,workflows,
    settings,users}` → 401, `/{ontologies,shapes-graphs}` → 405. The one non-Active
    bundle, `com.mobi.utils.cli`, is **Waiting by design** — its restore/migration
    operations need `@Reference(target="(id=systemTemp)")`, a temp repository that
    only exists mid-restore (`InversioningMigration.java:245`), so it is Waiting in any
    normal boot, not a regression.

## F-9 — Defect A (CEN embedded jar) is non-fatal to the backend but still noisy 🟡

With Defect B fixed, the boot still logs the single `Invalid CEN header (tag 0xbfef)`
from `org.apache.aries.javax.jax.rs-api-1.0.1` → `lib/geronimo-osgi-locator.jar`
(F-8 Defect A), and it now surfaces as a `NoClassDefFoundError:
org/apache/logging/log4j/simple/internal/SimpleProvider` in the `log4j.api` bundle's
activator plus one "Error installing boot features" — the geronimo SPI locator
failing to extract breaks the log4j2-API provider lookup. **Non-fatal:** logging works
via pax-logging, all 89 backend bundles are Active, and Mobi uses slf4j (not the
log4j2 API directly), so the degraded log4j2-API shim doesn't block functionality.
But it must be fixed before the JDK-25 boot (JDK 25's ZIP validation is at least as
strict). The live fix (rezip the embedded jar) is proven (F-8); the **repo-level fix
is still open** — options: a build-time rezip of the embedded jar in the staged aries
bundle, bump/replace the aries jax-rs-api bundle, or pin a clean geronimo-osgi-locator.
No documented JVM flag relaxes this CEN extra-field check (the
`disableZip64ExtraFieldValidation` flag covers only tag 0x0001, not 0xbfef).

**FIXED (2026-06-24) — committed build-time rezip.** A `maven-antrun-plugin` step in
`mobi-distribution/pom.xml` (phase `prepare-package`, i.e. after `karaf:assembly` stages
the system repo in `process-resources` and before `karaf:archive` builds the tar.gz/zip
in `package`) rezips the embedded `lib/geronimo-osgi-locator.jar` cleanly inside the
staged aries bundle and rebuilds the outer bundle with `<jar>` preserving its original
MANIFEST (so `Bundle-SymbolicName` and the `osgi.contract=JavaJAXRS` capability are
intact). Validated: full rebuild + cold boot → **CEN errors 0** (embedded jar now opens
under JDK 17.0.19 `ZipFile`, n=17), JavaJAXRS contract preserved, **89/90 com.mobi
bundles Active** (utils.cli Waiting by design), REST endpoints alive, clinit NPE 0. The
aries bundle (not jakarta.ws.rs-api) is kept because it uniquely provides the JavaJAXRS
contract (see Attempt 1). *Gotcha: the antrun plugin's runtime deps aren't all in `.m2`,
so this module no longer builds fully `-o` offline — the assembly build needs network
(or a primed `.m2`) for `maven-antrun-plugin:3.1.0`.*

**Separate, pre-existing non-fatal issue (NOT Defect A):** the vanilla
`org.apache.logging.log4j.api [222]` (2.17.1) bundle's OSGi activator throws
`NoClassDefFoundError: org/apache/logging/log4j/simple/internal/SimpleProvider`, so it
stays Resolved-not-Active and Karaf logs one "Error installing boot features". This was
*wrongly* lumped under Defect A earlier — it persists after the CEN fix, so it is
independent. **Non-fatal:** `log4j-core [223]` and `pax-logging-log4j2 [3]` are Active,
so logging works and the backend is fully up; Mobi uses slf4j. Left as a documented
low-priority cleanup.

**Attempt 1 — swap to `jakarta.ws.rs-api 2.1.6` — FAILED, reverted (2026-06-24).** The
idea: replace the aries spec bundle with `jakarta.ws.rs-api 2.1.6` (same `javax.ws.rs`
2.1 packages, clean OSGi bundle, no embedded jars). Built + booted: the CEN error and
the log4j.api activator failure **did disappear** (CEN count 0), confirming the
embedded jar was the only ZIP problem — **but the boot failed to provision** (104 log
lines, "Error installing boot features"). Resolver root cause:
`org.osgi.service.jaxrs/1.0.0` (and the aries `jax.rs.whiteboard`) require
`osgi.contract=JavaJAXRS;version=2.1.0`, which the **aries spec bundle provides**
(`Provide-Capability: osgi.contract;osgi.contract=JavaJAXRS;uses:="javax.ws.rs,…"`)
and **`jakarta.ws.rs-api 2.1.6` does not** (zero `Provide-Capability` headers). A clean
swap must therefore *also* supply the JavaJAXRS contract — via a fragment attached to
`jakarta.ws.rs-api`, or `wrap:` adding the capability. Reverted to the aries bundle to
keep the validated 89/90 boot. **Refined options for Defect A:** (a) keep the aries
bundle but strip/rezip its embedded `geronimo-osgi-locator.jar` at build time
(preserves the contract, surgical); (b) `jakarta.ws.rs-api` + a small fragment bundle
providing `osgi.contract=JavaJAXRS`; or (c) accept it as documented non-fatal noise and
proceed — it does not block the backend, and may warrant revisiting only as part of the
deferred `javax`→`jakarta` migration. The geronimo SPI locator it embeds was failing to
load anyway, so nothing functional depends on it.

## F-11 — The Pax-Exam IT gate IS runnable in this sandbox; it surfaced a chain of pre-existing version skews 🟡

**Correcting the earlier "ITs are CI-tier" assumption (F-10 etc.):** the 5 Pax-Exam IT
modules (`itests-{etl,orm,platform,rest,vfs}`) only need the assembled distribution and
a JVM — **no browser** (only `itests-web`/Nightwatch needs one). They run here. Running
`itests-rest` on JDK 25 against the v69 distribution booted real Karaf containers and
executed tests, surfacing latent dependency skews that had never been exercised in this
sandbox (the ITs were "disabled by default"):

1. **commons-io 2.11.0 (test) vs commons-compress 1.27.1 → `NoSuchMethodError
   BoundedInputStream.builder()`.** `mobi-itests/pom.xml` pinned commons-io 2.11.0
   (pre-`builder()`); commons-compress 1.27.1 (pulled for the gzip/tar path) calls it.
   **Fixed:** aligned to `${commons-io.version}` (2.18.0).
2. **commons-lang3 3.12.0 vs commons-compress 1.27.1 → `NoClassDefFoundError
   commons.lang3.SystemProperties`** in pax-exam's `ArchiveExtractor` (unpacking the
   distribution tar.gz needs commons-lang3 ≥3.13). **Fixed:** added a test-scoped
   commons-lang3 3.18.0 to `mobi-itests/pom.xml`.
   *(Both are test-classpath only, in `mobi-itests`; the distribution/runtime is
   unaffected. Pre-existing — commons-compress 1.27.1 outpaced its companions.)*
3. **log4j-api 2.17.1 vs pax-logging 2.3.3 (Karaf 4.4.11) → `NoClassDefFoundError
   log4j.simple.internal.SimpleProvider` → no `BootFinished` → ITs time out.** This one
   is **migration-induced and a real runtime issue** (see F-9 correction below), not
   test-infra. **OPEN.**

After fixes 1+2 the container boots and tests run (30-52 s each) but every test errors
with `gave up waiting for service org.apache.karaf.features.BootFinished` — blocked by
issue 3.

## F-9 correction — the `log4j.api` `SimpleProvider` error is NOT benign; it blocks `BootFinished`

F-9 called the `log4j.api` activator `NoClassDefFoundError: SimpleProvider` "pre-existing
non-fatal." The IT gate (F-11) disproves "non-fatal": it aborts boot-feature install, so
Karaf never publishes the `BootFinished` service. My manual boots (F-10) only checked
SPA 200 / REST — which come up regardless — and never verified `BootFinished`, so the
incompleteness went unseen. Mechanism: Mobi's `feature.xml:190-191` pins **log4j-api/core
2.17.1** (Dec 2021), which has **no** `org.apache.logging.log4j.simple.internal.SimpleProvider`
(a newer-log4j class); Karaf 4.4.11's **pax-logging 2.3.3** expects a newer log4j-api, so
the provider lookup hits a class absent from 2.17.1. **Migration-induced** (the Karaf bump
brought pax-logging 2.3.3). **Fix direction (unverified):** bump Mobi's standalone
log4j-api/core to the version pax-logging 2.3.3 aligns with (likely 2.24.x), or drop the
standalone log4j bundles if pax-logging already provides the API — then re-run the IT
gate. This needs a rebuild + IT-rerun cycle and may surface further skews.

## F-11 follow-through — log4j fix landed; Pax-Exam itests-rest now 7/8 green on JDK 25

Bumping the standalone log4j-api/core 2.17.1 → **2.25.4** (matching pax-logging-api
2.3.3's exported version, `feature.xml`) fixed the `SimpleProvider` NCDFE: the container
now reaches `BootFinished` and runs the REST ITs. `itests-rest` on JDK 25:
**CatalogRestIT, BasicAuthRestIT, DatasetRestIT, OntologyRestIT all GREEN** (7 tests);
only `UserRestIT.testDeleteUser` errors — see F-12.

**All 5 Pax-Exam modules run on JDK 25 (2026-06-26, committed state, unpatched shell) —
the failure line is crisp:**

| Module | Result on JDK 25 | Note |
|---|---|---|
| `itests-orm` (SourceGeneratorIT, OrmIT) | ✅ green | |
| `itests-platform` (BalanaIT, PlatformIT) | ✅ green | XACML/Balana + platform |
| `itests-vfs` (BasicVfsIT) | ✅ green | |
| `itests-rest` | ✅ REST green | only `UserRestIT` (shell) fails — F-12 |
| `itests-etl` (EtlIT, ImportIT) | ❌ fail | `setup` → `executeCommand` → F-12 |

**Every IT failure on JDK 25 is F-12** (`getSubject is not supported`, reached only via
`KarafTestSupport.executeCommand`). Every IT that does **not** use the Karaf shell
**passes on JDK 25**. So Mobi's REST / ORM / platform / VFS / backend layers are
JDK-25-clean; the sole runtime gap is the Karaf shell/CLI (F-12). This both confirms the
JDK-25 readiness of the application and isolates the one upstream blocker precisely.

## F-12 — Karaf 4.4.11's shell/CLI is broken on JDK 24+ (`Subject.getSubject` / JEP 486) ❌ hard upstream limit

The last IT failure (`UserRestIT`) is **not** a Mobi or test-infra bug — it's a genuine
JDK-25 incompatibility in Karaf's shell. `UserRestIT.setup` runs a Karaf shell command
(`KarafTestSupport.executeCommand`), which creates a shell session →
`org.apache.karaf.shell.support.ShellUtil.getCurrentUserName` →
`javax.security.auth.Subject.getSubject(AccessControlContext)`. **JDK 24+ (JEP 486,
Security Manager permanently disabled) makes `Subject.getSubject` throw
`UnsupportedOperationException` unconditionally.** Verified directly on JDK 25.0.3:
`getSubject` throws; `Subject.current()` (the replacement) works; and
`-Djava.security.manager=allow` **fails to even start the JVM** ("Enabling a Security
Manager is not supported"). The 4.4.11 `shell.core` bytecode confirms it still calls
`Subject.getSubject`.

**Scope:** affects the Karaf **shell / console / CLI**, not REST/boot. This also explains
two earlier sandbox mysteries: `bin/client` "hangs" and the Gogo SSH console was flaky/
empty **on JDK 25** (it worked on JDK 17) — all the same `Subject.getSubject` failure
during shell-session creation. **Mobi impact:** `com.mobi.utils.cli` (backup / restore /
migration) runs as Karaf shell commands, so those admin/ops paths are broken on JDK 25
with Karaf 4.4.11. The REST API and the full backend service layer are unaffected
(F-10/F-11: SPA + 7/8 REST ITs green).

**No in-tree fix available:**
- No JVM flag mitigates it (JEP 486 is final; `Subject.current()` is the only path).
- **No newer Karaf 4.4.x exists** (4.4.12–4.4.15 not found in Maven Central as of
  2026-06; 4.4.11 is latest) and **Karaf 4.5.0 (jakarta line) is not GA** — so neither a
  maintenance bump nor the jakarta line can fix it today.
- The real fix is upstream: Karaf's shell must use `Subject.current()` instead of
  `Subject.getSubject()`. Options for Mobi: (a) wait for a Karaf 4.4.x patch / 4.5.x GA
  that fixes the shell; (b) carry a patched fork (heavy); (c) accept the shell/CLI
  limitation on JDK 25 and run `utils.cli` operations on a JDK ≤21 until upstream lands.

**Carry-patch validated AND shown impractical (2026-06-26).** To de-risk option (b) I
patched `ShellUtil.getCurrentUserName` to prefer `Subject.current()` (reflectively, so
the one class still runs on JDK 17 where `current()` is absent — falls back to
`getSubject()`), compiled it `--release 17`, injected it into shell.core, rebuilt, and
re-ran `itests-rest`. Result: the patch **works** — that call site's error is gone — but
the failure simply **moves to the next `getSubject` call site**:
`org.apache.karaf.shell.impl.console.osgi.secured.SecuredSessionFactoryImpl.currentUserHasRole`
(shell.console). **Correction (2026-06-26): the surface is wider than "4 sites in 2
bundles" — that count was from scanning only the shell bundles.** A full bytecode scan of
all booted Karaf bundles for the JEP-486-affected `Subject` APIs finds the break is in
Karaf's whole **JAAS-integration layer**, not just the shell:
- `Subject.getSubject` (throws on JDK 24+): `org.apache.karaf.util.jaas.JaasHelper` —
  a shared util **replicated into 6 bundles** (`bundle.core`, `jaas.command`,
  `management.server`, `shell.console`, `shell.core`, `shell.ssh`) — plus
  `shell.support.ShellUtil` (shell.core), `shell.util.ShellUtil` (shell.console),
  `SecuredSessionFactoryImpl` (shell.core), `SecuredCommandProcessorImpl` (shell.console),
  `jaas.command.WhoamiCommand`, and `management.internal.BulkRequestContext`.
- `Subject.doAs` (also throws on JDK 24+): `jaas.modules.ldap.GSSAPILdapLoginModule`.

So it is **~8 distinct classes across 7 bundles** (JaasHelper replicated 6×), and it
breaks not only the shell/console/CLI but also **JMX management auth** (`management.server`)
and the **`jaas:*` commands**. Option (b) therefore means forking Karaf's JAAS layer
across 7 bundles — real, ongoing maintenance debt, re-verified each Karaf bump. **Conclusion:
option (b) is not worth it; the realistic paths are (a) the upstream fix
(`Subject.current()`/`callAs`) or (c) JDK ≤21 for the shell/JMX/CLI paths while REST runs
on 25.** The `.m2` injection was reverted (validation only); nothing patched is committed. *(Aside: in that run `CatalogRestIT.testUpdateInProgressCommit`
flaked `201 vs 400` — it passed in the prior run and is unrelated to the shell; the
REST IT suite has some order/timing-dependent state. Needs a rerun to confirm, not a
known regression.)*

**Qualifies [ADR-0004]:** the javax Karaf 4.4.x line *boots and serves REST* on JDK 25
(ADR-0004's load-bearing claim holds), **but its shell/CLI does not** — a residual
JDK-25 gap the ADR didn't anticipate. The staged Java-**21** checkpoint is unaffected
(`Subject.getSubject` still works on 21), so the 21 milestone remains fully green; the
shell gap is specifically a JDK-24+ problem.

## F-10 — The distribution BOOTS and serves on JDK 25 ✅ (the ADR-0004 load-bearing test)

With Defects A + B fixed and `jre.properties` synced (jre-19…jre-26 added,
`jre-25 = ${jre-24}, javax.sound`), the assembled distribution was booted on **JDK
25.0.3** (Karaf 4.4.11). Result — **it boots and serves**, with health equivalent to
the JDK-17 boot:
- SPA `/mobi/index.html` → 200; REST `/mobirest/{catalogs,ontologies,workflows}` →
  401/405 (registered + alive), same as JDK 17.
- `clinit` NPEs **0**, `Service factory returned null` **1** (the `ActionFactory`
  boot transient, recovered — its bundle is Active), ERROR lines **8** — the *same*
  non-fatal set as the JDK-17 boot (log4j.api `SimpleProvider` activator,
  `SimpleWorkflowManager` configProvider-null ordering log, one blueprint line).
- **No JDK-25-specific failures: 0 `UnsupportedClassVersionError`, 0
  `InaccessibleObjectException`/module-access errors.** pax-web 8 / Jetty 9.4 / Felix /
  RDF4J / OWLAPI / Netty all come up on JDK 25.

This answers the residual risk ADR-0004 relocated to Step 12: the `javax` pax-web 8 /
Jetty 9.4 stack runs on JDK 25.

**Full end-to-end closed (2026-06-24): v69 bundles boot on JDK 25.** The first pass
ran v52 (`<release>17`) bundles on the JDK-25 runtime. To remove the last gap, the
`<release>` was *temporarily* flipped to 25 (reverted after — the committed flip stays
staged-through-21 for CI per D-3) and:
- **Full `mvn clean install` on JDK 25 (release 25, all fixes):** reached the
  `com.mobi.web` module and failed **only** there on the ChromeHeadless env gap (no
  browser) — i.e. every Java module compiled to **class-file v69 and its unit tests
  passed on JDK 25** (fail-fast would have stopped earlier otherwise; consistent with
  F-1's 3270/0/0/15). The web `ng test` failure is the same documented environment gap,
  not a code/JDK fault.
- **v69 distribution assembled** (`-DskipTests` to skip the web gate) — Mobi bundles
  confirmed major=69 — and **booted on JDK 25.0.3**: SPA 200, REST
  `/mobirest/{catalogs,ontologies,workflows,provenance-data}` 401/405 (alive), clinit
  NPE 0, **0 UnsupportedClassVersion / 0 InaccessibleObject / 0 CEN**, ERROR lines 8
  (same non-fatal set as JDK 17). So Mobi **compiled for Java 25 runs on Java 25.**

The only remaining work is **CI-tier**: the formal staged `<release>` flip 17→21→25
(Java 21 isn't installed in this sandbox) and the Pax-Exam IT / Nightwatch functional
gates (need a CI image + headless browser). No code/runtime unknowns remain.

## Where this lives

- [README.md](README.md) — the status dashboard.
- [implementation-plan.md](implementation-plan.md) — step-by-step plan + wrap-ups.
- [ADR-0004](../../docs/adr/0004-karaf-jakarta-coupling-for-java-25.md).
