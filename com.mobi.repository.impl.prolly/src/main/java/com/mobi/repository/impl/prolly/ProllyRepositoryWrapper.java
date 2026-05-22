package com.mobi.repository.impl.prolly;

/*-
 * #%L
 * com.mobi.repository.impl.prolly
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

import com.dolthub.prolly.HeapBufferPool;
import com.dolthub.prolly.InMemoryNodeStore;
import com.dolthub.prolly.NodeStore;
import com.earasoft.prolly.rdf4j.obs.SailMetrics;
import com.earasoft.prolly.rdf4j.sail.ProllySail;
import com.earasoft.prolly.rdf4j.sail.RootMetaTreeStore;
import com.earasoft.prolly.storage.RocksNodeStore;
import com.mobi.repository.api.OsgiRepository;
import com.mobi.repository.base.OsgiRepositoryWrapper;
import com.mobi.repository.exception.RepositoryConfigException;
import com.mobi.repository.impl.sesame.RepositoryConfigHelper;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * OSGi Repository wrapper backed by {@code prolly-rdf4j}'s {@link ProllySail} —
 * a content-addressed Prolly Tree substrate exposed as an RDF4J {@code Sail}.
 *
 * <p>Registered as {@code repositorytype=prolly}; Mobi's
 * {@code SimpleRepositoryManager} discovers it through the {@link OsgiRepository}
 * service registration exactly like the {@code native} and {@code memory}
 * types. From Mobi's perspective it is a normal {@code SailRepository} — the
 * Prolly versioning underneath (commits, branches, merge) is not surfaced by
 * this wrapper; that would be a separate {@code VersioningService}
 * integration (see {@code prolly-rdf4j/plans/08-structural-merge.md} and the
 * Mobi-integration scoping notes).</p>
 *
 * <p>Lifecycle note: when durable, the {@link RocksNodeStore} owns the RocksDB
 * handle and its OS-level directory lock. {@code SailRepository.shutDown()}
 * does not close it, so this wrapper keeps a reference and closes it in
 * {@link #stop()} <em>after</em> the delegate has shut down — releasing the
 * lock so a subsequent {@code modified()} (or another process) can reopen the
 * directory.</p>
 */
@Component(
        immediate = true,
        service = { OsgiRepository.class },
        name = ProllyRepositoryWrapper.NAME,
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        property = {
                "repositorytype=" + ProllyRepositoryWrapper.REPOSITORY_TYPE
        }
)
@Designate(ocd = ProllyRepositoryConfig.class)
public class ProllyRepositoryWrapper extends OsgiRepositoryWrapper {

    protected static final String REPOSITORY_TYPE = "prolly";
    protected static final String NAME = "com.mobi.service.repository." + REPOSITORY_TYPE;

    /**
     * The RocksDB-backed NodeStore, retained only when the Repository is
     * durable. Closed in {@link #stop()} after the delegate shuts down.
     * {@code null} for an in-memory Repository.
     */
    private RocksNodeStore rocksStore;

    @Activate
    protected void start(final ProllyRepositoryConfig config) {
        RepositoryConfigHelper.validateBaseParams(config.id(), config.title());

        final NodeStore nodeStore;
        final RootMetaTreeStore metaTreeStore;
        if (StringUtils.isEmpty(config.dataDir())) {
            // In-memory: no durability, no commit-history sidecars. Mirrors the
            // memory store's "empty dataDir" behaviour.
            nodeStore = new InMemoryNodeStore();
            metaTreeStore = null;
        } else {
            try {
                Path dir = Path.of(config.dataDir());
                Files.createDirectories(dir);
                this.rocksStore = new RocksNodeStore(dir.toString());
                nodeStore = this.rocksStore;
                // RootMetaTreeStore.beside derives the commit-head sidecar path;
                // the 4-arg ProllySail ctor in turn derives CommitLog.beside(...)
                // and RefsStore.beside(...) from it.
                metaTreeStore = RootMetaTreeStore.beside(dir);
            } catch (Exception e) {
                // Most common cause: the directory is already locked by another
                // JVM. Translate into a config exception rather than leaking a
                // RocksDB stack trace through Karaf's component activation.
                throw new RepositoryConfigException(
                        new IllegalStateException("Could not open Prolly RocksDB store at '"
                                + config.dataDir() + "'. If a previous Mobi instance is still "
                                + "running it holds the directory lock — stop it first. "
                                + "Underlying error: " + e.getMessage(), e));
            }
        }

        // The 4-arg ProllySail ctor auto-restores from the RootMetaTreeStore on
        // init (latest commit), and falls back to in-memory CommitLog/RefsStore
        // when metaTreeStore is null. provenanceEnabled would need the 7-arg
        // ctor; kept off here for the OSS default — see TODO below.
        ProllySail sail = new ProllySail(nodeStore, new HeapBufferPool(),
                SailMetrics.noop(), metaTreeStore);
        // TODO: config.provenanceEnabled() currently has no effect — wiring it
        // requires the 7-arg ProllySail ctor (store, pool, metrics, rmt,
        // commitLog, refsStore, provenanceEnabled). Switch to that ctor and
        // pass the explicit CommitLog.beside/RefsStore.beside to honour the
        // config flag.

        Repository repo = new SailRepository(sail);
        setDelegate(repo);
        this.repositoryID = config.id();
        this.repositoryTitle = config.title();
    }

    @Deactivate
    protected void stop() {
        try {
            getDelegate().shutDown();
        } catch (RepositoryException e) {
            throw new RepositoryException("Could not shutdown Repository \"" + repositoryID + "\".", e);
        } finally {
            // Close the RocksDB handle AFTER the Sail has shut down, so the
            // directory lock is released and a later modified()/restart can
            // reopen the same dataDir.
            if (rocksStore != null) {
                rocksStore.close();
                rocksStore = null;
            }
        }
    }

    @Modified
    protected void modified(final ProllyRepositoryConfig config) {
        stop();
        start(config);
    }

    @Override
    public Class<ProllyRepositoryConfig> getConfigType() {
        return ProllyRepositoryConfig.class;
    }

    @Override
    public String getRepositoryType() {
        return REPOSITORY_TYPE;
    }

    @Override
    public Optional<Long> getLimit() {
        // The Prolly substrate is off-heap and bounded by disk, not heap, so
        // there is no small fixed ceiling to advertise. Empty = "no limit".
        return Optional.empty();
    }

    @Override
    public Optional<Long> getTripleCount() {
        try (RepositoryConnection conn = this.getConnection()) {
            return Optional.of(conn.size());
        }
    }
}
