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
    mockOntologyState
} from '../../../../../../test/js/Shared';

describe('Ontology Editor Page component', function() {
    var $compile, scope, ontologyStateSvc;

    beforeEach(function() {
        angular.mock.module('ontology-editor');
        mockComponent('ontology-editor', 'ontologySidebar');
        mockComponent('ontology-editor', 'ontologyTab');
        mockComponent('ontology-editor', 'openOntologyTab');
        mockOntologyState();

        inject(function(_$compile_, _$rootScope_, _ontologyStateService_) {
            $compile = _$compile_;
            scope = _$rootScope_;
            ontologyStateSvc = _ontologyStateService_;
        });

        this.element = $compile(angular.element('<ontology-editor-page></ontology-editor-page>'))(scope);
        scope.$digest();
        this.controller = this.element.controller('ontologyEditorPage');
    });

    afterEach(function() {
        $compile = null;
        scope = null;
        ontologyStateSvc = null;
        this.element.remove();
    });

    describe('contains the correct html', function() {
        it('for wrapping containers', function() {
            expect(this.element.prop('tagName')).toEqual('ONTOLOGY-EDITOR-PAGE');
            expect(this.element.querySelectorAll('.ontology-editor-page').length).toEqual(1);
        });
        it('with a ontology-sidebar', function() {
            expect(this.element.find('ontology-sidebar').length).toEqual(1);
        });
        it('depending on whether an ontology is selected', function() {
            spyOn(this.controller, 'isOpenTab').and.returnValue(true);
            scope.$digest();
            expect(this.element.find('open-ontology-tab').length).toEqual(1);
            expect(this.element.find('ontology-tab').length).toEqual(0);

            this.controller.isOpenTab.and.returnValue(false);
            scope.$digest();
            expect(this.element.find('open-ontology-tab').length).toEqual(0);
            expect(this.element.find('ontology-tab').length).toEqual(1);
        });
    });
    describe('controller methods', function() {
        it('should test whether the open ontology tab should be shown', function() {
            expect(this.controller.isOpenTab()).toEqual(false);
            ontologyStateSvc.listItem = {};
            expect(this.controller.isOpenTab()).toEqual(true);
        });
    });
});