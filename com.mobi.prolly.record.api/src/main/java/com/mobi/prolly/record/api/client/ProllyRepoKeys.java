package com.mobi.prolly.record.api.client;

/*-
 * #%L
 * com.mobi.prolly.record.api
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

import org.eclipse.rdf4j.model.Resource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Derives the prolly repo key for a record: a deterministic function of the record IRI, valid per
 * prolly's repo-name rules ({@code [A-Za-z0-9_./-]{1,128}}). The key is a readable sanitized slug
 * suffixed with a hash of the full IRI, so distinct IRIs that sanitize identically cannot collide.
 */
public final class ProllyRepoKeys {

    private static final int SLUG_MAX = 80;
    private static final int HASH_CHARS = 16;
    // server-side repoId rules: max 63 chars AND no '/' in a bare (non-org) repoId — the
    // path-traversal guard rejects it. Flat "mobi-<slug>-<hash16>" fits both.
    private static final int REPO_SLUG_MAX = 63 - "mobi-".length() - 1 - HASH_CHARS;

    private ProllyRepoKeys() {
    }

    /** The prolly branch name for a catalog branch IRI — stable, {@code <slug>-<hash16>}. */
    public static String forBranch(Resource branchIri) {
        String iri = branchIri.stringValue();
        String slug = iri.replaceAll("^https?://", "").replaceAll("[^A-Za-z0-9_.-]", "-");
        if (slug.length() > SLUG_MAX) {
            slug = slug.substring(slug.length() - SLUG_MAX);
        }
        return slug + "-" + sha256Hex(iri).substring(0, HASH_CHARS);
    }

    /**
     * The repo key for a record IRI — stable across calls, {@code mobi-<slug>-<hash16>}: within
     * the server's 63-char repoId limit and slash-free (a bare repoId rejects '/').
     */
    public static String forRecord(Resource recordIri) {
        String iri = recordIri.stringValue();
        String slug = iri.replaceAll("^https?://", "").replaceAll("[^A-Za-z0-9_.-]", "-");
        if (slug.length() > REPO_SLUG_MAX) {
            slug = slug.substring(slug.length() - REPO_SLUG_MAX);
        }
        return "mobi-" + slug + "-" + sha256Hex(iri).substring(0, HASH_CHARS);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
