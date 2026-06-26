# Upstream issue draft — Karaf 4.4.x shell broken on JDK 24+ (`Subject.getSubject` / JEP 486)

> Ready-to-file report for the Apache Karaf project (or for a Mobi tracking ticket).
> Grounded in `plans/java-25-migration/findings.md` F-12. File against Karaf **4.4.11**.

## Title

Shell/console unusable on JDK 24+: Karaf JAAS layer (`JaasHelper`/`ShellUtil`/JMX, ~8 classes / 7 bundles)
call `Subject.getSubject`, which throws `UnsupportedOperationException` after JEP 486

## Affected / environment

- **Karaf:** 4.4.11 (latest 4.4.x; the `javax` line)
- **JDK:** 24 and 25 (verified on Temurin/OpenJDK **25.0.3**). Fine on JDK ≤ 23.
- **Root cause:** [JEP 486](https://openjdk.org/jeps/486) permanently disabled the
  Security Manager in JDK 24. `javax.security.auth.Subject.getSubject(AccessControlContext)`
  now throws `java.lang.UnsupportedOperationException: getSubject is not supported`
  unconditionally, and `-Djava.security.manager=allow` makes the JVM **fail to start**
  ("Enabling a Security Manager is not supported"). The replacement API is
  `Subject.current()` (added in JDK 18).

## Symptom

Any shell command execution fails. Example stack (running a command via a headless
session, e.g. Karaf's own `KarafTestSupport.executeCommand`):

```
java.lang.UnsupportedOperationException: getSubject is not supported
    at java.base/javax.security.auth.Subject.getSubject(Subject.java:277)
    at org.apache.karaf.shell.support.ShellUtil.getCurrentUserName(ShellUtil.java:216)
    at org.apache.karaf.shell.impl.console.HeadlessSessionImpl.<init>(HeadlessSessionImpl.java:99)
    at org.apache.karaf.shell.impl.console.SessionFactoryImpl.create(SessionFactoryImpl.java:178)
    ...
```

and, once the first site is patched, the failure simply moves to the next site:

```
    at org.apache.karaf.shell.impl.console.osgi.secured.SecuredSessionFactoryImpl.currentUserHasRole(SecuredSessionFactoryImpl.java:285)
    at ...SecuredSessionFactoryImpl.isVisible(SecuredSessionFactoryImpl.java:139)
    at org.apache.karaf.shell.impl.console.RegistryImpl.getCommands(RegistryImpl.java:50)
    at org.apache.karaf.shell.impl.console.parsing.CommandLineParser.parse(CommandLineParser.java:84)
    at org.apache.karaf.shell.impl.console.HeadlessSessionImpl.execute(HeadlessSessionImpl.java:112)
```

Practical impact: `bin/client`, the SSH Gogo console, and headless/programmatic command
execution are all unusable on JDK 24+. (The OSGi runtime, HTTP/REST, and non-shell
features are unaffected.)

## All call sites (bytecode scan of the booted 4.4.11 bundles)

The break spans Karaf's whole **JAAS-integration layer**, not just the shell — `~8
distinct classes across 7 bundles` use the JEP-486-affected `Subject` APIs (fixing one
is not enough). `Subject.getSubject` (throws on JDK 24+):

| Bundle | Class |
|---|---|
| `bundle.core`, `jaas.command`, `management.server`, `shell.console`, `shell.core`, `shell.ssh` | `org.apache.karaf.util.jaas.JaasHelper` (shared util, **replicated into all 6**) |
| `shell.core` | `org.apache.karaf.shell.support.ShellUtil` (`getCurrentUserName`) |
| `shell.core` | `org.apache.karaf.shell.impl.console.osgi.secured.SecuredSessionFactoryImpl` (`currentUserHasRole`) |
| `shell.console` | `org.apache.karaf.shell.util.ShellUtil` |
| `shell.console` | `org.apache.karaf.shell.security.impl.SecuredCommandProcessorImpl` |
| `jaas.command` | `org.apache.karaf.jaas.command.WhoamiCommand` |
| `management.server` | `org.apache.karaf.management.internal.BulkRequestContext` (JMX auth) |

And `Subject.doAs` (also throws on JDK 24+):

| Bundle | Class |
|---|---|
| `jaas.modules` | `org.apache.karaf.jaas.modules.ldap.GSSAPILdapLoginModule` |

So the impact is broader than the interactive shell: **JMX management auth** and the
**`jaas:*` commands** break too. A Mobi-side carry-patch would mean forking the JAAS
layer across 7 bundles — which is why the fix belongs upstream.

## Suggested fix

Replace `Subject.getSubject(AccessController.getContext())` with `Subject.current()` at
all the sites in the table above (and `Subject.doAs` → `Subject.callAs` in the GSSAPI
module). To keep the bundles runnable on JDK 17 (where `Subject.current()` does
not exist), guard with reflection or a multi-release class:

```java
// Cross-JDK: prefer Subject.current() (JDK 18+, the only option on JDK 24+),
// fall back to Subject.getSubject() on JDK 17.
private static Subject currentSubject() {
    try {
        return (Subject) Subject.class.getMethod("current").invoke(null);
    } catch (NoSuchMethodException noCurrent) {                 // JDK 17
        return Subject.getSubject(AccessController.getContext());
    } catch (Throwable t) {
        return null;
    }
}
```

(Verified locally: this single change makes the *first* site work on JDK 25 and the
error moves to the next site — confirming the whole JAAS layer must be changed.)

## Reproduction

1. Assemble any Karaf 4.4.11 distribution; boot it on JDK 24+.
2. Run any shell command (`bin/client`, SSH to the console, or
   `KarafTestSupport.executeCommand`).
3. Observe `UnsupportedOperationException: getSubject is not supported`.

## Mobi-side disposition (for our tracker)

Until an upstream fix lands (a 4.4.x patch or 4.5.x GA), run admin CLI / `bin/client` /
`com.mobi.utils.cli` operations on **JDK ≤ 21**, while the REST runtime runs on JDK 25.
A local carry-patch (forking the two shell bundles) was validated but rejected as
impractical (~8 classes across 7 bundles -- the JAAS layer, ongoing divergence from upstream) — see
[findings.md](findings.md) F-12.
