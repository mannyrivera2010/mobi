package com.mobi.ontology.impl.repository;

/*-
 * #%L
 * com.mobi.ontology.impl.repository
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2016 - 2019 iNovex Information Systems, Inc.
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

import com.mobi.ontology.core.api.OntologyId;
import com.mobi.ontology.impl.core.AbstractOntologyId;
import com.mobi.rdf.api.IRI;
import com.mobi.rdf.api.Resource;
import com.mobi.rdf.api.ValueFactory;

import java.util.Optional;
import java.util.UUID;


public class SimpleOntologyId extends AbstractOntologyId {

    private IRI ontologyIRI;
    private IRI versionIRI;

    public static class Builder extends AbstractOntologyId.Builder {
        public Builder(ValueFactory factory) {
            this.factory = factory;
        }

        @Override
        public OntologyId build() {
            return new SimpleOntologyId(this);
        }
    }

    private SimpleOntologyId(Builder builder) {
        setUp(builder);

        if (builder.versionIRI != null) {
            this.ontologyIRI = factory.createIRI(builder.ontologyIRI.stringValue());
            this.versionIRI = factory.createIRI(builder.versionIRI.stringValue());
            this.identifier = factory.createIRI(builder.versionIRI.stringValue());
        } else if (builder.ontologyIRI != null) {
            this.ontologyIRI = factory.createIRI(builder.ontologyIRI.stringValue());
            this.identifier = factory.createIRI(builder.ontologyIRI.stringValue());
        } else if (builder.identifier != null) {
            this.identifier = builder.identifier;
        } else {
            this.identifier = factory.createIRI(DEFAULT_PREFIX + UUID.randomUUID());
        }

    }

    @Override
    public Optional<IRI> getOntologyIRI() {
        return Optional.ofNullable(ontologyIRI);
    }


    @Override
    public Optional<IRI> getVersionIRI() {
        return Optional.ofNullable(versionIRI);
    }


    @Override
    public Resource getOntologyIdentifier() {
        return identifier;
    }


    @Override
    public String toString() {
        if (ontologyIRI != null) {
            String template = "OntologyID(OntologyIRI(<%s>) VersionIRI(<%s>))";
            String versionString = versionIRI == null ? "" : versionIRI.stringValue();
            return String.format(template, ontologyIRI.stringValue(), versionString);
        } else {
            return "OntologyID(" + identifier.stringValue() + ')';
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof SimpleOntologyId) {
            SimpleOntologyId other = (SimpleOntologyId) obj;
            if (identifier.equals(other.getOntologyIdentifier())) {
                return this.getVersionIRI().equals(other.getVersionIRI());
            }
        }

        return false;
    }

    @Override
    public int hashCode() {
        return identifier.hashCode();
    }
}



