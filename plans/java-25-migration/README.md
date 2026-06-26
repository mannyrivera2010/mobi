# Java 25 Migration — status dashboard

The legible, at-a-glance state of moving Mobi from Java 17 to Java 25. This folder
groups the epic: this dashboard, the detailed [implementation-plan.md](implementation-plan.md)
(step-by-step with wrap-ups), and [findings.md](findings.md) (the empirical record —
what was measured, what surprised, what's refuted).

> **One-line status (2026-06-24, updated):** The **build and unit tests are PROVEN
> on Java 25** (Mobi's code + all runtime deps, unchanged). The **runtime/boot is the
> real open problem.** Repairing 4.4.11's broken file logging (F-7) made the boot
> readable — and revealed that the earlier "Karaf 4.4.11 fixes the cascade" claim was
> a **false positive read off a silenced log** (F-4 refuted). With logging restored,
> the real blocker — **Defect B** (`.rq` `getResourceAsStream` returns null in
> `<clinit>`, 21+ dead services) — was traced to **Step 5's `_noimportjava` stripping
> the substitutable self-import** and **fixed by dropping it**. A full-reactor rebuild
> + clean cold boot **validates the fix: 89/90 bundles Active, 0 dead services, ERROR
> 182→7, REST endpoints alive** (F-8). **Defect A** (the embedded jar's bad ZIP extra
> field) is now **fixed too** via a committed build-time rezip that keeps the aries
> bundle's JavaJAXRS contract (F-9) — CEN errors 0. With `jre.properties` synced
> (jre-19…jre-26), **the distribution now BOOTS and serves on JDK 25.0.3** — SPA 200,
> REST alive, 0 `UnsupportedClassVersion`, 0 module-access errors (F-10), the
> load-bearing ADR-0004 test. **And the v69 (release-25) distribution itself boots on
> JDK 25** — temp-flipped `<release>`→25 (reverted): units green on 25 and the v69
> distribution booted clean (F-10). **Mobi compiled-for-25 serves SPA + REST on 25.**
> **Newly found via the now-running IT gate (F-11, correcting "ITs are CI-tier"):** the
> boot does **not** reach Karaf's `BootFinished` — Mobi's pinned log4j-api 2.17.1 clashes
> with Karaf 4.4.11's pax-logging 2.3.3 (`SimpleProvider` NCDFE), aborting boot-feature
> install. **Now fixed** (log4j realigned 2.17.1→2.25.4 to match pax-logging): boot
> reaches `BootFinished` and **`itests-rest` is 7/8 green on JDK 25** (the Pax-Exam IT
> gate runs in-sandbox — also fixed 2 test-classpath skews: commons-io, commons-lang3).
> The one remaining IT failure (F-12) is a **hard upstream limit**: JEP 486 removed
> `Subject.getSubject` in JDK 24+, but Karaf 4.4.11's shell still calls it — so the
> shell/console/CLI (incl. `bin/client`, `utils.cli`) is broken on JDK 25 (REST/boot
> unaffected), and no newer Karaf 4.4.x / 4.5.0-GA exists to fix it. **The JDK-25
> runtime is validated for REST/boot; the Karaf shell needs an upstream fix or JDK ≤21.**

## Progress at a glance

| Area | State | Evidence |
|---|---|---|
| Toolchain bumps (compiler, surefire, bnd, Groovy/Spock, Mockito, ASM) | ✅ done | land green on Java 17 **and** 25 |
| Compile to class-file v69 on JDK 25 | ✅ proven | full reactor, `<release>25</release>` |
| Unit tests on JDK 25 | ✅ proven | **3270 / 0 / 0 / 15** — identical to the Java-17 baseline |
| Runtime deps (RDF4J 4.3.15, OWLAPI 5.5.0, ehcache 3.3.1) on JDK 25 | ✅ proven | build + unit-test clean, **unchanged** |
| Distribution assembles | ✅ proven | needs `-DskipTests` to skip the web Chrome test |
| Boot on old Karaf 4.4.3 + modern JDK 17.0.19 | ❌ broken | 108 errors, 21 dead services — now split into Defect A + Defect B (F-3/F-8) |
| 4.4.11 file logging | ✅ fixed | `log4j2.out.pattern`→`outpattern` sync; `karaf.log` 10→**29,009 lines** (F-7) |
| Boot on **Karaf 4.4.11** (JDK 17) | ✅ functional | after the B fix: **89/90 bundles Active, 0 dead services, ERROR 182→7**; REST endpoints 401/405 (registered). 1 Waiting bundle (`utils.cli`) is by-design (F-8) |
| ↳ Defect B (`.rq` `getResourceAsStream`→null in `<clinit>`) | ✅ fixed & validated | Step 5's `_noimportjava` stripped the substitutable self-import; dropped it, full rebuild + clean boot confirms 0 clinit NPE / 0 dead services (F-8) |
| ↳ Defect A (embedded `geronimo-osgi-locator.jar` bad ZIP extra field) | ✅ fixed (committed) | build-time antrun rezip in `mobi-distribution/pom.xml` keeps the aries bundle (JavaJAXRS contract) + clean embedded jar → CEN errors 0 (F-9). aries→jakarta swap tried & reverted (lost the contract) |
| `log4j.api` 2.17.1 → 2.25.4 (vs pax-logging 2.3.3) | ✅ fixed | realigned to pax-logging's log4j 2.25.4 → `SimpleProvider` NCDFE gone, boot reaches `BootFinished` (F-11) |
| **Pax-Exam IT gate (all 5 modules)** | 🟢 green except shell paths | on JDK 25: orm, platform, vfs fully green; rest green except `UserRestIT`; etl fails only in `executeCommand` setup. **Every failure is F-12** (Karaf shell `getSubject`); all non-shell ITs pass. Fixed to get here: log4j 2.25.4 + 2 test-classpath skews (commons-io, commons-lang3) |
| **Karaf shell/CLI on JDK 24+** (`Subject.getSubject`) | ❌ hard upstream limit | JEP 486: `Subject.getSubject` throws on JDK 24+; Karaf 4.4.11 uses it across its JAAS layer (**~8 classes / 7 bundles** -- shell, JMX, jaas commands) → shell/console/CLI (and `utils.cli`, `bin/client`, gogo) broken on JDK 25 (F-12). REST/boot unaffected. Carry-patch validated but **impractical** (forks ~8 classes across 7 Karaf bundles); needs upstream `Subject.current()` or JDK ≤21 for CLI |
| `jre.properties` sync (jre-19…jre-26) | ✅ done | synced from stock 4.4.11, Mobi customizations preserved; `jre-25 = ${jre-24}, javax.sound` |
| **Boot on JDK 25** | ✅ proven | distribution boots + serves on JDK 25.0.3: SPA 200, REST alive, clinit 0, **0 UnsupportedClassVersion / 0 module-access errors** (F-10) — the ADR-0004 load-bearing test |
| **v69 (release 25) build + boot on JDK 25** | ✅ proven | temp `<release>`→25 (reverted): units green on 25 (build reached the web gate), **v69 distribution booted on JDK 25** — Mobi-compiled-for-25 runs on 25 (F-10) |
| Staged `<release>` flip 17→21→25 + IT/functional gates | ⬜ CI-tier | all runtime unknowns retired; the formal flip (Java 21 not in this sandbox) + Pax-Exam ITs + Nightwatch need a CI image + headless browser |
| Staged release flip 17 → 21 → 25 with boot/IT/functional gates | ⬜ not started | needs CI / a fully bootable env |

Legend: ✅ proven · 🟡 partial/uncertain · ❌ confirmed broken (and why) · ⬜ not started

## The headline finding

**Java 25 is not hard in Mobi's code — it's hard in the old runtime plumbing, and
the runtime cost was under-counted.** Mobi's source compiles to v69 and its whole
unit suite passes on JDK 25 with runtime dependencies untouched; *every* build
blocker was an old bytecode-reading build tool (bndlib 5.1.1, ASM, Groovy 3), a
version bump each. The **real, still-open cost is the OSGi/Karaf boot.** The Karaf
4.4.3 → 4.4.11 bump was *thought* to fix the boot cascade, but that was a false
positive read off a log that 4.4.11 had silently broken (F-4 refuted). With logging
repaired (F-7), the boot is still broken and resolves into **two independent
defects**: (A) one embedded jar (`geronimo-osgi-locator.jar`) with a non-standard ZIP
extra field the hardened JDK rejects — *root-caused and fixed by a rezip*; and (B)
`getResourceAsStream("/*.rq")` returning null inside Felix despite a readable jar —
*the real open blocker*, cascading to 21 dead backend services. See
[findings.md](findings.md) F-7/F-8.

## What's open (the work that remains)

1. **Validate the Defect B fix across the full reactor (IN PROGRESS).** The fix
   (drop `_noimportjava` in `mobi-parent/pom.xml`) is proven on setting.api in
   isolation; a full `mvn clean install -DskipTests` rebuild + distribution
   reassembly + cold boot is running to confirm **all 21 services** come Active and
   the REST endpoints return non-404. Re-run the unit suite to confirm the manifest
   change is regression-free (expect the 3270/0/0/15 baseline).
2. **Defect A — land a repo-level fix for the bad embedded jar.** The live rezip
   proved the mechanism; the durable fix is to rezip `geronimo-osgi-locator.jar`
   inside the aries jax-rs-api bundle at assembly time (or upgrade the aries bundle,
   or pin a clean replacement). Re-scan for other embedded jars on each dependency
   bump (only one is bad today). *(Note: Defect A only kills services during the
   servicemix-specs SPI scan; with B fixed, confirm whether A still blocks any
   service on a clean rebuild — it may be lower priority than first thought.)*
3. **Sync `jre.properties`** from Karaf 4.4.11 (adds `jre-19`…`jre-25`; Mobi's stops
   at `jre-18`) — required for the JDK-25 boot.
4. **Boot the distribution on JDK 25** (the ADR-0004 key validation: pax-web 8 /
   Jetty 9.4 on JDK 25) — gated on a clean Java-17 boot first.
5. **Staged release flip** 17 → 21 → 25, each with units + Pax-Exam ITs + functional
   green — needs CI or a fully bootable environment (this sandbox lacks a headless
   browser, which also blocks the SPA Karma tests).

Done since the last revision: ✅ `pax-logging.cfg` sync (F-7, committed to source);
✅ Defect A root-cause + live fix (F-8); ✅ **Defect B root-caused & fixed** — it was
Step 5's `_noimportjava`, now removed from `mobi-parent/pom.xml` (F-8).

## Next concrete action

**Decide how to handle the Karaf-shell-on-JDK-25 limitation (F-12) — confirmed not
fixable in-tree.** A carry-patch was validated and shown impractical: `Subject.getSubject`
spans ~8 classes across 7 Karaf bundles (the JAAS layer: shell + JMX + jaas commands), so patching diverges Mobi from
upstream (whack-a-mole). Realistic choices: **(a)** wait for an upstream Karaf shell fix
(`Subject.current()`) — a 4.4.x patch or 4.5.x GA; or **(c)** accept that admin CLI /
`bin/client` / `utils.cli` run on JDK ≤21 until upstream lands, while the REST runtime is
on JDK 25. Captured as a caveat on [ADR-0004]. Everything else is done: log4j realigned,
the REST runtime + 3/5 IT modules' relevant tests green on JDK 25 (the shell-using IT
paths are the only thing the F-12 limit blocks).

Then the **CI-tier remainder:**
In a CI environment with a JDK-25 image and a headless browser: (1) re-run the unit
suite on the `_noimportjava`-free build to confirm the 3270/0/0/15 baseline holds;
(2) do the staged `<release>` flip — 17→**21** (boot + Pax-Exam ITs + Nightwatch green,
a shippable checkpoint), then 21→**25** (same gates) — flipping `<release>` and the
enforcer `requireJavaVersion` in `mobi-parent/pom.xml` and provisioning the JDK-25 CI
image (replacing `mobi-ci-container:feature-java-17`). The hard runtime unknowns are
already retired here: build-on-25 (F-1/F-2) and boot-on-25 (F-10) both proven, Defects
A + B fixed, `jre.properties` synced. Optional low-priority cleanups: the pre-existing
`log4j.api` `SimpleProvider` activator quirk (F-9) and the `SimpleWorkflowManager`
`configProvider`-null activation-ordering log.

## Environment notes (for whoever resumes)

- **JDKs** installed at `~/.local/jdks/`: `jdk-17.0.19+10`, `jdk-21.*`, `jdk-25.0.3+9`
  (jenv-registered). The repo `.java-version` pins `17.0`.
- **Working tree is uncommitted.** The pom changes (toolchain + Mockito +
  `karaf.version 4.4.11` + bndlib/ASM overrides) and the deleted MockMaker files
  live in the working tree on `master`. `<release>` is reverted to **17** so the
  tree builds on the current JDK.
- **Built distribution:** `mobi-distribution/target/mobi-distribution-4.3.0-SNAPSHOT.tar.gz`
  (Karaf 4.4.11). Scratch extract for boot tests at `/tmp/mobidist411/` — note it now
  has the **synced `pax-logging.cfg`** and the **rezipped `aries jax-rs-api` bundle**
  (Defect A live-fix) swapped in; the original jar is backed up at
  `/tmp/jaxrs-backup.jar`. Boots on JDK 17.0.19 via `bin/start` (run uninterrupted —
  a cold boot after clearing `data/cache` takes a minute; killing it mid-provision
  leaves a frozen log). `data/log/karaf.log` is now the readout channel.
- **Build incantations:** unit build `NODE_OPTIONS=--max_old_space_size=4096 mvn
  clean install`; JDK-25 build flips `<release>` to 25 + `-Denforcer.skip=true`;
  distribution assembly needs `-DskipTests` (skips the web Chrome/Karma test that
  has no browser here).
- **Verification gotchas this sandbox imposes:** no headless Chrome (blocks the web
  module's `npm run tests` and the Nightwatch functional suite); `bin/client` SSH
  shell hangs (so the Felix Gogo console is unreachable that way — file log is the
  readout). Karaf 4.4.11 file logging **was** broken until `pax-logging.cfg` was
  synced — now fixed (F-7).

## Where this lives

- [implementation-plan.md](implementation-plan.md) — the detailed step-by-step plan
  with per-step wrap-ups (the history).
- [findings.md](findings.md) — the measured findings and refuted hypotheses (F-1…F-12).
- [migration-summary.md](migration-summary.md) — consolidated change summary / PR
  description (what changed, validation, how to verify, what's left).
- [upstream-karaf-shell-jdk25.md](upstream-karaf-shell-jdk25.md) — ready-to-file Karaf
  issue for the F-12 shell/`Subject.getSubject` blocker.
- Serves: [ADR-0004](../../docs/adr/0004-karaf-jakarta-coupling-for-java-25.md)
  (Java 25 decoupled from javax→jakarta); relates to
  [ADR-0003](../../docs/adr/0003-rdf4j-4-to-5-upgrade.md).
