# Running Mobi + prolly-rdf4j (the two-process prolly-backed record setup)

How to run the Mobi distribution and the prolly-rdf4j sidecar together, so
`ProllyVersionedRDFRecord`s store their versioned content in an out-of-process,
content-addressed prolly repo over gRPC. Every command below was run and
verified on 2026-08-12 (Linux, Intel N150); where a step has a known failure
mode, it is listed in [Troubleshooting](#troubleshooting).

**What this gives you, measured:** reads at a commit *behind* a branch head are
served as O(1) prolly snapshots (~1 ms embedded bound) instead of the catalog's
revision-chain replay, which on a disk-backed native store takes **10.1 minutes**
at 500 commits behind head and **35.7 minutes** at 999
(`plans/out-of-process-prolly-service.md`, Step 0 wrap-up). Reads at master HEAD
were already fast (materialized HEAD graph) and are unaffected.

## The two processes

| Process | Repo | JDK | Ports (as used here) | Holds |
|---|---|---|---|---|
| Mobi (Karaf) | this repo | **17** (enforced) | 8443 HTTPS REST/UI, 8181 HTTP | Catalog metadata: records, branches, commit stubs, users, policies |
| prolly-server (Spring Boot) | `~/git/prolly-port` | **25** | 18080 REST, 19090 gRPC | Versioned content: one prolly repo per record, RocksDB on disk |

The coupling is the vendored `versioning.proto` contract
(`com.mobi.prolly.record.grpc/src/main/proto/versioning.proto`); Mobi generates
its own Java-17 stubs from it (the prolly jars are Java-25 bytecode).

## 1. Build

```bash
# Mobi (JDK 17) — full reactor, or at minimum the prolly modules + distribution
cd ~/git/mobi
mvn clean install -DskipTests            # full reactor is the safe path
# incremental alternative once ~/.m2 is warm:
#   mvn install -pl com.mobi.prolly.record.api,com.mobi.prolly.record.impl,\
#     com.mobi.prolly.record.grpc,com.mobi.prolly.record.rest,mobi-karaf-feature,mobi-distribution

# prolly-server (JDK 25)
cd ~/git/prolly-port
JAVA_HOME=~/.local/jdks/jdk-25.0.3+9 mvn -pl prolly-server -am package -DskipTests
# produces prolly-server/target/prolly-server-0.2.0-BETA.jar (Spring Boot repackaged)
```

## 2. Run the prolly-server sidecar

```bash
STORE=/path/to/prolly-store          # RocksDB lives here; per-repo under <STORE>/repos/
PROLLY_PORT=18080 ~/.local/jdks/jdk-25.0.3+9/bin/java \
  --enable-native-access=ALL-UNNAMED \
  -jar ~/git/prolly-port/prolly-server/target/prolly-server-0.2.0-BETA.jar \
  --prolly.rdf4j.enabled=true \
  --prolly.rdf4j.store-dir=$STORE \
  --prolly.rdf4j.bootstrap-admin-password=CHANGE-ME \
  --prolly.rdf4j.grpc.enabled=true \
  --prolly.rdf4j.grpc.port=19090
```

- `--prolly.rdf4j.bootstrap-admin-password` is **required on first boot**
  (creates the `admin` account; the server fails to start without it). Clear it
  after rotating the password.
- `--prolly.rdf4j.grpc.enabled=true` mounts the `ProllyVersioning` gRPC service
  — default is off. `--enable-native-access=ALL-UNNAMED` silences the RocksDB
  JNI warning.
- Wait for `Started ProllyServerApplication` in the log; verify with
  `curl -u admin:CHANGE-ME http://localhost:18080/repos` (lists `default`).
- Full server config reference:
  `prolly-port/newcomer-docs/operations/configuration-reference.md`.

> **Do not use the standalone `GrpcSyncServer` main for this** — it serves only
> the sync service; `ProllyVersioning` (CreateRepo, Snapshot, Commit, …) is
> wired only in the Spring Boot deployment above.

## 3. Configure and run Mobi

```bash
cd ~/git/mobi/mobi-distribution/target
tar xzf mobi-distribution-4.3.0-SNAPSHOT.tar.gz
cd mobi-distribution-4.3.0-SNAPSHOT

# point the gRPC client at the sidecar (the component is config-gated: without
# this file it stays down and an in-memory fake serves instead — see Notes)
cat > etc/com.mobi.prolly.record.grpc.GrpcProllyVersioningClient.cfg <<'EOF'
endpoint=localhost:19090
apiKey=
EOF

bin/start          # first boot takes a minute or two
```

- Web UI: `https://localhost:8443/mobi/index.html`, login `admin`/`admin`.
- Success signal in `data/log/karaf.log`:
  `Connected to prolly gRPC service at localhost:19090 — server dev contract 1.0 …`
- The cfg hot-reloads: editing it re-activates the client without a restart.

## 4. Smoke-test the whole loop (verified sequence)

```bash
CAT='http%3A%2F%2Fmobi.com%2Fcatalog-local'
# login (session cookie)
curl -sk -c /tmp/c.txt -X POST 'https://localhost:8443/mobirest/session?username=admin&password=admin'

# create a prolly-backed record from an RDF file
curl -sk -b /tmp/c.txt -X POST https://localhost:8443/mobirest/prolly-records \
  -F 'title=Demo' -F 'file=@mydata.ttl'
# -> {"recordId":"...","branchId":"...","commitId":"..."}; URL-encode these below
# verify: the repo now exists server-side
curl -s -u admin:CHANGE-ME http://localhost:18080/repos     # mobi-<slug>-<hash16>

# commit a change: stage an in-progress commit, then commit it
curl -sk -b /tmp/c.txt -X POST "https://localhost:8443/mobirest/catalogs/$CAT/records/$REC/in-progress-commit"
curl -sk -b /tmp/c.txt -X PUT  "https://localhost:8443/mobirest/catalogs/$CAT/records/$REC/in-progress-commit" \
  -F 'additions=[{"@id":"urn:demo#x","http://www.w3.org/2000/01/rdf-schema#label":[{"@value":"hi"}]}]'
curl -sk -b /tmp/c.txt -X POST "https://localhost:8443/mobirest/catalogs/$CAT/records/$REC/branches/$BR/commits?message=second"
# verify: the commit is in the prolly server's own log, with parent linkage
curl -s -u admin:CHANGE-ME "http://localhost:18080/repos/<repo>/sparql/commits"

# time-travel read at the FIRST commit (now behind head) — served by the gRPC
# snapshot, not the replay; returns the state without urn:demo#x
curl -sk -b /tmp/c.txt -H 'Accept: application/ld+json' \
  "https://localhost:8443/mobirest/catalogs/$CAT/records/$REC/branches/$BR/commits/$C1/resource"

# delete: removes the catalog record AND the server-side repo
curl -sk -b /tmp/c.txt -X DELETE "https://localhost:8443/mobirest/catalogs/$CAT/records/$REC"
```

## How it works (map of the pieces)

- `com.mobi.prolly.record.api` — the `ProllyVersionedRDFRecord`/`ProllyCommit`
  ontology + ORM, the `ProllyVersioningClient` boundary, `ProllyRepoKeys`
  (record IRI → `mobi-<slug>-<hash16>`).
- `com.mobi.prolly.record.impl` — the record service (repo created before any
  catalog write, compensated on failure; deleted best-effort after), the
  versioning service (**dual write**: the catalog commit flow runs unchanged and
  the delta mirrors to prolly, stubs stamped `prollyCommitHash`; a failed mirror
  degrades to replay with a warning, never fails the user's commit), the
  in-memory fake client, and the snapshot resolver.
- `com.mobi.prolly.record.grpc` — the gRPC client (18 MB fat bundle; channel
  built via the shaded `NettyChannelBuilder` directly — the OSGi-safe recipe),
  config-gated with `service.ranking=100` so it outranks the fake when configured.
- `com.mobi.prolly.record.rest` — `POST /mobirest/prolly-records`.
- `catalog.api`/`catalog.impl` — the `CommitSnapshotResolver` extension point:
  compiled-resource reads consult resolvers before replaying; commits without a
  `prollyCommitHash` (all other record types) are untouched.
- Type dispatch does the routing: the record's type selects the prolly
  RecordService/VersioningService; no flags, no changes to other record types.

The design record: `plans/prolly-versioned-rdf-record.md` (step wrap-ups carry
what was measured and corrected), `plans/grpc-prolly-versioning-client.md`
(the gRPC-in-Karaf recipe), `plans/prolly-two-process-production.md` (the
production hardening parking lot), `docs/adr/0002-*.md` (why out-of-process).

## Notes & current limits (honest list)

- **Without the cfg, an in-memory fake serves the client interface** — the type
  works end-to-end but content is heap-only and lost on restart. Fine for dev,
  never for production.
- **Repo ids**: server enforces ≤ 63 chars and no `/` in bare repoIds —
  `ProllyRepoKeys` respects both (test-pinned). Branch names allow
  `[A-Za-z0-9_./-]{1,128}`.
- **Branch-tip commits over gRPC degrade**: the server's `Commit` RPC lands only
  on the live head line, so mirroring a commit onto a non-head branch tip
  skips-with-warning (catalog stays authoritative; reads fall back to replay for
  those commits). Server-side commit-on-parent is the logged gap
  (`prolly-port/plans/grpc-versioning-service.md`).
- **Merge stays Mobi's**: the server itself reports `setUnionMergeOnly=true` —
  it cannot do blank-node-correct merges; Mobi computes the merge and mirrors a
  two-parent commit.
- **Auth is dev-mode here** (`apiKey=` empty, anonymous gRPC accepted). The
  production posture — loopback bind, single permission authority, key/PAT —
  is `plans/prolly-two-process-production.md` D-3, not yet wired.
- **Dual write, not thin stubs yet**: catalog revision graphs still exist, so
  everything (diffs, history REST) works even with prolly down; dropping them is
  a deferred follow-on.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| prolly-server: `Error creating bean 'bootstrapAdminInitializer'` | First boot without `--prolly.rdf4j.bootstrap-admin-password`. |
| prolly-server: `Failed to bind ... :9090 Address already in use` | Something (e.g. Prometheus) owns 9090 — set `--prolly.rdf4j.grpc.port=19090` and match the Mobi cfg. |
| Karaf log: `UNIMPLEMENTED: Method not found: ...ProllyVersioning/GetServerInfo` | You started the standalone `GrpcSyncServer` (sync-only). Run the Spring Boot `prolly-server` with `--prolly.rdf4j.grpc.enabled=true`. |
| Karaf log: `not reachable at activation; calls will retry lazily` | Sidecar down or wrong `endpoint=` in the cfg. Calls retry lazily; fix the cfg (hot-reloads). |
| Record create: `INVALID_ARGUMENT: repoId too long` / `path-traversal characters` | Old build of `ProllyRepoKeys` — rebuild `com.mobi.prolly.record.api` (fixed 2026-08-12). |
| `415` on the in-progress-commit PUT | Send multipart (`-F additions=...`), not urlencoded. |
| `bin/client` prints `Closed` | Karaf shell client issue on this setup — use the log + REST to verify instead. |
| DS `NoClassDefFoundError ... BaseVersioningService` after adding a bundle | You re-exported `com.mobi.catalog.api.*` with `provide:=true` — bnd embeds the classes without their root-level `.rq` resources. Export only your own packages. |
