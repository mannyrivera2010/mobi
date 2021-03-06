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
import { remove, sortBy, trim, map } from 'lodash';

const template = require('./newDatasetOverlay.component.html');

/**
 * @ngdoc component
 * @name datasets.component:newDatasetOverlay
 * @requires shared.service:datasetManagerService
 * @requires shared.service:datasetStateService
 * @requires shared.service:utilService
 *
 * @description
 * `newDatasetOverlay` is a component that creates content for a modal with a form containing fields for
 * creating a new Dataset Record. The fields are for the title, repository id, dataset IRI, description,
 * {@link shared.component:keywordSelect keywords}, and
 * {@link datasets.component:datasetsOntologyPicker ontologies to be linked} to the new Dataset
 * Record. The repository id is a static field for now. Meant to be used in conjunction with the
 * {@link shared.service:modalService}.
 *
 * @param {Function} close A function that closes the modal
 * @param {Function} dismiss A function that dismisses the modal
 */
const newDatasetOverlayComponent = {
    template,
    bindings: {
        close: '&',
        dismiss: '&'
    },
    controllerAs: 'dvm',
    controller: newDatasetOverlayComponentCtrl
};

newDatasetOverlayComponentCtrl.$inject = ['REGEX', 'datasetManagerService', 'datasetStateService', 'utilService'];

function newDatasetOverlayComponentCtrl(REGEX, datasetManagerService, datasetStateService, utilService) {
    var dvm = this;
    var state = datasetStateService;
    var dm = datasetManagerService;
    dvm.util = utilService;
    dvm.error = '';
    dvm.recordConfig = {
        title: '',
        repositoryId: 'system',
        datasetIRI: '',
        description: ''
    };
    dvm.keywords = [];
    dvm.selectedOntologies = [];
    dvm.iriPattern = REGEX.IRI;

    dvm.selectOntology = function(ontology) {
        dvm.selectedOntologies.push(ontology);
        dvm.selectedOntologies = sortBy(dvm.selectedOntologies, 'title');
    }
    dvm.unselectOntology = function(ontology) {
        remove(dvm.selectedOntologies, {recordId: ontology.recordId});
    }
    dvm.create = function() {
        dvm.recordConfig.keywords = map(dvm.keywords, trim);
        dvm.recordConfig.ontologies = map(dvm.selectedOntologies, 'recordId');
        dm.createDatasetRecord(dvm.recordConfig)
            .then(() => {
                dvm.util.createSuccessToast('Dataset successfully created');
                state.setResults();
                dvm.close();
            }, onError);
    }
    dvm.cancel = function() {
        dvm.dismiss();
    }

    function onError(errorMessage) {
        dvm.error = errorMessage;
    }
}

export default newDatasetOverlayComponent;