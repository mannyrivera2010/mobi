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

const template = require('./runMappingDownloadOverlay.component.html');

/**
 * @ngdoc component
 * @name mapper.component:runMappingDownloadOverlay
 * @requires $filter
 * @requires shared.service:mapperStateService
 * @requires shared.service:delimitedManagerService
 * @requires shared.service:utilService
 *
 * @description
 * `runMappingDownloadOverlay` is a component that creates content for a modal that contains a configuration
 * settings for running the currently selected {@link shared.service:mapperStateService#mapping mapping} against the
 * uploaded {@link shared.service:delimitedManagerService#dataRows delimited data} and downloading the results. This
 * includes a {@link shared.component:textInput text input} for the file name of the downloaded mapped data and a
 * {@link mapper.component:mapperSerializationSelect mapperSerializationSelect} for the RDF format of the mapped
 * data. Meant to be used in conjunction with the {@link shared.service:modalService}.
 *
 * @param {Function} close A function that closes the modal
 * @param {Function} dismiss A function that dismisses the modal
 */
const runMappingDownloadOverlayComponent = {
    template,
    bindings: {
        close: '&',
        dismiss: '&'
    },
    controllerAs: 'dvm',
    controller: runMappingDownloadOverlayComponentCtrl,
};

runMappingDownloadOverlayComponentCtrl.$inject = ['$filter', 'mapperStateService', 'delimitedManagerService', 'utilService'];

function runMappingDownloadOverlayComponentCtrl($filter, mapperStateService, delimitedManagerService, utilService) {
    var dvm = this;
    var state = mapperStateService;
    var dm = delimitedManagerService;
    dvm.util = utilService;
    dvm.fileName = '';
    dvm.format = 'turtle';
    dvm.errorMessage = '';

    dvm.$onInit = function() {
        dvm.fileName = $filter('camelCase')(state.mapping.record.title, 'class') + '_Data';
    }
    dvm.run = function() {
        if (state.editMapping && state.isMappingChanged()) {
            state.saveMapping().then(runMapping, onError);
        } else {
            runMapping(state.mapping.record.id);
        }
    }
    dvm.cancel = function() {
        dvm.dismiss();
    }

    function onError(errorMessage) {
        dvm.errorMessage = errorMessage;
    }
    function runMapping(id) {
        state.mapping.record.id = id;
        dm.mapAndDownload(id, dvm.format, dvm.fileName);
        dvm.util.createSuccessToast('Successfully ran mapping');
        reset();
    }
    function reset() {
        state.step = state.selectMappingStep;
        state.initialize();
        state.resetEdit();
        dm.reset();
        dvm.close();
    }
}

export default runMappingDownloadOverlayComponent;