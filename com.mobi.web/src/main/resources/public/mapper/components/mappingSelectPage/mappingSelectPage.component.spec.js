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
import {
    mockComponent,
    mockMappingManager,
    mockMapperState,
    mockUtil,
    mockModal
} from '../../../../../../test/js/Shared';

describe('Mapping Select Page component', function() {
    var $compile, scope, $q, mappingManagerSvc, mapperStateSvc, modalSvc, utilSvc;

    beforeEach(function() {
        angular.mock.module('mapper');
        mockComponent('mapper', 'mappingListBlock');
        mockComponent('mapper', 'mappingPreview');
        mockMappingManager();
        mockMapperState();
        mockUtil();
        mockModal();

        inject(function(_$compile_, _$rootScope_, _$q_, _mappingManagerService_, _mapperStateService_, _modalService_, _utilService_) {
            $compile = _$compile_;
            scope = _$rootScope_;
            $q = _$q_;
            mapperStateSvc = _mapperStateService_;
            mappingManagerSvc = _mappingManagerService_;
            modalSvc = _modalService_;
            utilSvc = _utilService_;
        });

        mapperStateSvc.mapping = {record: {title: 'Record'}, ontology: {'@id': 'ontology'}, jsonld: []};
        this.element = $compile(angular.element('<mapping-select-page></mapping-select-page>'))(scope);
        scope.$digest();
        this.controller = this.element.controller('mappingSelectPage');
    });

    afterEach(function() {
        $compile = null;
        scope = null;
        $q = null;
        mappingManagerSvc = null;
        mapperStateSvc = null;
        modalSvc = null;
        utilSvc = null;
        this.element.remove();
    });

    describe('controller methods', function() {
        it('should set the correct state for editing a mapping', function() {
            spyOn(this.controller, 'loadOntologyAndContinue');
            this.controller.edit();
            expect(mapperStateSvc.mappingSearchString).toEqual('');
            expect(mapperStateSvc.editMapping).toEqual(true);
            expect(this.controller.loadOntologyAndContinue).toHaveBeenCalled();
        });
        it('should set the correct state for running a mapping', function() {
            var mappedColumns = [{}];
            mapperStateSvc.getMappedColumns.and.returnValue(mappedColumns);
            spyOn(this.controller, 'loadOntologyAndContinue');
            this.controller.run();
            expect(mapperStateSvc.mappingSearchString).toEqual('');
            expect(mapperStateSvc.highlightIndexes).toEqual(mappedColumns);
            expect(this.controller.loadOntologyAndContinue).toHaveBeenCalled();
        });
        it('should open the downloadMappingOverlay', function() {
            this.controller.download();
            expect(modalSvc.openModal).toHaveBeenCalledWith('downloadMappingOverlay', {}, undefined, 'sm');
        });
        it('should set the correct state for duplicating a mapping', function() {
            this.controller.duplicate();
            expect(modalSvc.openModal).toHaveBeenCalledWith('createMappingOverlay');
        });
        describe('should load an ontology and continue', function() {
            beforeEach(function() {
                this.ontologies = [{}];
                this.step = mapperStateSvc.step;
                mappingManagerSvc.getSourceOntologies.and.returnValue($q.when(this.ontologies));
            });
            it('if the ontology and mapping are compatiable', function() {
                mapperStateSvc.getClasses.and.returnValue([{}]);
                mappingManagerSvc.areCompatible.and.returnValue(true);
                this.controller.loadOntologyAndContinue();
                scope.$apply();
                expect(mapperStateSvc.sourceOntologies).toEqual(this.ontologies);
                expect(mapperStateSvc.getClasses).toHaveBeenCalledWith(this.ontologies);
                expect(mapperStateSvc.availableClasses).toEqual([{}]);
                expect(mapperStateSvc.step).toEqual(mapperStateSvc.fileUploadStep);
                expect(utilSvc.createErrorToast).not.toHaveBeenCalled();
            });
            it('unless the ontology and mapping are incompatiable', function() {
                mappingManagerSvc.areCompatible.and.returnValue(false);
                this.controller.loadOntologyAndContinue();
                scope.$apply();
                expect(mapperStateSvc.sourceOntologies).toEqual([]);
                expect(mapperStateSvc.getClasses).not.toHaveBeenCalled();
                expect(mapperStateSvc.availableClasses).toEqual([]);
                expect(mapperStateSvc.step).toEqual(this.step);
                expect(utilSvc.createErrorToast).toHaveBeenCalled();
            });
        });
    });
    describe('contains the correct html', function() {
        it('for wrapping containers', function() {
            expect(this.element.prop('tagName')).toEqual('MAPPING-SELECT-PAGE');
            expect(this.element.querySelectorAll('.mapping-select-page').length).toEqual(1);
            expect(this.element.querySelectorAll('.row').length).toEqual(1);
            expect(this.element.querySelectorAll('.col-8').length).toEqual(1);
        });
        ['mapping-list-block', 'block', 'block-header', 'block-content', 'action-menu'].forEach(test => {
            it('with a ' + test, function() {
                expect(this.element.find(test).length).toEqual(1);
            });
        });
        it('with buttons for downloading, editing, running, and duplicating a mapping', function() {
            var buttons = this.element.querySelectorAll('.col-8 block-header action-menu a');
            expect(buttons.length).toEqual(4);
            _.forEach(_.toArray(buttons), function(button) {
                expect(['Edit', 'Run', 'Download', 'Duplicate']).toContain(angular.element(button).text().trim());
            });
        });
        it('depending on whether a mapping has been selected', function() {
            var mappingHeader = angular.element(this.element.querySelectorAll('.col-8 block-header .mapping-preview-header')[0]);
            expect(mappingHeader.hasClass('invisible')).toEqual(false);
            expect(this.element.querySelectorAll('.preview').length).toEqual(1);

            mapperStateSvc.mapping = undefined;
            scope.$digest();
            expect(mappingHeader.hasClass('invisible')).toEqual(true);
            expect(this.element.querySelectorAll('.preview').length).toEqual(0);
        });
        it('with the correct classes based on whether the source ontology record was set', function() {
            var sourceOntologyName = angular.element(this.element.querySelectorAll('.source-ontology')[0]);
            expect(sourceOntologyName.hasClass('text-danger')).toEqual(false);
            expect(sourceOntologyName.find('span').length).toEqual(0);

            delete mapperStateSvc.mapping.ontology;
            scope.$digest();
            expect(sourceOntologyName.hasClass('text-danger')).toEqual(true);
            expect(sourceOntologyName.find('span').length).toEqual(1);
        });
    });
    it('should call downloadMapping when the button is clicked', function() {
        spyOn(this.controller, 'download');
        var downloadButton = angular.element(this.element.querySelectorAll('.col-8 block-header .download-btn')[0]);
        angular.element(downloadButton).triggerHandler('click');
        expect(this.controller.download).toHaveBeenCalled();
    });
    it('should call edit when the button is clicked', function() {
        spyOn(this.controller, 'edit');
        var editButton = angular.element(this.element.querySelectorAll('.col-8 block-header .edit-btn')[0]);
        angular.element(editButton).triggerHandler('click');
        expect(this.controller.edit).toHaveBeenCalled();
    });
    it('should call run when the button is clicked', function() {
        spyOn(this.controller, 'run');
        var runButton = angular.element(this.element.querySelectorAll('.col-8 block-header .run-btn')[0]);
        angular.element(runButton).triggerHandler('click');
        expect(this.controller.run).toHaveBeenCalled();
    });
    it('should call duplicate when the button is clicked', function() {
        spyOn(this.controller, 'duplicate');
        var duplicateButton = angular.element(this.element.querySelectorAll('.col-8 block-header .duplicate-btn')[0]);
        angular.element(duplicateButton).triggerHandler('click');
        expect(this.controller.duplicate).toHaveBeenCalled();
    });
});