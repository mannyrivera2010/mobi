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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.mobi.prolly.record.api.client.ProllyRepoKeys;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.ValidatingValueFactory;
import org.junit.Test;

public class ProllyRepoKeysTest {

    private final ValueFactory vf = new ValidatingValueFactory();

    @Test
    public void repoKeyFitsServerLimitAndIsStable() {
        // the production shape: a long UUID record IRI
        String iri = "https://mobi.com/records#5af9a211-61b6-42b1-879b-4eccb7c5d9f3";
        String key = ProllyRepoKeys.forRecord(vf.createIRI(iri));
        assertTrue("repoId limit is 63 chars server-side, got " + key.length() + ": " + key,
                key.length() <= 63);
        assertTrue("bare repoIds must be slash-free: " + key, key.matches("[A-Za-z0-9_.-]{1,63}"));
        assertEquals(key, ProllyRepoKeys.forRecord(vf.createIRI(iri)));
    }

    @Test
    public void distinctIrisWithSameTailGetDistinctKeys() {
        // truncation keeps the IRI tail — the hash suffix must still disambiguate
        String tail = "x".repeat(80) + "#same-tail";
        String keyA = ProllyRepoKeys.forRecord(vf.createIRI("https://a.example/" + tail));
        String keyB = ProllyRepoKeys.forRecord(vf.createIRI("https://b.example/" + tail));
        assertNotEquals(keyA, keyB);
        assertTrue(keyA.length() <= 63 && keyB.length() <= 63);
    }

    @Test
    public void branchNameFitsBranchRules() {
        String name = ProllyRepoKeys.forBranch(
                vf.createIRI("https://mobi.com/branches#41674d73-74ff-4fa2-a52d-beda40f33b8f"));
        assertTrue(name.matches("[A-Za-z0-9_./-]{1,128}"));
    }
}
