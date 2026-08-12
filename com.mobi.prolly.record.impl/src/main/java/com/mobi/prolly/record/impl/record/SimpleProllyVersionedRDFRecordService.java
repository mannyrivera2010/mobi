package com.mobi.prolly.record.impl.record;

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

import com.mobi.catalog.api.ontologies.mcat.Commit;
import com.mobi.catalog.api.ontologies.mcat.MasterBranch;
import com.mobi.catalog.api.record.AbstractVersionedRDFRecordService;
import com.mobi.catalog.api.record.RecordService;
import com.mobi.catalog.api.record.config.RecordCreateSettings;
import com.mobi.catalog.api.record.config.RecordOperationConfig;
import com.mobi.jaas.api.ontologies.usermanagement.User;
import com.mobi.prolly.record.api.client.ProllyRepoKeys;
import com.mobi.prolly.record.api.client.ProllyVersioningClient;
import com.mobi.prolly.record.api.ontologies.prollyrecord.ProllyVersionedRDFRecord;
import com.mobi.prolly.record.api.ontologies.prollyrecord.ProllyVersionedRDFRecordFactory;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.OffsetDateTime;

@Component(
        immediate = true,
        service = { RecordService.class, SimpleProllyVersionedRDFRecordService.class }
)
public class SimpleProllyVersionedRDFRecordService
        extends AbstractVersionedRDFRecordService<ProllyVersionedRDFRecord> {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleProllyVersionedRDFRecordService.class);

    @Reference
    ProllyVersionedRDFRecordFactory prollyVersionedRDFRecordFactory;

    @Reference
    ProllyVersioningClient prollyClient;

    @Activate
    public void activate() {
        this.recordFactory = prollyVersionedRDFRecordFactory;
        checkForMissingPolicies();
    }

    @Override
    public Class<ProllyVersionedRDFRecord> getType() {
        return ProllyVersionedRDFRecord.class;
    }

    @Override
    public String getTypeIRI() {
        return ProllyVersionedRDFRecord.TYPE;
    }

    /**
     * Mirrors the base create-with-master-branch-and-initial-commit flow
     * ({@code AbstractVersionedRDFRecordService.createRecord}) with the repo lifecycle inserted
     * (plan D-4/D-8 of prolly-versioned-rdf-record): the prolly repo is created <b>before</b> any
     * catalog state is written — no repo, no record — and deleted again if the catalog half fails.
     */
    @Override
    public ProllyVersionedRDFRecord createRecord(User user, RecordOperationConfig config, OffsetDateTime issued,
                                                 OffsetDateTime modified, RepositoryConnection conn) {
        ProllyVersionedRDFRecord record = createRecordObject(config, issued, modified);
        String repoKey = ProllyRepoKeys.forRecord(record.getResource());
        record.setProllyRepoKey(repoKey);
        MasterBranch masterBranch = createMasterBranch(record);

        IRI catalogIdIRI = vf.createIRI(config.get(RecordCreateSettings.CATALOG_ID));
        Resource masterBranchId = record.getMasterBranch_resource().orElseThrow(() ->
                new IllegalStateException("VersionedRDFRecord must have a master Branch"));

        prollyClient.createRepo(repoKey);
        File versionedRdf = null;
        InitialLoad initialLoad = null;
        try {
            versionedRdf = createDataFile(config);
            initialLoad = loadHeadGraph(masterBranch, user, versionedRdf);

            conn.begin();
            addRecord(record, masterBranch, conn);
            commitManager.addInProgressCommit(catalogIdIRI, record.getResource(), initialLoad.ipc(), conn);

            Resource initialCommitIRI = versioningManager.commit(catalogIdIRI, record.getResource(), masterBranchId,
                    user, "The initial commit.", conn);
            Commit initialCommit = commitManager.getCommit(initialCommitIRI, conn).orElseThrow(
                    () -> new IllegalStateException("Could not retrieve commit " + initialCommitIRI.stringValue()));
            initialCommit.setInitialRevision(initialLoad.initialRevision());
            initialCommit.getModel().addAll(initialLoad.initialRevision().getModel());
            thingManager.updateObject(initialCommit, conn);

            conn.commit();
            writePolicies(user, record);
            versionedRdf.delete();
        } catch (Exception e) {
            try {
                prollyClient.deleteRepo(repoKey);
            } catch (Exception cleanupFailure) {
                LOG.warn("Could not clean up prolly repo {} after failed record creation", repoKey, cleanupFailure);
            }
            handleError(masterBranch, initialLoad == null ? null : initialLoad.initialRevision(), versionedRdf, e);
        }
        return record;
    }

    /**
     * Mirrors branch deletion to prolly so the repo's reachability tracks Mobi's branch pointers
     * (plan Step 7): catalog half via super, prolly pointer best-effort after.
     */
    @Override
    public java.util.Optional<java.util.List<Resource>> deleteBranch(Resource catalogId,
            Resource versionedRDFRecordId, Resource branchId, RepositoryConnection conn) {
        java.util.Optional<java.util.List<Resource>> deleted =
                super.deleteBranch(catalogId, versionedRDFRecordId, branchId, conn);
        try {
            org.eclipse.rdf4j.query.QueryResults.asModel(conn.getStatements(versionedRDFRecordId,
                            vf.createIRI(ProllyVersionedRDFRecord.prollyRepoKey_IRI), null,
                            versionedRDFRecordId))
                    .objects().stream().findFirst()
                    .map(org.eclipse.rdf4j.model.Value::stringValue)
                    .ifPresent(repoKey ->
                            prollyClient.deleteBranch(repoKey, ProllyRepoKeys.forBranch(branchId)));
        } catch (Exception e) {
            LOG.warn("Could not delete prolly branch for {} on record {}; the orphaned branch pointer "
                    + "is harmless", branchId, versionedRDFRecordId, e);
        }
        return deleted;
    }

    /**
     * Deletes the catalog half first (the transactional part), then the prolly repo best-effort:
     * an orphaned prolly repo is reclaimable garbage, but a catalog record pointing at a deleted
     * repo is not (plan Step 5).
     */
    @Override
    public ProllyVersionedRDFRecord delete(Resource recordId, User user, RepositoryConnection conn) {
        ProllyVersionedRDFRecord record = super.delete(recordId, user, conn);
        record.getProllyRepoKey().ifPresent(repoKey -> {
            try {
                prollyClient.deleteRepo(repoKey);
            } catch (Exception e) {
                LOG.warn("Could not delete prolly repo {} for deleted record {}; the orphaned repo is "
                        + "reclaimable", repoKey, recordId, e);
            }
        });
        return record;
    }
}
