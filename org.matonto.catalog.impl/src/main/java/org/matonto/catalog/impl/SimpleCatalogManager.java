package org.matonto.catalog.impl;

/*-
 * #%L
 * org.matonto.catalog.impl
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2016 iNovex Information Systems, Inc.
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

import aQute.bnd.annotation.component.*;
import aQute.bnd.annotation.metatype.Configurable;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.log4j.Logger;
import org.eclipse.jdt.annotation.Nullable;
import org.matonto.catalog.api.CatalogManager;
import org.matonto.catalog.api.PaginatedSearchParams;
import org.matonto.catalog.api.PaginatedSearchResults;
import org.matonto.catalog.api.builder.DistributionConfig;
import org.matonto.catalog.api.builder.RecordConfig;
import org.matonto.catalog.api.ontologies.mcat.*;
import org.matonto.catalog.config.CatalogConfig;
import org.matonto.catalog.util.SearchResults;
import org.matonto.exception.MatOntoException;
import org.matonto.jaas.ontologies.usermanagement.User;
import org.matonto.jaas.ontologies.usermanagement.UserFactory;
import org.matonto.persistence.utils.Bindings;
import org.matonto.query.TupleQueryResult;
import org.matonto.query.api.Binding;
import org.matonto.query.api.BindingSet;
import org.matonto.query.api.TupleQuery;
import org.matonto.rdf.api.*;
import org.matonto.rdf.orm.OrmFactory;
import org.matonto.repository.api.Repository;
import org.matonto.repository.api.RepositoryConnection;
import org.matonto.repository.base.RepositoryResult;
import org.matonto.repository.exception.RepositoryException;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component(
        configurationPolicy = ConfigurationPolicy.require,
        designateFactory = CatalogConfig.class,
        name = SimpleCatalogManager.COMPONENT_NAME
)
public class SimpleCatalogManager implements CatalogManager {

    static final String COMPONENT_NAME = "org.matonto.catalog.api.CatalogManager";
    private static final Logger log = Logger.getLogger(SimpleCatalogManager.class);
    private Repository repository;
    private ValueFactory vf;
    private ModelFactory mf;
    private CatalogFactory catalogFactory;
    private RecordFactory recordFactory;
    /*private UnversionedRecordFactory unversionedRecordFactory;
    private VersionedRecordFactory versionedRecordFactory;
    private VersionedRDFRecordFactory versionedRDFRecordFactory;
    private OntologyRecordFactory ontologyRecordFactory;
    private MappingRecordFactory mappingRecordFactory;
    private DatasetRecordFactory datasetRecordFactory;*/
    private DistributionFactory distributionFactory;
    /*private VersionFactory versionFactory;*/
    private BranchFactory branchFactory;
    private UserFactory userFactory;
    private InProgressCommitFactory inProgressCommitFactory;
    private CommitFactory commitFactory;
    private RevisionFactory revisionFactory;
    private IRI publishedCatalogIRI;
    private IRI unpublishedCatalogIRI;
    private Map<Resource, String> sortingOptions = new HashMap<>();

    public SimpleCatalogManager() {}

    @Reference(name = "repository")
    protected void setRepository(Repository repository) {
        this.repository = repository;
    }

    @Reference
    protected void setValueFactory(ValueFactory valueFactory) {
        vf = valueFactory;
    }

    @Reference
    protected void setModelFactory(ModelFactory modelFactory) {
        mf = modelFactory;
    }

    @Reference
    protected void setCatalogFactory(CatalogFactory catalogFactory) {
        this.catalogFactory = catalogFactory;
    }

    @Reference
    protected void setRecordFactory(RecordFactory recordFactory) {
        this.recordFactory = recordFactory;
    }

    /*@Reference
    protected void setUnversionedRecordFactory(UnversionedRecordFactory unversionedRecordFactory) {
        this.unversionedRecordFactory = unversionedRecordFactory;
    }

    @Reference
    protected void setVersionedRecordFactory(VersionedRecordFactory versionedRecordFactory) {
        this.versionedRecordFactory = versionedRecordFactory;
    }

    @Reference
    protected void setVersionedRDFRecordFactory(VersionedRDFRecordFactory versionedRDFRecordFactory) {
        this.versionedRDFRecordFactory = versionedRDFRecordFactory;
    }

    @Reference
    protected void setOntologyRecordFactory(OntologyRecordFactory ontologyRecordFactory) {
        this.ontologyRecordFactory = ontologyRecordFactory;
    }

    @Reference
    protected void setDatasetRecordFactory(DatasetRecordFactory datasetRecordFactory) {
        this.datasetRecordFactory = datasetRecordFactory;
    }

    @Reference
    protected void setMappingRecordFactory(MappingRecordFactory mappingRecordFactory) {
        this.mappingRecordFactory = mappingRecordFactory;
    }*/

    @Reference
    protected void setDistributionFactory(DistributionFactory distributionFactory) {
        this.distributionFactory = distributionFactory;
    }

    /*@Reference
    protected void setVersionFactory(VersionFactory versionFactory) {
        this.versionFactory = versionFactory;
    }*/

    @Reference
    protected void setBranchFactory(BranchFactory branchFactory) {
        this.branchFactory = branchFactory;
    }

    @Reference
    protected void setUserFactory(UserFactory userFactory) {
        this.userFactory = userFactory;
    }

    @Reference
    protected void setInProgressCommitFactory(InProgressCommitFactory inProgressCommitFactory) {
        this.inProgressCommitFactory = inProgressCommitFactory;
    }

    @Reference
    protected void setCommitFactory(CommitFactory commitFactory) {
        this.commitFactory = commitFactory;
    }

    @Reference
    protected void setRevisionFactory(RevisionFactory revisionFactory) {
        this.revisionFactory = revisionFactory;
    }

    private static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";

    private static final String DC_TERMS = "http://purl.org/dc/terms/";
    private static final String DC_TITLE = DC_TERMS + "title";
    private static final String DC_DESCRIPTION = DC_TERMS + "description";
    private static final String DC_ISSUED = DC_TERMS + "issued";
    private static final String DC_MODIFIED = DC_TERMS + "modified";

    private static final String PROV_O = "http://www.w3.org/ns/prov#";
    private static final String PROV_AT_TIME = PROV_O + "atTime";
    private static final String PROV_WAS_ASSOCIATED_WITH = PROV_O + "wasAssociatedWith";
    private static final String PROV_WAS_INFORMED_BY = PROV_O + "wasInformedBy";
    private static final String PROV_GENERATED = PROV_O + "generated";
    private static final String PROV_WAS_DERIVED_FROM = PROV_O + "wasDerivedFrom";

    private static final String RECORD_NAMESPACE = "https://matonto.org/records#";
    private static final String DISTRIBUTION_NAMESPACE = "https://matonto.org/distributions#";
    private static final String VERSION_NAMESPACE = "https://matonto.org/versions#";
    private static final String BRANCH_NAMESPACE = "https://matonto.org/branches#";
    private static final String IN_PROGRESS_COMMIT_NAMESPACE = "https://matonto.org/in-progress-commits#";
    private static final String COMMIT_NAMESPACE = "https://matonto.org/commits#";
    private static final String REVISION_NAMESPACE = "https://matonto.org/revisions#";
    private static final String ADDITIONS_NAMESPACE = "https://matonto.org/additions#";
    private static final String DELETIONS_NAMESPACE = "https://matonto.org/deletions#";
    private static final String DELETION_CONTEXT_FLAG = "https://matonto.org/has-deletions";

    // private static final String GET_RECORD_QUERY;
    private static final String FIND_RECORDS_QUERY;
    private static final String FIND_RECORDS_TYPE_FILTER_QUERY;
    private static final String COUNT_RECORDS_QUERY;
    private static final String COUNT_RECORDS_TYPE_FILTER_QUERY;
    private static final String GET_NEW_LATEST_VERSION;
    private static final String GET_COMMIT_CHAIN;
    private static final String COMMIT_BINDING = "commit";
    private static final String PARENT_BINDING = "parent";
    private static final String RECORD_BINDING = "record";
    private static final String RECORD_COUNT_BINDING = "record_count";
    private static final String TYPE_FILTER_BINDING = "type_filter";

    static {
        try {
            /*GET_RECORD_QUERY = IOUtils.toString(
                    SimpleCatalogManager.class.getResourceAsStream("/get-record.rq"),
                    "UTF-8"
            );*/
            FIND_RECORDS_QUERY = IOUtils.toString(
                    SimpleCatalogManager.class.getResourceAsStream("/find-records.rq"),
                    "UTF-8"
            );
            FIND_RECORDS_TYPE_FILTER_QUERY = IOUtils.toString(
                    SimpleCatalogManager.class.getResourceAsStream("/find-records-type-filter.rq"),
                    "UTF-8"
            );
            COUNT_RECORDS_QUERY = IOUtils.toString(
                    SimpleCatalogManager.class.getResourceAsStream("/count-records.rq"),
                    "UTF-8"
            );
            COUNT_RECORDS_TYPE_FILTER_QUERY = IOUtils.toString(
                    SimpleCatalogManager.class.getResourceAsStream("/count-records-type-filter.rq"),
                    "UTF-8"
            );
            GET_NEW_LATEST_VERSION = IOUtils.toString(
                    SimpleCatalogManager.class.getResourceAsStream("/get-new-latest-version.rq"),
                    "UTF-8"
            );
            GET_COMMIT_CHAIN = IOUtils.toString(
                    SimpleCatalogManager.class.getResourceAsStream("/get-commit-chain.rq"),
                    "UTF-8"
            );
        } catch (IOException e) {
            throw new MatOntoException(e);
        }
    }

    @Activate
    protected void start(Map<String, Object> props) {
        CatalogConfig config = Configurable.createConfigurable(CatalogConfig.class, props);
        publishedCatalogIRI = vf.createIRI(config.iri() + "-published");
        unpublishedCatalogIRI = vf.createIRI(config.iri() + "-unpublished");
        createSortingOptions();

        if (!resourceExists(publishedCatalogIRI)) {
            log.debug("Initializing the published MatOnto Catalog.");
            addCatalogToRepo(publishedCatalogIRI, config.title() + " (Published)", config.description());
        }

        if (!resourceExists(unpublishedCatalogIRI)) {
            log.debug("Initializing the unpublished MatOnto Catalog.");
            addCatalogToRepo(unpublishedCatalogIRI, config.title() + " (Unpublished)", config.description());
        }
    }

    @Modified
    protected void modified(Map<String, Object> props) {
        start(props);
    }

    @Override
    public Catalog getPublishedCatalog() throws MatOntoException {
        return getCatalog(publishedCatalogIRI);
    }

    @Override
    public Catalog getUnpublishedCatalog() throws MatOntoException {
        return getCatalog(unpublishedCatalogIRI);
    }

    @Override
    public PaginatedSearchResults<Record> findRecord(Resource catalogId, PaginatedSearchParams searchParams) {
        RepositoryConnection conn = repository.getConnection();
        Optional<Resource> typeParam = searchParams.getTypeFilter();

        // Get Total Count
        TupleQuery countQuery;
        if (typeParam.isPresent()) {
            countQuery = conn.prepareTupleQuery(COUNT_RECORDS_TYPE_FILTER_QUERY);
            countQuery.setBinding(TYPE_FILTER_BINDING, typeParam.get());
        } else {
            countQuery = conn.prepareTupleQuery(COUNT_RECORDS_QUERY);
        }

        TupleQueryResult countResults = countQuery.evaluate();

        int totalCount;
        BindingSet countBindingSet;
        if (countResults.hasNext()
                && (countBindingSet = countResults.next()).getBindingNames().contains(RECORD_COUNT_BINDING)) {
            totalCount = Bindings.requiredLiteral(countBindingSet, RECORD_COUNT_BINDING).intValue();
            countResults.close();
        } else {
            countResults.close();
            conn.close();
            return SearchResults.emptyResults();
        }

        log.debug("Resource count: " + totalCount);

        // Prepare Query
        int limit = searchParams.getLimit();
        int offset = searchParams.getOffset();

        String sortBinding;
        Resource sortByParam = searchParams.getSortBy();
        if (sortingOptions.get(sortByParam) != null) {
            sortBinding = sortingOptions.get(sortByParam);
        } else {
            log.warn("sortBy parameter must be in the allowed list. Sorting by modified date instead.");
            sortBinding = "modified";
        }

        String querySuffix;
        Optional<Boolean> ascendingParam = searchParams.getAscending();
        if (ascendingParam.isPresent() && ascendingParam.get()) {
            querySuffix = String.format("\nORDER BY ?%s\nLIMIT %d\nOFFSET %d", sortBinding,
                    limit, offset);
        } else {
            querySuffix = String.format("\nORDER BY DESC(?%s)\nLIMIT %d\nOFFSET %d",
                    sortBinding, limit, offset);
        }

        String queryString;
        TupleQuery query;
        if (typeParam.isPresent()) {
            queryString = FIND_RECORDS_TYPE_FILTER_QUERY + querySuffix;
            query = conn.prepareTupleQuery(queryString);
            query.setBinding(TYPE_FILTER_BINDING, typeParam.get());
        } else {
            queryString = FIND_RECORDS_QUERY + querySuffix;
            query = conn.prepareTupleQuery(queryString);
        }

        log.debug("Query String:\n" + queryString);
        log.debug("Query Plan:\n" + query);

        // Get Results
        TupleQueryResult result = query.evaluate();

        List<Record> records = new ArrayList<>();
        BindingSet resultsBindingSet;
        while (result.hasNext() && (resultsBindingSet = result.next()).getBindingNames().contains(RECORD_BINDING)) {
            Resource resource = vf.createIRI(Bindings.requiredResource(resultsBindingSet, RECORD_BINDING)
                    .stringValue());
            Record record = processRecordBindingSet(resultsBindingSet, resource);
            records.add(record);
        }

        result.close();
        conn.close();

        log.debug("Result set size: " + records.size());

        int pageNumber = (offset / limit) + 1;

        if (records.size() > 0) {
            return new SimpleSearchResults<>(records, totalCount, limit, pageNumber);
        } else {
            return SearchResults.emptyResults();
        }
    }

    @Override
    public Set<Resource> getRecordIds(Resource catalogId) {
        RepositoryConnection conn = null;
        Set<Resource> results = new HashSet<>();
        try {
            conn = repository.getConnection();
            RepositoryResult<Statement> statements = conn.getStatements(null, vf.createIRI(Record.catalog_IRI),
                    catalogId);
            statements.forEach(statement -> results.add(statement.getSubject()));
        } catch (RepositoryException e) {
            throw new MatOntoException("Error in repository connection.", e);
        } finally {
            closeConnection(conn);
        }
        return results;
    }

    @Override
    public <T extends Record> T createRecord(RecordConfig config, OrmFactory<T> factory) {
        OffsetDateTime now = OffsetDateTime.now();
        return addPropertiesToRecord(factory.createNew(vf.createIRI(RECORD_NAMESPACE + UUID.randomUUID())), config, now,
                now);
    }

    @Override
    public boolean addRecord(Resource catalogId, Record record) throws MatOntoException {
        if (!getRecord(catalogId, record.getResource()).isPresent()) {
            RepositoryConnection conn = null;
            try {
                conn = repository.getConnection();
                record.setCatalog(getCatalog(catalogId));
                conn.add(record.getModel(), record.getResource());
            } catch (RepositoryException e) {
                throw new MatOntoException("Error in repository connection", e);
            } finally {
                closeConnection(conn);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean updateRecord(Resource catalogId, Record newRecord) throws MatOntoException {
        if (getRecord(catalogId, newRecord.getResource()).isPresent()) {
            update(newRecord.getResource(), newRecord.getModel());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean removeRecord(Resource catalogId, Resource recordId) throws MatOntoException {
        if (getRecord(catalogId, recordId).isPresent()) {
            remove(recordId);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Optional<Record> getRecord(Resource catalogId, Resource recordId) throws MatOntoException {
        RepositoryConnection conn = null;
        Model recordModel = mf.createModel();
        try {
            conn = repository.getConnection();
            RepositoryResult<Statement> statements = conn.getStatements(recordId, null, null, recordId);
            statements.forEach(recordModel::add);
        } catch (RepositoryException e) {
            throw new MatOntoException("Error in repository connection.", e);
        } finally {
            closeConnection(conn);
        }
        if (recordModel.size() != 0 && recordModel.contains(recordId, vf.createIRI(Record.catalog_IRI), catalogId,
                recordId)) {
            return Optional.of(recordFactory.createNew(recordId, recordModel));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Distribution createDistribution(DistributionConfig config) {
        OffsetDateTime now = OffsetDateTime.now();

        Distribution distribution = distributionFactory.createNew(vf.createIRI(DISTRIBUTION_NAMESPACE + UUID.randomUUID()));
        distribution.setProperty(vf.createLiteral(config.getTitle()), vf.createIRI(DC_TITLE));
        distribution.setProperty(vf.createLiteral(now), vf.createIRI(DC_ISSUED));
        distribution.setProperty(vf.createLiteral(now), vf.createIRI(DC_MODIFIED));
        if (config.getDescription() != null) {
            distribution.setProperty(vf.createLiteral(config.getDescription()), vf.createIRI(DC_DESCRIPTION));
        }
        if (config.getFormat() != null) {
            distribution.setProperty(vf.createLiteral(config.getFormat()), vf.createIRI(DC_TERMS + "format"));
        }
        if (config.getAccessURL() != null) {
            distribution.setAccessURL(config.getAccessURL());
        }
        if (config.getDownloadURL() != null) {
            distribution.setDownloadURL(config.getDownloadURL());
        }

        return distribution;
    }

    @Override
    public boolean addDistributionToUnversionedRecord(Distribution distribution, Resource unversionedRecordId) {
        return addDistribution(distribution, unversionedRecordId, UnversionedRecord.unversionedDistribution_IRI);
    }

    @Override
    public boolean addDistributionToVersion(Distribution distribution, Resource versionId) {
        return addDistribution(distribution, versionId, Version.versionedDistribution_IRI);
    }

    @Override
    public boolean updateDistribution(Distribution newDistribution) {
        if (resourceExists(newDistribution.getResource())) {
            update(newDistribution.getResource(), newDistribution.getModel());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean removeDistributionFromUnversionedRecord(Resource distributionId, Resource unversionedRecordId) {
        return removeObjectWithRelationship(distributionId, unversionedRecordId,
                UnversionedRecord.unversionedDistribution_IRI);
    }

    @Override
    public boolean removeDistributionFromVersion(Resource distributionId, Resource versionId) {
        return removeObjectWithRelationship(distributionId, versionId, Version.versionedDistribution_IRI);
    }

    @Override
    public <T extends Version> T createVersion(String title, @Nullable String description, OrmFactory<T> factory) {
        OffsetDateTime now = OffsetDateTime.now();

        T version = factory.createNew(vf.createIRI(VERSION_NAMESPACE + UUID.randomUUID()));
        version.setProperty(vf.createLiteral(title), vf.createIRI(DC_TITLE));
        if (description != null) {
            version.setProperty(vf.createLiteral(description), vf.createIRI(DC_DESCRIPTION));
        }
        version.setProperty(vf.createLiteral(now), vf.createIRI(DC_ISSUED));
        version.setProperty(vf.createLiteral(now), vf.createIRI(DC_MODIFIED));

        return version;
    }

    @Override
    public boolean addVersion(Version version, Resource versionedRecordId) {
        if (!resourceExists(version.getResource()) && resourceExists(versionedRecordId)) {
            RepositoryConnection conn = null;
            try {
                conn = repository.getConnection();
                IRI latestVersionResource = vf.createIRI(VersionedRecord.latestVersion_IRI);
                conn.begin();
                conn.remove(versionedRecordId, latestVersionResource, null, versionedRecordId);
                conn.add(versionedRecordId, latestVersionResource, version.getResource(), versionedRecordId);
                conn.add(version.getModel(), version.getResource());
                conn.commit();
            } catch (RepositoryException e) {
                throw new MatOntoException("Error in repository connection", e);
            } finally {
                closeConnection(conn);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean updateVersion(Version newVersion) {
        if (resourceExists(newVersion.getResource())) {
            update(newVersion.getResource(), newVersion.getModel());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean removeVersion(Resource versionId, Resource versionedRecordId) {
        if (removeObjectWithRelationship(versionId, versionedRecordId, VersionedRecord.version_IRI)) {
            RepositoryConnection conn = null;
            try {
                conn = repository.getConnection();
                IRI latestVersionIRI = vf.createIRI(VersionedRecord.latestVersion_IRI);
                if (conn.getStatements(versionedRecordId, latestVersionIRI, versionId, versionedRecordId).hasNext()) {
                    conn.begin();
                    conn.remove(versionedRecordId, latestVersionIRI, versionId, versionedRecordId);
                    TupleQuery query = conn.prepareTupleQuery(GET_NEW_LATEST_VERSION);
                    query.setBinding(RECORD_BINDING, versionedRecordId);
                    TupleQueryResult result = query.evaluate();

                    Optional<Binding> binding;
                    if (result.hasNext() && (binding = result.next().getBinding("version")).isPresent()) {
                        conn.add(versionedRecordId, latestVersionIRI, binding.get().getValue(), versionedRecordId);
                    }
                    conn.commit();
                }
            } catch (RepositoryException e) {
                throw new MatOntoException("Error in repository connection", e);
            } finally {
                closeConnection(conn);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Branch createBranch(String title, @Nullable String description) {
        OffsetDateTime now = OffsetDateTime.now();

        Branch branch = branchFactory.createNew(vf.createIRI(BRANCH_NAMESPACE + UUID.randomUUID()));
        branch.setProperty(vf.createLiteral(title), vf.createIRI(DC_TITLE));
        branch.setProperty(vf.createLiteral(now), vf.createIRI(DC_ISSUED));
        branch.setProperty(vf.createLiteral(now), vf.createIRI(DC_MODIFIED));
        if (description != null) {
            branch.setProperty(vf.createLiteral(description), vf.createIRI(DC_DESCRIPTION));
        }

        return branch;
    }

    @Override
    public boolean addBranch(Branch branch, Resource versionedRDFRecordId) {
        if (!resourceExists(branch.getResource()) && resourceExists(versionedRDFRecordId)) {
            RepositoryConnection conn = null;
            try {
                conn = repository.getConnection();
                conn.begin();
                conn.add(versionedRDFRecordId, vf.createIRI(VersionedRDFRecord.branch_IRI), branch.getResource(),
                        versionedRDFRecordId);
                conn.add(branch.getModel(), branch.getResource());
                conn.commit();
            } catch (RepositoryException e) {
                throw new MatOntoException("Error in repository connection", e);
            } finally {
                closeConnection(conn);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean updateBranch(Branch newBranch) {
        if (resourceExists(newBranch.getResource())) {
            update(newBranch.getResource(), newBranch.getModel());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean removeBranch(Resource branchId, Resource versionedRDFRecordId) {
        return removeObjectWithRelationship(branchId, versionedRDFRecordId, VersionedRDFRecord.branch_IRI);
    }

    @Override
    public Commit createCommit(InProgressCommit inProgressCommit, String message) {
        IRI associatedWith = vf.createIRI(PROV_WAS_ASSOCIATED_WITH);
        IRI informedBy = vf.createIRI(PROV_WAS_INFORMED_BY);

        OffsetDateTime now = OffsetDateTime.now();
        Value user = inProgressCommit.getProperty(associatedWith).get();

        String metadata = now.toString() + user.stringValue();

        Set<Value> parents = inProgressCommit.getProperties(informedBy);
        if (parents.size() != 0) {
            metadata += parents.stream()
                    .sorted((iri1, iri2) -> iri1.stringValue().compareTo(iri2.stringValue()))
                    .map(Value::stringValue).collect(Collectors.joining(""));
        }

        Commit commit = commitFactory.createNew(vf.createIRI(COMMIT_NAMESPACE + DigestUtils.shaHex(metadata)));
        commit.setProperty(vf.createLiteral(now), vf.createIRI(PROV_AT_TIME));
        commit.setProperty(vf.createLiteral(message), vf.createIRI(DC_TITLE));
        commit.setProperty(user, associatedWith);

        IRI generatedIRI = vf.createIRI(PROV_GENERATED);
        commit.setProperty(inProgressCommit.getProperty(generatedIRI).get(), generatedIRI);
        if (parents.size() != 0) {
            commit.setProperties(parents, informedBy);
        }

        Model revisionModel = inProgressCommit.getModel();
        revisionModel.remove(inProgressCommit.getResource(), null, null);
        commit.getModel().addAll(revisionModel);

        return commit;
    }

    @Override
    public Optional<InProgressCommit> createInProgressCommit(@Nullable Set<Commit> parents, User user,
                                                             Resource branchId) {
        if (resourceExists(branchId)) {
            UUID uuid = UUID.randomUUID();

            Revision revision = revisionFactory.createNew(vf.createIRI(REVISION_NAMESPACE + uuid));
            revision.setAdditions(vf.createIRI(ADDITIONS_NAMESPACE + uuid));
            revision.setDeletions(vf.createIRI(DELETIONS_NAMESPACE + uuid));
            if (parents != null) {
                revision.setProperties(parents.stream().map(parent ->
                                parent.getProperty(vf.createIRI(PROV_GENERATED)).get()).collect(Collectors.toSet()),
                        vf.createIRI(PROV_WAS_DERIVED_FROM));
            }

            InProgressCommit inProgressCommit = inProgressCommitFactory.createNew(vf.createIRI(
                    IN_PROGRESS_COMMIT_NAMESPACE + uuid));
            inProgressCommit.setOnBranch(branchFactory.createNew(branchId));
            inProgressCommit.setProperty(user.getResource(), vf.createIRI(PROV_WAS_ASSOCIATED_WITH));
            inProgressCommit.setProperty(revision.getResource(), vf.createIRI(PROV_GENERATED));
            if (parents != null) {
                inProgressCommit.setProperties(parents.stream().map(Commit::getResource).collect(Collectors.toSet()),
                        vf.createIRI(PROV_WAS_INFORMED_BY));
            }
            inProgressCommit.getModel().addAll(revision.getModel());

            return Optional.of(inProgressCommit);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public boolean addAdditions(Model statements, Resource commitId) throws MatOntoException {
        if (resourceExists(commitId)) {
            RepositoryConnection conn = null;
            try {
                conn = repository.getConnection();
                Resource additions = getAdditionsResource(commitId, conn);
                Resource deletions = getDeletionsResource(commitId, conn);
                conn.begin();
                for (Statement statement : statements) {
                    if (!conn.getStatements(statement.getSubject(), statement.getPredicate(), statement.getObject(),
                            deletions).hasNext()) {
                        conn.add(statement, additions);
                    } else {
                        conn.remove(statement, deletions);
                    }
                }
                conn.commit();
            } catch (RepositoryException e) {
                throw new MatOntoException("Error in repository connection", e);
            } finally {
                closeConnection(conn);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean addDeletions(Model statements, Resource commitId) throws MatOntoException {
        if (resourceExists(commitId)) {
            RepositoryConnection conn = null;
            try {
                conn = repository.getConnection();
                Resource additions = getAdditionsResource(commitId, conn);
                Resource deletions = getDeletionsResource(commitId, conn);
                conn.begin();
                for (Statement statement : statements) {
                    if (!conn.getStatements(statement.getSubject(), statement.getPredicate(), statement.getObject(),
                            additions).hasNext()) {
                        conn.add(statement, deletions);
                    } else {
                        conn.remove(statement, additions);
                    }
                }
                conn.commit();
            } catch (RepositoryException e) {
                throw new MatOntoException("Error in repository connection", e);
            } finally {
                closeConnection(conn);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean addCommitToBranch(Commit commit, Resource branchId) throws MatOntoException {
        if (!resourceExists(commit.getResource()) && resourceExists(branchId)) {
            RepositoryConnection conn = null;
            try {
                conn = repository.getConnection();
                IRI headIRI = vf.createIRI(Branch.head_IRI);
                conn.begin();
                conn.remove(branchId, headIRI, null, branchId);
                conn.add(branchId, headIRI, commit.getResource(), branchId);
                conn.add(commit.getModel(), commit.getResource());
                conn.commit();
            } catch (RepositoryException e) {
                throw new MatOntoException("Error in repository connection", e);
            } finally {
                closeConnection(conn);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean addCommitToTag(Commit commit, Resource tagId) throws MatOntoException {
        if (!resourceExists(commit.getResource()) && resourceExists(tagId)) {
            RepositoryConnection conn = null;
            try {
                conn = repository.getConnection();
                IRI commitIRI = vf.createIRI(Tag.commit_IRI);
                conn.begin();
                conn.remove(tagId, commitIRI, null, tagId);
                conn.add(tagId, commitIRI, commit.getResource(), tagId);
                conn.add(commit.getModel(), commit.getResource());
                conn.commit();
            } catch (RepositoryException e) {
                throw new MatOntoException("Error in repository connection", e);
            } finally {
                closeConnection(conn);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean addInProgressCommit(InProgressCommit inProgressCommit) throws MatOntoException {
        if (!resourceExists(inProgressCommit.getResource())) {
            RepositoryConnection conn = null;
            try {
                conn = repository.getConnection();
                conn.add(inProgressCommit.getModel(), inProgressCommit.getResource());
            } catch (RepositoryException e) {
                throw new MatOntoException("Error in repository connection", e);
            } finally {
                closeConnection(conn);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Optional<Commit> getCommit(Resource commitId) throws MatOntoException {
        RepositoryConnection conn = null;
        Model commitModel = mf.createModel();
        try {
            conn = repository.getConnection();
            RepositoryResult<Statement> statements = conn.getStatements(null, null, null, commitId);
            statements.forEach(commitModel::add);
        } catch (RepositoryException e) {
            throw new MatOntoException("Error in repository connection.", e);
        } finally {
            closeConnection(conn);
        }
        if (commitModel.size() != 0) {
            return Optional.of(commitFactory.createNew(commitId, commitModel));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Set<Commit> getCommitChain(Resource commitId) {
        return null;
    }

    @Override
    public Optional<Model> getCompiledResource(Resource commitId) throws MatOntoException {
        if (resourceExists(commitId)) {
            RepositoryConnection conn = null;
            try {
                conn = repository.getConnection();
                Iterator<Value> iterator = getCommitChainIterator(commitId, conn);
                return Optional.of(createModelFromIterator(iterator, commitId, conn));
            } catch (RepositoryException e) {
                throw new MatOntoException("Error in repository connection", e);
            } finally {
                closeConnection(conn);
            }
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Map<String, Map<String, List<Model>>>> getConflicts(Resource commitId1, Resource commitId2) throws
            MatOntoException {
        if (resourceExists(commitId1) && resourceExists(commitId2)) {
            RepositoryConnection conn = null;
            try {
                conn = repository.getConnection();
                Iterator<Value> commitIterator1 = getCommitChainIterator(commitId1, conn);
                Iterator<Value> commitIterator2 = getCommitChainIterator(commitId2, conn);
                while (commitIterator1.hasNext() && commitIterator2.hasNext()) {
                    if (!commitIterator1.next().equals(commitIterator2.next())) {
                        break;
                    }
                }
                Set<String> conflicts = new HashSet<>();

                Model model1 = createModelFromIterator(commitIterator1, commitId1, conn);
                Model model2 = createModelFromIterator(commitIterator2, commitId2, conn);

                Map<Value, Set<Value>> map1 = new HashMap<>();
                model1.forEach(statement -> {
                    Value subject = statement.getSubject();
                    Value predicate = statement.getPredicate();
                    if (map1.containsKey(statement.getSubject())) {
                        map1.get(subject).add(predicate);
                    } else {
                        map1.put(subject, Stream.of(predicate).collect(Collectors.toSet()));
                    }
                    if (statement.getContext().isPresent() && predicate.equals(vf.createIRI(RDF_TYPE))) {
                        conflicts.add(subject.stringValue());
                    }
                });

                Map<Value, Set<Value>> map2 = new HashMap<>();
                model2.forEach(statement -> {
                    Value subject = statement.getSubject();
                    Value predicate = statement.getPredicate();
                    if (map2.containsKey(statement.getSubject())) {
                        map2.get(subject).add(predicate);
                    } else {
                        map2.put(subject, Stream.of(predicate).collect(Collectors.toSet()));
                    }
                    if (statement.getContext().isPresent() && predicate.equals(vf.createIRI(RDF_TYPE))) {
                        conflicts.add(subject.stringValue());
                    }
                });

                Set<Value> subjects1 = map1.keySet();
                Set<Value> subjects2 = map2.keySet();

                subjects1.retainAll(subjects2);

                subjects1.forEach(subject -> {
                    Set<Value> predicates1 = map1.get(subject);
                    Set<Value> predicates2 = map2.get(subject);
                    predicates1.retainAll(predicates2);
                    if (predicates1.size() != 0) {
                        conflicts.add(subject.stringValue());
                    }
                });

                conflicts.forEach(System.out::println);

                /*System.out.println(map1);
                System.out.println(map2);*/

                /*model1.forEach(s -> System.out.println("Commit 1: " + s.getSubject() + " " + s.getPredicate() + " "
                        + s.getObject() + " " + s.getContext()));
                model2.forEach(s -> System.out.println("Commit 2: " + s.getSubject() + " " + s.getPredicate() + " "
                        + s.getObject() + " " + s.getContext()));*/
            } catch (RepositoryException e) {
                throw new MatOntoException("Error in repository connection.", e);
            } finally {
                closeConnection(conn);
            }
        } else {
            throw new MatOntoException("One or both of the commit IRIs could not be found in the Repository");
        }
        return Optional.empty();
    }

    /**
     * Adds the model for a Catalog to the repository which contains the provided metadata using the provided Resource
     * as the context.
     *
     * @param catalogId The Resource identifying the Catalog you wish you create.
     * @param title The title text.
     * @param description The description text.
     */
    private void addCatalogToRepo(Resource catalogId, String title, String description) {
        RepositoryConnection conn = null;
        try {
            conn = repository.getConnection();
            OffsetDateTime now = OffsetDateTime.now();

            Catalog catalog = catalogFactory.createNew(catalogId);
            catalog.setProperty(vf.createLiteral(title), vf.createIRI(DC_TITLE));
            catalog.setProperty(vf.createLiteral(description), vf.createIRI(DC_DESCRIPTION));
            catalog.setProperty(vf.createLiteral(now), vf.createIRI(DC_ISSUED));
            catalog.setProperty(vf.createLiteral(now), vf.createIRI(DC_MODIFIED));

            conn.add(catalog.getModel(), catalogId);
        } catch (RepositoryException e) {
            throw new MatOntoException("Error in repository connection.", e);
        } finally {
            closeConnection(conn);
        }

    }

    /**
     * Gets the pre-existing catalog using the provided IRI.
     *
     * @param catalogId The Resource identifying the Catalog that the user wishes to get back.
     * @return The Catalog identified by the provided IRI.
     * @throws MatOntoException if RepositoryConnection has a problem.
     */
    private Catalog getCatalog(Resource catalogId) throws MatOntoException {
        RepositoryConnection conn = null;
        Model catalogModel = mf.createModel();
        try {
            conn = repository.getConnection();
            RepositoryResult<Statement> statements = conn.getStatements(catalogId, null, null, catalogId);
            statements.forEach(catalogModel::add);
        } catch (RepositoryException e) {
            throw new MatOntoException("Error in repository connection", e);
        } finally {
            closeConnection(conn);
        }
        return catalogFactory.getExisting(catalogId, catalogModel);
    }

    /**
     * Checks to see if the provided Resource exists in the Repository.
     *
     * @param resourceIRI The Resource to look for in the Repository
     * @return True if the Resource is in the Repository; otherwise, false
     */
    private boolean resourceExists(Resource resourceIRI) throws MatOntoException {
        RepositoryConnection conn = null;
        try {
            conn = repository.getConnection();
            return conn.getStatements(null, null, null, resourceIRI).hasNext();
        } catch (RepositoryException e) {
            throw new MatOntoException("Error in repository connection.", e);
        } finally {
            closeConnection(conn);
        }

    }

    /**
     * Closes the passed connection to the repository.
     *
     * @param conn a connection to the repository for mappings
     */
    private void closeConnection(RepositoryConnection conn) {
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (RepositoryException e) {
            log.warn("Could not close Repository." + e.toString());
        }
    }

    /**
     * Creates the base for the sorting options Object.
     */
    private void createSortingOptions() {
        sortingOptions.put(vf.createIRI(DC_MODIFIED), "modified");
        sortingOptions.put(vf.createIRI(DC_ISSUED), "issued");
        sortingOptions.put(vf.createIRI(DC_TITLE), "title");
    }

    /**
     * Adds the properties provided by the parameters to the provided Record.
     *
     * @param record The Record to add the properties to.
     * @param config The RecordConfig which contains the properties to set.
     * @param issued The OffsetDateTime of when the Record was issued.
     * @param modified The OffsetDateTime of when the Record was modified.
     * @param <T> An Object which extends the Record class.
     * @return T which contains all of the properties provided by the parameters.
     */
    private <T extends Record> T addPropertiesToRecord(T record, RecordConfig config, OffsetDateTime issued,
                                                       OffsetDateTime modified) {
        record.setProperty(vf.createLiteral(config.getTitle()), vf.createIRI(DC_TITLE));
        record.setProperty(vf.createLiteral(issued), vf.createIRI(DC_ISSUED));
        record.setProperty(vf.createLiteral(modified), vf.createIRI(DC_MODIFIED));
        record.setProperties(config.getPublishers().stream().map(User::getResource).collect(Collectors.toSet()),
                vf.createIRI(DC_TERMS + "publisher"));
        record.setProperty(vf.createLiteral(config.getIdentifier()), vf.createIRI(DC_TERMS + "identifier"));
        if (config.getDescription() != null) {
            record.setProperty(vf.createLiteral(config.getDescription()), vf.createIRI(DC_DESCRIPTION));
        }
        if (config.getKeywords() != null) {
            record.setKeyword(config.getKeywords().stream().map(keyword ->
                    vf.createLiteral(keyword)).collect(Collectors.toSet()));
        }
        return record;
    }

    /**
     * Creates a Record from the data provided in the BindingSet and Resource getting additional information from the
     * RepositoryConnection if necessary.
     *
     * @param bindingSet The BindingSet which contains the information about the Record.
     * @param resource The Resource which identifies the created Record.
     * @param conn The RepositoryConnection to get additional statements from the repository.
     * @return A Record created from the provided information.
     */
    private Record processRecordBindingSet(BindingSet bindingSet, Resource resource) {
        String title = Bindings.requiredLiteral(bindingSet, "title").stringValue();
        String identifier = Bindings.requiredLiteral(bindingSet, "identifier").stringValue();

        Set<User> publishers = new HashSet<>();
        bindingSet.getBinding("publisher").ifPresent(binding -> {
            String[] values = StringUtils.split(binding.getValue().stringValue(), ",");

            for (String value : values) {
                publishers.add(userFactory.createNew(vf.createIRI(value)));
            }
        });

        RecordConfig.Builder builder = new RecordConfig.Builder(title, identifier, publishers);

        bindingSet.getBinding("description").ifPresent(binding ->
                builder.description(binding.getValue().stringValue()));

        bindingSet.getBinding("keyword").ifPresent(binding ->
                builder.keywords(new HashSet<>(Arrays.asList(StringUtils.split(binding.getValue().stringValue(),
                        ",")))));

        OffsetDateTime issued = Bindings.requiredLiteral(bindingSet, "issued").dateTimeValue();
        OffsetDateTime modified = Bindings.requiredLiteral(bindingSet, "modified").dateTimeValue();

        return addPropertiesToRecord(recordFactory.createNew(resource), builder.build(), issued, modified);
    }

    /**
     * Adds the provided Distribution to the identified Resource adding a triple based on the provided predicate.
     *
     * @param distribution The Distribution to add to the Repository.
     * @param resourceId The Resource identified to get the Distribution added to it.
     * @param predicate The String containing the predicate for the new statement.
     * @return True if the Distribution was successfully added; otherwise, false.
     */
    private boolean addDistribution(Distribution distribution, Resource resourceId, String predicate) throws
            MatOntoException {
        if (!resourceExists(distribution.getResource()) && resourceExists(resourceId)) {
            RepositoryConnection conn = null;
            try {
                conn = repository.getConnection();
                conn.begin();
                conn.add(resourceId, vf.createIRI(predicate), distribution.getResource(), resourceId);
                conn.add(distribution.getModel(), distribution.getResource());
                conn.commit();
            } catch (RepositoryException e) {
                throw new MatOntoException("Error in repository connection", e);
            } finally {
                closeConnection(conn);
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Removes the Object identified by the Resource from the Resource identified to be removed from and removes
     * the Object statement using the provided String predicate.
     *
     * @param objectId The Resource identifying the Object to remove.
     * @param removeFromId The Resource identifying which Object to remove the Distribution from.
     * @param predicate The String identifying the predicate for the statement that needs to be removed.
     * @return True if the Distribution was successfully removed; otherwise, false.
     */
    private boolean removeObjectWithRelationship(Resource objectId, Resource removeFromId, String predicate) throws
            MatOntoException {
        RepositoryConnection conn = null;
        try {
            conn = repository.getConnection();
            IRI relationshipIRI = vf.createIRI(predicate);
            boolean hasRelationship = conn.getStatements(removeFromId, relationshipIRI, objectId, removeFromId)
                    .hasNext();
            if (resourceExists(objectId) && resourceExists(removeFromId) && hasRelationship) {
                conn.begin();
                conn.remove(objectId, null, null, objectId);
                conn.remove(removeFromId, relationshipIRI, objectId, removeFromId);
                conn.commit();
                return true;
            } else {
                return false;
            }
        } catch (RepositoryException e) {
            throw new MatOntoException("Error in repository connection", e);
        } finally {
            closeConnection(conn);
        }
    }

    /**
     * Updates the Resource which is identified using the provided Model.
     *
     * @param resourceId The Resource identifying the Object that you wish to update.
     * @param model The Model containing the underlying information about the Object you are updating.
     */
    private void update(Resource resourceId, Model model) throws MatOntoException {
        RepositoryConnection conn = null;
        try {
            conn = repository.getConnection();
            conn.begin();
            conn.remove(resourceId, null, null, resourceId);
            conn.add(model, resourceId);
            conn.commit();
        } catch (RepositoryException e) {
            throw new MatOntoException("Error in repository connection", e);
        } finally {
            closeConnection(conn);
        }
    }

    /**
     * Removes the Resource which is identified.
     *
     * @param resourceId The Resource identifying the element to be removed.
     */
    private void remove(Resource resourceId) throws MatOntoException {
        RepositoryConnection conn = null;
        try {
            conn = repository.getConnection();
            conn.remove(resourceId, null, null, resourceId);
        } catch (RepositoryException e) {
            throw new MatOntoException("Error in repository connection", e);
        } finally {
            closeConnection(conn);
        }
    }

    /**
     * Gets the Resource identifying the graph that contain the additions statements.
     *
     * @param commitId The Resource identifying the Commit that have the additions.
     * @param conn The RepositoryConnection to be used to get the Resource from.
     * @return The Resource for the additions graph.
     */
    private Resource getAdditionsResource(Resource commitId, RepositoryConnection conn) {
        return (Resource)conn.getStatements(null, vf.createIRI(Revision.additions_IRI), null, commitId).next()
                .getObject();
    }

    /**
     * Gets the Resource identifying the graph that contain the deletions statements.
     *
     * @param commitId The Resource identifying the Commit that have the deletions.
     * @param conn The RepositoryConnection to be used to get the Resource from.
     * @return The Resource for the deletions graph.
     */
    private Resource getDeletionsResource(Resource commitId, RepositoryConnection conn) {
        return (Resource)conn.getStatements(null, vf.createIRI(Revision.deletions_IRI), null, commitId).next()
                .getObject();
    }

    /**
     * Adds the statements from the Revision associated with the Commit identified by the provided Resource to the
     * provided Model using the RepositoryConnection to get the statements from the repository.
     *
     * @param model The Model to update.
     * @param commitId The Resource identifying the Commit.
     * @param conn The RepositoryConnection to query the repository.
     * @return A Model with the proper statements added.
     */
    private Model addRevisionStatementsToModel(Model model, Resource commitId, RepositoryConnection conn) {
        Resource additionsId = getAdditionsResource(commitId, conn);
        Resource deletionsId = getDeletionsResource(commitId, conn);
        conn.getStatements(null, null, null, additionsId).forEach(statement -> model.add(statement.getSubject(),
                statement.getPredicate(), statement.getObject()));
        conn.getStatements(null, null, null, deletionsId).forEach(statement -> {
            if(model.contains(statement.getSubject(), statement.getPredicate(), statement.getObject())) {
                model.remove(statement.getSubject(), statement.getPredicate(), statement.getObject());
            } else {
                model.add(statement.getSubject(), statement.getPredicate(), statement.getObject(),
                        vf.createIRI(DELETION_CONTEXT_FLAG));
            }
        });
        return model;
    }

    /**
     * Gets an iterator which contains all of the Resources (commits) leading up to the provided Resource identifying a
     * commit. NOTE: this iterator does not contain the commit which you started at.
     *
     * @param commitId The Resource identifying the commit that you want to get the chain for.
     * @param conn The RepositoryConnection which will be queried for the Commits.
     * @return Iterator of Values containing the requested commits.
     */
    private Iterator<Value> getCommitChainIterator(Resource commitId, RepositoryConnection conn) {
        TupleQuery query = conn.prepareTupleQuery(GET_COMMIT_CHAIN);
        query.setBinding(COMMIT_BINDING, commitId);
        TupleQueryResult result = query.evaluate();
        LinkedList<Value> commits = new LinkedList<>();
        result.forEach(bindingSet -> bindingSet.getBinding(PARENT_BINDING).ifPresent(binding ->
                commits.add(binding.getValue())));
        return commits.descendingIterator();
    }

    /**
     * Builds the Model based on the provided Iterator and Resource.
     *
     * @param iterator The Iterator of commits which are supposed to be contained in the Model.
     * @param commitId The Resource identifying the Commit which you started with.
     * @param conn The RepositoryConnection which contains the requested Commits.
     * @return The Model containing the summation of all the Commits statements.
     */
    private Model createModelFromIterator(Iterator<Value> iterator, Resource commitId, RepositoryConnection conn) {
        Model model = mf.createModel();
        iterator.forEachRemaining(value -> addRevisionStatementsToModel(model, (Resource)value, conn));
        return addRevisionStatementsToModel(model, commitId, conn);
    }
}
