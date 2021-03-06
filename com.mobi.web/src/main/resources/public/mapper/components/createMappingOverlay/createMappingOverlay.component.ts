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
import * as angular from 'angular';

const template = require('./createMappingOverlay.component.html');

/**
 * @ngdoc component
 * @name mapper.component:createMappingOverlay
 * @requires shared.service:mappingManagerService
 * @requires shared.service:mapperStateService
 *
 * @description
 * `createMappingOverlay` is a component that creates content for a modal with three inputs for metadata about a
 * new MappingRecord: a text input for the title, a {@link shared.component:textArea} for the description,
 * and a {@link shared.component:keywordSelect}. Meant to be used in conjunction with the
 * {@link shared.service:modalService}.
 *
 * @param {Function} close A function that closes the modal
 * @param {Function} dismiss A function that dismisses the modal
 */
const createMappingOverlayComponent = {
    template,
    bindings: {
        close: '&',
        dismiss: '&'
    },
    controllerAs: 'dvm',
    controller: createMappingOverlayComponentCtrl,
};

createMappingOverlayComponentCtrl.$inject = ['mappingManagerService', 'mapperStateService'];

function createMappingOverlayComponentCtrl(mappingManagerService, mapperStateService) {
    var dvm = this;
    var state = mapperStateService;
    var mm = mappingManagerService;

    dvm.errorMessage = '';
    dvm.newMapping = {};

    dvm.$onInit = function() {
        dvm.newMapping = state.createMapping();
        if (state.mapping) {
            dvm.newMapping.record = angular.copy(state.mapping.record);
            dvm.newMapping.jsonld = angular.copy(state.mapping.jsonld);
            dvm.newMapping.ontology = angular.copy(state.mapping.ontology);
        }
    }
    dvm.cancel = function() {
        state.editMapping = false;
        state.newMapping = false;
        dvm.dismiss();
    }
    dvm.continue = function() {
        var newId = mm.getMappingId(dvm.newMapping.record.title);
        if (dvm.newMapping.jsonld.length === 0) {
            dvm.newMapping.jsonld = mm.createNewMapping(newId);
            dvm.sourceOntologies = [];
            dvm.availableClasses = [];
            nextStep();
        } else {
            dvm.newMapping.jsonld = mm.copyMapping(dvm.newMapping.jsonld, newId);
            var sourceOntologyInfo = mm.getSourceOntologyInfo(dvm.newMapping.jsonld);
            mm.getSourceOntologies(sourceOntologyInfo)
                .then(ontologies => {
                    if (mm.areCompatible(dvm.newMapping.jsonld, ontologies)) {
                        state.sourceOntologies = ontologies;
                        state.availableClasses = state.getClasses(ontologies);
                        nextStep();
                    } else {
                        onError('The selected mapping is incompatible with its source ontologies');
                    }
                }, () => onError('Error retrieving mapping'));
        }
    }

    function nextStep() {
        state.mapping = dvm.newMapping;
        dvm.errorMessage = '';
        state.mapping.difference.additions = angular.copy(state.mapping.jsonld);
        state.mappingSearchString = '';
        state.step = state.fileUploadStep;
        dvm.close();
    }
    function onError(message) {
        dvm.errorMessage = message;
    }
}

export default createMappingOverlayComponent;