package com.mobi.ontology.core.api.change;

/*-
 * #%L
 * com.mobi.ontology.api
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2016 iNovex Information Systems, Inc.
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

import com.mobi.ontology.core.api.Ontology;

import java.util.Set;

public interface OntologyChangeset {

    Ontology getOntology();

    Ontology applyChanges();

    Set<OntologyChange> getAdditions();

    Set<OntologyChange> getRemovals();

    Set<OntologyChange> getModifications();

    Set<OntologyChange> getChanges();

    boolean addChange(OntologyChange change);
}