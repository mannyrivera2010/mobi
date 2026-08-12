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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.mobi.prolly.record.api.client.ProllyDiff;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.DynamicModelFactory;
import org.eclipse.rdf4j.model.impl.ValidatingValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

public class InMemoryProllyVersioningClientTest {

    private static final String REPO = "records/test";

    private final ValueFactory vf = new ValidatingValueFactory();
    private InMemoryProllyVersioningClient client;

    private Model modelOf(String subject, String label) {
        Model model = new DynamicModelFactory().createEmptyModel();
        model.add(vf.createIRI(subject), RDFS.LABEL, vf.createLiteral(label));
        return model;
    }

    private Model empty() {
        return new DynamicModelFactory().createEmptyModel();
    }

    @Before
    public void setUp() {
        client = new InMemoryProllyVersioningClient();
        client.createRepo(REPO);
    }

    @Test
    public void commitSnapshotRoundTrip() {
        String c1 = client.commit(REPO, modelOf("urn:a", "a"), empty(), List.of(), "root");
        String c2 = client.commit(REPO, modelOf("urn:b", "b"), empty(), List.of(c1), "add b");

        assertEquals(1, client.snapshot(REPO, c1).size());
        assertEquals(2, client.snapshot(REPO, c2).size());
        IRI a = vf.createIRI("urn:a");
        assertTrue(client.snapshot(REPO, c2).contains(a, RDFS.LABEL, vf.createLiteral("a")));
    }

    @Test
    public void deletionsApply() {
        String c1 = client.commit(REPO, modelOf("urn:a", "a"), empty(), List.of(), "root");
        String c2 = client.commit(REPO, empty(), modelOf("urn:a", "a"), List.of(c1), "remove a");
        assertEquals(0, client.snapshot(REPO, c2).size());
        assertEquals(1, client.snapshot(REPO, c1).size());
    }

    @Test
    public void hashesAreContentAddressed() {
        String c1 = client.commit(REPO, modelOf("urn:a", "a"), empty(), List.of(), "root");
        client.deleteRepo(REPO);
        client.createRepo(REPO);
        String c1Again = client.commit(REPO, modelOf("urn:a", "a"), empty(), List.of(), "root again");
        assertEquals(c1, c1Again);
        String c2 = client.commit(REPO, modelOf("urn:b", "b"), empty(), List.of(c1), "different state");
        assertNotEquals(c1, c2);
    }

    @Test
    public void branchLifecycle() {
        String c1 = client.commit(REPO, modelOf("urn:a", "a"), empty(), List.of(), "root");
        client.createBranch(REPO, "main", c1);
        assertEquals(Optional.of(c1), client.getBranchHead(REPO, "main"));
        String c2 = client.commit(REPO, modelOf("urn:b", "b"), empty(), List.of(c1), "next");
        client.setBranchHead(REPO, "main", c2);
        assertEquals(Optional.of(c2), client.getBranchHead(REPO, "main"));
        client.deleteBranch(REPO, "main");
        assertEquals(Optional.empty(), client.getBranchHead(REPO, "main"));
        try {
            client.createBranch(REPO, "bad", "no-such-commit");
            fail("Expected unknown commit to be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void diffBetweenCommits() {
        String c1 = client.commit(REPO, modelOf("urn:a", "a"), empty(), List.of(), "root");
        Model adds = modelOf("urn:b", "b");
        Model dels = modelOf("urn:a", "a");
        String c2 = client.commit(REPO, adds, dels, List.of(c1), "swap");

        ProllyDiff diff = client.diff(REPO, c1, c2);
        assertEquals(1, diff.getAdditions().size());
        assertEquals(1, diff.getDeletions().size());
        assertTrue(diff.getAdditions().contains(vf.createIRI("urn:b"), RDFS.LABEL, vf.createLiteral("b")));
        assertTrue(diff.getDeletions().contains(vf.createIRI("urn:a"), RDFS.LABEL, vf.createLiteral("a")));
    }

    @Test
    public void mergeBaseOfDivergedBranches() {
        String base = client.commit(REPO, modelOf("urn:base", "base"), empty(), List.of(), "base");
        String left = client.commit(REPO, modelOf("urn:l", "l"), empty(), List.of(base), "left");
        String right = client.commit(REPO, modelOf("urn:r", "r"), empty(), List.of(base), "right");
        assertEquals(Optional.of(base), client.getMergeBase(REPO, left, right));

        String merge = client.commit(REPO, empty(), empty(), List.of(left, right), "merge");
        assertEquals(Optional.of(left), client.getMergeBase(REPO, merge, left));
    }

    @Test
    public void leaseLifecycle() {
        String c1 = client.commit(REPO, modelOf("urn:a", "a"), empty(), List.of(), "root");
        String lease = client.acquireSnapshotLease(REPO, c1);
        assertTrue(client.activeLeases(REPO).containsKey(lease));
        client.renewSnapshotLease(REPO, lease);
        client.releaseSnapshotLease(REPO, lease);
        assertFalse(client.activeLeases(REPO).containsKey(lease));
    }

    @Test
    public void repoLifecycle() {
        assertTrue(client.repoExists(REPO));
        try {
            client.createRepo(REPO);
            fail("Expected duplicate repo to be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        client.deleteRepo(REPO);
        assertFalse(client.repoExists(REPO));
    }
}
