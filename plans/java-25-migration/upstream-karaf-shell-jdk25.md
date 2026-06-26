# Upstream issue draft — Karaf 4.4.x shell broken on JDK 24+ (`Subject.getSubject` / JEP 486)

> Ready-to-file report for the Apache Karaf project (or for a Mobi tracking ticket).
> Grounded in `plans/java-25-migration/findings.md` F-12. File against Karaf **4.4.11**.

## Title

Shell/console unusable on JDK 24+: `ShellUtil.getCurrentUserName` (and 3 other sites)
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

## All call sites (bytecode scan of the 4.4.11 shell bundles)

`Subject.getSubject` is invoked from **4 classes across 2 bundles** — fixing one is not
enough:

| Bundle | Class |
|---|---|
| `org.apache.karaf.shell.core` | `org.apache.karaf.shell.support.ShellUtil` (`getCurrentUserName`) |
| `org.apache.karaf.shell.core` | `org.apache.karaf.shell.impl.console.osgi.secured.SecuredSessionFactoryImpl` (`currentUserHasRole`) |
| `org.apache.karaf.shell.console` | `org.apache.karaf.shell.util.ShellUtil` |
| `org.apache.karaf.shell.console` | `org.apache.karaf.shell.security.impl.SecuredCommandProcessorImpl` |

## Suggested fix

Replace `Subject.getSubject(AccessController.getContext())` with `Subject.current()` at
all four sites. To keep the bundles runnable on JDK 17 (where `Subject.current()` does
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
error moves to the next site — confirming all four must be changed.)

## Reproduction

1. Assemble any Karaf 4.4.11 distribution; boot it on JDK 24+.
2. Run any shell command (`bin/client`, SSH to the console, or
   `KarafTestSupport.executeCommand`).
3. Observe `UnsupportedOperationException: getSubject is not supported`.

## Mobi-side disposition (for our tracker)

Until an upstream fix lands (a 4.4.x patch or 4.5.x GA), run admin CLI / `bin/client` /
`com.mobi.utils.cli` operations on **JDK ≤ 21**, while the REST runtime runs on JDK 25.
A local carry-patch (forking the two shell bundles) was validated but rejected as
impractical (4 sites, 2 bundles, ongoing divergence from upstream) — see
[findings.md](findings.md) F-12.
