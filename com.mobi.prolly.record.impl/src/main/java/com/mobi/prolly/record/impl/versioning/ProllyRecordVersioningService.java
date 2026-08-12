package com.mobi.prolly.record.impl.versioning;

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

import com.mobi.catalog.api.builder.Conflict;
import com.mobi.catalog.api.ontologies.mcat.Branch;
import com.mobi.catalog.api.ontologies.mcat.InProgressCommit;
import com.mobi.catalog.api.ontologies.mcat.MasterBranch;
import com.mobi.catalog.api.ontologies.mcat.Revision;
import com.mobi.catalog.api.ontologies.mcat.VersionedRDFRecord;
import com.mobi.catalog.api.versioning.BaseVersioningService;
import com.mobi.catalog.api.versioning.VersioningService;
import com.mobi.jaas.api.ontologies.usermanagement.User;
import com.mobi.prolly.record.api.client.ProllyRepoKeys;
import com.mobi.prolly.record.api.client.ProllyVersioningClient;
import com.mobi.prolly.record.api.ontologies.prollyrecord.ProllyCommit;
import com.mobi.prolly.record.api.ontologies.prollyrecord.ProllyVersionedRDFRecord;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.impl.DynamicModelFactory;
import org.eclipse.rdf4j.model.impl.ValidatingValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.query.QueryResults;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Versioning for prolly-backed records. Phase 3 of {@code plans/prolly-versioned-rdf-record.md}
 * lands this as a <b>dual write</b>: the base catalog commit flow runs unchanged (head graph,
 * reversed deltas, branch heads), and the same delta is mirrored into the record's prolly repo —
 * the catalog commit is linked to its prolly twin by {@code prollyCommitHash} and typed
 * {@code ProllyCommit}. The prolly hash chain is what Phase 4 routes reads through; dropping the
 * catalog delta graphs (the plan's D-4 thin stub) is deferred until every revision-walk is proven
 * routed.
 */
@Component(
        immediate = true,
        service = { VersioningService.class, ProllyRecordVersioningService.class }
)
public class ProllyRecordVersioningService extends BaseVersioningService<ProllyVersionedRDFRecord> {

    private static final Logger LOG = LoggerFactory.getLogger(ProllyRecordVersioningService.class);

    private final org.eclipse.rdf4j.model.ValueFactory valueFactory = new ValidatingValueFactory();

    @Reference
    ProllyVersioningClient prollyClient;

    @Activate
    void start(BundleContext context) {
        final ServiceReference<EventAdmin> ref = context.getServiceReference(EventAdmin.class);
        if (ref != null) {
            this.eventAdmin = context.getService(ref);
        }
    }

    @Override
    public String getTypeIRI() {
        return ProllyVersionedRDFRecord.TYPE;
    }

    @Override
    public Resource addMasterCommit(VersionedRDFRecord record, MasterBranch branch, User user, String message,
                                    RepositoryConnection conn) {
        PendingMirror pending = prepareMirror(record, branch, user, conn);
        Resource commitId = super.addMasterCommit(record, branch, user, message, conn);
        applyMirror(pending, commitId, branch, message, conn);
        return commitId;
    }

    @Override
    public Resource addBranchCommit(VersionedRDFRecord record, Branch branch, User user, String message,
                                    RepositoryConnection conn) {
        PendingMirror pending = prepareMirror(record, branch, user, conn);
        Resource commitId = super.addBranchCommit(record, branch, user, message, conn);
        applyMirror(pending, commitId, branch, message, conn);
        return commitId;
    }

    /**
     * Merge stays Mobi's (plan D-8): the base flow computes the blank-node-correct merge and the
     * catalog merge commit; the mirror writes a two-parent prolly commit whose state matches the
     * merged result, bracketed by a snapshot lease until the branch head attaches it.
     */
    @Override
    public Resource mergeIntoMaster(VersionedRDFRecord record, Branch sourceBranch, MasterBranch targetBranch,
                                    User user, Model additions, Model deletions,
                                    Map<Resource, Conflict> conflictMap, RepositoryConnection conn) {
        MergeParents parents = prepareMergeMirror(record, sourceBranch, targetBranch, conn);
        Resource mergeCommitId = super.mergeIntoMaster(record, sourceBranch, targetBranch, user, additions,
                deletions, conflictMap, conn);
        if (parents != null) {
            Resource headGraph = branchManager.getHeadGraph(targetBranch);
            Model mergedState = new DynamicModelFactory().createEmptyModel();
            mergedState.addAll(QueryResults.asModel(conn.getStatements(null, null, null, headGraph)));
            applyMergeMirror(parents, mergedState, mergeCommitId, targetBranch, conn);
        }
        return mergeCommitId;
    }

    @Override
    public Resource mergeIntoBranch(VersionedRDFRecord record, Branch sourceBranch, Branch targetBranch,
                                    User user, Model additions, Model deletions,
                                    Map<Resource, Conflict> conflictMap, RepositoryConnection conn) {
        MergeParents parents = prepareMergeMirror(record, sourceBranch, targetBranch, conn);
        Resource mergeCommitId = super.mergeIntoBranch(record, sourceBranch, targetBranch, user, additions,
                deletions, conflictMap, conn);
        if (parents != null) {
            // no head graph on a non-master branch: the merged state is the compiled resource at
            // the merge commit (a replay — the merge commit's own deltas are in the catalog)
            Model mergedState = compiledResourceManager.getCompiledResource(mergeCommitId, conn);
            applyMergeMirror(parents, mergedState, mergeCommitId, targetBranch, conn);
        }
        return mergeCommitId;
    }

    private record MergeParents(String repoKey, String targetHash, String sourceHash) {
    }

    private MergeParents prepareMergeMirror(VersionedRDFRecord record, Branch sourceBranch, Branch targetBranch,
                                            RepositoryConnection conn) {
        Optional<String> repoKey = repoKeyOf(record, conn);
        if (repoKey.isEmpty()) {
            LOG.warn("Prolly record {} has no prollyRepoKey; skipping prolly merge mirror", record.getResource());
            return null;
        }
        Optional<String> targetHash = targetBranch.getHead_resource().flatMap(head -> hashOf(head, conn));
        Optional<String> sourceHash = sourceBranch.getHead_resource().flatMap(head -> hashOf(head, conn));
        if (targetHash.isEmpty() || sourceHash.isEmpty()) {
            LOG.warn("Merge of {} into {} has unmirrored parent commits; skipping prolly merge mirror",
                    sourceBranch.getResource(), targetBranch.getResource());
            return null;
        }
        return new MergeParents(repoKey.get(), targetHash.get(), sourceHash.get());
    }

    private void applyMergeMirror(MergeParents parents, Model mergedState, Resource mergeCommitId,
                                  Branch targetBranch, RepositoryConnection conn) {
        // delta of the merged state against the first parent (the target head)
        Model parentState = prollyClient.snapshot(parents.repoKey(), parents.targetHash());
        Model additions = new DynamicModelFactory().createEmptyModel();
        Model deletions = new DynamicModelFactory().createEmptyModel();
        mergedState.stream().map(this::stripContext)
                .filter(st -> !parentState.contains(st.getSubject(), st.getPredicate(), st.getObject()))
                .forEach(additions::add);
        parentState.stream()
                .filter(st -> !mergedState.contains(st.getSubject(), st.getPredicate(), st.getObject()))
                .forEach(deletions::add);
        String hash = prollyClient.commit(parents.repoKey(), additions, deletions,
                List.of(parents.targetHash(), parents.sourceHash()), "merge");
        // lease the detached merge commit for the window before the branch head attaches it (D-8)
        String lease = prollyClient.acquireSnapshotLease(parents.repoKey(), hash);
        try {
            conn.add(mergeCommitId, RDF.TYPE, valueFactory.createIRI(ProllyCommit.TYPE), mergeCommitId);
            conn.add(mergeCommitId, valueFactory.createIRI(ProllyCommit.prollyCommitHash_IRI),
                    valueFactory.createLiteral(hash), mergeCommitId);
            conn.add(mergeCommitId, valueFactory.createIRI(ProllyVersionedRDFRecord.prollyRepoKey_IRI),
                    valueFactory.createLiteral(parents.repoKey()), mergeCommitId);
            prollyClient.setBranchHead(parents.repoKey(),
                    ProllyRepoKeys.forBranch(targetBranch.getResource()), hash);
        } finally {
            prollyClient.releaseSnapshotLease(parents.repoKey(), lease);
        }
    }

    private org.eclipse.rdf4j.model.Statement stripContext(org.eclipse.rdf4j.model.Statement st) {
        return st.getContext() == null ? st
                : valueFactory.createStatement(st.getSubject(), st.getPredicate(), st.getObject());
    }

    /**
     * What a prolly mirror needs, captured before the base flow consumes the InProgressCommit.
     * A root commit ({@code parents} empty) carries no delta here — the base flow pre-places the
     * initial state in the master HEAD graph with an empty InProgressCommit delta (measured in
     * the out-of-process plan's Step 0 harness corrections), so the root mirror snapshots the
     * HEAD graph after the base commit instead.
     */
    private record PendingMirror(String repoKey, Model additions, Model deletions, List<String> parents) {
    }

    private PendingMirror prepareMirror(VersionedRDFRecord record, Branch branch, User user,
                                        RepositoryConnection conn) {
        Optional<String> repoKey = repoKeyOf(record, conn);
        if (repoKey.isEmpty()) {
            LOG.warn("Prolly record {} has no prollyRepoKey; skipping prolly mirror", record.getResource());
            return null;
        }
        Optional<Resource> headCommit = branch.getHead_resource();
        if (headCommit.isEmpty()) {
            // root commit: delta comes from the HEAD graph post-commit (see PendingMirror javadoc)
            return new PendingMirror(repoKey.get(), null, null, List.of());
        }
        Optional<String> headHash = hashOf(headCommit.get(), conn);
        if (headHash.isEmpty()) {
            LOG.warn("Head commit {} of branch {} carries no prollyCommitHash; skipping prolly mirror "
                    + "for this commit", headCommit.get(), branch.getResource());
            return null;
        }
        InProgressCommit ipc = commitManager.getInProgressCommit(record.getResource(), user.getResource(), conn);
        Revision revision = revisionManager.getRevisionFromCommitId(ipc.getResource(), conn);
        Model additions = graphOf(revision.getAdditions(), conn);
        Model deletions = graphOf(revision.getDeletions(), conn);
        return new PendingMirror(repoKey.get(), additions, deletions, List.of(headHash.get()));
    }

    private void applyMirror(PendingMirror pending, Resource commitId, Branch branch, String message,
                             RepositoryConnection conn) {
        if (pending == null) {
            return;
        }
        Model additions = pending.additions();
        Model deletions = pending.deletions();
        if (pending.parents().isEmpty()) {
            if (!(branch instanceof MasterBranch)) {
                LOG.warn("Root commit on non-master branch {}; skipping prolly mirror", branch.getResource());
                return;
            }
            Resource headGraph = branchManager.getHeadGraph((MasterBranch) branch);
            additions = new DynamicModelFactory().createEmptyModel();
            additions.addAll(QueryResults.asModel(conn.getStatements(null, null, null, headGraph)));
            deletions = new DynamicModelFactory().createEmptyModel();
        }
        String hash = prollyClient.commit(pending.repoKey(), additions, deletions, pending.parents(), message);
        conn.add(commitId, RDF.TYPE, valueFactory.createIRI(ProllyCommit.TYPE), commitId);
        conn.add(commitId, valueFactory.createIRI(ProllyCommit.prollyCommitHash_IRI),
                valueFactory.createLiteral(hash), commitId);
        // the repo key rides on the stub so the snapshot resolver needs no record lookup
        conn.add(commitId, valueFactory.createIRI(ProllyVersionedRDFRecord.prollyRepoKey_IRI),
                valueFactory.createLiteral(pending.repoKey()), commitId);
        prollyClient.setBranchHead(pending.repoKey(), ProllyRepoKeys.forBranch(branch.getResource()), hash);
    }

    private Optional<String> repoKeyOf(VersionedRDFRecord record, RepositoryConnection conn) {
        return QueryResults.asModel(conn.getStatements(record.getResource(),
                        valueFactory.createIRI(ProllyVersionedRDFRecord.prollyRepoKey_IRI), null,
                        record.getResource()))
                .objects().stream().findFirst().map(v -> v.stringValue());
    }

    private Optional<String> hashOf(Resource commitId, RepositoryConnection conn) {
        return QueryResults.asModel(conn.getStatements(commitId,
                        valueFactory.createIRI(ProllyCommit.prollyCommitHash_IRI), null, commitId))
                .objects().stream().findFirst().map(v -> v.stringValue());
    }

    private Model graphOf(Optional<IRI> graph, RepositoryConnection conn) {
        Model model = new DynamicModelFactory().createEmptyModel();
        graph.ifPresent(g -> model.addAll(QueryResults.asModel(conn.getStatements(null, null, null, g))));
        return model;
    }
}
