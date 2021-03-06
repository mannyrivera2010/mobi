/*-
 * #%L
 * com.mobi.web
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
import { unset, isEqual, map } from 'lodash';

const template = require('./createClassOverlay.component.html');

/**
 * @ngdoc component
 * @name ontology-editor.component:createClassOverlay
 * @requires shared.service:ontologyStateService
 * @requires shared.service:prefixes
 * @requires ontology-editor.service:ontologyUtilsManagerService
 *
 * @description
 * `createClassOverlay` is a component that creates content for a modal that creates a class in the current
 * {@link shared.service:ontologyStateService selected ontology}. The form in the modal contains a
 * text input for the class name (which populates the {@link ontology-editor.component:staticIri IRI}), a
 * {@link shared.component:textArea} for the class description, an
 * {@link ontology-editor.component:advancedLanguageSelect}, and a
 * {@link ontology-editor.component:superClassSelect}. Meant to be used in conjunction with the
 * {@link shared.service:modalService}.
 *
 * @param {Function} close A function that closes the modal
 * @param {Function} dismiss A function that dismisses the modal
 */
const createClassOverlayComponent = {
    template,
    bindings: {
        close: '&',
        dismiss: '&'
    },
    controllerAs: 'dvm',
    controller: createClassOverlayComponentCtrl
};

createClassOverlayComponentCtrl.$inject = ['$filter', 'ontologyStateService', 'prefixes', 'ontologyUtilsManagerService'];

function createClassOverlayComponentCtrl($filter, ontologyStateService, prefixes, ontologyUtilsManagerService) {
    var dvm = this;
    dvm.prefixes = prefixes;
    dvm.os = ontologyStateService;
    dvm.ontoUtils = ontologyUtilsManagerService;
    dvm.prefix = dvm.os.getDefaultPrefix();
    dvm.values = [];
    dvm.clazz = {
        '@id': dvm.prefix,
        '@type': [prefixes.owl + 'Class'],
        [prefixes.dcterms + 'title']: [{
            '@value': ''
        }],
        [prefixes.dcterms + 'description']: [{
            '@value': ''
        }]
    };

    dvm.nameChanged = function() {
        if (!dvm.iriHasChanged) {
            dvm.clazz['@id'] = dvm.prefix + $filter('camelCase')(
                dvm.clazz[prefixes.dcterms + 'title'][0]['@value'], 'class');
        }
    }
    dvm.onEdit = function(iriBegin, iriThen, iriEnd) {
        dvm.iriHasChanged = true;
        dvm.clazz['@id'] = iriBegin + iriThen + iriEnd;
        dvm.os.setCommonIriParts(iriBegin, iriThen);
    }
    dvm.create = function() {
        if (isEqual(dvm.clazz[prefixes.dcterms + 'description'][0]['@value'], '')) {
            unset(dvm.clazz, prefixes.dcterms + 'description');
        }
        dvm.ontoUtils.addLanguageToNewEntity(dvm.clazz, dvm.language);
        // add the entity to the ontology
        dvm.os.addEntity(dvm.clazz);
        // update relevant lists
        dvm.os.addToClassIRIs(dvm.os.listItem, dvm.clazz['@id']);
        if (dvm.values.length) {
            dvm.clazz[prefixes.rdfs + 'subClassOf'] = dvm.values;
            var superClassIds = map(dvm.values, '@id');
            if (dvm.ontoUtils.containsDerivedConcept(superClassIds)) {
                dvm.os.listItem.derivedConcepts.push(dvm.clazz['@id']);
            }
            else if (dvm.ontoUtils.containsDerivedConceptScheme(superClassIds)) {
                dvm.os.listItem.derivedConceptSchemes.push(dvm.clazz['@id']);
            }
            dvm.ontoUtils.setSuperClasses(dvm.clazz['@id'], superClassIds);
        } else {
            dvm.os.listItem.classes.flat = dvm.os.flattenHierarchy(dvm.os.listItem.classes);
        }
        dvm.os.listItem.flatEverythingTree = dvm.os.createFlatEverythingTree(dvm.os.listItem);
        // Update InProgressCommit
        dvm.os.addToAdditions(dvm.os.listItem.ontologyRecord.recordId, dvm.clazz);
        // Save the changes to the ontology
        dvm.ontoUtils.saveCurrentChanges();
        // Open snackbar
        dvm.os.listItem.goTo.entityIRI = dvm.clazz['@id'];
        dvm.os.listItem.goTo.active = true;
        // hide the overlay
        dvm.close()
    }
    dvm.cancel = function() {
        dvm.dismiss();
    }
}

export default createClassOverlayComponent;