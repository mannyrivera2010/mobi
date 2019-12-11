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
    mockPrefixes,
    mockOntologyManager,
    mockOntologyState,
    mockUtil,
    injectUniqueKeyFilter,
    injectIndentConstant
} from '../../../../../../test/js/Shared';

describe('Hierarchy Tree component', function() {
    var $compile, scope, ontologyManagerSvc, ontologyStateSvc, utilSvc, prefixes;

    beforeEach(function() {
        angular.mock.module('ontology-editor');
        mockComponent('ontology-editor', 'treeItem');
        mockPrefixes();
        mockOntologyManager();
        mockOntologyState();
        mockUtil();
        injectUniqueKeyFilter();
        injectIndentConstant();

        inject(function(_$compile_, _$rootScope_, _ontologyManagerService_, _ontologyStateService_, _utilService_, _prefixes_) {
            $compile = _$compile_;
            scope = _$rootScope_;
            ontologyManagerSvc = _ontologyManagerService_;
            ontologyStateSvc = _ontologyStateService_;
            utilSvc = _utilService_;
            prefixes = _prefixes_;
        });

        scope.hierarchy = [{
            entityIRI: 'class1',
            indent: 0,
            path: []
        }, {
            entityIRI: 'class2',
            indent: 1,
            path: []
        }, {
            entityIRI: 'class3',
            indent: 0,
            path: []
        }];
        scope.index = 4;
        scope.updateSearch = jasmine.createSpy('updateSearch');
        scope.resetIndex = jasmine.createSpy('resetIndex');
        scope.clickItem = jasmine.createSpy('clickItem');
        this.element = $compile(angular.element('<hierarchy-tree hierarchy="hierarchy" index="index" update-search="updateSearch(value)" reset-index="resetIndex()" click-item="clickItem(iri)"></hierarchy-tree>'))(scope);
        scope.$digest();
        this.controller = this.element.controller('hierarchyTree');
    });

    afterEach(function() {
        $compile = null;
        scope = null;
        ontologyStateSvc = null;
        utilSvc = null;
        prefixes = null;
        this.element.remove();
    });

    describe('controller bound variable', function() {
        it('hierarchy should be one way bound', function() {
            this.controller.hierarchy = [];
            scope.$digest();
            expect(angular.copy(scope.hierarchy)).toEqual([{
                entityIRI: 'class1',
                indent: 0,
                path: [],
                entity: undefined,
                isOpened: false
            }, {
                entityIRI: 'class2',
                indent: 1,
                path: [],
                entity: undefined,
                isOpened: false
            }, {
                entityIRI: 'class3',
                indent: 0,
                path: [],
                entity: undefined,
                isOpened: false
            }]);
        });
        it('index should be one way bound', function() {
            this.controller.index = 0;
            scope.$digest();
            expect(scope.index).toEqual(4);
        });
        it('updateSearch should be called in the parent scope', function() {
            this.controller.updateSearch({value: 'value'});
            expect(scope.updateSearch).toHaveBeenCalledWith('value');
        });
        it('resetIndex should be called in the parent scope', function() {
            this.controller.resetIndex();
            expect(scope.resetIndex).toHaveBeenCalled();
        });
        it('clickItem should be called in the parent scope', function() {
            this.controller.clickItem({iri: 'iri'});
            expect(scope.clickItem).toHaveBeenCalledWith('iri');
        });
    });
    describe('contains the correct html', function() {
        beforeEach(function() {
            spyOn(this.controller, 'isShown').and.returnValue(true);
            scope.$apply();
        });
        it('for wrapping containers', function() {
            expect(this.element.prop('tagName')).toEqual('HIERARCHY-TREE');
        });
        it('based on .repeater-container', function() {
            expect(this.element.querySelectorAll('.repeater-container').length).toEqual(1);
        });
        it('based on tree-items', function() {
            expect(this.element.find('tree-item').length).toEqual(1);
        });
        it('based on .tree-item-wrapper', function() {
            expect(this.element.querySelectorAll('.tree-item-wrapper').length).toEqual(1);
        });
    });
    describe('controller methods', function() {
        it('toggleOpen should set the correct values', function() {
            spyOn(this.controller, 'isShown').and.returnValue(false);
            var node = {isOpened: false, path: ['a', 'b']};
            this.controller.toggleOpen(node);
            expect(node.isOpened).toEqual(true);
            expect(ontologyStateSvc.setOpened).toHaveBeenCalledWith('a.b', true);
            expect(this.controller.isShown).toHaveBeenCalled();
            expect(this.controller.filteredHierarchy).toEqual([]);
        });
        describe('click should call the correct methods', function() {
            it('if clickItem has been provided', function() {
                this.controller.click('iri');
                expect(ontologyStateSvc.selectItem).toHaveBeenCalledWith('iri');
                expect(scope.clickItem).toHaveBeenCalledWith('iri');
            });
            it('if clickItem has not been provided', function() {
                this.controller.clickItem = undefined;
                this.controller.click('iri');
                expect(ontologyStateSvc.selectItem).toHaveBeenCalledWith('iri');
                expect(scope.clickItem).not.toHaveBeenCalled();
            });
        });

        describe('matchesDropdownFilters', function() {
            beforeEach(function() {
                this.filterNode = {
                    indent: 1,
                    entityIRI: 'iri',
                    hasChildren: false,
                    path: ['recordId', 'otherIri', 'iri']
                };
            });
            it('returns true when all flagged dropdown filters return true', function() {
                this.controller.dropdownFilters.forEach(filter => {
                    filter.flag = true;
                    spyOn(filter, 'filter').and.returnValue(true);
                });
                expect(this.controller.matchesDropdownFilters(this.filterNode)).toEqual(true);
            });
            it('returns true when all flagged dropdown filters do not return true', function() {
                this.controller.dropdownFilters.forEach(filter => {
                    filter.flag = true;
                    spyOn(filter, 'filter').and.returnValue(false);
                });
                expect(this.controller.matchesDropdownFilters(this.filterNode)).toEqual(false);
            });
            it('returns true when there are no flagged dropdowns', function() {
                this.controller.dropdownFilters.forEach(filter => {
                    filter.flag = false;
                    spyOn(filter, 'filter').and.returnValue(false);
                });
                expect(this.controller.matchesDropdownFilters(this.filterNode)).toEqual(true);
            });
        });

        describe('processMatches', function() {
            beforeEach(function() {
                this.filterNode = {
                    indent: 1,
                    entityIRI: 'iri',
                    hasChildren: false,
                    path: ['recordId', 'otherIri', 'iri']
                };
                this.filterEntity = {
                    '@id': 'urn:id',
                    [prefixes.dcterms + 'title']: [{'@value': 'Title'}]
                };
                ontologyStateSvc.getEntityByRecordId.and.returnValue(this.filterEntity);
                spyOn(this.controller, 'openAllParents');
            });
            it('should return true when both the search and dropdown filter match', function() {
                this.controller.filterText = 'ti';
                this.controller.numDropdownFilters = 1;
                spyOn(this.controller, 'matchesDropdownFilters').and.returnValue(true);
                spyOn(this.controller, 'matchesSearchFilter').and.returnValue(true);
                expect(this.controller.processFilters(this.filterNode)).toEqual(true);
            });
            it('should return false when the search filter matches and dropdown filter does not match', function() {
                this.controller.filterText = 'ti';
                this.controller.numDropdownFilters = 0;
                spyOn(this.controller, 'matchesDropdownFilters').and.returnValue(false);
                spyOn(this.controller, 'matchesSearchFilter').and.returnValue(true);
                expect(this.controller.processFilters(this.filterNode)).toEqual(false);
            });
            it('should return false when the search filter matches and dropdown filter does not match', function() {
                this.controller.filterText = '';
                this.controller.numDropdownFilters = 1;
                spyOn(this.controller, 'matchesDropdownFilters').and.returnValue(true);
                spyOn(this.controller, 'matchesSearchFilter').and.returnValue(false);
                expect(this.controller.processFilters(this.filterNode)).toEqual(false);
            });
            it('should return true when both the search and dropdown filter do not match but the node has children', function() {
                this.filterNode.hasChildren = true;
                this.controller.filterText = '';
                this.controller.numDropdownFilters = 1;
                spyOn(this.controller, 'matchesDropdownFilters').and.returnValue(true);
                spyOn(this.controller, 'matchesSearchFilter').and.returnValue(false);
                expect(this.controller.processFilters(this.filterNode)).toEqual(true);
            });
        })

        describe('openAllParents', function() {
            beforeEach(function() {
                this.filterNode = {
                    indent: 2,
                    entityIRI: 'iri',
                    hasChildren: false,
                    path: ['recordId', 'otherIri', 'anotherIri', 'iri']
                };
                this.filterNodeParent = {
                    indent: 1,
                    entityIRI: 'anotherIri',
                    hasChildren: true,
                    path: ['recordId', 'otherIri', 'anotherIri']
                };
                this.filterNodeParentParent = {
                    indent: 0,
                    entityIRI: 'otherIri',
                    hasChildren: true,
                    path: ['recordId', 'otherIri']
                }
                this.controller.hierarchy = [this.filterNodeParentParent, this.filterNodeParent, this.filterNode];
            });
            it('successfully opens all parents', function() {
                this.controller.openAllParents(this.filterNode);
                expect(ontologyStateSvc.setOpened).not.toHaveBeenCalledWith(this.filterNode.path[0], true);
                expect(ontologyStateSvc.setOpened).toHaveBeenCalledWith(this.filterNode.path[0] + '.' + this.filterNode.path[1], true);
                expect(ontologyStateSvc.setOpened).toHaveBeenCalledWith(this.filterNode.path[0] + '.' + this.filterNode.path[1] + '.' + this.filterNode.path[2], true);
                expect(ontologyStateSvc.setOpened).not.toHaveBeenCalledWith(this.filterNode.path[0] + '.' + this.filterNode.path[1] + '.' + this.filterNode.path[2] + '.' + this.filterNode.path[3], true);
            });
            
        });

        describe('the active entity filter', function() {
            beforeEach(function() {
                this.importedNode = {
                    indent: 1,
                    entityIRI: 'iri',
                    hasChildren: false,
                    path: ['recordId', 'otherIri', 'iri'],
                    entity: {
                        '@id': 'urn:id',
                        [prefixes.dcterms + 'title']: [{'@value': 'Title'}],
                        mobi: {
                            imported: true
                        }
                    }
                };
                this.activeEntityNode = {
                    indent: 1,
                    entityIRI: 'iri',
                    hasChildren: false,
                    path: ['recordId', 'otherIri', 'iri'],
                    entity: {
                        '@id': 'urn:id',
                        [prefixes.dcterms + 'title']: [{'@value': 'Title'}]
                    }
                };
            });
            it('matches when an entity is not imported', function() {
                expect(this.controller.activeEntityFilter.filter(this.activeEntityNode)).toEqual(true);
            });
            it('does not match when an entity imported', function() {
                expect(this.controller.activeEntityFilter.filter(this.importedNode)).toEqual(false);
            });
        });

        describe('the search filter', function() {
            beforeEach(function() {
                this.filterNode = {
                    indent: 1,
                    entityIRI: 'iri',
                    hasChildren: false,
                    path: ['recordId', 'otherIri', 'iri'],
                    entity: {
                        '@id': 'urn:id',
                        [prefixes.dcterms + 'title']: [{'@value': 'Title'}],
                        mobi: {
                            imported: true
                        }
                    }
                };

                this.noMatchNode = {
                    indent: 1,
                    entityIRI: 'iri',
                    hasChildren: false,
                    path: ['recordId', 'otherIri', 'iri'],
                    entity: {
                        '@id': 'urn:title',
                    }
                };

                this.controller.filterText = 'ti';

                ontologyManagerSvc.entityNameProps = [prefixes.dcterms + 'title'];
            });

            describe('has filter text', function() {
                describe('and the entity has matching search properties', function() {
                    it('that have at least one matching text value', function() {
                        expect(this.controller.matchesSearchFilter(this.filterNode)).toEqual(true);
                    });
                    describe('that do not have a matching text value', function () {
                        beforeEach(function () {
                            utilSvc.getBeautifulIRI.and.returnValue('id');
                        });
                        it('and does not have a matching entity local name', function () {
                                expect(this.controller.matchesSearchFilter(this.noMatchNode)).toEqual(false);
                        });
                        it('and does have a matching entity local name', function() {
                            utilSvc.getBeautifulIRI.and.returnValue('title');
                            expect(this.controller.matchesSearchFilter(this.noMatchNode)).toEqual(true);
                        });
                    });
                });
                it('and the entity does not have matching search properties', function() {
                    ontologyManagerSvc.entityNameProps = [];
                    expect(this.controller.matchesSearchFilter(this.filterNode)).toEqual(false);
                });
            });     
            it('does not have filter text', function() {
                this.controller.filterText = '';
                expect(this.controller.matchesSearchFilter(this.filterNode)).toEqual(true);
            });
        });       

        describe('isShown filter', function () {
            describe('indent is greater than 0 and areParentsOpen is true', function () {
                beforeEach(function() {
                    this.node = {
                        indent: 1,
                        entityIRI: 'iri',
                        path: ['recordId', 'otherIRI', 'andAnotherIRI', 'iri']
                    };
                });
                describe('and filterText is set and node is parent node without a text match', function() {
                    beforeEach(function() {
                        this.controller.filterText = 'text';
                        this.node.parentNoMatch = true;
                        ontologyStateSvc.areParentsOpen.and.returnValue(true);
                    });
                    it('and has a child that has a text match', function() {
                        this.node.displayNode = true;
                        expect(this.controller.isShown(this.node)).toEqual(true);
                        expect(ontologyStateSvc.areParentsOpen).toHaveBeenCalledWith(this.node);
                    });
                    it('and does not have a child with a text match', function() {
                        expect(this.controller.isShown(this.node)).toEqual(false);
                        expect(ontologyStateSvc.areParentsOpen).toHaveBeenCalledWith(this.node);
                    });
                });
                it('and filterText is not set and is not a parent node without a text match', function() {
                    ontologyStateSvc.areParentsOpen.and.returnValue(true);
                    expect(this.controller.isShown(this.node)).toEqual(true);
                    expect(ontologyStateSvc.areParentsOpen).toHaveBeenCalledWith(this.node);
                });
            });
            describe('indent is 0', function () {
                beforeEach(function() {
                    this.node = {
                        indent: 0,
                        entityIRI: 'iri',
                        path: ['recordId', 'iri']
                    };
                });
                describe('and filterText is set and node is parent node without a text match', function() {
                    beforeEach(function() {
                        this.controller.filterText = 'text';
                        this.node.parentNoMatch = true;
                    });
                    it('and has a child that has a text match', function() {
                        this.node.displayNode = true;
                        ontologyStateSvc.areParentsOpen.and.returnValue(true);
                        expect(this.controller.isShown(this.node)).toEqual(true);
                    });
                    it('and does not have a child with a text match', function() {
                        ontologyStateSvc.areParentsOpen.and.returnValue(false);
                        expect(this.controller.isShown(this.node)).toEqual(false);
                    });
                });
                it('and filterText is not set and is not a parent node without a text match', function() {
                    ontologyStateSvc.areParentsOpen.and.returnValue(true);
                    expect(this.controller.isShown(this.node)).toEqual(true);
                });
            });
            describe('indent is greater than 0 and areParentsOpen is false', function () {
                beforeEach(function() {
                    this.node = {
                        indent: 1,
                        entityIRI: 'iri',
                        path: ['recordId', 'otherIRI', 'iri']
                    };
                });
                describe('and filterText is set and node is parent node without a text match', function() {
                    beforeEach(function() {
                        this.controller.filterText = 'text';
                        this.node.parentNoMatch = true;
                        ontologyStateSvc.areParentsOpen.and.returnValue(false);
                    });
                    it('and has a child that has a text match', function() {
                        this.node.displayNode = true;
                        expect(this.controller.isShown(this.node)).toEqual(false);
                        expect(ontologyStateSvc.areParentsOpen).toHaveBeenCalledWith(this.node);
                    });
                    it('and does not have a child with a text match', function() {
                        expect(this.controller.isShown(this.node)).toEqual(false);
                        expect(ontologyStateSvc.areParentsOpen).toHaveBeenCalledWith(this.node);
                    });
                });
                it('and filterText is not set and is not a parent node without a text match', function() {
                    ontologyStateSvc.areParentsOpen.and.returnValue(false);
                    expect(this.controller.isShown(this.node)).toEqual(false);
                    expect(ontologyStateSvc.areParentsOpen).toHaveBeenCalledWith(this.node);
                });
            });
        });
    });
});
