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
    mockOntologyManager,
    mockMapperState
} from '../../../../../../test/js/Shared';

fdescribe('Class Preview component', function() {
    var $compile, scope, ontologyManagerSvc, mapperStateSvc;

    beforeEach(function() {
        angular.mock.module('mapper');
        mockOntologyManager();
        mockMapperState();

        inject(function(_$compile_, _$rootScope_, _ontologyManagerService_, _mapperStateService_) {
            $compile = _$compile_;
            scope = _$rootScope_;
            ontologyManagerSvc = _ontologyManagerService_;
            mapperStateSvc = _mapperStateService_;
        });

        scope.classObj = {};
        scope.ontologies = [];
        this.element = $compile(angular.element('<class-preview class-obj="classObj" ontologies="ontologies"></class-preview>'))(scope);
        scope.$digest();
        this.controller = this.element.controller('classPreview');
    });

    afterEach(function() {
        $compile = null;
        scope = null;
        ontologyManagerSvc = null;
        mapperStateSvc = null;
        this.element.remove();
    });

    describe('controller bound variable', function() {
        it('classObj should be one way bound', function() {
            this.controller.classObj = {'@id': ''};
            scope.$digest();
            expect(scope.classObj).toEqual({});
        });
        it('ontologies should be one way bound', function() {
            this.controller.ontologies = [{}];
            scope.$digest();
            expect(scope.ontologies).toEqual([]);
        });
    });
    it('should set the correct variables when the classObj changes', function() {
        var props = [
            {propObj: {'@id': '1'}},
            {propObj: {'@id': '2'}},
            {propObj: {'@id': '3'}},
            {propObj: {'@id': '4'}},
            {propObj: {'@id': '5'}},
            {propObj: {'@id': '6'}},
            {propObj: {'@id': '7'}},
            {propObj: {'@id': '8'}},
            {propObj: {'@id': '9'}},
            {propObj: {'@id': '10'}},
            {propObj: {'@id': '11'}}
        ];
        mapperStateSvc.getClassProps.and.returnValue(props);
        ontologyManagerSvc.getEntityName.and.returnValue('Name');
        ontologyManagerSvc.getEntityDescription.and.returnValue('Description');
        ontologyManagerSvc.isDeprecated.and.returnValue(false);
        scope.classObj = {'@id': ''};
        scope.$digest();
        expect(mapperStateSvc.getClassProps).toHaveBeenCalledWith(this.controller.ontologies, this.controller.classObj['@id']);
        expect(this.controller.name).toEqual('Name');
        expect(this.controller.description).toEqual('Description');
        expect(this.controller.total).toEqual(props.length);
        expect(this.controller.props.length).toEqual(10);
        _.forEach(this.controller.props, prop => {
            expect(prop.name).toEqual('Name');
            expect(prop.isDeprecated).toEqual(false);
        });
    });
    describe('contains the correct html', function() {
        it('for wrapping containers', function() {
            expect(this.element.prop('tagName')).toEqual('CLASS-PREVIEW');
            expect(this.element.querySelectorAll('.class-preview').length).toEqual(1);
        });
        it('depending on whether classObj has any properties', function() {
            var propList = angular.element(this.element.querySelectorAll('ul')[0]);
            expect(propList.html()).toContain('None');

            this.controller.props = [{}];
            scope.$digest();
            expect(propList.html()).not.toContain('None');
            expect(this.element.querySelectorAll('ul li').length > 0).toEqual(true);
        });
        it('depending on whether classObj has more than 10 properties', function() {
            this.controller.props = [{}];
            this.controller.total = 9;
            scope.$digest();
            var item = angular.element(this.element.querySelectorAll('ul li')[0]);
            expect(item.hasClass('last')).toEqual(true);
            expect(item.hasClass('limited')).toEqual(false);

            this.controller.total = 11;
            scope.$digest();
            expect(item.hasClass('last')).toEqual(true);
            expect(item.hasClass('limited')).toEqual(true);
        });
        it('depending on whether a property is deprecated', function() {
            this.controller.props = [{isDeprecated: true}];
            scope.$digest();
            expect(this.element.querySelectorAll('ul li span.deprecated').length).toEqual(1);
        });
    });
});