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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.mobi.catalog.api.Catalogs;
import com.mobi.catalog.api.ontologies.mcat.Catalog;
import com.mobi.catalog.api.ontologies.mcat.Commit;
import com.mobi.catalog.api.ontologies.mcat.InProgressCommit;
import com.mobi.catalog.api.ontologies.mcat.MasterBranch;
import com.mobi.catalog.api.ontologies.mcat.Revision;
import com.mobi.catalog.api.versioning.VersioningService;
import com.mobi.catalog.impl.SimpleBranchManager;
import com.mobi.catalog.impl.SimpleCommitManager;
import com.mobi.catalog.impl.SimpleRecordManager;
import com.mobi.catalog.impl.SimpleRevisionManager;
import com.mobi.catalog.impl.SimpleThingManager;
import com.mobi.jaas.api.ontologies.usermanagement.User;
import com.mobi.ontologies.provo.Agent;
import com.mobi.prolly.record.api.client.ProllyRepoKeys;
import com.mobi.prolly.record.api.ontologies.prollyrecord.ProllyCommit;
import com.mobi.prolly.record.api.ontologies.prollyrecord.ProllyVersionedRDFRecord;
import com.mobi.prolly.record.impl.client.InMemoryProllyVersioningClient;
import com.mobi.rdf.orm.OrmFactory;
import com.mobi.rdf.orm.Thing;
import com.mobi.rdf.orm.test.OrmEnabledTestCase;
import com.mobi.repository.impl.sesame.memory.MemoryRepositoryWrapper;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.vocabulary.OWL;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.query.QueryResults;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.EventAdmin;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class ProllyRecordVersioningServiceTest extends OrmEnabledTestCase {

    private AutoCloseable closeable;
    private ProllyRecordVersioningService service;
    private MemoryRepositoryWrapper repo;
    private InMemoryProllyVersioningClient prollyClient;
    private SimpleCommitManager commitManager;
    private SimpleThingManager thingManager;

    private final OrmFactory<MasterBranch> masterBranchFactory = getRequiredOrmFactory(MasterBranch.class);
    private final OrmFactory<Revision> revisionFactory = getRequiredOrmFactory(Revision.class);
    private final OrmFactory<InProgressCommit> inProgressCommitFactory =
            getRequiredOrmFactory(InProgressCommit.class);
    private final OrmFactory<Agent> agentFactory = getRequiredOrmFactory(Agent.class);
    private final OrmFactory<ProllyVersionedRDFRecord> recordFactory =
            getRequiredOrmFactory(ProllyVersionedRDFRecord.class);

    private User user;
    private ProllyVersionedRDFRecord record;
    private MasterBranch masterBranch;
    private IRI masterHeadGraph;
    private String repoKey;
    private int ipcCounter = 0;

    @Mock
    private EventAdmin eventAdmin;

    @Mock
    private BundleContext context;

    @Mock
    private ServiceReference<EventAdmin> serviceReference;

    @Before
    public void setUp() throws Exception {
        repo = new MemoryRepositoryWrapper();
        repo.setDelegate(new SailRepository(new MemoryStore()));

        OrmFactory<User> userFactory = getRequiredOrmFactory(User.class);
        OrmFactory<Catalog> catalogFactory = getRequiredOrmFactory(Catalog.class);

        Catalog catalog = catalogFactory.createNew(VALUE_FACTORY.createIRI("http://mobi.com/test/catalogs#catalog-test"));
        user = userFactory.createNew(VALUE_FACTORY.createIRI("http://test.com#user"));
        record = recordFactory.createNew(VALUE_FACTORY.createIRI("http://test.com#record"));
        repoKey = ProllyRepoKeys.forRecord(record.getResource());
        record.setProllyRepoKey(repoKey);
        masterHeadGraph = VALUE_FACTORY.createIRI(record.getResource().stringValue() + "/HEAD");
        masterBranch = masterBranchFactory.createNew(VALUE_FACTORY.createIRI("http://test.com#masterBranch"));
        masterBranch.setHeadGraph(masterHeadGraph);
        record.setMasterBranch(masterBranch);
        record.setBranch(Collections.singleton(masterBranch));
        record.setCatalog(catalog);

        closeable = MockitoAnnotations.openMocks(this);
        when(context.getServiceReference(EventAdmin.class)).thenReturn(serviceReference);
        when(context.getService(serviceReference)).thenReturn(eventAdmin);

        SimpleBranchManager branchManager = new SimpleBranchManager();
        commitManager = new SimpleCommitManager();
        thingManager = new SimpleThingManager();
        SimpleRevisionManager revisionManager = new SimpleRevisionManager();
        SimpleRecordManager recordManager = new SimpleRecordManager();
        injectOrmFactoryReferencesIntoService(branchManager);
        injectOrmFactoryReferencesIntoService(commitManager);
        injectOrmFactoryReferencesIntoService(thingManager);
        injectOrmFactoryReferencesIntoService(revisionManager);
        injectOrmFactoryReferencesIntoService(recordManager);
        setField(SimpleBranchManager.class, "thingManager", branchManager, thingManager);
        setField(SimpleBranchManager.class, "recordManager", branchManager, recordManager);
        setField(SimpleCommitManager.class, "thingManager", commitManager, thingManager);
        setField(SimpleCommitManager.class, "recordManager", commitManager, recordManager);
        setField(SimpleCommitManager.class, "revisionManager", commitManager, revisionManager);
        setField(SimpleCommitManager.class, "branchManager", commitManager, branchManager);
        setField(SimpleRevisionManager.class, "thingManager", revisionManager, thingManager);
        setField(SimpleRecordManager.class, "thingManager", recordManager, thingManager);

        prollyClient = new InMemoryProllyVersioningClient();
        prollyClient.createRepo(repoKey);

        service = new ProllyRecordVersioningService();
        injectOrmFactoryReferencesIntoService(service);
        service.commitManager = commitManager;
        service.thingManager = thingManager;
        service.branchManager = branchManager;
        service.revisionManager = revisionManager;
        service.prollyClient = prollyClient;
        service.start(context);

        try (RepositoryConnection conn = repo.getConnection()) {
            addThing(catalog, conn);
            addThing(record, conn);
            addThing(masterBranch, conn);
        }
    }

    @After
    public void tearDown() throws Exception {
        closeable.close();
        repo.shutDown();
    }

    @Test
    public void typeIRIIsProllyRecord() {
        assertEquals(ProllyVersionedRDFRecord.TYPE, service.getTypeIRI());
        assertTrue(service instanceof VersioningService);
    }

    @Test
    public void initialMasterCommitMirrorsHeadGraphState() {
        try (RepositoryConnection conn = repo.getConnection()) {
            // initial state pre-placed in the HEAD graph, empty InProgressCommit — the real
            // createRecord shape
            conn.add(VALUE_FACTORY.createIRI("urn:test#a"), RDF.TYPE, OWL.CLASS, masterHeadGraph);
            stageInProgressCommit(conn, null, null);

            Resource commitId = service.addMasterCommit(record, masterBranch, user, "initial", conn);

            String hash = hashOf(commitId, conn);
            assertTrue(conn.hasStatement(commitId, RDF.TYPE,
                    VALUE_FACTORY.createIRI(ProllyCommit.TYPE), false));
            Model snapshot = prollyClient.snapshot(repoKey, hash);
            assertEquals(1, snapshot.size());
            assertEquals(Optional.of(hash),
                    prollyClient.getBranchHead(repoKey, ProllyRepoKeys.forBranch(masterBranch.getResource())));
        }
    }

    @Test
    public void secondMasterCommitMirrorsDeltaWithParent() {
        try (RepositoryConnection conn = repo.getConnection()) {
            conn.add(VALUE_FACTORY.createIRI("urn:test#a"), RDF.TYPE, OWL.CLASS, masterHeadGraph);
            stageInProgressCommit(conn, null, null);
            Resource initial = service.addMasterCommit(record, masterBranch, user, "initial", conn);
            String initialHash = hashOf(initial, conn);

            refreshMasterBranch(conn);
            Model adds = MODEL_FACTORY.createEmptyModel();
            adds.add(VALUE_FACTORY.createIRI("urn:test#b"), RDFS.LABEL, VALUE_FACTORY.createLiteral("b"));
            stageInProgressCommit(conn, adds, null);
            Resource second = service.addMasterCommit(record, masterBranch, user, "second", conn);
            String secondHash = hashOf(second, conn);

            Model snapshot = prollyClient.snapshot(repoKey, secondHash);
            assertEquals(2, snapshot.size());
            // parent chain: the initial commit is an ancestor of the second
            assertEquals(Optional.of(initialHash),
                    prollyClient.getMergeBase(repoKey, secondHash, initialHash));
            assertEquals(Optional.of(secondHash),
                    prollyClient.getBranchHead(repoKey, ProllyRepoKeys.forBranch(masterBranch.getResource())));
        }
    }

    @Test
    public void missingRepoKeySkipsMirrorButCommits() {
        try (RepositoryConnection conn = repo.getConnection()) {
            conn.remove(record.getResource(),
                    VALUE_FACTORY.createIRI(ProllyVersionedRDFRecord.prollyRepoKey_IRI), null,
                    record.getResource());
            conn.add(VALUE_FACTORY.createIRI("urn:test#a"), RDF.TYPE, OWL.CLASS, masterHeadGraph);
            stageInProgressCommit(conn, null, null);

            Resource commitId = service.addMasterCommit(record, masterBranch, user, "initial", conn);

            assertTrue(conn.hasStatement(commitId, RDF.TYPE,
                    VALUE_FACTORY.createIRI(Commit.TYPE), false));
            org.junit.Assert.assertFalse(conn.hasStatement(commitId,
                    VALUE_FACTORY.createIRI(ProllyCommit.prollyCommitHash_IRI), null, false));
        }
    }

    @Test
    public void branchCommitAndMergeIntoMasterMirror() throws Exception {
        OrmFactory<com.mobi.catalog.api.ontologies.mcat.Branch> branchFactory =
                getRequiredOrmFactory(com.mobi.catalog.api.ontologies.mcat.Branch.class);
        try (RepositoryConnection conn = repo.getConnection()) {
            // initial commit on master
            conn.add(VALUE_FACTORY.createIRI("urn:test#a"), RDF.TYPE, OWL.CLASS, masterHeadGraph);
            stageInProgressCommit(conn, null, null);
            Resource initial = service.addMasterCommit(record, masterBranch, user, "initial", conn);
            String initialHash = hashOf(initial, conn);

            // fork branch1 at the initial commit and commit a delta on it
            Commit initialCommit = commitManager.getCommit(initial, conn).orElseThrow();
            com.mobi.catalog.api.ontologies.mcat.Branch branch1 =
                    branchFactory.createNew(VALUE_FACTORY.createIRI("http://test.com#branch1"));
            branch1.setHead(initialCommit);
            record.setBranch(Set.of(masterBranch, branch1));
            conn.add(record.getResource(),
                    VALUE_FACTORY.createIRI("http://mobi.com/ontologies/catalog#branch"),
                    branch1.getResource(), record.getResource());
            addThing(branch1, conn);

            Model branchAdds = MODEL_FACTORY.createEmptyModel();
            branchAdds.add(VALUE_FACTORY.createIRI("urn:test#b"), RDFS.LABEL,
                    VALUE_FACTORY.createLiteral("from branch"));
            stageInProgressCommit(conn, branchAdds, null);
            Resource branchCommit = service.addBranchCommit(record, branch1, user, "branch work", conn);
            String branchHash = hashOf(branchCommit, conn);
            assertEquals(Optional.of(branchHash),
                    prollyClient.getBranchHead(repoKey, ProllyRepoKeys.forBranch(branch1.getResource())));

            // merge branch1 into master
            refreshMasterBranch(conn);
            Model branch1Model = QueryResults.asModel(
                    conn.getStatements(null, null, null, branch1.getResource()));
            com.mobi.catalog.api.ontologies.mcat.Branch branch1Fresh =
                    branchFactory.getExisting(branch1.getResource(), branch1Model).orElseThrow();
            Resource mergeCommit = service.mergeIntoMaster(record, branch1Fresh, masterBranch, user,
                    MODEL_FACTORY.createEmptyModel(), MODEL_FACTORY.createEmptyModel(),
                    new java.util.HashMap<>(), conn);
            String mergeHash = hashOf(mergeCommit, conn);

            // two-parent commit: both heads are ancestors
            assertEquals(Optional.of(initialHash),
                    prollyClient.getMergeBase(repoKey, mergeHash, initialHash));
            assertEquals(Optional.of(branchHash),
                    prollyClient.getMergeBase(repoKey, mergeHash, branchHash));
            // merged snapshot equals the post-merge HEAD graph state
            Model headState = QueryResults.asModel(conn.getStatements(null, null, null, masterHeadGraph));
            Model snapshot = prollyClient.snapshot(repoKey, mergeHash);
            assertEquals(headState.size(), snapshot.size());
            // master branch pointer moved, lease released
            assertEquals(Optional.of(mergeHash),
                    prollyClient.getBranchHead(repoKey, ProllyRepoKeys.forBranch(masterBranch.getResource())));
            assertTrue(prollyClient.activeLeases(repoKey).isEmpty());
        }
    }

    @Test
    public void snapshotResolverMatchesReplay() throws Exception {
        // two commits, then read the FIRST (behind head) both ways
        Resource first;
        try (RepositoryConnection conn = repo.getConnection()) {
            conn.add(VALUE_FACTORY.createIRI("urn:test#a"), RDF.TYPE, OWL.CLASS, masterHeadGraph);
            stageInProgressCommit(conn, null, null);
            first = service.addMasterCommit(record, masterBranch, user, "initial", conn);
            refreshMasterBranch(conn);
            Model adds = MODEL_FACTORY.createEmptyModel();
            adds.add(VALUE_FACTORY.createIRI("urn:test#b"), RDFS.LABEL, VALUE_FACTORY.createLiteral("b"));
            stageInProgressCommit(conn, adds, null);
            service.addMasterCommit(record, masterBranch, user, "second", conn);
        }

        com.mobi.catalog.impl.SimpleCompiledResourceManager replayManager = compiledResourceManager(false);
        com.mobi.catalog.impl.SimpleCompiledResourceManager routedManager = compiledResourceManager(true);
        try (RepositoryConnection conn = repo.getConnection()) {
            Model viaReplay = replayManager.getCompiledResource(first, conn);
            Model viaSnapshot = routedManager.getCompiledResource(first, conn);
            assertEquals(1, viaSnapshot.size());
            assertTrue(org.eclipse.rdf4j.model.util.Models.isomorphic(viaReplay, viaSnapshot));
        }
    }

    /** A real SimpleCompiledResourceManager over this test's repo, with or without the resolver. */
    private com.mobi.catalog.impl.SimpleCompiledResourceManager compiledResourceManager(boolean routed)
            throws Exception {
        com.mobi.catalog.config.CatalogConfigProvider configProvider =
                org.mockito.Mockito.mock(com.mobi.catalog.config.CatalogConfigProvider.class);
        when(configProvider.getRepository()).thenReturn(repo);
        com.mobi.catalog.impl.SimpleCompiledResourceManager manager =
                new com.mobi.catalog.impl.SimpleCompiledResourceManager();
        injectOrmFactoryReferencesIntoService(manager);
        setField(com.mobi.catalog.impl.SimpleCompiledResourceManager.class, "configProvider",
                manager, configProvider);
        setField(com.mobi.catalog.impl.SimpleCompiledResourceManager.class, "commitManager",
                manager, commitManager);
        setField(com.mobi.catalog.impl.SimpleCompiledResourceManager.class, "revisionManager",
                manager, fieldOf(SimpleCommitManager.class, "revisionManager", commitManager));
        setField(com.mobi.catalog.impl.SimpleCompiledResourceManager.class, "thingManager",
                manager, thingManager);
        if (routed) {
            com.mobi.prolly.record.impl.client.ProllySnapshotResolver resolver =
                    new com.mobi.prolly.record.impl.client.ProllySnapshotResolver();
            setField(com.mobi.prolly.record.impl.client.ProllySnapshotResolver.class, "prollyClient",
                    resolver, prollyClient);
            setField(com.mobi.catalog.impl.SimpleCompiledResourceManager.class, "snapshotResolvers",
                    manager, java.util.List.of(resolver));
        }
        return manager;
    }

    private static Object fieldOf(Class<?> clazz, String name, Object target) throws Exception {
        Field f = clazz.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    /** Creates + persists a fresh InProgressCommit for the user, with optional add/del models. */
    private void stageInProgressCommit(RepositoryConnection conn, Model adds, Model dels) {
        ipcCounter++;
        UUID uuid = UUID.randomUUID();
        IRI addGraph = VALUE_FACTORY.createIRI(Catalogs.DELTAS_NAMESPACE + uuid + "-A");
        IRI delGraph = VALUE_FACTORY.createIRI(Catalogs.DELTAS_NAMESPACE + uuid + "-B");
        Revision revision = revisionFactory.createNew(
                VALUE_FACTORY.createIRI("http://test.com#revision-ipc-" + ipcCounter));
        revision.setAdditions(addGraph);
        revision.setDeletions(delGraph);
        InProgressCommit ipc = inProgressCommitFactory.createNew(
                VALUE_FACTORY.createIRI("http://test.com#ipc-" + ipcCounter));
        ipc.setWasAssociatedWith(Set.of(agentFactory.createNew(user.getResource())));
        ipc.setOnVersionedRDFRecord(record);
        ipc.setGenerated(Set.of(revision));
        ipc.getModel().addAll(revision.getModel());
        if (adds != null) {
            conn.add(adds, addGraph);
        }
        if (dels != null) {
            conn.add(dels, delGraph);
        }
        addThing(ipc, conn);
    }

    /** Reloads the master branch state (head pointer) from the store into the shared object. */
    private void refreshMasterBranch(RepositoryConnection conn) {
        Model model = QueryResults.asModel(
                conn.getStatements(null, null, null, masterBranch.getResource()));
        masterBranch = masterBranchFactory.getExisting(masterBranch.getResource(), model)
                .orElseThrow(() -> new IllegalStateException("master branch missing"));
    }

    private String hashOf(Resource commitId, RepositoryConnection conn) {
        return QueryResults.asModel(conn.getStatements(commitId,
                        VALUE_FACTORY.createIRI(ProllyCommit.prollyCommitHash_IRI), null))
                .objects().stream().findFirst()
                .orElseThrow(() -> new AssertionError("commit " + commitId + " has no prolly hash"))
                .stringValue();
    }

    private void addThing(Thing thing, RepositoryConnection conn) {
        conn.add(thing.getModel(), thing.getResource());
    }

    private static void setField(Class<?> clazz, String name, Object target, Object value) throws Exception {
        Field f = clazz.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
