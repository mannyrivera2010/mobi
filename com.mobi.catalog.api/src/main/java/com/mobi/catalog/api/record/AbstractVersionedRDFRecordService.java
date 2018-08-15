package com.mobi.catalog.api.record;

/*-
 * #%L
 * com.mobi.catalog.api
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2016 - 2018 iNovex Information Systems, Inc.
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

import com.mobi.catalog.api.Catalogs;
import com.mobi.catalog.api.builder.Difference;
import com.mobi.catalog.api.mergerequest.MergeRequestManager;
import com.mobi.catalog.api.ontologies.mcat.Branch;
import com.mobi.catalog.api.ontologies.mcat.BranchFactory;
import com.mobi.catalog.api.ontologies.mcat.Commit;
import com.mobi.catalog.api.ontologies.mcat.CommitFactory;
import com.mobi.catalog.api.ontologies.mcat.VersionedRDFRecord;
import com.mobi.catalog.api.record.config.RecordCreateSettings;
import com.mobi.catalog.api.record.config.RecordExportSettings;
import com.mobi.catalog.api.record.config.RecordOperationConfig;
import com.mobi.catalog.api.record.config.VersionedRDFRecordCreateSettings;
import com.mobi.catalog.api.record.config.VersionedRDFRecordExportSettings;
import com.mobi.catalog.api.versioning.VersioningManager;
import com.mobi.exception.MobiException;
import com.mobi.jaas.api.ontologies.usermanagement.User;
import com.mobi.ontologies.dcterms._Thing;
import com.mobi.persistence.utils.BatchExporter;
import com.mobi.persistence.utils.ResourceUtils;
import com.mobi.rdf.api.IRI;
import com.mobi.rdf.api.Model;
import com.mobi.rdf.api.Resource;
import com.mobi.rdf.api.Statement;
import com.mobi.repository.api.RepositoryConnection;
import com.mobi.repository.base.RepositoryResult;
import com.mobi.security.policy.api.ontologies.policy.Policy;
import com.mobi.security.policy.api.xacml.XACMLPolicy;
import com.mobi.security.policy.api.xacml.XACMLPolicyManager;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

/**
 * Defines functionality for VersionedRDFRecordService. Provides common methods for exporting and deleting a Record.
 * Overrides exportRecord() and deleteRecord() to perform VersionedRDFRecord specific operations such as writing
 * out Branches, Commits, and Tags.
 * @param <T> of VersionedRDFRecord
 */
public abstract class AbstractVersionedRDFRecordService<T extends VersionedRDFRecord>
        extends AbstractRecordService<T> implements RecordService<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractVersionedRDFRecordService.class);
    private static final String USER_IRI_BINDING = "%USERIRI%";
    private static final String RECORD_IRI_BINDING = "%RECORDIRI%";
    private static final String ENCODED_RECORD_IRI_BINDING = "%RECORDIRIENCODED%";
    private static final String POLICY_IRI_BINDING = "%POLICYIRI%";
    private static final String ENCODED_POLICY_IRI_BINDING = "%POLICYIRIENCODED%";

    protected CommitFactory commitFactory;
    protected BranchFactory branchFactory;
    protected MergeRequestManager mergeRequestManager;
    protected VersioningManager versioningManager;
    protected XACMLPolicyManager xacmlPolicyManager;

    @Override
    protected void exportRecord(T record, RecordOperationConfig config, RepositoryConnection conn) {
        BatchExporter exporter = config.get(RecordExportSettings.BATCH_EXPORTER);
        writeRecordData(record, exporter);
        if (config.get(VersionedRDFRecordExportSettings.WRITE_VERSIONED_DATA)) {
            writeVersionedRDFData(record, config.get(VersionedRDFRecordExportSettings.BRANCHES_TO_EXPORT),
                    exporter, conn);
        }
    }

    @Override
    public T createRecord(User user, RecordOperationConfig config, OffsetDateTime issued, OffsetDateTime modified,
                          RepositoryConnection conn) {
        T record = createRecordObject(config, issued, modified);
        Branch masterBranch = createMasterBranch(record);
        conn.begin();
        addRecord(record, masterBranch, conn);
        IRI catalogIdIRI = valueFactory.createIRI(config.get(RecordCreateSettings.CATALOG_ID));
        Resource masterBranchId = record.getMasterBranch_resource().orElseThrow(() ->
                new IllegalStateException("VersionedRDFRecord must have a master Branch"));
        Model model = config.get(VersionedRDFRecordCreateSettings.INITIAL_COMMIT_DATA);
        conn.commit();
        versioningManager.commit(catalogIdIRI, record.getResource(),
                masterBranchId, user, "The initial commit.", model, null);
        writePolicies(user, record);
        return record;
    }

    /**
     * Creates two policy files for the Record based on default templates. One policy to control who can view and modify
     * the Record. The other for who can modify the aforementioned policy.
     *
     * @param user The user who created the Record and associated Policy files
     * @param record The record the Policy files control
     */
    protected void writePolicies(User user, T record) {
        try {
            // Record Policy
            InputStream recordPolicyStream = AbstractVersionedRDFRecordService.class
                    .getResourceAsStream("/recordPolicy.xml");
            String encodedRecordIRI = ResourceUtils.encode(record.getResource().stringValue());

            String[] search = {USER_IRI_BINDING, RECORD_IRI_BINDING, ENCODED_RECORD_IRI_BINDING};
            String[] replace = {user.getResource().stringValue(), record.getResource().stringValue(), encodedRecordIRI};
            String recordPolicy = StringUtils.replaceEach(IOUtils.toString(recordPolicyStream, "UTF-8"),
                    search, replace);

            Resource recordPolicyResource = addPolicy(recordPolicy);

            // Policy for the Record Policy
            search[1] = POLICY_IRI_BINDING;
            search[2] = ENCODED_POLICY_IRI_BINDING;
            replace[1] = recordPolicyResource.stringValue();
            InputStream policyPolicyStream = AbstractVersionedRDFRecordService.class
                    .getResourceAsStream("/policyPolicy.xml");
            String policyPolicy = StringUtils.replaceEach(IOUtils.toString(policyPolicyStream, "UTF-8"),
                    search, replace);
            addPolicy(policyPolicy);

        } catch (IOException e) {
            throw new MobiException("Error writing record policy.", e);
        }
    }

    /**
     * Uses the {@link XACMLPolicyManager} to add the Policy file to the repository and virtual filesystem.
     *
     * @param policyString A string representation of a policy
     * @return The {@link Resource} of the new Policy
     */
    protected Resource addPolicy(String policyString) {
        XACMLPolicy policy = new XACMLPolicy(policyString, valueFactory);
        return xacmlPolicyManager.addPolicy(policy);
    }

    /**
     * Deletes the two Policy files associated with the provided Record.
     *
     * @param record The Record whose policies to delete
     * @param conn A RepositoryConnection to use for lookup
     */
    protected void deletePolicies(T record, RepositoryConnection conn) {
        RepositoryResult<Statement> results = conn.getStatements(null,
                valueFactory.createIRI(Policy.relatedResource_IRI), record.getResource());
        if (!results.hasNext()) {
            LOGGER.info("Could not find policy for record: " + record.getResource().stringValue()
                    + ". Continuing with record deletion.");
        }
        Resource recordPolicyId = results.next().getSubject();

        results = conn.getStatements(null, valueFactory.createIRI(Policy.relatedResource_IRI), recordPolicyId);
        if (!results.hasNext()) {
            LOGGER.info("Could not find policy policy for record: " + record.getResource().stringValue()
                    + " with a policyId of: " + recordPolicyId.stringValue() + ". Continuing with record deletion.");
        }
        Resource policyPolicyId = results.next().getSubject();
        xacmlPolicyManager.deletePolicy(recordPolicyId);
        xacmlPolicyManager.deletePolicy(policyPolicyId);
    }

    /**
     * Adds the record and masterBranch to the repository.
     *
     * @param record The VersionedRDFRecord to add to the repository
     * @param masterBranch The initialized masterBranch to add to the repository
     * @param conn A RepositoryConnection to use for lookup
     */
    protected void addRecord(T record, Branch masterBranch, RepositoryConnection conn) {
        utilsService.addObject(record, conn);
        utilsService.addObject(masterBranch, conn);
    }

    /**
     * Creates a MasterBranch to be initialized based on (record, conn) from the repository.
     *
     * @param record The VersionedRDFRecord to add to a MasterBranch
     */
    protected Branch createMasterBranch(VersionedRDFRecord record) {
        Branch branch = createBranch("MASTER", "The master branch.");
        record.setMasterBranch(branch);
        Set<Branch> branches = record.getBranch_resource().stream()
                .map(branchFactory::createNew)
                .collect(Collectors.toSet());
        branches.add(branch);
        record.setBranch(branches);
        return branch;
    }

    /**
     * Creates a branch specific to (title, description, factory).
     *
     * @param title Name of desired branch
     * @param description Short description of the title branch
     */
    protected Branch createBranch(@Nonnull String title, String description) {
        OffsetDateTime now = OffsetDateTime.now();

        Branch branch = branchFactory.createNew(valueFactory.createIRI(Catalogs.BRANCH_NAMESPACE + UUID.randomUUID()));
        branch.setProperty(valueFactory.createLiteral(title), valueFactory.createIRI(_Thing.title_IRI));
        branch.setProperty(valueFactory.createLiteral(now), valueFactory.createIRI(_Thing.issued_IRI));
        branch.setProperty(valueFactory.createLiteral(now), valueFactory.createIRI(_Thing.modified_IRI));
        if (description != null) {
            branch.setProperty(valueFactory.createLiteral(description), valueFactory.createIRI(_Thing.description_IRI));
        }
        return branch;
    }

    @Override
    protected void deleteRecord(T record, RepositoryConnection conn) {
        deleteRecordObject(record, conn);
        deletePolicies(record, conn);
        deleteVersionedRDFData(record, conn);
    }

    /**
     * Deletes VersionedRDFRecord specific data (Branches, Commits, Tags) from the repository.
     *
     * @param record The VersionedRDFRecord to delete
     * @param conn A RepositoryConnection to use for lookup
     */
    protected void deleteVersionedRDFData(T record, RepositoryConnection conn) {
        mergeRequestManager.deleteMergeRequestsWithRecordId(record.getResource(), conn);
        record.getVersion_resource().forEach(resource -> utilsService.removeVersion(record.getResource(),
                resource, conn));
        conn.remove(record.getResource(), valueFactory.createIRI(VersionedRDFRecord.masterBranch_IRI),
                null, record.getResource());
        List<Resource> deletedCommits = new ArrayList<>();
        record.getBranch_resource().forEach(resource -> utilsService.removeBranch(record.getResource(),
                resource, deletedCommits, conn));
    }

    /**
     * Writes the VersionedRDFRecord data (Branches, Commits, Tags) to the provided ExportWriter
     * If the provided branchesToWrite is empty, will write out all branches.
     *
     * @param record The VersionedRDFRecord to write versioned data
     * @param branchesToWrite The Set of Resources identifying branches to write out
     * @param exporter The ExportWriter to write the VersionedRDFRecord to
     * @param conn A RepositoryConnection to use for lookup
     */
    protected void writeVersionedRDFData(VersionedRDFRecord record, Set<Resource> branchesToWrite,
                                       BatchExporter exporter, RepositoryConnection conn) {
        Set<Resource> processedCommits = new HashSet<>();

        // Write Branches
        record.getBranch_resource().forEach(branchResource -> {
            if (branchesToWrite.isEmpty() || branchesToWrite.contains(branchResource)) {
                Branch branch = utilsService.getBranch(record, branchResource, branchFactory, conn);
                branch.getModel().forEach(exporter::handleStatement);
                Resource headIRI = utilsService.getHeadCommitIRI(branch);

                // Write Commits
                for (Resource commitId : utilsService.getCommitChain(headIRI, false, conn)) {

                    if (processedCommits.contains(commitId)) {
                        break;
                    } else {
                        processedCommits.add(commitId);
                    }

                    // Write Commit/Revision Data
                    Commit commit = utilsService.getExpectedObject(commitId, commitFactory, conn);
                    commit.getModel().forEach(exporter::handleStatement);

                    // Write Additions/Deletions Graphs
                    Difference revisionChanges = utilsService.getRevisionChanges(commitId, conn);
                    revisionChanges.getAdditions().forEach(exporter::handleStatement);
                    revisionChanges.getDeletions().forEach(exporter::handleStatement);
                }
            }
        });
    }
}
