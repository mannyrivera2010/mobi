package org.matonto.catalog.api.builder;

/*-
 * #%L
 * org.matonto.catalog.api
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2016 - 2017 iNovex Information Systems, Inc.
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

import org.matonto.rdf.api.IRI;
import org.matonto.rdf.api.Model;

public class Conflict {
    private IRI iri;
    private Model original;
    private Difference left;
    private Difference right;

    private Conflict(Builder builder) {
        this.iri = builder.iri;
        this.original = builder.original;
        this.left = builder.left;
        this.right = builder.right;
    }

    public IRI getIRI() {
        return iri;
    }

    public Model getOriginal() {
        return original;
    }

    public Difference getLeftDifference() {
        return left;
    }

    public Difference getRightDifference() {
        return right;
    }

    public static class Builder {
        private final IRI iri;
        private final Model original;
        private Difference left;
        private Difference right;

        /**
         * The builder for a SimpleConflict which takes the subject IRI string, original Model, the first Model being
         * compared (left), and the second Model being compared (right). The left and right Models are further broken
         * down into additions and deletions.
         *
         * @param original The Model identifying the original state for this conflict.
         * @param iri The IRI identifying the entity for this conflict.
         */
        public Builder(Model original, IRI iri) {
            this.original = original;
            this.iri = iri;
        }

        public Builder leftDifference(Difference left) {
            this.left = left;
            return this;
        }

        public Builder rightDifference(Difference right) {
            this.right = right;
            return this;
        }

        public Conflict build() {
            return new Conflict(this);
        }
    }
}