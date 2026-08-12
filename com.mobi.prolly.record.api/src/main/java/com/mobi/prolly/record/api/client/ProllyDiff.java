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

import org.eclipse.rdf4j.model.Model;

/**
 * The statement-level delta between two prolly commits: what {@code to} adds over {@code from} and
 * what it removes.
 */
public final class ProllyDiff {
    private final Model additions;
    private final Model deletions;

    public ProllyDiff(Model additions, Model deletions) {
        this.additions = additions;
        this.deletions = deletions;
    }

    public Model getAdditions() {
        return additions;
    }

    public Model getDeletions() {
        return deletions;
    }
}
