package com.mobi.prolly.record.impl.client;

/*-
 * #%L
 * com.mobi.prolly.record.impl
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

import com.mobi.prolly.record.api.client.ProllyDiff;
import com.mobi.prolly.record.api.client.ProllyVersioningClient;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.DynamicModelFactory;
import org.eclipse.rdf4j.model.impl.ValidatingValueFactory;
import org.eclipse.rdf4j.rio.helpers.NTriplesUtil;
import org.osgi.service.component.annotations.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory, content-addressed implementation of {@link ProllyVersioningClient} — the test and
 * development harness for the prolly-backed record type (plan
 * {@code plans/prolly-versioned-rdf-record.md} D-6). Commit hashes are deterministic: SHA-256 over
 * the parents plus the canonicalized resulting state, so identical states from identical histories
 * hash identically, matching prolly's content-addressing semantics.
 *
 * <p><b>Not a persistence layer.</b> Content lives on the heap and vanishes on restart. The gRPC
 * client from {@code plans/grpc-prolly-versioning-client.md} replaces this registration in a
 * production deployment (plan Phase 5).
 */
@Component(immediate = true, service = ProllyVersioningClient.class)
public class InMemoryProllyVersioningClient implements ProllyVersioningClient {

    private static final class CommitNode {
        final Set<Statement> state;
        final List<String> parents;
        final String message;

        CommitNode(Set<Statement> state, List<String> parents, String message) {
            this.state = state;
            this.parents = parents;
            this.message = message;
        }
    }

    private static final class Repo {
        final Map<String, CommitNode> commits = new HashMap<>();
        final Map<String, String> branches = new HashMap<>();
        final Map<String, String> leases = new HashMap<>();
    }

    private final Map<String, Repo> repos = new HashMap<>();
    private final AtomicLong leaseCounter = new AtomicLong();
    private final ValueFactory vf = new ValidatingValueFactory();

    @Override
    public synchronized void createRepo(String repoKey) {
        if (repos.containsKey(repoKey)) {
            throw new IllegalArgumentException("Repo already exists: " + repoKey);
        }
        repos.put(repoKey, new Repo());
    }

    @Override
    public synchronized void deleteRepo(String repoKey) {
        repos.remove(repoKey);
    }

    @Override
    public synchronized boolean repoExists(String repoKey) {
        return repos.containsKey(repoKey);
    }

    @Override
    public synchronized Model snapshot(String repoKey, String commitHash) {
        Model model = new DynamicModelFactory().createEmptyModel();
        model.addAll(getCommit(repoKey, commitHash).state);
        return model;
    }

    @Override
    public synchronized String commit(String repoKey, Model additions, Model deletions,
                                      List<String> parents, String message) {
        Repo repo = getRepo(repoKey);
        Set<Statement> state = new HashSet<>();
        if (!parents.isEmpty()) {
            state.addAll(getCommit(repoKey, parents.get(0)).state);
        }
        deletions.forEach(st -> state.remove(stripContext(st)));
        additions.forEach(st -> state.add(stripContext(st)));
        String hash = hashOf(state, parents);
        repo.commits.put(hash, new CommitNode(state, List.copyOf(parents), message));
        return hash;
    }

    @Override
    public synchronized void createBranch(String repoKey, String branch, String commitHash) {
        Repo repo = getRepo(repoKey);
        if (repo.branches.containsKey(branch)) {
            throw new IllegalArgumentException("Branch already exists: " + branch);
        }
        getCommit(repoKey, commitHash);
        repo.branches.put(branch, commitHash);
    }

    @Override
    public synchronized void deleteBranch(String repoKey, String branch) {
        getRepo(repoKey).branches.remove(branch);
    }

    @Override
    public synchronized Optional<String> getBranchHead(String repoKey, String branch) {
        return Optional.ofNullable(getRepo(repoKey).branches.get(branch));
    }

    @Override
    public synchronized void setBranchHead(String repoKey, String branch, String commitHash) {
        getCommit(repoKey, commitHash);
        getRepo(repoKey).branches.put(branch, commitHash);
    }

    @Override
    public synchronized ProllyDiff diff(String repoKey, String fromCommit, String toCommit) {
        Set<Statement> from = getCommit(repoKey, fromCommit).state;
        Set<Statement> to = getCommit(repoKey, toCommit).state;
        Model additions = new DynamicModelFactory().createEmptyModel();
        Model deletions = new DynamicModelFactory().createEmptyModel();
        to.stream().filter(st -> !from.contains(st)).forEach(additions::add);
        from.stream().filter(st -> !to.contains(st)).forEach(deletions::add);
        return new ProllyDiff(additions, deletions);
    }

    @Override
    public synchronized Optional<String> getMergeBase(String repoKey, String commitA, String commitB) {
        Set<String> ancestorsOfA = ancestors(repoKey, commitA);
        Deque<String> queue = new ArrayDeque<>();
        queue.add(commitB);
        Set<String> seen = new HashSet<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!seen.add(current)) {
                continue;
            }
            if (ancestorsOfA.contains(current)) {
                return Optional.of(current);
            }
            queue.addAll(getCommit(repoKey, current).parents);
        }
        return Optional.empty();
    }

    @Override
    public synchronized String acquireSnapshotLease(String repoKey, String commitHash) {
        getCommit(repoKey, commitHash);
        String leaseId = "lease-" + leaseCounter.incrementAndGet();
        getRepo(repoKey).leases.put(leaseId, commitHash);
        return leaseId;
    }

    @Override
    public synchronized void renewSnapshotLease(String repoKey, String leaseId) {
        if (!getRepo(repoKey).leases.containsKey(leaseId)) {
            throw new IllegalArgumentException("Unknown lease: " + leaseId);
        }
    }

    @Override
    public synchronized void releaseSnapshotLease(String repoKey, String leaseId) {
        getRepo(repoKey).leases.remove(leaseId);
    }

    /** Test hook: the leases currently held on a repo. */
    public synchronized Map<String, String> activeLeases(String repoKey) {
        return Map.copyOf(getRepo(repoKey).leases);
    }

    /** Test hook: every repo key currently present. */
    public synchronized Set<String> repoKeys() {
        return Set.copyOf(repos.keySet());
    }

    private Repo getRepo(String repoKey) {
        Repo repo = repos.get(repoKey);
        if (repo == null) {
            throw new IllegalArgumentException("Unknown repo: " + repoKey);
        }
        return repo;
    }

    private CommitNode getCommit(String repoKey, String commitHash) {
        CommitNode node = getRepo(repoKey).commits.get(commitHash);
        if (node == null) {
            throw new IllegalArgumentException("Unknown commit " + commitHash + " in repo " + repoKey);
        }
        return node;
    }

    private Set<String> ancestors(String repoKey, String commitHash) {
        Set<String> result = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(commitHash);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (result.add(current)) {
                queue.addAll(getCommit(repoKey, current).parents);
            }
        }
        return result;
    }

    private Statement stripContext(Statement st) {
        return st.getContext() == null ? st
                : vf.createStatement(st.getSubject(), st.getPredicate(), st.getObject());
    }

    private static String hashOf(Set<Statement> state, List<String> parents) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String parent : parents) {
                digest.update(parent.getBytes(StandardCharsets.UTF_8));
            }
            Set<String> canonical = new TreeSet<>();
            List<Statement> statements = new ArrayList<>(state);
            for (Statement st : statements) {
                canonical.add(NTriplesUtil.toNTriplesString(st.getSubject()) + " "
                        + NTriplesUtil.toNTriplesString(st.getPredicate()) + " "
                        + NTriplesUtil.toNTriplesString(st.getObject()));
            }
            for (String line : canonical) {
                digest.update(line.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
