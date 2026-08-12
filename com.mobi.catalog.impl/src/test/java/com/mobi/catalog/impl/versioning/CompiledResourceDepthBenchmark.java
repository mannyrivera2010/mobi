package com.mobi.catalog.impl.versioning;

/*-
 * #%L
 * com.mobi.catalog.impl
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
import static org.mockito.Mockito.when;

import com.mobi.catalog.api.Catalogs;
import com.mobi.catalog.api.ontologies.mcat.Catalog;
import com.mobi.catalog.api.ontologies.mcat.Commit;
import com.mobi.catalog.api.ontologies.mcat.InProgressCommit;
import com.mobi.catalog.api.ontologies.mcat.MasterBranch;
import com.mobi.catalog.api.ontologies.mcat.Revision;
import com.mobi.catalog.api.ontologies.mcat.VersionedRDFRecord;
import com.mobi.catalog.config.CatalogConfigProvider;
import com.mobi.catalog.impl.ManagerTestConstants;
import com.mobi.catalog.impl.SimpleBranchManager;
import com.mobi.catalog.impl.SimpleCommitManager;
import com.mobi.catalog.impl.SimpleCompiledResourceManager;
import com.mobi.catalog.impl.SimpleRecordManager;
import com.mobi.catalog.impl.SimpleRevisionManager;
import com.mobi.catalog.impl.SimpleThingManager;
import com.mobi.jaas.api.ontologies.usermanagement.User;
import com.mobi.ontologies.provo.Agent;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Plan `plans/out-of-process-prolly-service.md` Phase 0 Step 0: measure whether
 * {@code getCompiledResource} cost grows with commit-chain depth. Named without a
 * "Test" suffix so surefire does not auto-run it; run explicitly with
 * {@code mvn -pl com.mobi.catalog.impl test -Dtest=CompiledResourceDepthBenchmark}.
 * Chain depth is configurable via {@code -Dbench.depth} (default 1000), reps per
 * cell via {@code -Dbench.reps} (default 5), and the backing store via
 * {@code -Dbench.store=memory|native} (default memory; native uses an on-disk
 * RDF4J NativeStore under target/, the store production Mobi runs).
 */
public class CompiledResourceDepthBenchmark extends OrmEnabledTestCase {

    private static final int DEPTH = Integer.getInteger("bench.depth", 1000);
    private static final int REPS = Integer.getInteger("bench.reps", 5);
    private static final String STORE = System.getProperty("bench.store", "memory");

    private AutoCloseable closeable;
    private MemoryRepositoryWrapper repo;
    private SimpleVersioningService service;
    private SimpleCompiledResourceManager compiledResourceManager;
    private SimpleThingManager thingManager;
    private SimpleCommitManager commitManager;

    private final OrmFactory<MasterBranch> masterBranchFactory = getRequiredOrmFactory(MasterBranch.class);
    private final OrmFactory<Revision> revisionFactory = getRequiredOrmFactory(Revision.class);
    private final OrmFactory<InProgressCommit> inProgressCommitFactory = getRequiredOrmFactory(InProgressCommit.class);
    private final OrmFactory<Agent> agentFactory = getRequiredOrmFactory(Agent.class);

    private User user;
    private VersionedRDFRecord record;
    private MasterBranch masterBranch;
    private IRI masterHeadGraph;

    @Mock
    private EventAdmin eventAdmin;

    @Mock
    private BundleContext context;

    @Mock
    private ServiceReference<EventAdmin> serviceReference;

    @Mock
    private CatalogConfigProvider configProvider;

    private java.io.File dataDir;

    @Before
    public void setUp() throws Exception {
        repo = new MemoryRepositoryWrapper();
        if ("native".equals(STORE)) {
            dataDir = java.nio.file.Files.createTempDirectory(
                    java.nio.file.Paths.get("target"), "bench-native-").toFile();
            repo.setDelegate(new SailRepository(
                    new org.eclipse.rdf4j.sail.nativerdf.NativeStore(dataDir)));
        } else {
            repo.setDelegate(new SailRepository(new MemoryStore()));
        }

        OrmFactory<VersionedRDFRecord> versionedRDFRecordFactory = getRequiredOrmFactory(VersionedRDFRecord.class);
        OrmFactory<User> userFactory = getRequiredOrmFactory(User.class);
        OrmFactory<Catalog> catalogFactory = getRequiredOrmFactory(Catalog.class);

        Catalog catalog = catalogFactory.createNew(ManagerTestConstants.CATALOG_IRI);
        user = userFactory.createNew(VALUE_FACTORY.createIRI("http://test.com#user"));
        record = versionedRDFRecordFactory.createNew(VALUE_FACTORY.createIRI("http://test.com#record"));
        masterHeadGraph = VALUE_FACTORY.createIRI(record.getResource().stringValue() + "/HEAD");
        masterBranch = masterBranchFactory.createNew(VALUE_FACTORY.createIRI("http://test.com#masterBranch"));
        masterBranch.setHeadGraph(masterHeadGraph);
        record.setMasterBranch(masterBranch);
        record.setBranch(Collections.singleton(masterBranch));
        record.setCatalog(catalog);

        closeable = MockitoAnnotations.openMocks(this);
        when(context.getServiceReference(EventAdmin.class)).thenReturn(serviceReference);
        when(context.getService(serviceReference)).thenReturn(eventAdmin);
        when(configProvider.getRepository()).thenReturn(repo);
        when(configProvider.getLocalCatalogIRI()).thenReturn(ManagerTestConstants.CATALOG_IRI);

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

        service = new SimpleVersioningService();
        injectOrmFactoryReferencesIntoService(service);
        service.commitManager = commitManager;
        service.thingManager = thingManager;
        service.branchManager = branchManager;
        service.revisionManager = revisionManager;
        service.start(context);

        compiledResourceManager = new SimpleCompiledResourceManager();
        injectOrmFactoryReferencesIntoService(compiledResourceManager);
        setField(SimpleCompiledResourceManager.class, "configProvider", compiledResourceManager, configProvider);
        setField(SimpleCompiledResourceManager.class, "commitManager", compiledResourceManager, commitManager);
        setField(SimpleCompiledResourceManager.class, "revisionManager", compiledResourceManager, revisionManager);
        setField(SimpleCompiledResourceManager.class, "thingManager", compiledResourceManager, thingManager);

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
        if (dataDir != null) {
            try (var paths = java.nio.file.Files.walk(dataDir.toPath())) {
                paths.sorted(java.util.Comparator.reverseOrder())
                        .map(java.nio.file.Path::toFile)
                        .forEach(java.io.File::delete);
            }
        }
    }

    @Test
    public void measureDepthCurve() throws Exception {
        List<Resource> commits = new ArrayList<>();
        List<int[]> headCheckpoints = new ArrayList<>();   // {depth, bestMs, modelSize}
        long buildStart = System.nanoTime();

        try (RepositoryConnection conn = repo.getConnection()) {
            for (int i = 1; i <= DEPTH; i++) {
                commits.add(commitOnMaster(conn, i));
                if (i == 10 || i == 100 || i == DEPTH) {
                    long[] r = timeRead(commits.get(commits.size() - 1), conn);
                    headCheckpoints.add(new int[]{i, (int) r[0], (int) r[1]});
                }
            }
            long buildSeconds = (System.nanoTime() - buildStart) / 1_000_000_000;

            System.out.println("## getCompiledResource depth curve (Phase 0 Step 0)");
            System.out.println("store: " + STORE + "; chain build: " + DEPTH
                    + " master commits via addMasterCommit in " + buildSeconds
                    + "s; 2 added triples per commit; min of " + REPS + " reps per cell");
            System.out.println();
            System.out.println("| chain length | read at HEAD (ms) | model size |");
            System.out.println("|---|---|---|");
            for (int[] row : headCheckpoints) {
                System.out.println("| " + row[0] + " | " + row[1] + " | " + row[2] + " |");
            }

            System.out.println();
            System.out.println("| distance behind head (of " + DEPTH + ") | read (ms) | model size |");
            System.out.println("|---|---|---|");
            for (int distance : new int[]{0, 10, 100, 500, DEPTH - 1}) {
                if (distance >= DEPTH) {
                    continue;
                }
                Resource commitId = commits.get(commits.size() - 1 - distance);
                long[] r = timeRead(commitId, conn);
                System.out.println("| " + distance + " | " + r[0] + " | " + r[1] + " |");
                // 2 triples per commit; the model at distance k holds the first (DEPTH - k) deltas
                assertEquals(2L * (DEPTH - distance), r[1]);
            }
        }
    }

    private Resource commitOnMaster(RepositoryConnection conn, int i) {
        UUID uuid = UUID.randomUUID();
        IRI addGraph = VALUE_FACTORY.createIRI(Catalogs.DELTAS_NAMESPACE + uuid + "-A");
        IRI delGraph = VALUE_FACTORY.createIRI(Catalogs.DELTAS_NAMESPACE + uuid + "-B");
        Revision revision = revisionFactory.createNew(VALUE_FACTORY.createIRI("http://test.com#revision-ipc-" + i));
        revision.setAdditions(addGraph);
        revision.setDeletions(delGraph);
        InProgressCommit ipc = inProgressCommitFactory.createNew(VALUE_FACTORY.createIRI("http://test.com#ipc-" + i));
        ipc.setWasAssociatedWith(Collections.singleton(agentFactory.createNew(user.getResource())));
        ipc.setOnVersionedRDFRecord(record);
        ipc.setGenerated(Set.of(revision));
        ipc.getModel().addAll(revision.getModel());

        IRI entity = VALUE_FACTORY.createIRI("urn:bench#entity" + i);
        // The initial commit's state is pre-placed in the HEAD graph with empty deltas
        // (mirrors AbstractVersionedRDFRecordService.createRecord + addMasterCommitInitialTest);
        // every later commit's state arrives as InProgressCommit additions.
        IRI target = (i == 1) ? masterHeadGraph : addGraph;
        conn.add(entity, RDF.TYPE, OWL.CLASS, target);
        conn.add(entity, RDFS.LABEL, VALUE_FACTORY.createLiteral("entity " + i), target);
        addThing(ipc, conn);

        return service.addMasterCommit(record, masterBranch, user, "commit " + i, conn);
    }

    /** Returns {bestMillis, modelSize}, min over {@link #REPS} runs of the verified call. */
    private long[] timeRead(Resource commitId, RepositoryConnection conn) {
        long best = Long.MAX_VALUE;
        long size = -1;
        for (int i = 0; i < REPS; i++) {
            long t0 = System.nanoTime();
            Model m = compiledResourceManager.getCompiledResource(commitId, conn);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            best = Math.min(best, ms);
            size = m.size();
        }
        return new long[]{best, size};
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
