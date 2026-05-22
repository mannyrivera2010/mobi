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

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Configuration for a Prolly Tree backed Repository.
 *
 * <p>{@code prolly-rdf4j} presents a content-addressed Prolly Tree substrate as
 * an RDF4J {@code Sail}. Storage is off-heap (heap stays flat regardless of
 * dataset size) and the four quad indexes (SPOC / POSC / OSPC / CSPO) are
 * always built — so, unlike the native store, there is no {@code tripleIndexes}
 * knob to tune.</p>
 *
 * <p>When {@code dataDir} is empty the Sail runs fully in-memory (an
 * {@code InMemoryNodeStore}); when set, it opens a RocksDB-backed
 * {@code RocksNodeStore} under that directory and persists commit history in
 * sidecar files ({@code root-head}, {@code commits.log}, {@code refs/}).</p>
 */
@ObjectClassDefinition(name = "ProllyRepositoryConfig",
        description = "Configuration for a Prolly Tree (prolly-rdf4j) Repository")
public @interface ProllyRepositoryConfig {

    /**
     * The Repository ID.
     *
     * @return String representing the Repository ID.
     */
    @AttributeDefinition(name = "id", description = "The ID of the Repository")
    String id();

    /**
     * The Repository Title.
     *
     * @return String representing the Repository Title.
     */
    @AttributeDefinition(name = "title", description = "The Title of the Repository")
    String title();

    /**
     * The data directory where the Prolly Tree substrate is stored. When empty
     * the Repository runs in-memory and its contents do not survive a restart.
     *
     * @return String representing the directory where the repository data is stored.
     */
    @AttributeDefinition(required = false, name = "dataDir",
            description = "The directory of the Repository. Empty = in-memory.")
    String dataDir() default "";

    /**
     * Whether the Sail records a per-triple provenance index (ADR-0001). When
     * enabled, every {@code addStatement} also records the parent commit hash
     * into a sidecar index, and each commit's RootMetaTree gains a
     * {@code provenance} entry. Off by default — it is opt-in because it adds
     * write-path cost and changes the on-disk RootMetaTree shape.
     *
     * @return {@code true} to enable the provenance index.
     */
    @AttributeDefinition(required = false, name = "provenanceEnabled",
            description = "Record a per-triple provenance index (ADR-0001)")
    boolean provenanceEnabled() default false;
}
