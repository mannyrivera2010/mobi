package com.mobi.prolly.record.api.client;

/*-
 * #%L
 * com.mobi.prolly.record.api
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2016 - 2026 iNovex Information Systems, Inc.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */

import org.eclipse.rdf4j.model.Model;

import java.util.List;
import java.util.Optional;

/**
 * The Mobi-side boundary to an out-of-process prolly-rdf4j versioning service. One prolly repo holds
 * one {@code ProllyVersionedRDFRecord}'s versioned content; commits are content-addressed and
 * identified by hex hashes. Implementations: an in-memory fake for tests and development
 * ({@code InMemoryProllyVersioningClient}), and — once the gRPC transport lands
 * (plans/grpc-prolly-versioning-client.md) — a client over the shared {@code versioning.proto}
 * contract, or prolly's REST endpoints as the fallback.
 */
public interface ProllyVersioningClient {

    /** Creates the repo for a record; fails if it already exists. */
    void createRepo(String repoKey);

    /** Deletes the repo and all its content; no-op if absent. */
    void deleteRepo(String repoKey);

    /** Whether the repo exists. */
    boolean repoExists(String repoKey);

    /** The full graph at a commit. */
    Model snapshot(String repoKey, String commitHash);

    /**
     * Writes a commit whose state is the first parent's state plus {@code additions} minus
     * {@code deletions}; empty parents list creates a root commit. Returns the new commit's hash.
     */
    String commit(String repoKey, Model additions, Model deletions, List<String> parents, String message);

    /** Creates a branch pointing at a commit; fails if the branch exists. */
    void createBranch(String repoKey, String branch, String commitHash);

    /** Deletes a branch pointer (not its commits); no-op if absent. */
    void deleteBranch(String repoKey, String branch);

    /** The commit hash a branch points at, if the branch exists. */
    Optional<String> getBranchHead(String repoKey, String branch);

    /** Moves (or creates) a branch pointer to a commit. */
    void setBranchHead(String repoKey, String branch, String commitHash);

    /** The additions/deletions between two commits' states ({@code from} → {@code to}). */
    ProllyDiff diff(String repoKey, String fromCommit, String toCommit);

    /** The best common ancestor of two commits, if any. */
    Optional<String> getMergeBase(String repoKey, String commitA, String commitB);

    /**
     * Pins a commit that is not (yet) reachable from any branch against server garbage collection.
     * Lease only detached commits — reachable history is already GC-safe. Returns a lease id.
     */
    String acquireSnapshotLease(String repoKey, String commitHash);

    /** Renews a lease before its time-to-live expires. */
    void renewSnapshotLease(String repoKey, String leaseId);

    /** Releases a lease, making the commit reclaimable if still unreachable. */
    void releaseSnapshotLease(String repoKey, String leaseId);
}
