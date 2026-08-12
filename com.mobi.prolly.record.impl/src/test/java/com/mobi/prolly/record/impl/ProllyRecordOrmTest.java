package com.mobi.prolly.record.impl;

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

import com.mobi.catalog.api.ontologies.mcat.Commit;
import com.mobi.catalog.api.ontologies.mcat.VersionedRDFRecord;
import com.mobi.prolly.record.api.ontologies.prollyrecord.ProllyCommit;
import com.mobi.prolly.record.api.ontologies.prollyrecord.ProllyVersionedRDFRecord;
import com.mobi.rdf.orm.OrmFactory;
import com.mobi.rdf.orm.test.OrmEnabledTestCase;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.junit.Test;

import java.util.Optional;

public class ProllyRecordOrmTest extends OrmEnabledTestCase {

    private final OrmFactory<ProllyVersionedRDFRecord> recordFactory =
            getRequiredOrmFactory(ProllyVersionedRDFRecord.class);
    private final OrmFactory<ProllyCommit> commitFactory = getRequiredOrmFactory(ProllyCommit.class);

    @Test
    public void recordRoundTripsRepoKey() {
        IRI recordIRI = VALUE_FACTORY.createIRI("urn:test#record");
        ProllyVersionedRDFRecord record = recordFactory.createNew(recordIRI);
        record.setProllyRepoKey("records/urn-test-record");

        Optional<ProllyVersionedRDFRecord> reread = recordFactory.getExisting(recordIRI, record.getModel());
        assertTrue(reread.isPresent());
        assertEquals(Optional.of("records/urn-test-record"), reread.get().getProllyRepoKey());
    }

    @Test
    public void recordCarriesVersionedRDFRecordType() {
        ProllyVersionedRDFRecord record = recordFactory.createNew(VALUE_FACTORY.createIRI("urn:test#record"));
        assertTrue(record.getModel().contains(record.getResource(), RDF.TYPE,
                VALUE_FACTORY.createIRI(VersionedRDFRecord.TYPE)));
        assertTrue(record.getModel().contains(record.getResource(), RDF.TYPE,
                VALUE_FACTORY.createIRI(ProllyVersionedRDFRecord.TYPE)));
    }

    @Test
    public void commitStubRoundTripsHashAndStaysACommit() {
        IRI commitIRI = VALUE_FACTORY.createIRI("urn:test#commit");
        ProllyCommit stub = commitFactory.createNew(commitIRI);
        stub.setProllyCommitHash("a3f9c2");

        Optional<ProllyCommit> reread = commitFactory.getExisting(commitIRI, stub.getModel());
        assertTrue(reread.isPresent());
        assertEquals(Optional.of("a3f9c2"), reread.get().getProllyCommitHash());
        assertTrue(stub.getModel().contains(commitIRI, RDF.TYPE, VALUE_FACTORY.createIRI(Commit.TYPE)));
    }
}
