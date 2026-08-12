package com.mobi.catalog.api;

/*-
 * #%L
 * com.mobi.catalog.api
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

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.repository.RepositoryConnection;

import java.util.Optional;

/**
 * An extension point for storage backends that can produce the full compiled state of a commit
 * directly — an O(1) snapshot read — instead of the catalog's revision-chain replay.
 * {@code SimpleCompiledResourceManager} consults every registered resolver before replaying; the
 * first non-empty answer wins. Implementations answer {@link Optional#empty()} for commits they do
 * not own, so resolvers for one backend never affect records of another.
 */
public interface CommitSnapshotResolver {

    /**
     * The full graph at {@code commitId}, if this resolver's backend holds that commit's state.
     *
     * @param commitId The commit whose compiled state is requested
     * @param conn     A connection to the catalog repository for metadata lookup
     * @return the complete model at the commit, or empty if this resolver does not own the commit
     */
    Optional<Model> snapshot(Resource commitId, RepositoryConnection conn);
}
