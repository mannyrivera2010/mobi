package org.matonto.etl.api.config.rdf;

/*-
 * #%L
 * org.matonto.etl.api
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

import org.openrdf.rio.RDFFormat;

import java.io.OutputStream;

public class RDFExportConfig extends BaseExportConfig {
    private String subj;
    private String pred;
    private String objIRI;
    private String objLit;

    protected RDFExportConfig(Builder builder) {
        super(builder);
        this.subj = builder.subj;
        this.pred = builder.pred;
        this.objIRI = builder.objIRI;
        this.objLit = builder.objLit;
    }

    public String getSubj() {
        return subj;
    }

    public String getPred() {
        return pred;
    }

    public String getObjIRI() {
        return objIRI;
    }

    public String getObjLit() {
        return objLit;
    }

    public static class Builder extends BaseExportConfig.Builder {
        private String subj;
        private String pred;
        private String objIRI;
        private String objLit;

        /**
         * Creates a new Builder for an RDFExportConfig.
         *
         * @param output The OutputStream for the exported data.
         * @param format The RDFFormat for the exported data.
         */
        public Builder(OutputStream output, RDFFormat format) {
            super(output, format);
        }

        /**
         * Sets the subject to restrict all exported triples to.
         *
         * @param subj A subject string
         * @return The Builder
         */
        public Builder subj(String subj) {
            this.subj = subj;
            return this;
        }

        /**
         * Sets the subject to restrict all exported triples to.
         *
         * @param pred A predicate string
         * @return The Builder
         */
        public Builder pred(String pred) {
            this.pred = pred;
            return this;
        }

        /**
         * An object IRI to restrict all exported triples. Takes precedence over objLit.
         *
         * @param objIRI An object IRI string
         * @return The Builder
         */
        public Builder objIRI(String objIRI) {
            this.objIRI = objIRI;
            return this;
        }

        /**
         * Sets the object literal to restrict all exported triples. Will only be used if objIRI is not passed.
         *
         * @param objLit An object literal string
         * @return The Builder
         */
        public Builder objLit(String objLit) {
            this.objLit = objLit;
            return this;
        }

        public RDFExportConfig build() {
            return new RDFExportConfig(this);
        }
    }
}
