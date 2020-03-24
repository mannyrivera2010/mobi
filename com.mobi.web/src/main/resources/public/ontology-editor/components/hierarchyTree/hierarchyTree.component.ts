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

import {filter, some, every} from 'lodash';

import './hierarchyTree.component.scss';

const template = require('./hierarchyTree.component.html');

/**
 * @ngdoc component
 * @name ontology-editor.component:hierarchyTree
 * @requires shared.service:ontologyManagerService
 * @requires shared.service:ontologyStateService
 * @requires ontology-editor.service:ontologyUtilsManagerService
 * @requires shared.service:utilService
 * @requires shared.service:prefixes
 *
 * @description
 * `hierarchyTree` is a component which creates a `div` containing a {@link shared.component:searchBar} and
 * hierarchy of {@link ontology-editor.component:treeItem}. When search text is provided, the hierarchy filters what is
 * shown based on value matches with predicates in the {@link shared.service:ontologyManagerService entityNameProps}.
 *
 * @param {Object[]} hierarchy An array which represents a flattened hierarchy
 * @param {Function} updateSearch A function to update the state variable used to track the search filter text
 */
const hierarchyTreeComponent = {
    template,
    bindings: {
        hierarchy: '<',
        index: '<',
        updateSearch: '&',
        resetIndex: '&',
        clickItem: '&?'
    },
    controllerAs: 'dvm',
    controller: hierarchyTreeComponentCtrl
};

hierarchyTreeComponentCtrl.$inject = ['ontologyManagerService', 'ontologyStateService', 'utilService', 'INDENT'];

function hierarchyTreeComponentCtrl(ontologyManagerService, ontologyStateService, utilService, INDENT) {
    var dvm = this;
    var om = ontologyManagerService;
    var util = utilService;
    dvm.indent = INDENT;
    dvm.os = ontologyStateService;
    dvm.searchText = '';
    dvm.filterText = '';
    dvm.filteredHierarchy = [];
    dvm.preFilteredHierarchy = [];
    dvm.midFilteredHierarchy = [];
    dvm.activeTab = '';
    dvm.dropdownFilterActive = false;
    dvm.activeEntityFilter = {
        name: 'Active Entities Only',
        checked: false,
        flag: false, 
        filter: function(node) {
            var match = true;
            if (node.entity.hasOwnProperty('mobi')) {
                if (node.entity.mobi.imported) {
                    match = false;
                }
            }
            return match;
        }
    };

    dvm.dropdownFilters = [angular.copy(dvm.activeEntityFilter)];

    dvm.$onInit = function() {
        dvm.activeTab = dvm.os.getActiveKey();
        update();
    }
    function clearSelection() {
        dvm.dropdownFilterActive = false;
        dvm.dropdownFilters = [angular.copy(dvm.activeEntityFilter)];
        dvm.searchText = dvm.os.listItem.editorTabStates[dvm.activeTab].searchText;
        dvm.filterText = dvm.os.listItem.editorTabStates[dvm.activeTab].searchText;
    }
    dvm.$onChanges = function(changesObj) {
        if (!changesObj.hierarchy || !changesObj.hierarchy.isFirstChange()) {
            clearSelection();
            update();
        }
    }
    dvm.$onDestroy = function() {
        if (dvm.os.listItem.editorTabStates) {
            dvm.resetIndex();
        }
    }
    dvm.click = function(entityIRI) {
        dvm.os.selectItem(entityIRI);
        if (dvm.clickItem) {
            dvm.clickItem({iri: entityIRI});
        }
    }
    dvm.onKeyup = function() {
        dvm.filterText = dvm.searchText;
        dvm.dropdownFilterActive = some(dvm.dropdownFilters, 'flag');
        update();
    }
    dvm.toggleOpen = function(node) {
        node.isOpened = !node.isOpened;
        node.isOpened ? dvm.os.listItem.editorTabStates[dvm.activeTab].open[node.joinedPath] = true : delete dvm.os.listItem.editorTabStates[dvm.activeTab].open[node.joinedPath];
        dvm.filteredHierarchy = filter(dvm.preFilteredHierarchy, dvm.isShown);
    }
    dvm.matchesSearchFilter = function(node) {
        var searchMatch = false;
        // Check all possible name fields and entity fields to see if the value matches the search text
        some(om.entityNameProps, key => some(node.entity[key], value => {
            if (value['@value'].toLowerCase().includes(dvm.filterText.toLowerCase()))
                searchMatch = true;
        }));

        if (searchMatch) {
            return true;
        }

        // Check if beautified entity id matches search text
        if (util.getBeautifulIRI(node.entity['@id']).toLowerCase().includes(dvm.filterText.toLowerCase())) {
            searchMatch = true;
        }
        
        return searchMatch;
    }
    dvm.matchesDropdownFilters = function(node) {
        return every(dvm.dropdownFilters, filter => filter.flag ? filter.filter(node) : true);
    }
    dvm.searchFilter = function(node) {
        delete node.underline;
        delete node.parentNoMatch;
        delete node.displayNode;

        if (dvm.filterText || dvm.dropdownFilterActive) {
            delete node.isOpened;
            var match = false;
            
            if(dvm.matchesSearchFilter(node) && dvm.matchesDropdownFilters(node)) {
                match = true;
                dvm.openAllParents(node);
                node.underline = true;
            }

            // Always return true for parents, but if the parent is not a match, set a property called parentNoMatch = true
            if (!match && node.hasChildren) {
                node.parentNoMatch = true;
                return true;
            }
            return match;
        } else {
            return true;
        }
    }
    // Start at the current node and go up through the parents marking each path as an iriToOpen. If a path is already present in dvm.os.listItem.editorTabStates[dvm.activeTab].open, it means it was already marked as an iriToOpen by another one of it's children. In that scenario we know all of it's parents will also be open, and we can break out of the loop.
    dvm.openAllParents = function(node) {
        for (var i = node.path.length - 1; i > 1; i--) {
            var fullPath = dvm.os.joinPath(node.path.slice(0, i));

            if (dvm.os.listItem.editorTabStates[dvm.activeTab].open[fullPath]) {
                break;
            }

            dvm.os.listItem.editorTabStates[dvm.activeTab].open[fullPath] = true;
        }
    }
    dvm.isShown = function (node) {
        var displayNode = (node.indent > 0 && dvm.os.areParentsOpen(node, dvm.activeTab)) || node.indent === 0;
        if ((dvm.dropdownFilterActive || dvm.filterText) && node.parentNoMatch) {
            if (node.displayNode === undefined) {
                return false;
            } else {
                return displayNode && node.displayNode;
            }
        }
        return displayNode;
    }
    dvm.openEntities = function(node) {
        var toOpen = dvm.os.listItem.editorTabStates[dvm.activeTab].open[node.joinedPath];
        if (toOpen) {
            if (!node.isOpened) {
                node.isOpened = true;
            }
            node.displayNode = true; 
        }
        return true;
    }

    function update() {
        if (dvm.filterText || dvm.dropdownFilterActive) {
            dvm.os.listItem.editorTabStates[dvm.activeTab].open = {};
        }
        dvm.updateSearch({value: dvm.filterText});
        dvm.preFilteredHierarchy = dvm.hierarchy.filter(dvm.searchFilter);
        dvm.midFilteredHierarchy = dvm.preFilteredHierarchy.filter(dvm.openEntities);
        dvm.filteredHierarchy = dvm.midFilteredHierarchy.filter(dvm.isShown);
    }
}

export default hierarchyTreeComponent;
