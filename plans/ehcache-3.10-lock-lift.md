---
status: complete
priority: P3
category: cache
epic: ehcache-modernization
---

# ehcache 3.3.1 → 3.10 lock-lift (plain bundle → Declarative Services)

Lift the long-standing ehcache version lock. `mobi-parent/pom.xml` pins
`ehcache.version = 3.3.1` (2017) with the comment *"3.10.0 is out, but causes
unsatisfied ehcache services"* — but the java-25 spike (Step 2 / findings.md D-5)
diagnosed that "unsatisfied services" as a **fixable OSGi-wiring mismatch, not a real
incompatibility**: ehcache changed from a plain JSR-107 SPI bundle (3.3) to a
**Declarative Services bundle** (3.10), and Mobi still does the 3.3-era SPI lookup. This
plan does the bundle-model migration so Mobi runs on a maintained ehcache. **Decoupled
from Java 25** — 3.3.1 has no JDK-25 hazard (jar-scanned: 0 `sun.misc.Unsafe`/
`jdk.internal` refs), so it rides that upgrade unchanged; this is standalone
modernization on its own timeline.

> **COMPLETE (2026-06-26).** Lifted to ehcache **3.10.8**. The fix was smaller than
> feared: one API call (`Expirations` → `ExpiryPolicyBuilder`, Step 1) and one
> `SimpleCacheManager` rework (static JSR-107 SPI lookup → `@Reference CachingProvider`,
> Step 3) — **no feature.xml change needed** (Step 2 was a no-op; `scr` + JAXB were
> already provided). Validated by a clean boot (CachingProvider registered as an OSGi
> service, all cache bundles Active, `bundle:diag` clean, 89/90 unchanged) and a green
> `itests-platform`. The "unsatisfied ehcache services" the lock comment warned of was
> exactly the stale static SPI lookup vs ehcache 3.10's DS-published provider.

## Goal

`mobi-parent` pins ehcache **3.10.x**, the reactor compiles and unit-tests green, and
the assembled distribution boots with the JCache/JSR-107 caches **Active** — the
security/policy cache (`PolicyCacheConfiguration`) and any other caches obtained through
`SimpleCacheManager` work, with **no "unsatisfied ehcache services"** in the boot log.
Persistence/security ITs that exercise caching are green.

## Decisions

- **D-1: Upgrade to ehcache 3.10.x; do not stay on 3.3.1.** 3.3.1 is unmaintained and
  the lock is a fixable wiring issue, not a code incompatibility (java-25 Step 2). The
  move is decoupled from Java 25, so it is justified on its own modernization merits.
- **D-2: The root cause is the bundle model, not (mostly) the code API.** ehcache 3.10
  ships `Service-Component: OSGI-INF/*.xml` and `Require-Capability:
  osgi.extender=osgi.component [1.3.0,2.0.0)`, registering `EhcacheCachingProvider` and
  ~30 factories as **DS components** (diagnosed from the bundle manifests in java-25
  Step 2). The fix is to provide the SCR extender (already a boot feature), add the
  bundles 3.10 newly requires, and make `SimpleCacheManager` consume the
  DS-registered provider instead of the 3.3-era static SPI lookup.
- **D-3: One real code-API break to fix.** `org.ehcache.expiry.Expirations` was removed
  in 3.x after 3.3 → replace with `ExpiryPolicyBuilder`
  (`PolicyCacheConfiguration.java:34`, `:84`).
- **D-4: Satisfy ehcache 3.10's non-optional `javax.xml.bind` import.** 3.10 imports
  JAXB (`javax.xml.bind` 2.2+) non-optionally; ensure a JAXB-API (and runtime) bundle is
  present in the feature. This intersects the java-25 `jre.properties` work (the faked
  `javax.xml.bind` export) — verify on the target JDK.

## What's there already (anchoring)

Snapshot 2026-06; re-anchor before resuming.

- **The lock:** `mobi-parent/pom.xml:63` — `<ehcache.version>3.3.1</ehcache.version>`
  with the "unsatisfied ehcache services" comment; `javax.cache.version 1.1.1` (`:68`).
- **Wiring (plain bundle):** `mobi-karaf-feature/src/main/feature/feature.xml:121` —
  `<bundle>mvn:org.ehcache/ehcache/${ehcache.version}</bundle>` (no `scr` dependency
  attached to it).
- **The stale SPI lookup:** `com.mobi.cache.impl.core/src/main/java/com/mobi/cache/impl/core/SimpleCacheManager.java:96`
  — `Caching.getCachingProvider("org.ehcache.jsr107.EhcacheCachingProvider", cl)`. This
  static JSR-107 lookup is what fails when 3.10 registers the provider as a DS component
  instead of a plain SPI service.
- **The removed API:** `com.mobi.security.policy.api/src/main/java/com/mobi/security/policy/api/cache/PolicyCacheConfiguration.java:34`
  (`import org.ehcache.expiry.Expirations`) and `:84` (`.withExpiry(Expirations.noExpiration())`).
- **SCR is already booted:** `mobi-distribution/pom.xml` `bootFeatures` includes `scr`,
  so the `osgi.extender=osgi.component` capability 3.10 needs is available — it just is
  not wired to the ehcache bundle today.
- **No JDK-25 hazard (so this is standalone):** java-25 findings.md F-1 / Step 2 — 3.3.1
  builds + unit-tests clean on JDK 25, and 3.10's only JDK-sensitive surface is the
  `javax.xml.bind` import (D-4).

## Phase 1 — Version bump + code-API fix (compiles on the current JDK)

- [x] **Step 1.** Bump `ehcache.version` 3.3.1 → **3.10.x** (`mobi-parent/pom.xml:63`,
  drop the lock comment). Fix the one compile break: `Expirations.noExpiration()` →
  `ExpiryPolicyBuilder.noExpiration()` in `PolicyCacheConfiguration.java`. **Gate:**
  `com.mobi.cache.*` and `com.mobi.security.policy.*` modules compile and their unit
  tests pass.

  *(Done 2026-06-26. Bumped to **3.10.8** (latest 3.10.x) and changed
  `PolicyCacheConfiguration.java:34/84` from `org.ehcache.expiry.Expirations.noExpiration()`
  to `org.ehcache.config.builders.ExpiryPolicyBuilder.noExpiration()`.* `org.ehcache`
  is imported by **exactly one** main-source file (that one), so the API surface was
  tiny. **Gate GREEN:** `mvn -o clean install -am` over the 8 cache + security-policy
  modules → **BUILD SUCCESS**, 0 failures/errors (e.g. `BalanaPolicyManagerTest` 36/0,
  `security.policy.rest` 35/0, `cache.impl.repository` 7/0, `PolicyCacheImplTest` 1/0,
  `MobiPIPTest` 7/0, `BalanaPDPTest` 7/0). **Surprises:** (1) `Expirations` and the
  `withExpiry(Expiry)` overload both still exist in 3.10.8 — the original line may have
  compiled unchanged; the move to the non-deprecated `ExpiryPolicyBuilder` is correct
  regardless. (2) Two false-start build failures, neither ehcache-related: `-pl` without
  `-am` fails on rdf-orm-codegen modules (the plugin's foreign import needs
  `persistence.api` in-reactor), and a non-`clean` build tripped
  `UnsupportedClassVersionError` on **stale v69 test classes** left in `target/` from the
  earlier JDK-25 full builds — `-am` + `clean` fixed both. Note for later: a full
  verification build must be `clean` to purge those stale v69 artifacts reactor-wide.)*

## Phase 2 — OSGi runtime wiring (the real lock-lift)

- [x] **Step 2.** Make the feature satisfy ehcache 3.10's requirements: attach the SCR
  extender so the ehcache DS components activate, and add the JAXB API (+ runtime)
  bundle(s) for 3.10's non-optional `javax.xml.bind` import (`feature.xml`). **Gate:**
  the distribution assembles and the ehcache bundle resolves (no unresolved
  `osgi.extender` / `javax.xml.bind` requirement at boot).

  *(Done 2026-06-26 — **no feature.xml change was needed**. Investigated ehcache 3.10.8's
  manifest: it needs `osgi.extender=osgi.component` (SCR) and a non-optional
  `javax.xml.bind [2.2,3)`. Both are **already provided**: `scr` is a global
  `bootFeature` (`mobi-distribution/pom.xml`), so it processes the DS components of any
  installed bundle including `org.ehcache`; and the `mobi-jaxb` feature already ships
  `jakarta.xml.bind-api 2.3.3` + `jaxb-osgi 2.3.3` (exports `javax.xml.bind` 2.3 ∈
  `[2.2,3)`), with the servicemix jaxb-api-2.2 specs as a fallback. **Gate GREEN by
  boot:** the `org.ehcache` bundle and all `com.mobi.cache.*` bundles are **Active**, and
  ehcache's `javax.cache.spi.CachingProvider` is **registered as an OSGi service**
  (`Provided by: Ehcache 3 (259)`) — see Step 4. So the only real change is the
  `SimpleCacheManager` rework in Step 3.)*
- [x] **Step 3.** Rework `SimpleCacheManager` to obtain the **DS-registered**
  `EhcacheCachingProvider` (e.g. an `@Reference` to the JCache `CachingProvider`/
  `CacheManager` service the ehcache DS bundle publishes) instead of the static
  `Caching.getCachingProvider(fqcn, cl)` SPI call. **Gate:** `com.mobi.cache.impl.core`
  unit tests green; `SimpleCacheManager` returns a working `CacheManager`.

  *(Done 2026-06-26. Replaced the static `Caching.getCachingProvider(
  "org.ehcache.jsr107.EhcacheCachingProvider", classloader)` lookup
  (`SimpleCacheManager.java:96`) with field injection `@Reference CachingProvider
  provider;`, dropped the now-unused `import javax.cache.Caching`, and removed
  `provider.close()` from `@Deactivate` (the provider is now an injected OSGi service
  owned by the ehcache bundle, not created here). **Root cause confirmed:** ehcache 3.10
  publishes `javax.cache.spi.CachingProvider` as an **OSGi service** via DS and dropped
  the `osgi.serviceloader` capability that `spifly` used to bridge for 3.3.1 — so the
  static JSR-107 ServiceLoader lookup no longer resolved under OSGi (the "unsatisfied
  ehcache services" the lock comment described). `com.mobi.cache.impl.core` has **no unit
  test** for this class, so verification is by compile + boot (Step 4). **Gate GREEN:**
  full `mvn clean install -DskipTests` → BUILD SUCCESS; bnd generated the DS reference;
  at boot the component's `provider` reference is satisfied (cache bundle Active,
  `bundle:diag` clean — Step 4).)*

## Phase 3 — Boot / integration validation

- [x] **Step 4.** Boot the assembled distribution and confirm the cache layer is live:
  the security/policy cache and any JCache caches are created and usable, with **no
  "Service factory returned null" / unsatisfied ehcache services** in `karaf.log`.
  **Gate:** the security-policy and any cache-exercising Pax-Exam ITs are green.

  *(Done 2026-06-26 on Karaf 4.4.11 + JDK 17. Booted the reassembled distribution
  (ehcache 3.10.8): **0 ehcache-related errors**, no "unsatisfied ehcache services", no
  cache/setting/policy "Service factory returned null". Via Gogo: all `com.mobi.cache.*`
  + `org.ehcache` bundles **Active**; `service:list javax.cache.spi.CachingProvider` →
  registered by `EhcacheCachingProvider` (Provided by `Ehcache 3 (259)`); `bundle:diag`
  on `cache.impl.core` and `security.policy.impl.core` printed nothing (no unsatisfied
  refs); **89/90 com.mobi bundles Active** (the 1 Waiting is the by-design `utils.cli`,
  unchanged — no regression). **IT gate GREEN:** `itests-platform` (XACML/policy layer,
  which uses the policy cache) on the ehcache-3.10 distribution — **BalanaIT 1/0,
  PlatformIT 2/0**, 0 ehcache mentions. The lock is lifted.)*

## Out of scope

- The **Java 25 move** — decoupled (3.3.1 rides it unchanged); see
  [java-25-migration](java-25-migration/README.md).
- ehcache **clustering / Terracotta** and off-heap/disk tiers.
- Migrating **off** JCache/JSR-107 to ehcache's native API.

## Where this lives

- `mobi-parent/pom.xml` — `ehcache.version` (`:63`), `javax.cache.version` (`:68`).
- `mobi-karaf-feature/src/main/feature/feature.xml` — the ehcache bundle (`:121`) + the
  SCR/JAXB wiring this plan adds.
- `com.mobi.cache.impl.core/.../SimpleCacheManager.java` — the provider lookup (`:96`).
- `com.mobi.security.policy.api/.../cache/PolicyCacheConfiguration.java` — `Expirations`
  (`:34`, `:84`).
- Serves / relates to: [java-25-migration](java-25-migration/README.md) Step 2 / D-5
  (which diagnosed the lock and deferred this lift here).
