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

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mobi.catalog.api.BranchManager;
import com.mobi.catalog.api.CatalogProvUtils;
import com.mobi.catalog.api.CommitManager;
import com.mobi.catalog.api.DifferenceManager;
import com.mobi.catalog.api.RevisionManager;
import com.mobi.catalog.api.ThingManager;
import com.mobi.catalog.api.VersionManager;
import com.mobi.catalog.api.mergerequest.MergeRequestManager;
import com.mobi.catalog.api.ontologies.mcat.Catalog;
import com.mobi.catalog.api.ontologies.mcat.Commit;
import com.mobi.catalog.api.ontologies.mcat.Revision;
import com.mobi.catalog.api.record.RecordService;
import com.mobi.catalog.api.record.config.OperationConfig;
import com.mobi.catalog.api.record.config.RecordCreateSettings;
import com.mobi.catalog.api.record.config.RecordOperationConfig;
import com.mobi.catalog.api.record.config.VersionedRDFRecordCreateSettings;
import com.mobi.catalog.api.versioning.VersioningManager;
import com.mobi.catalog.config.CatalogConfigProvider;
import com.mobi.jaas.api.engines.EngineManager;
import com.mobi.jaas.api.ontologies.usermanagement.User;
import com.mobi.ontologies.dcterms._Thing;
import com.mobi.prolly.record.api.client.ProllyRepoKeys;
import com.mobi.prolly.record.api.client.ProllyVersioningClient;
import com.mobi.prolly.record.api.ontologies.prollyrecord.ProllyVersionedRDFRecord;
import com.mobi.prolly.record.impl.client.InMemoryProllyVersioningClient;
import com.mobi.prov.api.ontologies.mobiprov.CreateActivity;
import com.mobi.rdf.orm.OrmFactory;
import com.mobi.rdf.orm.test.OrmEnabledTestCase;
import com.mobi.repository.impl.sesame.memory.MemoryRepositoryWrapper;
import com.mobi.security.policy.api.xacml.XACMLPolicy;
import com.mobi.security.policy.api.xacml.XACMLPolicyManager;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public class SimpleProllyVersionedRDFRecordServiceTest extends OrmEnabledTestCase {

    private final IRI catalogId = VALUE_FACTORY.createIRI("http://mobi.com/test/catalogs#catalog-test");
    private final IRI commitIRI = VALUE_FACTORY.createIRI("http://mobi.com/test/commits#commit");
    private final IRI recordPolicyIRI =
            VALUE_FACTORY.createIRI("http://mobi.com/policies/record/encoded-record-policy");

    private AutoCloseable closeable;
    private SimpleProllyVersionedRDFRecordService recordService;
    private User user;
    private MemoryRepositoryWrapper repository;
    private InMemoryProllyVersioningClient prollyClient;

    private final OrmFactory<Catalog> catalogFactory = getRequiredOrmFactory(Catalog.class);
    private final OrmFactory<User> userFactory = getRequiredOrmFactory(User.class);
    private final OrmFactory<Commit> commitFactory = getRequiredOrmFactory(Commit.class);

    @Mock
    private ThingManager thingManager;

    @Mock
    private BranchManager branchManager;

    @Mock
    private CommitManager commitManager;

    @Mock
    private VersionManager versionManager;

    @Mock
    private DifferenceManager differenceManager;

    @Mock
    private CatalogProvUtils provUtils;

    @Mock
    private MergeRequestManager mergeRequestManager;

    @Mock
    private VersioningManager versioningManager;

    @Mock
    private RevisionManager revisionManager;

    @Mock
    private XACMLPolicyManager xacmlPolicyManager;

    @Mock
    private CatalogConfigProvider configProvider;

    @Mock
    private EngineManager engineManager;

    @Mock
    private CreateActivity createActivity;

    @Before
    public void setUp() throws Exception {
        System.setProperty("karaf.etc",
                SimpleProllyVersionedRDFRecordServiceTest.class.getResource("/").getPath());
        repository = new MemoryRepositoryWrapper();
        repository.setDelegate(new SailRepository(new MemoryStore()));

        recordService = new SimpleProllyVersionedRDFRecordService();
        user = userFactory.createNew(VALUE_FACTORY.createIRI("http://test.org/user"));

        closeable = MockitoAnnotations.openMocks(this);
        when(provUtils.startCreateActivity(any())).thenReturn(createActivity);
        when(xacmlPolicyManager.addPolicy(any(XACMLPolicy.class))).thenReturn(recordPolicyIRI);
        when(configProvider.getRepository()).thenReturn(repository);
        when(configProvider.getLocalCatalogIRI()).thenReturn(catalogId);

        Revision revision = mock(Revision.class);
        when(revision.getAdditions()).thenReturn(Optional.of(VALUE_FACTORY.createIRI("http://revision/add")));
        when(revision.getDeletions()).thenReturn(Optional.of(VALUE_FACTORY.createIRI("http://revision/del")));
        when(revision.getModel()).thenReturn(MODEL_FACTORY.createEmptyModel());
        when(revisionManager.createRevision(any())).thenReturn(revision);

        Commit initialCommit = commitFactory.createNew(commitIRI);
        IRI initialCommitIri = VALUE_FACTORY.createIRI("http://mobi.com/commit#initial");
        when(versioningManager.commit(eq(catalogId), any(Resource.class), any(Resource.class), eq(user),
                anyString(), any(RepositoryConnection.class))).thenReturn(initialCommitIri);
        when(commitManager.getCommit(eq(initialCommitIri), any(RepositoryConnection.class)))
                .thenReturn(Optional.of(initialCommit));

        injectOrmFactoryReferencesIntoService(recordService);
        recordService.provUtils = provUtils;
        recordService.versioningManager = versioningManager;
        recordService.mergeRequestManager = mergeRequestManager;
        recordService.xacmlPolicyManager = xacmlPolicyManager;
        recordService.engineManager = engineManager;
        recordService.configProvider = configProvider;
        recordService.recordFactory = recordService.prollyVersionedRDFRecordFactory;
        recordService.thingManager = thingManager;
        recordService.branchManager = branchManager;
        recordService.commitManager = commitManager;
        recordService.differenceManager = differenceManager;
        recordService.versionManager = versionManager;
        recordService.revisionManager = revisionManager;
        prollyClient = new InMemoryProllyVersioningClient();
        recordService.prollyClient = prollyClient;
    }

    @After
    public void reset() throws Exception {
        try (RepositoryConnection connection = repository.getConnection()) {
            connection.clear();
        }
        closeable.close();
    }

    @Test
    public void getTypeAndTypeIRITest() {
        assertEquals(ProllyVersionedRDFRecord.class, recordService.getType());
        assertEquals(ProllyVersionedRDFRecord.TYPE, recordService.getTypeIRI());
        assertTrue(recordService instanceof RecordService);
    }

    @Test
    public void createTest() throws Exception {
        RecordOperationConfig config = new OperationConfig();
        config.set(RecordCreateSettings.CATALOG_ID, catalogId.stringValue());
        config.set(VersionedRDFRecordCreateSettings.INPUT_STREAM,
                getClass().getResourceAsStream("/test-data.ttl"));
        config.set(VersionedRDFRecordCreateSettings.FILE_NAME, "test-data.ttl");
        config.set(RecordCreateSettings.RECORD_TITLE, "Prolly Test Record");
        config.set(RecordCreateSettings.RECORD_DESCRIPTION, "A prolly-backed record");
        config.set(RecordCreateSettings.RECORD_KEYWORDS, Set.of("prolly"));
        config.set(RecordCreateSettings.RECORD_PUBLISHERS, Collections.singleton(user));

        ProllyVersionedRDFRecord record;
        try (RepositoryConnection connection = repository.getConnection()) {
            record = recordService.create(user, config, connection);
        }

        Optional<Value> optTitle = record.getProperty(VALUE_FACTORY.createIRI(_Thing.title_IRI));
        assertTrue(optTitle.isPresent());
        assertEquals("Prolly Test Record", optTitle.get().stringValue());
        Optional<Resource> optCatalogId = record.getCatalog_resource();
        assertTrue(optCatalogId.isPresent());
        assertEquals(catalogId.stringValue(), optCatalogId.get().stringValue());
        assertEquals(1, record.getBranch_resource().size());
        assertTrue(record.getMasterBranch_resource().isPresent());

        verify(thingManager, times(2)).addObject(any(), any(RepositoryConnection.class));
        verify(provUtils).startCreateActivity(eq(user));
        verify(provUtils).endCreateActivity(any(CreateActivity.class), any(IRI.class));

        // The repo lifecycle: key derived from the record IRI, stored on the record, repo created
        String repoKey = record.getProllyRepoKey()
                .orElseThrow(() -> new AssertionError("record must carry its prolly repo key"));
        assertEquals(ProllyRepoKeys.forRecord(record.getResource()), repoKey);
        assertTrue(prollyClient.repoExists(repoKey));
    }

    @Test
    public void createRepoFailureFailsCreate() {
        ProllyVersioningClient failingClient = org.mockito.Mockito.mock(ProllyVersioningClient.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("prolly unavailable"))
                .when(failingClient).createRepo(org.mockito.ArgumentMatchers.anyString());
        recordService.prollyClient = failingClient;

        RecordOperationConfig config = new OperationConfig();
        config.set(RecordCreateSettings.CATALOG_ID, catalogId.stringValue());
        config.set(VersionedRDFRecordCreateSettings.INPUT_STREAM,
                getClass().getResourceAsStream("/test-data.ttl"));
        config.set(VersionedRDFRecordCreateSettings.FILE_NAME, "test-data.ttl");
        config.set(RecordCreateSettings.RECORD_TITLE, "Doomed Record");
        config.set(RecordCreateSettings.RECORD_PUBLISHERS, Collections.singleton(user));

        try (RepositoryConnection connection = repository.getConnection()) {
            recordService.create(user, config, connection);
            org.junit.Assert.fail("create must fail when the prolly repo cannot be created");
        } catch (Exception expected) {
            // no repo -> no record
        }
        org.mockito.Mockito.verify(versioningManager, org.mockito.Mockito.never()).commit(any(Resource.class),
                any(Resource.class), any(Resource.class), any(User.class), anyString(),
                any(RepositoryConnection.class));
    }

    @Test
    public void createCatalogFailureCleansUpRepo() {
        org.mockito.Mockito.doThrow(new IllegalStateException("catalog write failed")).when(versioningManager)
                .commit(any(Resource.class), any(Resource.class), any(Resource.class), any(User.class),
                        anyString(), any(RepositoryConnection.class));

        RecordOperationConfig config = new OperationConfig();
        config.set(RecordCreateSettings.CATALOG_ID, catalogId.stringValue());
        config.set(VersionedRDFRecordCreateSettings.INPUT_STREAM,
                getClass().getResourceAsStream("/test-data.ttl"));
        config.set(VersionedRDFRecordCreateSettings.FILE_NAME, "test-data.ttl");
        config.set(RecordCreateSettings.RECORD_TITLE, "Doomed Record");
        config.set(RecordCreateSettings.RECORD_PUBLISHERS, Collections.singleton(user));

        try (RepositoryConnection connection = repository.getConnection()) {
            recordService.create(user, config, connection);
            org.junit.Assert.fail("create must fail when the catalog half fails");
        } catch (Exception expected) {
            // compensated below
        }
        // The compensation deleted the repo created before the catalog failure: no repos remain
        org.junit.Assert.assertTrue(prollyClient.repoKeys().isEmpty());
    }

    @Test
    public void deleteRemovesProllyRepo() {
        ProllyVersionedRDFRecord testRecord = deletableRecord();
        String repoKey = testRecord.getProllyRepoKey().orElseThrow();
        prollyClient.createRepo(repoKey);
        when(thingManager.optObject(any(IRI.class), any(OrmFactory.class), any(RepositoryConnection.class)))
                .thenReturn(Optional.of(testRecord));
        when(provUtils.startDeleteActivity(any(User.class), any(IRI.class)))
                .thenReturn(getRequiredOrmFactory(com.mobi.prov.api.ontologies.mobiprov.DeleteActivity.class)
                        .createNew(VALUE_FACTORY.createIRI("urn:deleteActivity")));

        try (RepositoryConnection connection = repository.getConnection()) {
            recordService.delete(testRecord.getResource(), user, connection);
        }
        org.junit.Assert.assertFalse(prollyClient.repoExists(repoKey));
    }

    @Test
    public void deleteRepoFailureStillDeletesRecord() {
        ProllyVersionedRDFRecord testRecord = deletableRecord();
        // repo intentionally NOT created in the fake: deleteRepo is a no-op there, so simulate a
        // failing client instead
        ProllyVersioningClient failingClient = org.mockito.Mockito.mock(ProllyVersioningClient.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("prolly unavailable"))
                .when(failingClient).deleteRepo(org.mockito.ArgumentMatchers.anyString());
        recordService.prollyClient = failingClient;
        when(thingManager.optObject(any(IRI.class), any(OrmFactory.class), any(RepositoryConnection.class)))
                .thenReturn(Optional.of(testRecord));
        when(provUtils.startDeleteActivity(any(User.class), any(IRI.class)))
                .thenReturn(getRequiredOrmFactory(com.mobi.prov.api.ontologies.mobiprov.DeleteActivity.class)
                        .createNew(VALUE_FACTORY.createIRI("urn:deleteActivity")));

        try (RepositoryConnection connection = repository.getConnection()) {
            ProllyVersionedRDFRecord deleted =
                    recordService.delete(testRecord.getResource(), user, connection);
            assertEquals(testRecord.getResource(), deleted.getResource());
        }
        org.mockito.Mockito.verify(failingClient).deleteRepo(org.mockito.ArgumentMatchers.anyString());
    }

    private ProllyVersionedRDFRecord deletableRecord() {
        OrmFactory<ProllyVersionedRDFRecord> factory = getRequiredOrmFactory(ProllyVersionedRDFRecord.class);
        ProllyVersionedRDFRecord testRecord =
                factory.createNew(VALUE_FACTORY.createIRI("urn:test#deletable"));
        testRecord.setProperty(VALUE_FACTORY.createLiteral("Deletable"),
                VALUE_FACTORY.createIRI(_Thing.title_IRI));
        testRecord.setCatalog(catalogFactory.createNew(catalogId));
        testRecord.setProllyRepoKey(ProllyRepoKeys.forRecord(testRecord.getResource()));
        return testRecord;
    }

}
