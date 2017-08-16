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

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.matonto.catalog.api.ontologies.mcat.GraphRevision;
import org.matonto.catalog.api.ontologies.mcat.GraphRevisionFactory;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.Rio;
import org.openrdf.sail.memory.MemoryStore;

import org.matonto.catalog.api.CatalogUtilsService;
import org.matonto.catalog.api.PaginatedSearchParams;
import org.matonto.catalog.api.PaginatedSearchResults;
import org.matonto.catalog.api.builder.Conflict;
import org.matonto.catalog.api.builder.Difference;
import org.matonto.catalog.api.ontologies.mcat.Branch;
import org.matonto.catalog.api.ontologies.mcat.BranchFactory;
import org.matonto.catalog.api.ontologies.mcat.Catalog;
import org.matonto.catalog.api.ontologies.mcat.CatalogFactory;
import org.matonto.catalog.api.ontologies.mcat.Commit;
import org.matonto.catalog.api.ontologies.mcat.CommitFactory;
import org.matonto.catalog.api.ontologies.mcat.Distribution;
import org.matonto.catalog.api.ontologies.mcat.DistributionFactory;
import org.matonto.catalog.api.ontologies.mcat.InProgressCommit;
import org.matonto.catalog.api.ontologies.mcat.InProgressCommitFactory;
import org.matonto.catalog.api.ontologies.mcat.Record;
import org.matonto.catalog.api.ontologies.mcat.RecordFactory;
import org.matonto.catalog.api.ontologies.mcat.Revision;
import org.matonto.catalog.api.ontologies.mcat.RevisionFactory;
import org.matonto.catalog.api.ontologies.mcat.Tag;
import org.matonto.catalog.api.ontologies.mcat.TagFactory;
import org.matonto.catalog.api.ontologies.mcat.UnversionedRecord;
import org.matonto.catalog.api.ontologies.mcat.UnversionedRecordFactory;
import org.matonto.catalog.api.ontologies.mcat.UserBranch;
import org.matonto.catalog.api.ontologies.mcat.UserBranchFactory;
import org.matonto.catalog.api.ontologies.mcat.Version;
import org.matonto.catalog.api.ontologies.mcat.VersionFactory;
import org.matonto.catalog.api.ontologies.mcat.VersionedRDFRecord;
import org.matonto.catalog.api.ontologies.mcat.VersionedRDFRecordFactory;
import org.matonto.catalog.api.ontologies.mcat.VersionedRecord;
import org.matonto.catalog.api.ontologies.mcat.VersionedRecordFactory;
import org.matonto.jaas.api.ontologies.usermanagement.User;
import org.matonto.jaas.api.ontologies.usermanagement.UserFactory;
import org.matonto.ontologies.dcterms._Thing;
import org.matonto.ontologies.provo.Activity;
import org.matonto.ontologies.provo.Entity;
import org.matonto.ontologies.provo.InstantaneousEvent;
import org.matonto.persistence.utils.RepositoryResults;
import org.matonto.rdf.api.IRI;
import org.matonto.rdf.api.Model;
import org.matonto.rdf.api.ModelFactory;
import org.matonto.rdf.api.Resource;
import org.matonto.rdf.api.ValueFactory;
import org.matonto.rdf.core.impl.sesame.LinkedHashModelFactory;
import org.matonto.rdf.core.impl.sesame.SimpleValueFactory;
import org.matonto.rdf.core.utils.Values;
import org.matonto.rdf.orm.OrmFactory;
import org.matonto.rdf.orm.conversion.ValueConverterRegistry;
import org.matonto.rdf.orm.conversion.impl.DefaultValueConverterRegistry;
import org.matonto.rdf.orm.conversion.impl.DoubleValueConverter;
import org.matonto.rdf.orm.conversion.impl.FloatValueConverter;
import org.matonto.rdf.orm.conversion.impl.IRIValueConverter;
import org.matonto.rdf.orm.conversion.impl.IntegerValueConverter;
import org.matonto.rdf.orm.conversion.impl.LiteralValueConverter;
import org.matonto.rdf.orm.conversion.impl.ResourceValueConverter;
import org.matonto.rdf.orm.conversion.impl.ShortValueConverter;
import org.matonto.rdf.orm.conversion.impl.StringValueConverter;
import org.matonto.rdf.orm.conversion.impl.ValueValueConverter;
import org.matonto.rdf.orm.impl.ThingFactory;
import org.matonto.repository.api.Repository;
import org.matonto.repository.api.RepositoryConnection;
import org.matonto.repository.impl.sesame.SesameRepositoryWrapper;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SimpleCatalogManagerTest {

    private Repository repo;
    private SimpleCatalogManager manager;
    private ValueFactory vf = SimpleValueFactory.getInstance();
    private ModelFactory mf = LinkedHashModelFactory.getInstance();
    private ValueConverterRegistry vcr = new DefaultValueConverterRegistry();
    private CatalogFactory catalogFactory = new CatalogFactory();
    private RecordFactory recordFactory = new RecordFactory();
    private UnversionedRecordFactory unversionedRecordFactory = new UnversionedRecordFactory();
    private VersionedRecordFactory versionedRecordFactory = new VersionedRecordFactory();
    private VersionedRDFRecordFactory versionedRDFRecordFactory = new VersionedRDFRecordFactory();
    private DistributionFactory distributionFactory = new DistributionFactory();
    private BranchFactory branchFactory = new BranchFactory();
    private InProgressCommitFactory inProgressCommitFactory = new InProgressCommitFactory();
    private CommitFactory commitFactory = new CommitFactory();
    private RevisionFactory revisionFactory = new RevisionFactory();
    private ThingFactory thingFactory = new ThingFactory();
    private VersionFactory versionFactory = new VersionFactory();
    private TagFactory tagFactory = new TagFactory();
    private UserBranchFactory userBranchFactory = new UserBranchFactory();
    private UserFactory userFactory = new UserFactory();
    private GraphRevisionFactory graphRevisionFactory = new GraphRevisionFactory();

    private IRI distributedCatalogId;
    private IRI localCatalogId;
    private final IRI typeIRI = vf.createIRI(org.matonto.ontologies.rdfs.Resource.type_IRI);
    private final IRI titleIRI = vf.createIRI(_Thing.title_IRI);
    private final IRI descriptionIRI = vf.createIRI(_Thing.description_IRI);
    private final IRI modifiedIRI = vf.createIRI(_Thing.modified_IRI);
    private final IRI issuedIRI = vf.createIRI(_Thing.issued_IRI);
    private final IRI EMPTY_IRI = vf.createIRI("http://matonto.org/test#empty");
    private final IRI NEW_IRI = vf.createIRI("http://matonto.org/test#new");
    private final IRI USER_IRI = vf.createIRI("http://matonto.org/test#user");
    private final IRI RECORD_IRI = vf.createIRI("http://matonto.org/test/records#record");
    private final IRI UNVERSIONED_RECORD_IRI = vf.createIRI("http://matonto.org/test/records#unversioned-record");
    private final IRI VERSIONED_RECORD_IRI = vf.createIRI("http://matonto.org/test/records#versioned-record");
    private final IRI VERSIONED_RDF_RECORD_IRI = vf.createIRI("http://matonto.org/test/records#versioned-rdf-record");
    private final IRI DISTRIBUTION_IRI = vf.createIRI("http://matonto.org/test/distributions#distribution");
    private final IRI LATEST_VERSION_IRI = vf.createIRI("http://matonto.org/test/versions#latest-version");
    private final IRI VERSION_IRI = vf.createIRI("http://matonto.org/test/versions#version");
    private final IRI TAG_IRI = vf.createIRI("http://matonto.org/test/versions#tag");
    private final IRI LATEST_TAG_IRI = vf.createIRI("http://matonto.org/test/versions#latest-tag");
    private final IRI MASTER_BRANCH_IRI = vf.createIRI("http://matonto.org/test/branches#master");
    private final IRI BRANCH_IRI = vf.createIRI("http://matonto.org/test/branches#branch");
    private final IRI USER_BRANCH_IRI = vf.createIRI("http://matonto.org/test/branches#user-branch");
    private final IRI COMMIT_IRI = vf.createIRI("http://matonto.org/test/commits#commit");
    private final IRI IN_PROGRESS_COMMIT_IRI = vf.createIRI("http://matonto.org/test/commits#in-progress-commit");

    private static final String COMMITS = "http://matonto.org/test/commits#";
    private static final String ADDITIONS = "https://matonto.org/additions#";
    private static final String DELETIONS = "https://matonto.org/deletions#";
    private static final String REVISIONS = "http://matonto.org/test/revisions#";
    private static final String BRANCHES = "http://matonto.org/test/branches#";
    private static final String RECORDS = "http://matonto.org/test/records#";
    private static final String GRAPHS = "http://matonto.org/test/graphs#";

    private static final int TOTAL_SIZE = 7;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Mock
    private CatalogUtilsService utilsService;

    @Before
    public void setUp() throws Exception {
        repo = new SesameRepositoryWrapper(new SailRepository(new MemoryStore()));
        repo.initialize();

        catalogFactory.setModelFactory(mf);
        catalogFactory.setValueFactory(vf);
        catalogFactory.setValueConverterRegistry(vcr);

        recordFactory.setModelFactory(mf);
        recordFactory.setValueFactory(vf);
        recordFactory.setValueConverterRegistry(vcr);

        unversionedRecordFactory.setModelFactory(mf);
        unversionedRecordFactory.setValueFactory(vf);
        unversionedRecordFactory.setValueConverterRegistry(vcr);

        versionedRecordFactory.setModelFactory(mf);
        versionedRecordFactory.setValueFactory(vf);
        versionedRecordFactory.setValueConverterRegistry(vcr);

        versionedRDFRecordFactory.setModelFactory(mf);
        versionedRDFRecordFactory.setValueFactory(vf);
        versionedRDFRecordFactory.setValueConverterRegistry(vcr);

        commitFactory.setModelFactory(mf);
        commitFactory.setValueFactory(vf);
        commitFactory.setValueConverterRegistry(vcr);

        inProgressCommitFactory.setModelFactory(mf);
        inProgressCommitFactory.setValueFactory(vf);
        inProgressCommitFactory.setValueConverterRegistry(vcr);

        revisionFactory.setModelFactory(mf);
        revisionFactory.setValueFactory(vf);
        revisionFactory.setValueConverterRegistry(vcr);

        branchFactory.setModelFactory(mf);
        branchFactory.setValueFactory(vf);
        branchFactory.setValueConverterRegistry(vcr);

        distributionFactory.setModelFactory(mf);
        distributionFactory.setValueFactory(vf);
        distributionFactory.setValueConverterRegistry(vcr);

        versionFactory.setModelFactory(mf);
        versionFactory.setValueFactory(vf);
        versionFactory.setValueConverterRegistry(vcr);

        tagFactory.setModelFactory(mf);
        tagFactory.setValueFactory(vf);
        tagFactory.setValueConverterRegistry(vcr);

        userFactory.setModelFactory(mf);
        userFactory.setValueFactory(vf);
        userFactory.setValueConverterRegistry(vcr);

        versionedRDFRecordFactory.setModelFactory(mf);
        versionedRDFRecordFactory.setValueFactory(vf);
        versionedRDFRecordFactory.setValueConverterRegistry(vcr);

        userBranchFactory.setModelFactory(mf);
        userBranchFactory.setValueFactory(vf);
        userBranchFactory.setValueConverterRegistry(vcr);

        thingFactory.setModelFactory(mf);
        thingFactory.setValueFactory(vf);
        thingFactory.setValueConverterRegistry(vcr);

        graphRevisionFactory.setModelFactory(mf);
        graphRevisionFactory.setValueFactory(vf);
        graphRevisionFactory.setValueConverterRegistry(vcr);

        vcr.registerValueConverter(catalogFactory);
        vcr.registerValueConverter(recordFactory);
        vcr.registerValueConverter(unversionedRecordFactory);
        vcr.registerValueConverter(versionedRecordFactory);
        vcr.registerValueConverter(versionedRDFRecordFactory);
        vcr.registerValueConverter(distributionFactory);
        vcr.registerValueConverter(branchFactory);
        vcr.registerValueConverter(inProgressCommitFactory);
        vcr.registerValueConverter(commitFactory);
        vcr.registerValueConverter(revisionFactory);
        vcr.registerValueConverter(versionFactory);
        vcr.registerValueConverter(tagFactory);
        vcr.registerValueConverter(thingFactory);
        vcr.registerValueConverter(userFactory);
        vcr.registerValueConverter(versionedRDFRecordFactory);
        vcr.registerValueConverter(userBranchFactory);
        vcr.registerValueConverter(graphRevisionFactory);
        vcr.registerValueConverter(new ResourceValueConverter());
        vcr.registerValueConverter(new IRIValueConverter());
        vcr.registerValueConverter(new DoubleValueConverter());
        vcr.registerValueConverter(new IntegerValueConverter());
        vcr.registerValueConverter(new FloatValueConverter());
        vcr.registerValueConverter(new ShortValueConverter());
        vcr.registerValueConverter(new StringValueConverter());
        vcr.registerValueConverter(new ValueValueConverter());
        vcr.registerValueConverter(new LiteralValueConverter());

        MockitoAnnotations.initMocks(this);
        manager = new SimpleCatalogManager();
        manager.setRepository(repo);
        manager.setValueFactory(vf);
        manager.setModelFactory(mf);
        manager.setCatalogFactory(catalogFactory);
        manager.setRecordFactory(recordFactory);
        manager.setDistributionFactory(distributionFactory);
        manager.setBranchFactory(branchFactory);
        manager.setInProgressCommitFactory(inProgressCommitFactory);
        manager.setCommitFactory(commitFactory);
        manager.setRevisionFactory(revisionFactory);
        manager.setVersionedRDFRecordFactory(versionedRDFRecordFactory);
        manager.setVersionFactory(versionFactory);
        manager.setTagFactory(tagFactory);
        manager.setUnversionedRecordFactory(unversionedRecordFactory);
        manager.setVersionedRecordFactory(versionedRecordFactory);
        manager.setUtils(utilsService);

        InputStream testData = getClass().getResourceAsStream("/testCatalogData.trig");

        try (RepositoryConnection conn = repo.getConnection()) {
            conn.add(Values.matontoModel(Rio.parse(testData, "", RDFFormat.TRIG)));
        }

        Map<String, Object> props = new HashMap<>();
        props.put("title", "MatOnto Test Catalog");
        props.put("description", "This is a test catalog");
        props.put("iri", "http://matonto.org/test/catalogs#catalog");

        manager.start(props);

        distributedCatalogId = vf.createIRI("http://matonto.org/test/catalogs#catalog-distributed");
        localCatalogId = vf.createIRI("http://matonto.org/test/catalogs#catalog-local");

        when(utilsService.getExpectedObject(any(Resource.class), any(OrmFactory.class), any(RepositoryConnection.class))).thenAnswer(i ->
                i.getArgumentAt(1, OrmFactory.class).createNew(i.getArgumentAt(0, Resource.class)));
        when(utilsService.getRecord(any(Resource.class), any(Resource.class), any(OrmFactory.class), any(RepositoryConnection.class))).thenAnswer(i ->
                i.getArgumentAt(2, OrmFactory.class).createNew(i.getArgumentAt(1, Resource.class)));
        when(utilsService.getObject(any(Resource.class), any(OrmFactory.class), any(RepositoryConnection.class))).thenAnswer(i ->
                i.getArgumentAt(1, OrmFactory.class).createNew(i.getArgumentAt(0, Resource.class)));
        when(utilsService.getUnversionedDistribution(any(Resource.class), any(Resource.class), any(Resource.class), any(RepositoryConnection.class))).thenAnswer(i ->
                distributionFactory.createNew(i.getArgumentAt(2, Resource.class)));
        when(utilsService.getVersion(any(Resource.class), any(Resource.class), any(Resource.class), any(OrmFactory.class), any(RepositoryConnection.class))).thenAnswer(i ->
                i.getArgumentAt(3, OrmFactory.class).createNew(i.getArgumentAt(2, Resource.class)));
        when(utilsService.getVersionedDistribution(any(Resource.class), any(Resource.class), any(Resource.class), any(Resource.class), any(RepositoryConnection.class))).thenAnswer(i ->
                distributionFactory.createNew(i.getArgumentAt(3, Resource.class)));
        when(utilsService.getInProgressCommit(any(Resource.class), any(Resource.class), any(Resource.class), any(RepositoryConnection.class))).thenAnswer(i ->
                inProgressCommitFactory.createNew(i.getArgumentAt(2, Resource.class)));
        when(utilsService.throwAlreadyExists(any(Resource.class), any(OrmFactory.class))).thenReturn(new IllegalArgumentException());
        when(utilsService.throwThingNotFound(any(Resource.class), any(OrmFactory.class))).thenReturn(new IllegalStateException());
    }

    @After
    public void tearDown() throws Exception {
        repo.shutDown();
    }

    @Test
    public void testGetDistributedCatalogIRI() throws Exception {
        IRI iri = manager.getDistributedCatalogIRI();
        assertEquals(distributedCatalogId, iri);
    }

    @Test
    public void testGetLocalCatalogIRI() throws Exception {
        IRI iri = manager.getLocalCatalogIRI();
        assertEquals(localCatalogId, iri);
    }

    /* getDistributedCatalog */

    @Test
    public void testGetDistributedCatalog() throws Exception {
        Catalog result = manager.getDistributedCatalog();
        verify(utilsService).getExpectedObject(eq(distributedCatalogId), eq(catalogFactory), any(RepositoryConnection.class));
        assertEquals(distributedCatalogId, result.getResource());
    }

    /* getLocalCatalog */

    @Test
    public void testGetLocalCatalog() throws Exception {
        Catalog result = manager.getLocalCatalog();
        verify(utilsService).getExpectedObject(eq(localCatalogId), eq(catalogFactory), any(RepositoryConnection.class));
        assertEquals(localCatalogId, result.getResource());
    }

    /* findRecords */

    @Test
    public void testFindRecordsReturnsCorrectDataFirstPage() throws Exception {
        // Setup:
        int limit = 1;
        int offset = 0;
        PaginatedSearchParams searchParams = new PaginatedSearchParams.Builder().limit(limit).offset(offset).build();

        // when
        PaginatedSearchResults<Record> records = manager.findRecord(distributedCatalogId, searchParams);

        // then
        assertEquals(1, records.getPage().size());
        assertEquals(TOTAL_SIZE, records.getTotalSize());
        assertEquals(1, records.getPageSize());
        assertEquals(1, records.getPageNumber());
    }

    @Test
    public void testFindRecordsReturnsCorrectDataSecondPage() throws Exception {
        // Setup:
        int limit = 1;
        int offset = 1;
        PaginatedSearchParams searchParams = new PaginatedSearchParams.Builder().limit(limit).offset(offset).build();

        // when
        PaginatedSearchResults<Record> records = manager.findRecord(distributedCatalogId, searchParams);

        // then
        assertEquals(1, records.getPage().size());
        assertEquals(TOTAL_SIZE, records.getTotalSize());
        assertEquals(1, records.getPageSize());
        assertEquals(2, records.getPageNumber());
    }

    @Test
    public void testFindRecordsReturnsCorrectDataOnePage() throws Exception {
        // Setup:
        int limit = 1000;
        int offset = 0;
        PaginatedSearchParams searchParams = new PaginatedSearchParams.Builder().limit(limit).offset(offset).build();

        // when
        PaginatedSearchResults<Record> records = manager.findRecord(distributedCatalogId, searchParams);

        // then
        assertEquals(TOTAL_SIZE, records.getPage().size());
        assertEquals(TOTAL_SIZE, records.getTotalSize());
        assertEquals(1000, records.getPageSize());
        assertEquals(1, records.getPageNumber());
    }

    @Test
    public void testFindRecordsOrdering() throws Exception {
        // Setup:
        int limit = 1;
        int offset = 0;
        PaginatedSearchParams searchParams1 = new PaginatedSearchParams.Builder().limit(limit).offset(offset).sortBy(modifiedIRI).ascending(true).build();
        PaginatedSearchParams searchParams2 = new PaginatedSearchParams.Builder().limit(limit).offset(offset).sortBy(modifiedIRI).ascending(false).build();
        PaginatedSearchParams searchParams3 = new PaginatedSearchParams.Builder().limit(limit).offset(offset).sortBy(issuedIRI).ascending(true).build();
        PaginatedSearchParams searchParams4 = new PaginatedSearchParams.Builder().limit(limit).offset(offset).sortBy(issuedIRI).ascending(false).build();
        PaginatedSearchParams searchParams5 = new PaginatedSearchParams.Builder().limit(limit).offset(offset).sortBy(titleIRI).ascending(true).build();
        PaginatedSearchParams searchParams6 = new PaginatedSearchParams.Builder().limit(limit).offset(offset).sortBy(titleIRI).ascending(false).build();

        // when
        PaginatedSearchResults<Record> resources1 = manager.findRecord(distributedCatalogId, searchParams1);
        PaginatedSearchResults<Record> resources2 = manager.findRecord(distributedCatalogId, searchParams2);
        PaginatedSearchResults<Record> resources3 = manager.findRecord(distributedCatalogId, searchParams3);
        PaginatedSearchResults<Record> resources4 = manager.findRecord(distributedCatalogId, searchParams4);
        PaginatedSearchResults<Record> resources5 = manager.findRecord(distributedCatalogId, searchParams5);
        PaginatedSearchResults<Record> resources6 = manager.findRecord(distributedCatalogId, searchParams6);

        // then
        verify(utilsService, atLeastOnce()).getRecord(eq(distributedCatalogId), any(Resource.class), eq(recordFactory), any(RepositoryConnection.class));
        assertEquals(RECORD_IRI, resources1.getPage().iterator().next().getResource());
        assertEquals(VERSIONED_RDF_RECORD_IRI, resources2.getPage().iterator().next().getResource());
        assertEquals(UNVERSIONED_RECORD_IRI, resources3.getPage().iterator().next().getResource());
        assertEquals(VERSIONED_RECORD_IRI, resources4.getPage().iterator().next().getResource());
        assertEquals(RECORD_IRI, resources5.getPage().iterator().next().getResource());
        assertEquals(resources6.getPage().iterator().next().getResource().stringValue(), "http://matonto.org/test/records#versioned-record-missing-version");
    }

    @Test
    public void testFindRecordsWithSearchText() throws Exception {
        // given
        int limit = 10;
        int offset = 0;
        PaginatedSearchParams searchParams = new PaginatedSearchParams.Builder().limit(limit).offset(offset).searchText("Unversioned").build();
        // when
        PaginatedSearchResults<Record> records = manager.findRecord(distributedCatalogId, searchParams);
        // then
        assertEquals(2, records.getPage().size());
        assertEquals(2, records.getTotalSize());
        assertEquals(10, records.getPageSize());
        assertEquals(1, records.getPageNumber());
    }

    @Test
    public void testFindRecordWithEmptyRepository() throws Exception {
        // Setup:
        Repository repo2 = new SesameRepositoryWrapper(new SailRepository(new MemoryStore()));
        repo2.initialize();
        manager.setRepository(repo2);
        int limit = 1;
        int offset = 0;
        PaginatedSearchParams searchParams = new PaginatedSearchParams.Builder().limit(limit).offset(offset).build();

        // when
        PaginatedSearchResults<Record> records = manager.findRecord(distributedCatalogId, searchParams);

        // then
        assertEquals(0, records.getPage().size());
        assertEquals(0, records.getTotalSize());
    }

    @Test
    public void testFindRecordWithNoEntries() throws Exception {
        // Setup:
        int limit = 1;
        int offset = 0;
        PaginatedSearchParams searchParams = new PaginatedSearchParams.Builder().limit(limit).offset(offset).build();

        // when
        PaginatedSearchResults<Record> records = manager.findRecord(localCatalogId, searchParams);

        // then
        assertEquals(0, records.getPage().size());
        assertEquals(0, records.getTotalSize());
    }

    @Test
    public void testFindRecordsWithTypeFilter() throws Exception {
        // Setup:
        int limit = 1000;
        int offset = 0;
        PaginatedSearchParams versionedSearchParams = new PaginatedSearchParams.Builder().limit(limit).offset(offset).typeFilter(versionedRecordFactory.getTypeIRI()).build();
        PaginatedSearchParams unversionedSearchParams = new PaginatedSearchParams.Builder().limit(limit).offset(offset).typeFilter(unversionedRecordFactory.getTypeIRI()).build();
        PaginatedSearchParams fullSearchParams = new PaginatedSearchParams.Builder().limit(limit).offset(offset).build();

        // when
        PaginatedSearchResults<Record> versionedRecords = manager.findRecord(distributedCatalogId, versionedSearchParams);
        PaginatedSearchResults<Record> unversionedRecords = manager.findRecord(distributedCatalogId, unversionedSearchParams);
        PaginatedSearchResults<Record> fullRecords = manager.findRecord(distributedCatalogId, fullSearchParams);

        // then
        assertTrue(true);
        assertEquals(4, versionedRecords.getPage().size());
        assertEquals(4, versionedRecords.getTotalSize());
        assertEquals(2, unversionedRecords.getPage().size());
        assertEquals(2, unversionedRecords.getTotalSize());
        assertEquals(TOTAL_SIZE, fullRecords.getPage().size());
        assertEquals(TOTAL_SIZE, fullRecords.getTotalSize());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindRecordWithOffsetThatIsTooLarge() {
        // given
        int limit = 10;
        int offset = 100;
        PaginatedSearchParams searchParams = new PaginatedSearchParams.Builder().limit(limit).offset(offset).build();
        // when
        manager.findRecord(distributedCatalogId, searchParams);
    }

    /* getRecordIds */

    @Test
    public void testGetRecordIds() throws Exception {
        Set<Resource> results = manager.getRecordIds(distributedCatalogId);
        verify(utilsService).validateResource(eq(distributedCatalogId), eq(vf.createIRI(Catalog.TYPE)), any(RepositoryConnection.class));
        assertEquals(TOTAL_SIZE, results.size());
        assertTrue(results.contains(RECORD_IRI));
        assertTrue(results.contains(UNVERSIONED_RECORD_IRI));
        assertTrue(results.contains(VERSIONED_RECORD_IRI));
        assertTrue(results.contains(VERSIONED_RDF_RECORD_IRI));
    }

    /* addRecord */

    @Test
    public void testAddRecord() throws Exception {
        // Setup:
        Record record = recordFactory.createNew(NEW_IRI);

        manager.addRecord(distributedCatalogId, record);
        verify(utilsService).getObject(eq(distributedCatalogId), eq(catalogFactory), any(RepositoryConnection.class));
        verify(utilsService).addObject(eq(record), any(RepositoryConnection.class));
        assertTrue(record.getCatalog_resource().isPresent());
        assertEquals(distributedCatalogId, record.getCatalog_resource().get());
    }

    @Test
    public void testAddUnversionedRecord() throws Exception {
        // Setup:
        UnversionedRecord record = unversionedRecordFactory.createNew(NEW_IRI);

        manager.addRecord(distributedCatalogId, record);
        verify(utilsService).getObject(eq(distributedCatalogId), eq(catalogFactory), any(RepositoryConnection.class));
        verify(utilsService).addObject(eq(record), any(RepositoryConnection.class));
        assertTrue(record.getCatalog_resource().isPresent());
        assertEquals(distributedCatalogId, record.getCatalog_resource().get());
    }

    @Test
    public void testAddVersionedRecord() throws Exception {
        // Setup:
        VersionedRecord record = versionedRecordFactory.createNew(NEW_IRI);

        manager.addRecord(distributedCatalogId, record);
        verify(utilsService).getObject(eq(distributedCatalogId), eq(catalogFactory), any(RepositoryConnection.class));
        verify(utilsService).addObject(eq(record), any(RepositoryConnection.class));
        assertTrue(record.getCatalog_resource().isPresent());
        assertEquals(distributedCatalogId, record.getCatalog_resource().get());
    }

    @Test
    public void testAddVersionedRDFRecord() throws Exception {
        // Setup:
        VersionedRDFRecord record = versionedRDFRecordFactory.createNew(NEW_IRI);

        manager.addRecord(distributedCatalogId, record);
        verify(utilsService).getObject(eq(distributedCatalogId), eq(catalogFactory), any(RepositoryConnection.class));
        verify(utilsService).updateObject(eq(record), any(RepositoryConnection.class));
        verify(utilsService, times(0)).addObject(eq(record), any(RepositoryConnection.class));
        verify(utilsService).addObject(any(Branch.class), any(RepositoryConnection.class));
        assertTrue(record.getCatalog_resource().isPresent());
        assertEquals(distributedCatalogId, record.getCatalog_resource().get());
        assertTrue(record.getMasterBranch_resource().isPresent());
        assertEquals(1, record.getBranch_resource().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddRecordWithTakenResource() {
        // Setup:
        Record record = recordFactory.createNew(RECORD_IRI);

        manager.addRecord(distributedCatalogId, record);
        verify(utilsService, times(0)).addObject(eq(record), any(RepositoryConnection.class));
        verify(utilsService).throwAlreadyExists(RECORD_IRI, recordFactory);
    }

    /* updateRecord */

    @Test
    public void testUpdateRecord() throws Exception {
        // Setup:
        Record record = recordFactory.createNew(RECORD_IRI);
        record.setKeyword(Stream.of(vf.createLiteral("keyword1")).collect(Collectors.toSet()));

        manager.updateRecord(distributedCatalogId, record);
        verify(utilsService).validateRecord(eq(distributedCatalogId), eq(RECORD_IRI), eq(vf.createIRI(Record.TYPE)), any(RepositoryConnection.class));
        verify(utilsService).updateObject(eq(record), any(RepositoryConnection.class));
    }

    @Test
    public void testUpdateUnversionedRecord() throws Exception {
        // Setup:
        UnversionedRecord record = unversionedRecordFactory.createNew(UNVERSIONED_RECORD_IRI);
        record.setKeyword(Stream.of(vf.createLiteral("keyword1")).collect(Collectors.toSet()));

        manager.updateRecord(distributedCatalogId, record);
        verify(utilsService).validateRecord(eq(distributedCatalogId), eq(UNVERSIONED_RECORD_IRI), eq(vf.createIRI(Record.TYPE)), any(RepositoryConnection.class));
        verify(utilsService).updateObject(eq(record), any(RepositoryConnection.class));
    }

    @Test
    public void testUpdateVersionedRecord() throws Exception {
        // Setup:
        VersionedRecord record = versionedRecordFactory.createNew(VERSIONED_RECORD_IRI);
        record.setKeyword(Stream.of(vf.createLiteral("keyword1")).collect(Collectors.toSet()));

        manager.updateRecord(distributedCatalogId, record);
        verify(utilsService).validateRecord(eq(distributedCatalogId), eq(VERSIONED_RECORD_IRI), eq(vf.createIRI(Record.TYPE)), any(RepositoryConnection.class));
        verify(utilsService).updateObject(eq(record), any(RepositoryConnection.class));
    }

    @Test
    public void testUpdateVersionedRDFRecord() throws Exception {
        // Setup:
        VersionedRDFRecord record = versionedRDFRecordFactory.createNew(VERSIONED_RDF_RECORD_IRI);
        record.setKeyword(Stream.of(vf.createLiteral("keyword1")).collect(Collectors.toSet()));

        manager.updateRecord(distributedCatalogId, record);
        verify(utilsService).validateRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(vf.createIRI(Record.TYPE)), any(RepositoryConnection.class));
        verify(utilsService).updateObject(eq(record), any(RepositoryConnection.class));
    }

    /* removeRecord */

    @Test
    public void testRemoveRecord() throws Exception {
        manager.removeRecord(distributedCatalogId, RECORD_IRI);
        verify(utilsService).getRecord(eq(distributedCatalogId), eq(RECORD_IRI), eq(recordFactory), any(RepositoryConnection.class));
        verify(utilsService).removeObject(any(Record.class), any(RepositoryConnection.class));
    }

    @Test
    public void testRemoveUnversionedRecord() throws Exception {
        // Setup:
        UnversionedRecord record = unversionedRecordFactory.createNew(UNVERSIONED_RECORD_IRI);
        Distribution dist = distributionFactory.createNew(DISTRIBUTION_IRI);
        record.setUnversionedDistribution(Collections.singleton(dist));
        doReturn(record).when(utilsService).getRecord(eq(distributedCatalogId), eq(UNVERSIONED_RECORD_IRI), eq(recordFactory), any(RepositoryConnection.class));

        manager.removeRecord(distributedCatalogId, UNVERSIONED_RECORD_IRI);
        record.getUnversionedDistribution_resource().forEach(resource -> verify(utilsService).remove(eq(resource), any(RepositoryConnection.class)));
        verify(utilsService).removeObject(any(UnversionedRecord.class), any(RepositoryConnection.class));
    }

    @Test
    public void testRemoveVersionedRecord() throws Exception {
        // Setup:
        VersionedRecord record = versionedRecordFactory.createNew(VERSIONED_RECORD_IRI);
        Version version = versionFactory.createNew(VERSION_IRI);
        record.setVersion(Collections.singleton(version));
        record.setLatestVersion(version);
        Distribution dist = distributionFactory.createNew(DISTRIBUTION_IRI);
        version.setVersionedDistribution(Collections.singleton(dist));
        doReturn(record).when(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RECORD_IRI), eq(recordFactory), any(RepositoryConnection.class));
        doReturn(version).when(utilsService).getObject(eq(VERSION_IRI), eq(versionFactory), any(RepositoryConnection.class));

        manager.removeRecord(distributedCatalogId, VERSIONED_RECORD_IRI);
        verify(utilsService).getObject(eq(VERSION_IRI), eq(versionFactory), any(RepositoryConnection.class));
        verify(utilsService).remove(eq(VERSION_IRI), any(RepositoryConnection.class));
        verify(utilsService).remove(eq(DISTRIBUTION_IRI), any(RepositoryConnection.class));
    }

    @Test
    public void testRemoveVersionedRDFRecord() throws Exception {
        // Setup:
        VersionedRDFRecord record = versionedRDFRecordFactory.createNew(VERSIONED_RDF_RECORD_IRI);
        Tag tag = tagFactory.createNew(TAG_IRI);
        Distribution dist = distributionFactory.createNew(DISTRIBUTION_IRI);
        Branch branch = branchFactory.createNew(MASTER_BRANCH_IRI);
        tag.setVersionedDistribution(Collections.singleton(dist));
        record.setVersion(Collections.singleton(tag));
        record.setLatestVersion(tag);
        record.setBranch(Collections.singleton(branch));
        record.setMasterBranch(branch);
        doReturn(record).when(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(recordFactory), any(RepositoryConnection.class));
        doReturn(tag).when(utilsService).getObject(eq(TAG_IRI), eq(versionFactory), any(RepositoryConnection.class));
        doReturn(branch).when(utilsService).getObject(eq(BRANCH_IRI), eq(branchFactory), any(RepositoryConnection.class));

        manager.removeRecord(distributedCatalogId, VERSIONED_RDF_RECORD_IRI);
        verify(utilsService).getObject(eq(TAG_IRI), eq(versionFactory), any(RepositoryConnection.class));
        verify(utilsService).remove(eq(TAG_IRI), any(RepositoryConnection.class));
        verify(utilsService).remove(eq(DISTRIBUTION_IRI), any(RepositoryConnection.class));
        verify(utilsService).getObject(eq(MASTER_BRANCH_IRI), eq(branchFactory), any(RepositoryConnection.class));
        verify(utilsService).remove(eq(MASTER_BRANCH_IRI), any(RepositoryConnection.class));
    }

    /* getRecord */

    @Test
    public void testGetRecord() throws Exception {
        // Setup:
        Record record = recordFactory.createNew(RECORD_IRI);
        record.setCatalog(catalogFactory.createNew(distributedCatalogId));
        doReturn(Optional.of(record)).when(utilsService).optObject(eq(RECORD_IRI), eq(recordFactory), any(RepositoryConnection.class));

        Optional<Record> result = manager.getRecord(distributedCatalogId, RECORD_IRI, recordFactory);
        verify(utilsService).validateResource(eq(distributedCatalogId), eq(catalogFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).optObject(eq(RECORD_IRI), eq(recordFactory), any(RepositoryConnection.class));
        assertTrue(result.isPresent());
        assertEquals(record, result.get());
    }

    @Test
    public void testGetUnversionedRecord() throws Exception {
        // Setup:
        UnversionedRecord record = unversionedRecordFactory.createNew(UNVERSIONED_RECORD_IRI);
        record.setCatalog(catalogFactory.createNew(distributedCatalogId));
        doReturn(Optional.of(record)).when(utilsService).optObject(eq(UNVERSIONED_RECORD_IRI), eq(unversionedRecordFactory), any(RepositoryConnection.class));

        Optional<UnversionedRecord> result = manager.getRecord(distributedCatalogId, UNVERSIONED_RECORD_IRI, unversionedRecordFactory);
        verify(utilsService).validateResource(eq(distributedCatalogId), eq(catalogFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).optObject(eq(UNVERSIONED_RECORD_IRI), eq(unversionedRecordFactory), any(RepositoryConnection.class));
        assertTrue(result.isPresent());
        assertEquals(record, result.get());
    }

    @Test
    public void testGetVersionedRecord() throws Exception {
        // Setup:
        VersionedRecord record = versionedRecordFactory.createNew(VERSIONED_RECORD_IRI);
        record.setCatalog(catalogFactory.createNew(distributedCatalogId));
        doReturn(Optional.of(record)).when(utilsService).optObject(eq(VERSIONED_RECORD_IRI), eq(versionedRecordFactory), any(RepositoryConnection.class));

        Optional<VersionedRecord> result = manager.getRecord(distributedCatalogId, VERSIONED_RECORD_IRI, versionedRecordFactory);
        verify(utilsService).validateResource(eq(distributedCatalogId), eq(catalogFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).optObject(eq(VERSIONED_RECORD_IRI), eq(versionedRecordFactory), any(RepositoryConnection.class));
        assertTrue(result.isPresent());
        assertEquals(record, result.get());
    }

    @Test
    public void testGetVersionedRDFRecord() throws Exception {
        // Setup:
        VersionedRDFRecord record = versionedRDFRecordFactory.createNew(VERSIONED_RDF_RECORD_IRI);
        record.setCatalog(catalogFactory.createNew(distributedCatalogId));
        doReturn(Optional.of(record)).when(utilsService).optObject(eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRDFRecordFactory), any(RepositoryConnection.class));

        Optional<VersionedRDFRecord> result = manager.getRecord(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, versionedRDFRecordFactory);
        verify(utilsService).validateResource(eq(distributedCatalogId), eq(catalogFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).optObject(eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRDFRecordFactory), any(RepositoryConnection.class));
        assertTrue(result.isPresent());
        assertEquals(record, result.get());
    }

    @Test
    public void testGetRecordWithNoCatalog() {
        // Setup:
        Record record = recordFactory.createNew(EMPTY_IRI);
        doReturn(Optional.of(record)).when(utilsService).optObject(eq(EMPTY_IRI), eq(recordFactory), any(RepositoryConnection.class));
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Record " + EMPTY_IRI + " does not have a Catalog set");

        manager.getRecord(distributedCatalogId, EMPTY_IRI, recordFactory);
    }

    @Test
    public void testGetRecordFromWrongCatalog() {
        // Setup:
        Record record = recordFactory.createNew(RECORD_IRI);
        record.setCatalog(catalogFactory.createNew(distributedCatalogId));
        doReturn(Optional.of(record)).when(utilsService).optObject(eq(RECORD_IRI), eq(recordFactory), any(RepositoryConnection.class));

        Optional<Record> result = manager.getRecord(localCatalogId, RECORD_IRI, recordFactory);
        verify(utilsService).validateResource(eq(localCatalogId), eq(catalogFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).optObject(eq(RECORD_IRI), eq(recordFactory), any(RepositoryConnection.class));
        assertFalse(result.isPresent());
    }

    /* getUnversionedDistributions */

    @Test
    public void testGetUnversionedDistributions() throws Exception {
        // Setup:
        UnversionedRecord record = unversionedRecordFactory.createNew(UNVERSIONED_RECORD_IRI);
        Distribution dist = distributionFactory.createNew(DISTRIBUTION_IRI);
        record.setUnversionedDistribution(Collections.singleton(dist));
        doReturn(record).when(utilsService).getRecord(eq(distributedCatalogId), eq(UNVERSIONED_RECORD_IRI), eq(unversionedRecordFactory), any(RepositoryConnection.class));

        Set<Distribution> distributions = manager.getUnversionedDistributions(distributedCatalogId, record.getResource());
        verify(utilsService).getRecord(eq(distributedCatalogId), eq(UNVERSIONED_RECORD_IRI), eq(unversionedRecordFactory), any(RepositoryConnection.class));
        record.getUnversionedDistribution_resource().forEach(resource -> verify(utilsService).getExpectedObject(eq(resource), eq(distributionFactory), any(RepositoryConnection.class)));
        assertEquals(1, distributions.size());
    }

    /* addUnversionedDistribution */

    @Test
    public void testAddUnversionedDistribution() throws Exception {
        // Setup:
        UnversionedRecord record = unversionedRecordFactory.createNew(NEW_IRI);
        doReturn(record).when(utilsService).getRecord(eq(distributedCatalogId), eq(UNVERSIONED_RECORD_IRI), eq(unversionedRecordFactory), any(RepositoryConnection.class));
        Distribution dist = distributionFactory.createNew(NEW_IRI);

        manager.addUnversionedDistribution(distributedCatalogId, UNVERSIONED_RECORD_IRI, dist);
        verify(utilsService).getRecord(eq(distributedCatalogId), eq(UNVERSIONED_RECORD_IRI), eq(unversionedRecordFactory), any(RepositoryConnection.class));
        verify(utilsService).updateObject(eq(record), any(RepositoryConnection.class));
        verify(utilsService).addObject(eq(dist), any(RepositoryConnection.class));
        assertEquals(1, record.getUnversionedDistribution_resource().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddUnversionedDistributionWithTakenResource() {
        // Setup:
        Distribution dist = distributionFactory.createNew(DISTRIBUTION_IRI);

        manager.addUnversionedDistribution(distributedCatalogId, UNVERSIONED_RECORD_IRI, dist);
        verify(utilsService, times(0)).addObject(eq(dist), any(RepositoryConnection.class));
        verify(utilsService).throwAlreadyExists(DISTRIBUTION_IRI, distributionFactory);
    }

    /* updateUnversionedDistribution */

    @Test
    public void testUpdateUnversionedDistribution() throws Exception {
        // Setup:
        Distribution dist = distributionFactory.createNew(DISTRIBUTION_IRI);
        dist.getModel().add(DISTRIBUTION_IRI, titleIRI, vf.createLiteral("New Title"));

        manager.updateUnversionedDistribution(distributedCatalogId, UNVERSIONED_RECORD_IRI, dist);
        verify(utilsService).validateUnversionedDistribution(eq(distributedCatalogId), eq(UNVERSIONED_RECORD_IRI), eq(DISTRIBUTION_IRI), any(RepositoryConnection.class));
        verify(utilsService).updateObject(eq(dist), any(RepositoryConnection.class));
    }

    /* removeUnversionedDistribution */

    @Test
    public void testRemoveUnversionedDistribution() throws Exception {
        // Setup:
        IRI distributionIRI = vf.createIRI(UnversionedRecord.unversionedDistribution_IRI);
        try (RepositoryConnection conn = repo.getConnection()) {
            assertTrue(conn.getStatements(UNVERSIONED_RECORD_IRI, distributionIRI, DISTRIBUTION_IRI, UNVERSIONED_RECORD_IRI).hasNext());

            manager.removeUnversionedDistribution(distributedCatalogId, UNVERSIONED_RECORD_IRI, DISTRIBUTION_IRI);
            verify(utilsService).getUnversionedDistribution(eq(distributedCatalogId), eq(UNVERSIONED_RECORD_IRI), eq(DISTRIBUTION_IRI), any(RepositoryConnection.class));
            verify(utilsService).remove(eq(DISTRIBUTION_IRI), any(RepositoryConnection.class));
            assertFalse(conn.getStatements(UNVERSIONED_RECORD_IRI, distributionIRI, DISTRIBUTION_IRI, UNVERSIONED_RECORD_IRI).hasNext());
        }
    }

    /* getUnversionedDistribution */

    @Test
    public void testGetUnversionedDistribution() throws Exception {
        // Setup:
        UnversionedRecord record = unversionedRecordFactory.createNew(UNVERSIONED_RECORD_IRI);
        Distribution dist = distributionFactory.createNew(DISTRIBUTION_IRI);
        record.setUnversionedDistribution(Collections.singleton(dist));
        doReturn(record).when(utilsService).getRecord(eq(distributedCatalogId), eq(UNVERSIONED_RECORD_IRI), eq(unversionedRecordFactory), any(RepositoryConnection.class));
        doReturn(dist).when(utilsService).getExpectedObject(eq(DISTRIBUTION_IRI), eq(distributionFactory), any(RepositoryConnection.class));

        Optional<Distribution> result = manager.getUnversionedDistribution(distributedCatalogId, UNVERSIONED_RECORD_IRI, DISTRIBUTION_IRI);
        verify(utilsService).getRecord(eq(distributedCatalogId), eq(UNVERSIONED_RECORD_IRI), eq(unversionedRecordFactory), any(RepositoryConnection.class));
        verify(utilsService).getExpectedObject(eq(DISTRIBUTION_IRI), eq(distributionFactory), any(RepositoryConnection.class));
        assertTrue(result.isPresent());
        assertEquals(dist, result.get());
    }

    @Test
    public void testGetUnversionedDistributionOfWrongRecord() throws Exception {
        Optional<Distribution> result = manager.getUnversionedDistribution(distributedCatalogId, UNVERSIONED_RECORD_IRI, EMPTY_IRI);
        assertFalse(result.isPresent());
    }

    /* getVersions */

    @Test
    public void testGetVersions() throws Exception {
        // Setup:
        VersionedRecord record = versionedRecordFactory.createNew(VERSIONED_RECORD_IRI);
        Version version = versionFactory.createNew(VERSION_IRI);
        record.setVersion(Collections.singleton(version));
        doReturn(record).when(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RECORD_IRI), eq(versionedRecordFactory), any(RepositoryConnection.class));

        Set<Version> versions = manager.getVersions(distributedCatalogId, VERSIONED_RECORD_IRI);
        verify(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RECORD_IRI), eq(versionedRecordFactory), any(RepositoryConnection.class));
        record.getVersion_resource().forEach(resource -> verify(utilsService).getExpectedObject(eq(resource), eq(versionFactory), any(RepositoryConnection.class)));
        assertEquals(1, versions.size());
    }

    /* addVersion */

    @Test
    public void testAddVersion() throws Exception {
        // Setup:
        VersionedRecord record = versionedRecordFactory.createNew(VERSIONED_RECORD_IRI);
        doReturn(record).when(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RECORD_IRI), eq(versionedRecordFactory), any(RepositoryConnection.class));
        Version version = versionFactory.createNew(NEW_IRI);

        manager.addVersion(distributedCatalogId, VERSIONED_RECORD_IRI, version);
        verify(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RECORD_IRI), eq(versionedRecordFactory), any(RepositoryConnection.class));
        verify(utilsService).updateObject(eq(record), any(RepositoryConnection.class));
        verify(utilsService).addObject(eq(version), any(RepositoryConnection.class));
        assertTrue(record.getLatestVersion_resource().isPresent());
        assertEquals(NEW_IRI, record.getLatestVersion_resource().get());
        assertEquals(1, record.getVersion_resource().size());
    }

    @Test
    public void testAddTag() throws Exception {
        // Setup:
        VersionedRDFRecord record = versionedRDFRecordFactory.createNew(VERSIONED_RDF_RECORD_IRI);
        doReturn(record).when(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRecordFactory), any(RepositoryConnection.class));
        Tag tag = tagFactory.createNew(NEW_IRI);

        manager.addVersion(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, tag);
        verify(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRecordFactory), any(RepositoryConnection.class));
        verify(utilsService).updateObject(eq(record), any(RepositoryConnection.class));
        verify(utilsService).addObject(eq(tag), any(RepositoryConnection.class));
        assertTrue(record.getLatestVersion_resource().isPresent());
        assertEquals(NEW_IRI, record.getLatestVersion_resource().get());
        assertEquals(1, record.getVersion_resource().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddVersionWithTakenResource() {
        // Setup:
        Version version = versionFactory.createNew(VERSION_IRI);

        manager.addVersion(distributedCatalogId, VERSIONED_RECORD_IRI, version);
        verify(utilsService, times(0)).addObject(eq(version), any(RepositoryConnection.class));
        verify(utilsService).throwAlreadyExists(VERSION_IRI, distributionFactory);
    }

    /* updateVersion */

    @Test
    public void testUpdateVersion() throws Exception {
        // Setup:
        Version version = versionFactory.createNew(VERSION_IRI);
        version.getModel().add(VERSION_IRI, titleIRI, vf.createLiteral("New Title"));

        manager.updateVersion(distributedCatalogId, VERSIONED_RECORD_IRI, version);
        verify(utilsService).validateVersion(eq(distributedCatalogId), eq(VERSIONED_RECORD_IRI), eq(VERSION_IRI), any(RepositoryConnection.class));
        verify(utilsService).updateObject(eq(version), any(RepositoryConnection.class));
    }

    @Test
    public void testUpdateTag() throws Exception {
        // Setup:
        Tag tag = tagFactory.createNew(TAG_IRI);
        tag.getModel().add(TAG_IRI, titleIRI, vf.createLiteral("New Title"));

        manager.updateVersion(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, tag);
        verify(utilsService).validateVersion(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(TAG_IRI), any(RepositoryConnection.class));
        verify(utilsService).updateObject(eq(tag), any(RepositoryConnection.class));
    }

    /* removeVersion */

    @Test
    public void testRemoveVersion() throws Exception {
        // Setup:
        IRI versionIRI = vf.createIRI(VersionedRecord.version_IRI);
        IRI latestIRI = vf.createIRI(VersionedRecord.latestVersion_IRI);
        Version version = versionFactory.createNew(LATEST_VERSION_IRI);
        version.setVersionedDistribution(Collections.singleton(distributionFactory.createNew(DISTRIBUTION_IRI)));
        doReturn(version).when(utilsService).getVersion(eq(distributedCatalogId), eq(VERSIONED_RECORD_IRI), eq(LATEST_VERSION_IRI), eq(versionFactory), any(RepositoryConnection.class));
        try (RepositoryConnection conn = repo.getConnection()) {
            assertTrue(conn.getStatements(VERSIONED_RECORD_IRI, latestIRI, LATEST_VERSION_IRI, VERSIONED_RECORD_IRI).hasNext());
            assertTrue(conn.getStatements(VERSIONED_RECORD_IRI, versionIRI, LATEST_VERSION_IRI, VERSIONED_RECORD_IRI).hasNext());

            manager.removeVersion(distributedCatalogId, VERSIONED_RECORD_IRI, LATEST_VERSION_IRI);
            verify(utilsService).getVersion(eq(distributedCatalogId), eq(VERSIONED_RECORD_IRI), eq(LATEST_VERSION_IRI), eq(versionFactory), any(RepositoryConnection.class));
            verify(utilsService).remove(eq(LATEST_VERSION_IRI), any(RepositoryConnection.class));
            verify(utilsService).remove(eq(DISTRIBUTION_IRI), any(RepositoryConnection.class));
            assertTrue(conn.getStatements(VERSIONED_RECORD_IRI, latestIRI, VERSION_IRI, VERSIONED_RECORD_IRI).hasNext());
            assertFalse(conn.getStatements(VERSIONED_RECORD_IRI, versionIRI, LATEST_VERSION_IRI, VERSIONED_RECORD_IRI).hasNext());
        }
    }

    /* getVersion */

    @Test
    public void testGetVersion() throws Exception {
        // Setup:
        VersionedRecord record = versionedRecordFactory.createNew(VERSIONED_RECORD_IRI);
        Version version = versionFactory.createNew(VERSION_IRI);
        record.setVersion(Collections.singleton(version));
        doReturn(record).when(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RECORD_IRI), eq(versionedRecordFactory), any(RepositoryConnection.class));
        doReturn(version).when(utilsService).getExpectedObject(eq(VERSION_IRI), eq(versionFactory), any(RepositoryConnection.class));

        Optional<Version> result = manager.getVersion(distributedCatalogId, VERSIONED_RECORD_IRI, VERSION_IRI, versionFactory);
        verify(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RECORD_IRI), eq(versionedRecordFactory), any(RepositoryConnection.class));
        verify(utilsService).getExpectedObject(eq(VERSION_IRI), eq(versionFactory), any(RepositoryConnection.class));
        assertTrue(result.isPresent());
        assertEquals(version, result.get());
    }

    @Test
    public void testGetTag() throws Exception {
        // Setup:
        VersionedRDFRecord record = versionedRDFRecordFactory.createNew(VERSIONED_RDF_RECORD_IRI);
        Tag tag = tagFactory.createNew(TAG_IRI);
        record.setVersion(Collections.singleton(tag));
        doReturn(record).when(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRecordFactory), any(RepositoryConnection.class));
        doReturn(tag).when(utilsService).getExpectedObject(eq(TAG_IRI), eq(tagFactory), any(RepositoryConnection.class));

        Optional<Tag> result = manager.getVersion(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, TAG_IRI, tagFactory);
        verify(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRecordFactory), any(RepositoryConnection.class));
        verify(utilsService).getExpectedObject(eq(TAG_IRI), eq(tagFactory), any(RepositoryConnection.class));
        assertTrue(result.isPresent());
        assertEquals(tag, result.get());
    }

    @Test
    public void testGetVersionOfWrongRecord() throws Exception {
        Optional<Version> result = manager.getVersion(distributedCatalogId, VERSIONED_RECORD_IRI, EMPTY_IRI, versionFactory);
        assertFalse(result.isPresent());
    }

    /* getLatestVersion */

    @Test
    public void getLatestVersion() throws Exception {
        // Setup:
        VersionedRecord record = versionedRecordFactory.createNew(VERSIONED_RECORD_IRI);
        Version version = versionFactory.createNew(LATEST_VERSION_IRI);
        record.setLatestVersion(version);
        doReturn(record).when(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RECORD_IRI), eq(versionedRecordFactory), any(RepositoryConnection.class));
        doReturn(version).when(utilsService).getExpectedObject(eq(LATEST_VERSION_IRI), eq(versionFactory), any(RepositoryConnection.class));

        Optional<Version> result = manager.getLatestVersion(distributedCatalogId, VERSIONED_RECORD_IRI, versionFactory);
        verify(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RECORD_IRI), eq(versionedRecordFactory), any(RepositoryConnection.class));
        verify(utilsService).getExpectedObject(eq(LATEST_VERSION_IRI), eq(versionFactory), any(RepositoryConnection.class));
        assertTrue(result.isPresent());
        assertEquals(version, result.get());
    }

    @Test
    public void getLatestTag() throws Exception {
        // Setup:
        VersionedRDFRecord record = versionedRDFRecordFactory.createNew(VERSIONED_RDF_RECORD_IRI);
        Tag tag = tagFactory.createNew(TAG_IRI);
        record.setLatestVersion(tag);
        doReturn(record).when(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRecordFactory), any(RepositoryConnection.class));
        doReturn(tag).when(utilsService).getExpectedObject(eq(TAG_IRI), eq(tagFactory), any(RepositoryConnection.class));

        Optional<Tag> result = manager.getLatestVersion(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, tagFactory);
        verify(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRecordFactory), any(RepositoryConnection.class));
        verify(utilsService).getExpectedObject(eq(TAG_IRI), eq(tagFactory), any(RepositoryConnection.class));
        assertTrue(result.isPresent());
        assertEquals(tag, result.get());
    }

    @Test
    public void getLatestVersionOnRecordNotSet() {
        Optional<Version> result = manager.getLatestVersion(distributedCatalogId, VERSIONED_RECORD_IRI, versionFactory);
        assertFalse(result.isPresent());
    }

    /* getTaggedCommit */

    @Test
    public void testGetTaggedCommit() throws Exception {
        // Setup:
        Tag tag = tagFactory.createNew(TAG_IRI);
        Commit commit = commitFactory.createNew(COMMIT_IRI);
        tag.setCommit(commit);
        doReturn(tag).when(utilsService).getExpectedObject(eq(TAG_IRI), eq(tagFactory), any(RepositoryConnection.class));
        doReturn(commit).when(utilsService).getExpectedObject(eq(COMMIT_IRI), eq(commitFactory), any(RepositoryConnection.class));

        Commit result = manager.getTaggedCommit(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, TAG_IRI);
        verify(utilsService).validateVersion(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(TAG_IRI), any(RepositoryConnection.class));
        verify(utilsService).getExpectedObject(eq(TAG_IRI), eq(tagFactory), any(RepositoryConnection.class));
        verify(utilsService).getExpectedObject(eq(COMMIT_IRI), eq(commitFactory), any(RepositoryConnection.class));
        assertEquals(commit, result);
    }

    @Test
    public void testGetTaggedCommitWithoutCommitSet() {
        // Setup:
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Tag " + TAG_IRI + " does not have a Commit set");

        manager.getTaggedCommit(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, TAG_IRI);
    }

    /* getVersionedDistributions */

    @Test
    public void testGetVersionedDistributions() throws Exception {
        // Setup:
        Version version = versionFactory.createNew(VERSION_IRI);
        Distribution dist = distributionFactory.createNew(DISTRIBUTION_IRI);
        version.setVersionedDistribution(Collections.singleton(dist));
        doReturn(version).when(utilsService).getVersion(eq(distributedCatalogId), eq(VERSIONED_RECORD_IRI), eq(VERSION_IRI), eq(versionFactory), any(RepositoryConnection.class));

        Set<Distribution> distributions = manager.getVersionedDistributions(distributedCatalogId, VERSIONED_RECORD_IRI, VERSION_IRI);
        verify(utilsService).getVersion(eq(distributedCatalogId), eq(VERSIONED_RECORD_IRI), eq(VERSION_IRI), eq(versionFactory), any(RepositoryConnection.class));
        version.getVersionedDistribution_resource().forEach(resource -> verify(utilsService).getExpectedObject(eq(resource), eq(distributionFactory), any(RepositoryConnection.class)));
        assertEquals(1, distributions.size());
    }

    /* addVersionedDistribution */

    @Test
    public void testAddVersionedDistribution() throws Exception {
        // Setup:
        Version version = versionFactory.createNew(VERSION_IRI);
        Distribution dist = distributionFactory.createNew(NEW_IRI);
        doReturn(version).when(utilsService).getVersion(eq(distributedCatalogId), eq(VERSIONED_RECORD_IRI), eq(VERSION_IRI), eq(versionFactory), any(RepositoryConnection.class));

        manager.addVersionedDistribution(distributedCatalogId, VERSIONED_RECORD_IRI, VERSION_IRI, dist);
        verify(utilsService).getVersion(eq(distributedCatalogId), eq(VERSIONED_RECORD_IRI), eq(VERSION_IRI), eq(versionFactory), any(RepositoryConnection.class));
        verify(utilsService).updateObject(eq(version), any(RepositoryConnection.class));
        verify(utilsService).addObject(eq(dist), any(RepositoryConnection.class));
        assertEquals(1, version.getVersionedDistribution_resource().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddVersionedDistributionWithTakenResource() {
        // Setup:
        Distribution dist = distributionFactory.createNew(DISTRIBUTION_IRI);

        manager.addVersionedDistribution(distributedCatalogId, VERSIONED_RECORD_IRI, VERSION_IRI, dist);
        verify(utilsService, times(0)).addObject(eq(dist), any(RepositoryConnection.class));
        verify(utilsService).throwAlreadyExists(DISTRIBUTION_IRI, distributionFactory);
    }

    /* updateVersionedDistribution */

    @Test
    public void testUpdateVersionedDistribution() throws Exception {
        // Setup:
        Distribution dist = distributionFactory.createNew(DISTRIBUTION_IRI);
        dist.getModel().add(DISTRIBUTION_IRI, titleIRI, vf.createLiteral("New Title"));

        manager.updateVersionedDistribution(distributedCatalogId, VERSIONED_RECORD_IRI, VERSION_IRI, dist);
        verify(utilsService).validateVersionedDistribution(eq(distributedCatalogId), eq(VERSIONED_RECORD_IRI), eq(VERSION_IRI), eq(DISTRIBUTION_IRI), any(RepositoryConnection.class));
        verify(utilsService).updateObject(eq(dist), any(RepositoryConnection.class));
    }

    /* removeVersionedDistribution */

    @Test
    public void testRemoveVersionedDistribution() throws Exception {
        // Setup:
        IRI distributionIRI = vf.createIRI(Version.versionedDistribution_IRI);
        try (RepositoryConnection conn = repo.getConnection()) {
            assertTrue(conn.getStatements(VERSION_IRI, distributionIRI, DISTRIBUTION_IRI, VERSION_IRI).hasNext());

            manager.removeVersionedDistribution(distributedCatalogId, VERSIONED_RECORD_IRI, VERSION_IRI, DISTRIBUTION_IRI);
            verify(utilsService).getVersionedDistribution(eq(distributedCatalogId), eq(VERSIONED_RECORD_IRI), eq(VERSION_IRI), eq(DISTRIBUTION_IRI), any(RepositoryConnection.class));
            verify(utilsService).remove(eq(DISTRIBUTION_IRI), any(RepositoryConnection.class));
            assertFalse(conn.getStatements(VERSION_IRI, distributionIRI, DISTRIBUTION_IRI, VERSION_IRI).hasNext());
        }
    }

    /* getVersionedDistribution */

    @Test
    public void testGetVersionedDistribution() throws Exception {
        // Setup:
        Version version = versionFactory.createNew(VERSION_IRI);
        Distribution dist = distributionFactory.createNew(DISTRIBUTION_IRI);
        version.setVersionedDistribution(Collections.singleton(dist));
        doReturn(version).when(utilsService).getVersion(eq(distributedCatalogId), eq(VERSIONED_RECORD_IRI), eq(VERSION_IRI), eq(versionFactory), any(RepositoryConnection.class));
        doReturn(dist).when(utilsService).getExpectedObject(eq(DISTRIBUTION_IRI), eq(distributionFactory), any(RepositoryConnection.class));

        Optional<Distribution> result = manager.getVersionedDistribution(distributedCatalogId, VERSIONED_RECORD_IRI, VERSION_IRI, DISTRIBUTION_IRI);
        verify(utilsService).getVersion(eq(distributedCatalogId), eq(VERSIONED_RECORD_IRI), eq(VERSION_IRI), eq(versionFactory), any(RepositoryConnection.class));
        verify(utilsService).getExpectedObject(eq(DISTRIBUTION_IRI), eq(distributionFactory), any(RepositoryConnection.class));
        assertTrue(result.isPresent());
        assertEquals(dist, result.get());
    }

    @Test
    public void testGetVersionedDistributionOfWrongVersion() {
        Optional<Distribution> result = manager.getVersionedDistribution(distributedCatalogId, VERSIONED_RECORD_IRI, VERSION_IRI, EMPTY_IRI);
        assertFalse(result.isPresent());
    }

    /* getBranches */

    @Test
    public void testGetBranches() throws Exception {
        // Setup:
        VersionedRDFRecord record = versionedRDFRecordFactory.createNew(VERSIONED_RDF_RECORD_IRI);
        record.setBranch(Collections.singleton(branchFactory.createNew(BRANCH_IRI)));
        doReturn(record).when(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRDFRecordFactory), any(RepositoryConnection.class));

        Set<Branch> branches = manager.getBranches(distributedCatalogId, VERSIONED_RDF_RECORD_IRI);
        verify(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRDFRecordFactory), any(RepositoryConnection.class));
        record.getBranch_resource().forEach(resource -> verify(utilsService).getExpectedObject(eq(resource), eq(branchFactory), any(RepositoryConnection.class)));
        assertEquals(1, branches.size());
    }

    /* addBranch */

    @Test
    public void testAddBranch() throws Exception {
        // Setup:
        VersionedRDFRecord record = versionedRDFRecordFactory.createNew(VERSIONED_RDF_RECORD_IRI);
        doReturn(record).when(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRDFRecordFactory), any(RepositoryConnection.class));
        Branch branch = branchFactory.createNew(NEW_IRI);

        manager.addBranch(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, branch);
        verify(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRDFRecordFactory), any(RepositoryConnection.class));
        verify(utilsService).updateObject(eq(record), any(RepositoryConnection.class));
        verify(utilsService).addObject(eq(branch), any(RepositoryConnection.class));
        assertEquals(1, record.getBranch_resource().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddBranchWithTakenResource() {
        // Setup:
        Branch branch = branchFactory.createNew(BRANCH_IRI);

        manager.addBranch(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, branch);
        verify(utilsService, times(0)).addObject(eq(branch), any(RepositoryConnection.class));
        verify(utilsService).throwAlreadyExists(BRANCH_IRI, distributionFactory);
    }

    /* addMasterBranch */

    @Test
    public void testAddMasterBranch() throws Exception {
        // Setup:
        VersionedRDFRecord record = versionedRDFRecordFactory.createNew(VERSIONED_RDF_RECORD_IRI);
        doReturn(record).when(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRDFRecordFactory), any(RepositoryConnection.class));

        manager.addMasterBranch(distributedCatalogId, VERSIONED_RDF_RECORD_IRI);
        verify(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRDFRecordFactory), any(RepositoryConnection.class));
        verify(utilsService).updateObject(eq(record), any(RepositoryConnection.class));
        verify(utilsService).addObject(any(Branch.class), any(RepositoryConnection.class));
        assertTrue(record.getMasterBranch_resource().isPresent());
        assertEquals(1, record.getBranch_resource().size());
    }

    @Test
    public void testAddMasterBranchToRecordWithMasterBranchAlready() {
        // Setup:
        VersionedRDFRecord record = versionedRDFRecordFactory.createNew(VERSIONED_RDF_RECORD_IRI);
        record.setMasterBranch(branchFactory.createNew(MASTER_BRANCH_IRI));
        doReturn(record).when(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRDFRecordFactory), any(RepositoryConnection.class));
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Record " + VERSIONED_RDF_RECORD_IRI + " already has a master Branch.");

        manager.addMasterBranch(distributedCatalogId, VERSIONED_RDF_RECORD_IRI);
        verify(utilsService, times(0)).addObject(any(Branch.class), any(RepositoryConnection.class));
    }

    /* updateBranch */

    @Test
    public void testUpdateBranch() throws Exception {
        // Setup:
        Branch branch = branchFactory.createNew(BRANCH_IRI);
        branch.getModel().add(BRANCH_IRI, titleIRI, vf.createLiteral("New Title"));

        manager.updateBranch(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, branch);
        verify(utilsService).validateBranch(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(BRANCH_IRI), any(RepositoryConnection.class));
        verify(utilsService).updateObject(eq(branch), any(RepositoryConnection.class));
    }

    @Test
    public void testUpdateUserBranch() throws Exception {
        // Setup:
        UserBranch branch = userBranchFactory.createNew(USER_BRANCH_IRI);
        branch.getModel().add(USER_BRANCH_IRI, titleIRI, vf.createLiteral("New Title"));

        manager.updateBranch(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, branch);
        verify(utilsService).validateBranch(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(USER_BRANCH_IRI), any(RepositoryConnection.class));
        verify(utilsService).updateObject(eq(branch), any(RepositoryConnection.class));
    }

    @Test
    public void testUpdateMasterBranch() {
        // Setup:
        Branch branch = branchFactory.createNew(MASTER_BRANCH_IRI);
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Branch " + MASTER_BRANCH_IRI + " is the master Branch and cannot be updated.");

        manager.updateBranch(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, branch);
        verify(utilsService, times(0)).updateObject(eq(branch), any(RepositoryConnection.class));
    }

    /* updateHead */

    @Test
    public void testUpdateHead() throws Exception {
        // Setup:
        Branch branch = branchFactory.createNew(BRANCH_IRI);
        doReturn(branch).when(utilsService).getBranch(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(BRANCH_IRI), eq(branchFactory), any(RepositoryConnection.class));

        manager.updateHead(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, BRANCH_IRI, COMMIT_IRI);
        verify(utilsService).getBranch(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(BRANCH_IRI), eq(branchFactory), any(RepositoryConnection.class));
        verify(utilsService).validateResource(eq(COMMIT_IRI), eq(commitFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).updateObject(eq(branch), any(RepositoryConnection.class));
        assertTrue(branch.getHead_resource().isPresent());
        assertEquals(COMMIT_IRI, branch.getHead_resource().get());
    }

    /* removeBranch */

    @Test
    public void testRemoveBranch() throws Exception {
        // Setup:
        Resource commitIdToRemove = vf.createIRI(COMMITS + "conflict2");
        IRI additionsToRemove = vf.createIRI(ADDITIONS + "conflict2");
        IRI deletionsToRemove = vf.createIRI(DELETIONS + "conflict2");
        Resource revisionToRemove = vf.createIRI(REVISIONS + "conflict2");
        Resource commitIdToKeep = vf.createIRI(COMMITS + "conflict0");
        Resource additionsToKeep = vf.createIRI(ADDITIONS + "conflict0");
        Resource deletionsToKeep = vf.createIRI(DELETIONS + "conflict0");

        Branch branch = branchFactory.createNew(BRANCH_IRI);
        branch.setHead(commitFactory.createNew(commitIdToRemove));
        doReturn(branch).when(utilsService).getBranch(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(BRANCH_IRI), eq(branchFactory), any(RepositoryConnection.class));

        doReturn(Stream.of(commitIdToRemove, commitIdToKeep).collect(Collectors.toList())).when(utilsService).getCommitChain(eq(commitIdToRemove), eq(false), any(RepositoryConnection.class));

        Revision revision = revisionFactory.createNew(revisionToRemove);
        revision.setAdditions(additionsToRemove);
        revision.setDeletions(deletionsToRemove);
        doReturn(revision).when(utilsService).getRevision(eq(commitIdToRemove), any(RepositoryConnection.class));

        IRI headIRI = vf.createIRI(Branch.head_IRI);
        IRI versionIRI = vf.createIRI(VersionedRecord.version_IRI);
        IRI branchIRI = vf.createIRI(VersionedRDFRecord.branch_IRI);
        try (RepositoryConnection conn = repo.getConnection()) {
            assertTrue(conn.getStatements(VERSIONED_RDF_RECORD_IRI, branchIRI, BRANCH_IRI, VERSIONED_RDF_RECORD_IRI).hasNext());
            assertTrue(conn.getStatements(VERSIONED_RDF_RECORD_IRI, versionIRI, LATEST_TAG_IRI, VERSIONED_RDF_RECORD_IRI).hasNext());
            // Remove the head statement so that commit logic works
            conn.remove(BRANCH_IRI, headIRI, null);

            manager.removeBranch(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, BRANCH_IRI);
            verify(utilsService).getBranch(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(BRANCH_IRI), eq(branchFactory), any(RepositoryConnection.class));
            verify(utilsService).getCommitChain(eq(commitIdToRemove), eq(false), any(RepositoryConnection.class));
            verify(utilsService).remove(eq(BRANCH_IRI), any(RepositoryConnection.class));
            verify(utilsService).remove(eq(commitIdToRemove), any(RepositoryConnection.class));
            verify(utilsService, times(0)).remove(eq(commitIdToKeep), any(RepositoryConnection.class));
            verify(utilsService).remove(eq(additionsToRemove), any(RepositoryConnection.class));
            verify(utilsService).remove(eq(deletionsToRemove), any(RepositoryConnection.class));
            verify(utilsService, times(0)).remove(eq(additionsToKeep), any(RepositoryConnection.class));
            verify(utilsService, times(0)).remove(eq(deletionsToKeep), any(RepositoryConnection.class));
            verify(utilsService).remove(eq(LATEST_TAG_IRI), any(RepositoryConnection.class));
            assertFalse(conn.getStatements(VERSIONED_RDF_RECORD_IRI, branchIRI, BRANCH_IRI, VERSIONED_RDF_RECORD_IRI).hasNext());
            assertFalse(conn.getStatements(VERSIONED_RDF_RECORD_IRI, versionIRI, LATEST_TAG_IRI, VERSIONED_RDF_RECORD_IRI).hasNext());
        }
    }

    @Test
    public void testRemoveBranchWithQuads() throws Exception {
        // TODO: This does not test if a chain of commits is properly removed. Requires real utilsService.
        // Setup:
        IRI record = vf.createIRI(RECORDS + "quad-versioned-rdf-record");
        IRI branchToRemove = vf.createIRI(BRANCHES + "quad-branch");
        Resource commit0 = vf.createIRI(COMMITS + "quad-test0");
        Resource commit1 = vf.createIRI(COMMITS + "quad-test1");
        Resource commit2 = vf.createIRI(COMMITS + "quad-test2");
        IRI additionsToRemove = vf.createIRI(ADDITIONS + "quad-test2");
        IRI deletionsToRemove = vf.createIRI(DELETIONS + "quad-test2");
        IRI graphAdditionsToRemove = vf.createIRI(ADDITIONS + "quad-test2%00http%3A%2F%2Fmatonto.org%2Ftest%2Fgraphs%23quad-graph1");
        IRI graphDeletionsToRemove = vf.createIRI(DELETIONS + "quad-test2%00http%3A%2F%2Fmatonto.org%2Ftest%2Fgraphs%23quad-graph1");
        Resource revisionToRemove = vf.createIRI(REVISIONS + "quad-test2");

        Branch branch = branchFactory.createNew(branchToRemove);
        branch.setHead(commitFactory.createNew(commit2));
        doReturn(branch).when(utilsService).getBranch(eq(distributedCatalogId), eq(record), eq(branchToRemove), eq(branchFactory), any(RepositoryConnection.class));

        doReturn(Stream.of(commit2, commit1, commit0).collect(Collectors.toList()))
                .when(utilsService).getCommitChain(eq(commit2), eq(false), any(RepositoryConnection.class));

        GraphRevision graphRevision = graphRevisionFactory.createNew(vf.createBNode());
        graphRevision.setRevisionedGraph(vf.createIRI(GRAPHS + "quad-graph1"));
        graphRevision.setAdditions(graphAdditionsToRemove);
        graphRevision.setDeletions(graphDeletionsToRemove);

        Set<GraphRevision> graphRevisions = new HashSet<>();
        graphRevisions.add(graphRevision);

        Revision revision = revisionFactory.createNew(revisionToRemove, graphRevision.getModel());
        revision.setAdditions(additionsToRemove);
        revision.setDeletions(deletionsToRemove);
        revision.setGraphRevision(graphRevisions);

        doReturn(revision).when(utilsService).getRevision(eq(commit2), any(RepositoryConnection.class));

        IRI headIRI = vf.createIRI(Branch.head_IRI);
        IRI branchIRI = vf.createIRI(VersionedRDFRecord.branch_IRI);
        try (RepositoryConnection conn = repo.getConnection()) {
            assertTrue(conn.getStatements(record, branchIRI, branchToRemove, record).hasNext());
            // Remove the head statement so that commit logic works
            conn.remove(branchToRemove, headIRI, null);

            manager.removeBranch(distributedCatalogId, record, branchToRemove);
            verify(utilsService).getBranch(eq(distributedCatalogId), eq(record), eq(branchToRemove), eq(branchFactory), any(RepositoryConnection.class));
            verify(utilsService).getCommitChain(eq(commit2), eq(false), any(RepositoryConnection.class));
            verify(utilsService).remove(eq(branchToRemove), any(RepositoryConnection.class));
            verify(utilsService).remove(eq(commit2), any(RepositoryConnection.class));
            verify(utilsService).remove(eq(additionsToRemove), any(RepositoryConnection.class));
            verify(utilsService).remove(eq(deletionsToRemove), any(RepositoryConnection.class));
            verify(utilsService).remove(eq(graphAdditionsToRemove), any(RepositoryConnection.class));
            verify(utilsService).remove(eq(graphDeletionsToRemove), any(RepositoryConnection.class));
            assertFalse(conn.getStatements(record, branchIRI, branchToRemove, record).hasNext());
        }
    }

    @Test
    public void testRemoveBranchWithNoHead() {
        // Setup:
        Branch branch = branchFactory.createNew(BRANCH_IRI);
        doReturn(branch).when(utilsService).getBranch(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(BRANCH_IRI), eq(branchFactory), any(RepositoryConnection.class));
        IRI branchIRI = vf.createIRI(VersionedRDFRecord.branch_IRI);
        try (RepositoryConnection conn = repo.getConnection()) {
            assertTrue(conn.getStatements(VERSIONED_RDF_RECORD_IRI, branchIRI, BRANCH_IRI, VERSIONED_RDF_RECORD_IRI).hasNext());

            manager.removeBranch(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, BRANCH_IRI);
            verify(utilsService).getBranch(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(BRANCH_IRI), eq(branchFactory), any(RepositoryConnection.class));
            verify(utilsService).remove(eq(BRANCH_IRI), any(RepositoryConnection.class));
            assertFalse(conn.getStatements(VERSIONED_RDF_RECORD_IRI, branchIRI, BRANCH_IRI, VERSIONED_RDF_RECORD_IRI).hasNext());
        }
    }

    @Test
    public void testRemoveMasterBranch() {
        // Setup:
        Branch branch = branchFactory.createNew(MASTER_BRANCH_IRI);
        doReturn(branch).when(utilsService).getBranch(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(MASTER_BRANCH_IRI), eq(branchFactory), any(RepositoryConnection.class));
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Branch " + MASTER_BRANCH_IRI + " is the master Branch and cannot be removed.");

        manager.removeBranch(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, MASTER_BRANCH_IRI);
        verify(utilsService).getBranch(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(MASTER_BRANCH_IRI), eq(branchFactory), any(RepositoryConnection.class));
        verify(utilsService, times(0)).remove(eq(MASTER_BRANCH_IRI), any(RepositoryConnection.class));
    }

    /* getBranch */

    @Test
    public void testGetBranch() throws Exception {
        // Setup:
        VersionedRDFRecord record = versionedRDFRecordFactory.createNew(VERSIONED_RDF_RECORD_IRI);
        Branch branch = branchFactory.createNew(BRANCH_IRI);
        record.setBranch(Collections.singleton(branch));
        doReturn(record).when(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRDFRecordFactory), any(RepositoryConnection.class));
        doReturn(branch).when(utilsService).getExpectedObject(eq(BRANCH_IRI), eq(branchFactory), any(RepositoryConnection.class));

        Optional<Branch> result = manager.getBranch(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, BRANCH_IRI, branchFactory);
        verify(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRDFRecordFactory), any(RepositoryConnection.class));
        verify(utilsService).getExpectedObject(eq(BRANCH_IRI), eq(branchFactory), any(RepositoryConnection.class));
        assertTrue(result.isPresent());
        assertEquals(branch, result.get());
    }

    @Test
    public void testGetUserBranch() throws Exception {
        // Setup:
        VersionedRDFRecord record = versionedRDFRecordFactory.createNew(VERSIONED_RDF_RECORD_IRI);
        UserBranch branch = userBranchFactory.createNew(USER_BRANCH_IRI);
        record.setBranch(Collections.singleton(branch));
        doReturn(record).when(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRDFRecordFactory), any(RepositoryConnection.class));
        doReturn(branch).when(utilsService).getExpectedObject(eq(USER_BRANCH_IRI), eq(userBranchFactory), any(RepositoryConnection.class));

        Optional<UserBranch> result = manager.getBranch(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, USER_BRANCH_IRI, userBranchFactory);
        verify(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRDFRecordFactory), any(RepositoryConnection.class));
        verify(utilsService).getExpectedObject(eq(USER_BRANCH_IRI), eq(userBranchFactory), any(RepositoryConnection.class));
        assertTrue(result.isPresent());
        assertEquals(branch, result.get());
    }

    @Test
    public void testGetBranchOfWrongRecord() throws Exception {
        Optional<Branch> result = manager.getBranch(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, EMPTY_IRI, branchFactory);
        assertFalse(result.isPresent());
    }

    /* getMasterBranch */

    @Test
    public void testGetMasterBranch() throws Exception {
        // Setup:
        VersionedRDFRecord record = versionedRDFRecordFactory.createNew(VERSIONED_RDF_RECORD_IRI);
        Branch branch = branchFactory.createNew(MASTER_BRANCH_IRI);
        record.setMasterBranch(branch);
        doReturn(record).when(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRDFRecordFactory), any(RepositoryConnection.class));
        doReturn(branch).when(utilsService).getExpectedObject(eq(MASTER_BRANCH_IRI), eq(branchFactory), any(RepositoryConnection.class));

        Branch result = manager.getMasterBranch(distributedCatalogId, VERSIONED_RDF_RECORD_IRI);
        verify(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRDFRecordFactory), any(RepositoryConnection.class));
        verify(utilsService).getExpectedObject(eq(MASTER_BRANCH_IRI), eq(branchFactory), any(RepositoryConnection.class));
        assertEquals(branch, result);
    }

    @Test
    public void testGetMasterBranchOfRecordWithoutMasterSet() {
        // Setup:
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Record " + VERSIONED_RDF_RECORD_IRI + " does not have a master Branch set.");

        manager.getMasterBranch(distributedCatalogId, VERSIONED_RDF_RECORD_IRI);
    }

    /* createCommit */

    @Test
    public void testCreateCommit() throws Exception {
        // Setup:
        IRI provGenerated = vf.createIRI(Activity.generated_IRI);
        IRI provAtTime = vf.createIRI(InstantaneousEvent.atTime_IRI);
        IRI provWasAssociatedWith = vf.createIRI(Activity.wasAssociatedWith_IRI);
        IRI revisionId = vf.createIRI("http://matonto.org/revisions#test");
        Resource generation = vf.createIRI("http://matonto.org/test");
        Resource generation2 = vf.createIRI("http://matonto.org/test2");
        Commit base = commitFactory.createNew(COMMIT_IRI);
        base.setProperty(generation, provGenerated);
        Commit auxiliary = commitFactory.createNew(COMMIT_IRI);
        auxiliary.setProperty(generation2, provGenerated);
        InProgressCommit inProgressCommit = inProgressCommitFactory.createNew(IN_PROGRESS_COMMIT_IRI);
        inProgressCommit.setProperty(vf.createIRI("http://matonto.org/user"), provWasAssociatedWith);
        inProgressCommit.setProperty(revisionId, provGenerated);
        Revision revision = revisionFactory.createNew(revisionId);
        inProgressCommit.getModel().addAll(revision.getModel());

        Commit result = manager.createCommit(inProgressCommit, "message", null, null);
        assertTrue(result.getProperty(provAtTime).isPresent());
        assertEquals("message", result.getProperty(titleIRI).get().stringValue());
        assertFalse(result.getBaseCommit().isPresent());
        assertFalse(result.getAuxiliaryCommit().isPresent());
        assertFalse(result.getModel().contains(IN_PROGRESS_COMMIT_IRI, null, null));
        assertTrue(result.getModel().contains(revisionId, null, null));

        result = manager.createCommit(inProgressCommit, "message", base, auxiliary);
        assertTrue(result.getProperty(provAtTime).isPresent());
        assertEquals("message", result.getProperty(titleIRI).get().stringValue());
        assertTrue(result.getBaseCommit_resource().isPresent() && result.getBaseCommit_resource().get().equals(COMMIT_IRI));
        assertTrue(result.getAuxiliaryCommit_resource().isPresent() && result.getAuxiliaryCommit_resource().get().equals(COMMIT_IRI));
        assertFalse(result.getModel().contains(IN_PROGRESS_COMMIT_IRI, null, null));
        assertTrue(result.getModel().contains(revisionId, null, null));
    }

    @Test
    public void testCreateCommitWithOnlyAuxiliary() {
        //Setup:
        InProgressCommit inProgressCommit = inProgressCommitFactory.createNew(IN_PROGRESS_COMMIT_IRI);
        Commit auxiliary = commitFactory.createNew(COMMIT_IRI);
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Commit must have a base commit in order to have an auxiliary commit");

        manager.createCommit(inProgressCommit, "message", null, auxiliary);
    }

    /* createInProgressCommit */

    @Test
    public void testCreateInProgressCommit() throws Exception {
        // Setup:
        IRI provWasDerivedFrom = vf.createIRI(Entity.wasDerivedFrom_IRI);
        IRI provGenerated = vf.createIRI(Activity.generated_IRI);
        IRI provWasAssociatedWith = vf.createIRI(Activity.wasAssociatedWith_IRI);
        IRI provWasInformedBy = vf.createIRI(Activity.wasInformedBy_IRI);
        User user = userFactory.createNew(USER_IRI);

        InProgressCommit result = manager.createInProgressCommit(user);
        assertTrue(result.getProperty(provWasAssociatedWith).isPresent());
        assertEquals(USER_IRI.stringValue(), result.getProperty(provWasAssociatedWith).get().stringValue());
        assertTrue(result.getProperty(provGenerated).isPresent());
        Revision revision = revisionFactory.createNew((Resource) result.getProperty(provGenerated).get(),
                result.getModel());
        assertTrue(revision.getAdditions().isPresent());
        assertTrue(revision.getDeletions().isPresent());

        result = manager.createInProgressCommit(user);
        assertEquals(USER_IRI.stringValue(), result.getProperty(provWasAssociatedWith).get().stringValue());
        assertTrue(result.getProperty(provGenerated).isPresent());
        assertFalse(result.getProperty(provWasInformedBy).isPresent());
        revision = revisionFactory.createNew((Resource) result.getProperty(provGenerated).get(),
                result.getModel());
        assertTrue(revision.getAdditions().isPresent());
        assertTrue(revision.getDeletions().isPresent());
        assertFalse(revision.getProperty(provWasDerivedFrom).isPresent());
    }

    /* updateInProgressCommit(Resource, Resource, Resource, Model, Model) */

    @Test
    public void testUpdateInProgressCommit() throws Exception {
        // Setup:
        Model additions = mf.createModel();
        Model deletions = mf.createModel();

        manager.updateInProgressCommit(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, IN_PROGRESS_COMMIT_IRI, additions, deletions);
        verify(utilsService).validateInProgressCommit(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(IN_PROGRESS_COMMIT_IRI), any(RepositoryConnection.class));
        verify(utilsService).updateCommit(eq(IN_PROGRESS_COMMIT_IRI), eq(additions), eq(deletions), any(RepositoryConnection.class));
    }

    /* updateInProgressCommit(Resource, Resource, Resource, Model, Model) */

    @Test
    public void testUpdateInProgressCommitWithUser() throws Exception {
        // Setup:
        User user = userFactory.createNew(USER_IRI);
        Model additions = mf.createModel();
        Model deletions = mf.createModel();
        InProgressCommit commit = inProgressCommitFactory.createNew(IN_PROGRESS_COMMIT_IRI);
        doReturn(commit).when(utilsService).getInProgressCommit(eq(VERSIONED_RDF_RECORD_IRI), eq(USER_IRI), any(RepositoryConnection.class));

        manager.updateInProgressCommit(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, user, additions, deletions);
        verify(utilsService).validateRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRDFRecordFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).getInProgressCommit(eq(VERSIONED_RDF_RECORD_IRI), eq(USER_IRI), any(RepositoryConnection.class));
        verify(utilsService).updateCommit(eq(commit), eq(additions), eq(deletions), any(RepositoryConnection.class));
    }

    /* addInProgressCommit */

    @Test
    public void testAddInProgressCommit() throws Exception {
        // Setup:
        User user = userFactory.createNew(USER_IRI);
        InProgressCommit commit = inProgressCommitFactory.createNew(NEW_IRI);
        commit.setProperty(user.getResource(), vf.createIRI(Activity.wasAssociatedWith_IRI));
        doReturn(Optional.empty()).when(utilsService).getInProgressCommitIRI(eq(VERSIONED_RDF_RECORD_IRI), eq(USER_IRI), any(RepositoryConnection.class));

        manager.addInProgressCommit(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, commit);
        verify(utilsService).getInProgressCommitIRI(eq(VERSIONED_RDF_RECORD_IRI), eq(USER_IRI), any(RepositoryConnection.class));
        verify(utilsService).getRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRDFRecordFactory), any(RepositoryConnection.class));
        verify(utilsService).addObject(eq(commit), any(RepositoryConnection.class));
        assertTrue(commit.getOnVersionedRDFRecord_resource().isPresent());
        assertEquals(VERSIONED_RDF_RECORD_IRI, commit.getOnVersionedRDFRecord_resource().get());
    }

    @Test
    public void testAddInProgressCommitWithNoUser() {
        // Setup:
        InProgressCommit commit = inProgressCommitFactory.createNew(NEW_IRI);
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("User not set on InProgressCommit " + commit.getResource());

        manager.addInProgressCommit(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, commit);
        verify(utilsService, times(0)).addObject(eq(commit), any(RepositoryConnection.class));
    }

    @Test
    public void testAddInProgressCommitWhenYouAlreadyHaveOne() {
        // Setup:
        InProgressCommit commit = inProgressCommitFactory.createNew(NEW_IRI);
        commit.setProperty(USER_IRI, vf.createIRI(Activity.wasAssociatedWith_IRI));
        doReturn(Optional.of(NEW_IRI)).when(utilsService).getInProgressCommitIRI(eq(VERSIONED_RDF_RECORD_IRI), eq(USER_IRI), any(RepositoryConnection.class));
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("User " + USER_IRI + " already has an InProgressCommit for Record " + VERSIONED_RDF_RECORD_IRI);

        manager.addInProgressCommit(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, commit);
        verify(utilsService, times(0)).addObject(eq(commit), any(RepositoryConnection.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddInProgressCommitWithTakenResource() {
        InProgressCommit commit = inProgressCommitFactory.createNew(IN_PROGRESS_COMMIT_IRI);
        commit.setProperty(USER_IRI, vf.createIRI(Activity.wasAssociatedWith_IRI));
        doReturn(Optional.empty()).when(utilsService).getInProgressCommitIRI(eq(VERSIONED_RDF_RECORD_IRI), eq(USER_IRI), any(RepositoryConnection.class));

        manager.addInProgressCommit(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, commit);
        verify(utilsService, times(0)).addObject(eq(commit), any(RepositoryConnection.class));
        verify(utilsService).throwAlreadyExists(IN_PROGRESS_COMMIT_IRI, inProgressCommitFactory);
    }

    /* getCommit */

    @Test
    public void testGetCommitThatIsNotTheHead() throws Exception {
        // Setup:
        Resource headId = vf.createIRI(COMMITS + "test4a");
        Resource commitId = vf.createIRI(COMMITS + "test0");
        Branch branch = branchFactory.createNew(MASTER_BRANCH_IRI);
        Commit commit = commitFactory.createNew(commitId);
        doReturn(headId).when(utilsService).getHeadCommitIRI(branch);
        doReturn(branch).when(utilsService).getExpectedObject(eq(MASTER_BRANCH_IRI), eq(branchFactory), any(RepositoryConnection.class));
        doReturn(commit).when(utilsService).getExpectedObject(eq(commitId), eq(commitFactory), any(RepositoryConnection.class));
        doReturn(true).when(utilsService).commitInBranch(eq(MASTER_BRANCH_IRI), eq(commitId), any(RepositoryConnection.class));
        doReturn(Stream.of(headId, commitId).collect(Collectors.toList())).when(utilsService).getCommitChain(eq(headId), eq(false), any(RepositoryConnection.class));

        Optional<Commit> result = manager.getCommit(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, MASTER_BRANCH_IRI, commitId);
        verify(utilsService).validateBranch(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(MASTER_BRANCH_IRI), any(RepositoryConnection.class));
        verify(utilsService).commitInBranch(eq(MASTER_BRANCH_IRI), eq(commitId), any(RepositoryConnection.class));
        verify(utilsService).getExpectedObject(eq(commitId), eq(commitFactory), any(RepositoryConnection.class));
        assertTrue(result.isPresent());
        assertEquals(commit, result.get());
    }

    @Test
    public void testGetCommitThatIsTheHead() throws Exception {
        // Setup:
        Resource commitId = vf.createIRI(COMMITS + "test4a");
        Branch branch = branchFactory.createNew(MASTER_BRANCH_IRI);
        Commit commit = commitFactory.createNew(commitId);
        doReturn(commitId).when(utilsService).getHeadCommitIRI(branch);
        doReturn(branch).when(utilsService).getExpectedObject(eq(MASTER_BRANCH_IRI), eq(branchFactory), any(RepositoryConnection.class));
        doReturn(commit).when(utilsService).getExpectedObject(eq(commitId), eq(commitFactory), any(RepositoryConnection.class));
        doReturn(true).when(utilsService).commitInBranch(eq(MASTER_BRANCH_IRI), eq(commitId), any(RepositoryConnection.class));

        Optional<Commit> result = manager.getCommit(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, MASTER_BRANCH_IRI, commitId);
        verify(utilsService).validateBranch(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(MASTER_BRANCH_IRI), any(RepositoryConnection.class));
        verify(utilsService).commitInBranch(eq(MASTER_BRANCH_IRI), eq(commitId), any(RepositoryConnection.class));
        verify(utilsService).getExpectedObject(eq(commitId), eq(commitFactory), any(RepositoryConnection.class));
        verify(utilsService, times(0)).getCommitChain(eq(commitId), eq(false), any(RepositoryConnection.class));
        assertTrue(result.isPresent());
        assertEquals(commit, result.get());
    }

    @Test
    public void testGetCommitThatDoesNotBelongToBranch() {
        // Setup:
        Resource headId = vf.createIRI(COMMITS + "test4a");
        Branch branch = branchFactory.createNew(MASTER_BRANCH_IRI);
        doReturn(headId).when(utilsService).getHeadCommitIRI(branch);
        doReturn(branch).when(utilsService).getExpectedObject(eq(MASTER_BRANCH_IRI), eq(branchFactory), any(RepositoryConnection.class));
        doReturn(false).when(utilsService).commitInBranch(eq(MASTER_BRANCH_IRI), eq(COMMIT_IRI), any(RepositoryConnection.class));

        Optional<Commit> result = manager.getCommit(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, MASTER_BRANCH_IRI, COMMIT_IRI);
        verify(utilsService).validateBranch(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(MASTER_BRANCH_IRI), any(RepositoryConnection.class));
        verify(utilsService, times(0)).getExpectedObject(eq(COMMIT_IRI), eq(commitFactory), any(RepositoryConnection.class));
        assertFalse(result.isPresent());
    }

    /* getHeadCommit */

    @Test
    public void getHeadCommit() throws Exception {
        // Setup:
        Resource commitId = vf.createIRI(COMMITS + "test4a");
        Branch branch = branchFactory.createNew(BRANCH_IRI);
        Commit commit = commitFactory.createNew(commitId);
        doReturn(commitId).when(utilsService).getHeadCommitIRI(branch);
        doReturn(branch).when(utilsService).getExpectedObject(eq(BRANCH_IRI), eq(branchFactory), any(RepositoryConnection.class));
        doReturn(commit).when(utilsService).getExpectedObject(eq(commitId), eq(commitFactory), any(RepositoryConnection.class));

        Commit result = manager.getHeadCommit(distributedCatalogId, RECORD_IRI, BRANCH_IRI);
        verify(utilsService).validateBranch(eq(distributedCatalogId), eq(RECORD_IRI), eq(BRANCH_IRI), any(RepositoryConnection.class));
        verify(utilsService).getExpectedObject(eq(BRANCH_IRI), eq(branchFactory), any(RepositoryConnection.class));
        verify(utilsService).getExpectedObject(eq(commitId), eq(commitFactory), any(RepositoryConnection.class));
        assertEquals(commit, result);
    }

    /* getInProgressCommit(Resource, Resource, User) */

    @Test
    public void testGetInProgressCommitWithUser() throws Exception {
        // Setup:
        User user = userFactory.createNew(USER_IRI);
        InProgressCommit commit = inProgressCommitFactory.createNew(IN_PROGRESS_COMMIT_IRI);
        doReturn(Optional.of(IN_PROGRESS_COMMIT_IRI)).when(utilsService).getInProgressCommitIRI(eq(VERSIONED_RDF_RECORD_IRI), eq(USER_IRI), any(RepositoryConnection.class));
        doReturn(commit).when(utilsService).getExpectedObject(eq(IN_PROGRESS_COMMIT_IRI), eq(inProgressCommitFactory), any(RepositoryConnection.class));

        Optional<InProgressCommit> result = manager.getInProgressCommit(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, user);
        verify(utilsService).validateRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRDFRecordFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).getInProgressCommitIRI(eq(VERSIONED_RDF_RECORD_IRI), eq(USER_IRI), any(RepositoryConnection.class));
        verify(utilsService).getExpectedObject(eq(IN_PROGRESS_COMMIT_IRI), eq(inProgressCommitFactory), any(RepositoryConnection.class));
        assertTrue(result.isPresent());
        assertEquals(commit, result.get());
    }

    @Test
    public void testGetInProgressCommitForUserWithoutOne() {
        // Setup:
        User user = userFactory.createNew(USER_IRI);
        doReturn(Optional.empty()).when(utilsService).getInProgressCommitIRI(eq(VERSIONED_RDF_RECORD_IRI), eq(user.getResource()), any(RepositoryConnection.class));

        Optional<InProgressCommit> result = manager.getInProgressCommit(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, user);
        verify(utilsService).validateRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRDFRecordFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).getInProgressCommitIRI(eq(VERSIONED_RDF_RECORD_IRI), eq(user.getResource()), any(RepositoryConnection.class));
        verify(utilsService, times(0)).getExpectedObject(eq(IN_PROGRESS_COMMIT_IRI), eq(inProgressCommitFactory), any(RepositoryConnection.class));
        assertFalse(result.isPresent());
    }

    /* getInProgressCommit(Resource, Resource, Resource) */

    @Test
    public void testGetInProgressCommitWithResource() throws Exception {
        // Setup:
        InProgressCommit commit = inProgressCommitFactory.createNew(IN_PROGRESS_COMMIT_IRI);
        commit.setOnVersionedRDFRecord(versionedRDFRecordFactory.createNew(VERSIONED_RDF_RECORD_IRI));
        doReturn(Optional.of(commit)).when(utilsService).optObject(eq(IN_PROGRESS_COMMIT_IRI), eq(inProgressCommitFactory), any(RepositoryConnection.class));

        Optional<InProgressCommit> result = manager.getInProgressCommit(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, IN_PROGRESS_COMMIT_IRI);
        verify(utilsService).validateRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRDFRecordFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).optObject(eq(IN_PROGRESS_COMMIT_IRI), eq(inProgressCommitFactory), any(RepositoryConnection.class));
        assertTrue(result.isPresent());
        assertEquals(commit, result.get());
    }

    @Test
    public void testGetMissingInProgressCommitWithResource() {
        // Setup:
        doReturn(Optional.empty()).when(utilsService).optObject(eq(EMPTY_IRI), eq(inProgressCommitFactory), any(RepositoryConnection.class));

        Optional<InProgressCommit> result = manager.getInProgressCommit(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, EMPTY_IRI);
        verify(utilsService).validateRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRDFRecordFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).optObject(eq(EMPTY_IRI), eq(inProgressCommitFactory), any(RepositoryConnection.class));
        assertFalse(result.isPresent());
    }

    @Test
    public void testGetInProgressCommitWithResourceForWrongRecord() {
        // Setup:
        InProgressCommit commit = inProgressCommitFactory.createNew(IN_PROGRESS_COMMIT_IRI);
        commit.setOnVersionedRDFRecord(versionedRDFRecordFactory.createNew(EMPTY_IRI));
        doReturn(Optional.of(commit)).when(utilsService).optObject(eq(IN_PROGRESS_COMMIT_IRI), eq(inProgressCommitFactory), any(RepositoryConnection.class));

        Optional<InProgressCommit> result = manager.getInProgressCommit(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, IN_PROGRESS_COMMIT_IRI);
        verify(utilsService).validateRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRDFRecordFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).optObject(eq(IN_PROGRESS_COMMIT_IRI), eq(inProgressCommitFactory), any(RepositoryConnection.class));
        assertFalse(result.isPresent());
    }

    @Test
    public void testGetInProgressCommitWithResourceAndNoRecordSet() {
        // Setup:
        InProgressCommit commit = inProgressCommitFactory.createNew(IN_PROGRESS_COMMIT_IRI);
        doReturn(Optional.of(commit)).when(utilsService).optObject(eq(IN_PROGRESS_COMMIT_IRI), eq(inProgressCommitFactory), any(RepositoryConnection.class));
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("InProgressCommit " + IN_PROGRESS_COMMIT_IRI + " has no Record set.");

        manager.getInProgressCommit(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, IN_PROGRESS_COMMIT_IRI);
    }

    /* getCommitDifference */

    @Test
    public void testGetCommitDifference() throws Exception {
        // Setup:
        Model addModel = mf.createModel(Collections.singleton(
                vf.createStatement(vf.createIRI("http://matonto.org/test/add"), titleIRI, vf.createLiteral("Add"))));
        Model delModel = mf.createModel(Collections.singleton(
                vf.createStatement(vf.createIRI("http://matonto.org/test/delete"), titleIRI, vf.createLiteral("Delete"))));
        Difference expect = new Difference.Builder().additions(addModel).deletions(delModel).build();
        doReturn(expect).when(utilsService).getCommitDifference(eq(COMMIT_IRI), any(RepositoryConnection.class));

        Difference result = manager.getCommitDifference(COMMIT_IRI);
        verify(utilsService).validateResource(eq(COMMIT_IRI), eq(commitFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).getCommitDifference(eq(COMMIT_IRI), any(RepositoryConnection.class));
        assertEquals(expect, result);
    }

    /* removeInProgressCommit(Resource, Resource, Resource) */

    @Test
    public void testRemoveInProgressCommit() throws Exception {
        // Setup:
        InProgressCommit commit = inProgressCommitFactory.createNew(IN_PROGRESS_COMMIT_IRI);
        doReturn(commit).when(utilsService).getInProgressCommit(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(IN_PROGRESS_COMMIT_IRI), any(RepositoryConnection.class));

        manager.removeInProgressCommit(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, IN_PROGRESS_COMMIT_IRI);
        verify(utilsService).getInProgressCommit(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(IN_PROGRESS_COMMIT_IRI), any(RepositoryConnection.class));
        verify(utilsService).removeInProgressCommit(eq(commit), any(RepositoryConnection.class));
    }

    /* removeInProgressCommit(Resource, Resource, User) */

    @Test
    public void testRemoveInProgressCommitWithUser() throws Exception {
        // Setup:
        User user = userFactory.createNew(USER_IRI);
        InProgressCommit commit = inProgressCommitFactory.createNew(IN_PROGRESS_COMMIT_IRI);
        doReturn(commit).when(utilsService).getInProgressCommit(eq(VERSIONED_RDF_RECORD_IRI), eq(USER_IRI), any(RepositoryConnection.class));

        manager.removeInProgressCommit(distributedCatalogId, VERSIONED_RDF_RECORD_IRI, user);
        verify(utilsService).validateRecord(eq(distributedCatalogId), eq(VERSIONED_RDF_RECORD_IRI), eq(versionedRDFRecordFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).getInProgressCommit(eq(VERSIONED_RDF_RECORD_IRI), eq(USER_IRI), any(RepositoryConnection.class));
        verify(utilsService).removeInProgressCommit(eq(commit), any(RepositoryConnection.class));
    }

    /* applyInProgressCommit */

    @Test
    public void testApplyInProgressCommit() throws Exception {
        // Setup:
        Difference diff = new Difference.Builder().build();
        Model entity = mf.createModel();
        Model expected = mf.createModel();
        doReturn(diff).when(utilsService).getCommitDifference(eq(IN_PROGRESS_COMMIT_IRI), any(RepositoryConnection.class));
        doReturn(expected).when(utilsService).applyDifference(entity, diff);

        Model result = manager.applyInProgressCommit(IN_PROGRESS_COMMIT_IRI, entity);
        assertEquals(expected, result);
        verify(utilsService).validateResource(eq(IN_PROGRESS_COMMIT_IRI), eq(inProgressCommitFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).getCommitDifference(eq(IN_PROGRESS_COMMIT_IRI), any(RepositoryConnection.class));
        verify(utilsService).applyDifference(entity, diff);
    }

    /* getCompiledResource */

    @Test
    public void testGetCompiledResourceWithUnmergedPast() throws Exception {
        // Setup:
        Resource commitId = vf.createIRI(COMMITS + "test0");
        Model expected = mf.createModel();
        expected.add(vf.createIRI("http://matonto.org/test/ontology"), typeIRI, vf.createIRI("http://www.w3.org/2002/07/owl#Ontology"));
        doReturn(expected).when(utilsService).getCompiledResource(eq(commitId), any(RepositoryConnection.class));

        Model result = manager.getCompiledResource(commitId);
        verify(utilsService).validateResource(eq(commitId), eq(commitFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).getCompiledResource(eq(commitId), any(RepositoryConnection.class));
        result.forEach(statement -> assertTrue(expected.contains(statement)));
    }

    @Test
    public void testGetCompiledResourceWithPathAndUnmergedPast() throws Exception {
        // Setup:
        Resource commitId = vf.createIRI(COMMITS + "test0");
        Model expected = mf.createModel();
        expected.add(vf.createIRI("http://matonto.org/test/ontology"), typeIRI, vf.createIRI("http://www.w3.org/2002/07/owl#Ontology"));
        doReturn(expected).when(utilsService).getCompiledResource(eq(commitId), any(RepositoryConnection.class));

        Model result = manager.getCompiledResource(RECORD_IRI, BRANCH_IRI, commitId);
        verify(utilsService).validateCommitPath(eq(localCatalogId), eq(RECORD_IRI), eq(BRANCH_IRI), eq(commitId), any(RepositoryConnection.class));
        verify(utilsService).getCompiledResource(eq(commitId), any(RepositoryConnection.class));
        result.forEach(statement -> assertTrue(expected.contains(statement)));
    }

    /* getConflicts */

    @Test
    public void testGetConflictsClassDeletion() throws Exception {
        // Setup:
        // Class deletion
        IRI sub = vf.createIRI("http://test.com#sub");
        Resource leftId = vf.createIRI(COMMITS + "conflict1");
        Resource rightId = vf.createIRI(COMMITS + "conflict2");

        Model leftModel = mf.createModel();
        leftModel.add(sub, typeIRI, vf.createIRI("http://test.com#Type"));
        Difference leftDiff = new Difference.Builder()
                .additions(mf.createModel())
                .deletions(leftModel)
                .build();

        Model rightModel = mf.createModel();
        rightModel.add(sub, typeIRI, vf.createIRI("http://test.com#Type"));
        Difference rightDiff = new Difference.Builder()
                .additions(rightModel)
                .deletions(mf.createModel())
                .build();

        Model originalModel = mf.createModel();
        originalModel.add(sub, descriptionIRI, vf.createLiteral("Description"));
        setUpConflictTest(leftId, rightId, leftDiff, rightDiff, originalModel);

        Set<Conflict> result = manager.getConflicts(leftId, rightId);
        verify(utilsService).validateResource(eq(leftId), eq(commitFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).validateResource(eq(rightId), eq(commitFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).getCommitChain(eq(leftId), eq(true), any(RepositoryConnection.class));
        verify(utilsService).getCommitChain(eq(rightId), eq(true), any(RepositoryConnection.class));
        verify(utilsService, times(2)).getRevisionChanges(anyListOf(Resource.class), any(RepositoryConnection.class));
        verify(utilsService).getCompiledResource(eq(COMMIT_IRI), any(RepositoryConnection.class));
        assertEquals(1, result.size());
        result.forEach(conflict -> {
            assertEquals(0, conflict.getOriginal().size());
            Difference left = conflict.getLeftDifference();
            Difference right = conflict.getRightDifference();
            assertEquals(0, left.getAdditions().size());
            assertEquals(1, right.getAdditions().size());
            assertEquals(0, right.getDeletions().size());
            assertEquals(1, left.getDeletions().size());
            Stream.of(left.getDeletions(), conflict.getOriginal()).forEach(model -> model.forEach(statement -> {
                assertEquals(sub, statement.getSubject());
                assertEquals(typeIRI, statement.getPredicate());
            }));
        });
    }

    @Test
    public void testGetConflictsSamePropertyAltered() throws Exception {
        // Setup:
        // Both altered same title
        IRI sub = vf.createIRI("http://test.com#sub");
        Resource leftId = vf.createIRI(COMMITS + "conflict1-2");
        Resource rightId = vf.createIRI(COMMITS + "conflict2-2");

        Model leftAdds = mf.createModel();
        Model leftDels = mf.createModel();
        leftAdds.add(sub, titleIRI, vf.createLiteral("Title Left"));
        leftDels.add(sub, titleIRI, vf.createLiteral("Title"));
        Difference leftDiff = new Difference.Builder()
                .additions(leftAdds)
                .deletions(leftDels)
                .build();

        Model rightAdds = mf.createModel();
        Model rightDels = mf.createModel();
        rightAdds.add(sub, titleIRI, vf.createLiteral("Title Right"));
        rightDels.add(sub, titleIRI, vf.createLiteral("Title"));
        Difference rightDiff = new Difference.Builder()
                .additions(rightAdds)
                .deletions(rightDels)
                .build();

        Model originalModel = mf.createModel();
        originalModel.add(sub, titleIRI, vf.createLiteral("Title"));
        setUpConflictTest(leftId, rightId, leftDiff, rightDiff, originalModel);

        doReturn(Collections.singletonList(leftDiff)).when(utilsService).getCommitChain(eq(leftId), eq(false), any(RepositoryConnection.class));
        doReturn(Collections.singletonList(rightDiff)).when(utilsService).getCommitChain(eq(rightId), eq(false), any(RepositoryConnection.class));

        Set<Conflict> result = manager.getConflicts(leftId, rightId);
        verify(utilsService).validateResource(eq(leftId), eq(commitFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).validateResource(eq(rightId), eq(commitFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).getCommitChain(eq(leftId), eq(true), any(RepositoryConnection.class));
        verify(utilsService).getCommitChain(eq(rightId), eq(true), any(RepositoryConnection.class));
        verify(utilsService, times(2)).getRevisionChanges(anyListOf(Resource.class), any(RepositoryConnection.class));
        verify(utilsService).getCompiledResource(eq(COMMIT_IRI), any(RepositoryConnection.class));
        assertEquals(1, result.size());
        result.forEach(conflict -> {
            assertEquals(1, conflict.getOriginal().size());
            Difference left = conflict.getLeftDifference();
            Difference right = conflict.getRightDifference();
            assertEquals(1, left.getAdditions().size());
            assertEquals(1, right.getAdditions().size());
            assertEquals(0, right.getDeletions().size());
            assertEquals(0, left.getDeletions().size());
            Stream.of(conflict.getOriginal(), left.getAdditions(), right.getAdditions()).forEach(model -> model.forEach(statement -> {
                assertEquals(sub, statement.getSubject());
                assertEquals(titleIRI, statement.getPredicate());
            }));
        });
    }

    @Test
    public void testGetConflictsChainAddsAndRemovesStatement() throws Exception {
        // Setup:
        // Second chain has two commits which adds then removes something
        IRI sub = vf.createIRI("http://test.com#sub");
        Resource leftId = vf.createIRI(COMMITS + "conflict1-3");
        Resource rightId = vf.createIRI(COMMITS + "conflict3-3");

        Difference leftDiff = new Difference.Builder()
                .additions(mf.createModel())
                .deletions(mf.createModel())
                .build();

        Model rightAdds = mf.createModel();
        rightAdds.add(sub, titleIRI, vf.createLiteral("Title Right"));
        Difference rightDiff = new Difference.Builder()
                .additions(rightAdds)
                .deletions(mf.createModel())
                .build();

        Model originalModel = mf.createModel();
        originalModel.add(sub, titleIRI, vf.createLiteral("Title"));
        setUpConflictTest(leftId, rightId, leftDiff, rightDiff, originalModel);

        Set<Conflict> result = manager.getConflicts(leftId, rightId);
        verify(utilsService).validateResource(eq(leftId), eq(commitFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).validateResource(eq(rightId), eq(commitFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).getCommitChain(eq(leftId), eq(true), any(RepositoryConnection.class));
        verify(utilsService).getCommitChain(eq(rightId), eq(true), any(RepositoryConnection.class));
        verify(utilsService, times(2)).getRevisionChanges(anyListOf(Resource.class), any(RepositoryConnection.class));
        verify(utilsService).getCompiledResource(eq(COMMIT_IRI), any(RepositoryConnection.class));
        assertEquals(0, result.size());
    }

    @Test
    public void testGetConflictsPropertyChangeOnSingleBranch() throws Exception {
        // Setup:
        // Change a property on one branch
        IRI sub = vf.createIRI("http://test.com#sub");
        Resource leftId = vf.createIRI(COMMITS + "conflict1-4");
        Resource rightId = vf.createIRI(COMMITS + "conflict2-4");

        Model leftAdds = mf.createModel();
        leftAdds.add(sub, descriptionIRI, vf.createLiteral("Description"));
        Difference leftDiff = new Difference.Builder()
                .additions(leftAdds)
                .deletions(mf.createModel())
                .build();

        Model rightAdds = mf.createModel();
        Model rightDels = mf.createModel();
        rightAdds.add(sub, titleIRI, vf.createLiteral("Title Right"));
        rightDels.add(sub, titleIRI, vf.createLiteral("Title"));
        Difference rightDiff = new Difference.Builder()
                .additions(rightAdds)
                .deletions(rightDels)
                .build();

        Model originalModel = mf.createModel();
        originalModel.add(sub, titleIRI, vf.createLiteral("Title"));
        setUpConflictTest(leftId, rightId, leftDiff, rightDiff, originalModel);

        Set<Conflict> result = manager.getConflicts(leftId, rightId);
        verify(utilsService).validateResource(eq(leftId), eq(commitFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).validateResource(eq(rightId), eq(commitFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).getCommitChain(eq(leftId), eq(true), any(RepositoryConnection.class));
        verify(utilsService).getCommitChain(eq(rightId), eq(true), any(RepositoryConnection.class));
        verify(utilsService, times(2)).getRevisionChanges(anyListOf(Resource.class), any(RepositoryConnection.class));
        verify(utilsService).getCompiledResource(eq(COMMIT_IRI), any(RepositoryConnection.class));
        assertEquals(0, result.size());
    }

    @Test
    public void testGetConflictsOneRemovesOtherAddsToProperty() throws Exception {
        // Setup:
        // One branch removes property while other adds another to it
        IRI sub = vf.createIRI("http://test.com#sub");
        Resource leftId = vf.createIRI(COMMITS + "conflict1-5");
        Resource rightId = vf.createIRI(COMMITS + "conflict2-5");

        Model leftAdds = mf.createModel();
        leftAdds.add(sub, titleIRI, vf.createLiteral("Title Left"));
        Difference leftDiff = new Difference.Builder()
                .additions(leftAdds)
                .deletions(mf.createModel())
                .build();

        Model rightAdds = mf.createModel();
        Model rightDels = mf.createModel();
        rightAdds.add(sub, titleIRI, vf.createLiteral("Title Right"));
        rightDels.add(sub, titleIRI, vf.createLiteral("Title"));
        Difference rightDiff = new Difference.Builder()
                .additions(rightAdds)
                .deletions(rightDels)
                .build();

        Model originalModel = mf.createModel();
        originalModel.add(sub, titleIRI, vf.createLiteral("Title"));
        setUpConflictTest(leftId, rightId, leftDiff, rightDiff, originalModel);

        Set<Conflict> result = manager.getConflicts(leftId, rightId);
        verify(utilsService).validateResource(eq(leftId), eq(commitFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).validateResource(eq(rightId), eq(commitFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).getCommitChain(eq(leftId), eq(true), any(RepositoryConnection.class));
        verify(utilsService).getCommitChain(eq(rightId), eq(true), any(RepositoryConnection.class));
        verify(utilsService, times(2)).getRevisionChanges(anyListOf(Resource.class), any(RepositoryConnection.class));
        verify(utilsService).getCompiledResource(eq(COMMIT_IRI), any(RepositoryConnection.class));
        assertEquals(1, result.size());
        result.forEach(conflict -> {
            assertEquals(1, conflict.getOriginal().size());
            Difference left = conflict.getLeftDifference();
            Difference right = conflict.getRightDifference();
            assertEquals(1, left.getAdditions().size());
            assertEquals(1, right.getAdditions().size());
            assertEquals(1, right.getDeletions().size());
            assertEquals(0, left.getDeletions().size());
            Stream.of(conflict.getOriginal(), left.getAdditions(), right.getDeletions())
                    .forEach(model -> model.forEach(statement -> {
                        assertEquals(sub, statement.getSubject());
                        assertEquals(titleIRI, statement.getPredicate());
                    }));
        });
    }

    @Test
    public void testGetConflictsWithOnlyOneCommit() throws Exception {
        // Setup:
        IRI sub = vf.createIRI("http://test.com#sub");
        Resource leftId = vf.createIRI(COMMITS + "conflict1-4");

        Model leftAdds = mf.createModel();
        leftAdds.add(sub, descriptionIRI, vf.createLiteral("Title Left"));
        Difference leftDiff = new Difference.Builder()
                .additions(leftAdds)
                .deletions(mf.createModel())
                .build();

        Model rightAdds = mf.createModel();
        rightAdds.add(sub, titleIRI, vf.createLiteral("Description"));
        Difference rightDiff = new Difference.Builder()
                .additions(rightAdds)
                .deletions(mf.createModel())
                .build();

        setUpConflictTest(leftId, COMMIT_IRI, leftDiff, rightDiff, leftAdds);
        doReturn(Stream.of(COMMIT_IRI).collect(Collectors.toList())).when(utilsService).getCommitChain(eq(COMMIT_IRI), eq(true), any(RepositoryConnection.class));

        Set<Conflict> result = manager.getConflicts(leftId, COMMIT_IRI);
        verify(utilsService, atLeastOnce()).validateResource(eq(leftId), eq(commitFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService, atLeastOnce()).validateResource(eq(COMMIT_IRI), eq(commitFactory.getTypeIRI()), any(RepositoryConnection.class));
        verify(utilsService).getCommitChain(eq(leftId), eq(true), any(RepositoryConnection.class));
        verify(utilsService).getCommitChain(eq(COMMIT_IRI), eq(true), any(RepositoryConnection.class));
        verify(utilsService, times(2)).getRevisionChanges(anyListOf(Resource.class), any(RepositoryConnection.class));
        verify(utilsService).getCompiledResource(eq(COMMIT_IRI), any(RepositoryConnection.class));
        assertEquals(0, result.size());
    }

    @Test
    public void testGetConflictsDisconnectedNodes() throws Exception {
        // Setup:
        IRI commitId = vf.createIRI(COMMITS + "new");
        doReturn(Collections.singletonList(COMMIT_IRI)).when(utilsService).getCommitChain(eq(COMMIT_IRI), eq(true), any(RepositoryConnection.class));
        doReturn(Collections.singletonList(commitId)).when(utilsService).getCommitChain(eq(commitId), eq(true), any(RepositoryConnection.class));
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("No common parent between Commit " + commitId + " and " + COMMIT_IRI);

        manager.getConflicts(commitId, COMMIT_IRI);
    }

    /* getDiff */

    @Test
    public void testGetDiff() throws Exception {
        try (RepositoryConnection conn = repo.getConnection()) {
            // Setup:
            Model original = RepositoryResults.asModel(conn.getStatements(null, null, null, vf.createIRI("http://matonto.org/test/diff1")), mf);
            Model changed = RepositoryResults.asModel(conn.getStatements(null, null, null, vf.createIRI("http://matonto.org/test/diff2")), mf);
            Model additions = mf.createModel();
            conn.getStatements(null, null, null, vf.createIRI("http://matonto.org/test/diff/additions"))
                    .forEach(additions::add);
            Model deletions = mf.createModel();
            conn.getStatements(null, null, null, vf.createIRI("http://matonto.org/test/diff/deletions"))
                    .forEach(deletions::add);

            Difference diff = manager.getDiff(original, changed);
            assertEquals(additions.size(), diff.getAdditions().size());
            diff.getAdditions().forEach(s -> assertTrue(additions.contains(s.getSubject(), s.getPredicate(),
                    s.getObject())));
            assertEquals(deletions.size(), diff.getDeletions().size());
            diff.getDeletions().forEach(s -> assertTrue(deletions.contains(s.getSubject(), s.getPredicate(),
                    s.getObject())));
        }
    }

    @Test
    public void testGetDiffOppositeOfPrevious() throws Exception {
        try (RepositoryConnection conn = repo.getConnection()) {
            // Setup:
            Model changed = RepositoryResults.asModel(conn.getStatements(null, null, null, vf.createIRI("http://matonto.org/test/diff1")), mf);
            Model original = RepositoryResults.asModel(conn.getStatements(null, null, null, vf.createIRI("http://matonto.org/test/diff2")), mf);
            Model deletions = mf.createModel();
            conn.getStatements(null, null, null, vf.createIRI("http://matonto.org/test/diff/additions"))
                    .forEach(deletions::add);
            Model additions = mf.createModel();
            conn.getStatements(null, null, null, vf.createIRI("http://matonto.org/test/diff/deletions"))
                    .forEach(additions::add);

            Difference diff = manager.getDiff(original, changed);
            assertEquals(deletions.size(), diff.getDeletions().size());
            diff.getDeletions().forEach(s -> assertTrue(deletions.contains(s.getSubject(), s.getPredicate(),
                    s.getObject())));
            assertEquals(additions.size(), diff.getAdditions().size());
            diff.getAdditions().forEach(s -> assertTrue(additions.contains(s.getSubject(), s.getPredicate(),
                    s.getObject())));
        }
    }

    @Test
    public void testGetDiffOfSameModel() throws Exception {
        try (RepositoryConnection conn = repo.getConnection()) {
            // Setup:
            Model original = RepositoryResults.asModel(conn.getStatements(null, null, null, vf.createIRI("http://matonto.org/test/diff2")), mf);

            Difference diff = manager.getDiff(original, original);
            assertEquals(0, diff.getAdditions().size());
            assertEquals(0, diff.getDeletions().size());
        }
    }

    private void setUpConflictTest(Resource leftId, Resource rightId, Difference leftDiff, Difference rightDiff, Model originalModel) {
        doReturn(Stream.of(leftId, COMMIT_IRI).collect(Collectors.toList())).when(utilsService).getCommitChain(eq(leftId), eq(true), any(RepositoryConnection.class));
        doReturn(Stream.of(rightId, COMMIT_IRI).collect(Collectors.toList())).when(utilsService).getCommitChain(eq(rightId), eq(true), any(RepositoryConnection.class));
        doAnswer(i -> {
            List<Resource> commits = i.getArgumentAt(0, List.class);
            if (commits.isEmpty() || commits.get(0).equals(rightId)) {
                return rightDiff;
            } else if (commits.get(0).equals(leftId)) {
                return leftDiff;
            } else {
                return mf.createModel();
            }
        }).when(utilsService).getRevisionChanges(anyListOf(Resource.class), any(RepositoryConnection.class));
        doReturn(originalModel).when(utilsService).getCompiledResource(eq(COMMIT_IRI), any(RepositoryConnection.class));
    }
}
