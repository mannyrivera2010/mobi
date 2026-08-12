package com.mobi.prolly.record.impl.client;

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

import com.mobi.catalog.api.CommitSnapshotResolver;
import com.mobi.prolly.record.api.client.ProllyVersioningClient;
import com.mobi.prolly.record.api.ontologies.prollyrecord.ProllyCommit;
import com.mobi.prolly.record.api.ontologies.prollyrecord.ProllyVersionedRDFRecord;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.ValidatingValueFactory;
import org.eclipse.rdf4j.query.QueryResults;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Routes compiled-resource reads for prolly-backed commits to the prolly snapshot (plan
 * {@code plans/prolly-versioned-rdf-record.md} D-5 / Step 8): a commit stub written by
 * {@code ProllyRecordVersioningService} carries its {@code prollyCommitHash} and repo key; any
 * commit without them belongs to another backend and is answered with empty.
 */
@Component(immediate = true, service = CommitSnapshotResolver.class)
public class ProllySnapshotResolver implements CommitSnapshotResolver {

    private static final Logger LOG = LoggerFactory.getLogger(ProllySnapshotResolver.class);

    private final ValueFactory vf = new ValidatingValueFactory();

    @Reference
    ProllyVersioningClient prollyClient;

    @Override
    public Optional<Model> snapshot(Resource commitId, RepositoryConnection conn) {
        Optional<String> hash = literalOf(commitId, ProllyCommit.prollyCommitHash_IRI, conn);
        if (hash.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> repoKey = literalOf(commitId, ProllyVersionedRDFRecord.prollyRepoKey_IRI, conn);
        if (repoKey.isEmpty()) {
            LOG.warn("Commit {} carries a prollyCommitHash but no repo key; falling back to replay", commitId);
            return Optional.empty();
        }
        try {
            return Optional.of(prollyClient.snapshot(repoKey.get(), hash.get()));
        } catch (Exception e) {
            LOG.warn("Prolly snapshot for commit {} failed; falling back to replay", commitId, e);
            return Optional.empty();
        }
    }

    private Optional<String> literalOf(Resource commitId, String predicate, RepositoryConnection conn) {
        return QueryResults.asModel(conn.getStatements(commitId, vf.createIRI(predicate), null, commitId))
                .objects().stream().findFirst().map(v -> v.stringValue());
    }
}
