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

import { unionWith, get, map, isEqual, forEach, chunk} from 'lodash';

import './commitChangesDisplay.component.scss';

const template = require('./commitChangesDisplay.component.html');

/**
 * @ngdoc component
 * @name shared.component:commitChangesDisplay
 * @requires shared.service:utilService
 *
 * @description
 * `commitChangesDisplay` is a component that creates a sequence of divs displaying the changes made to entities
 * separated by additions and deletions. Each changes display uses the `.property-values` class. The display of an
 * entity's name can be optionally controlled by the provided `entityNameFunc` function and defaults to the
 * {@link shared.service:utilService beautified local name} of the IRI.
 *
 * @param {Object[]} additions An array of JSON-LD objects representing statements added
 * @param {Object[]} deletions An array of JSON-LD objects representing statements deleted
 * @param {Function} [entityNameFunc=undefined] An optional function to retrieve the name of an entity by it's IRI.
 * The component will pass the IRI of the entity as the only argument
 */
const commitChangesDisplayComponent = {
    template,
    bindings: {
        commitId: '<',
        entityNameFunc: '<?',
        showMoreChangesFunc: '<'
    },
    controllerAs: 'dvm',
    controller: commitChangesDisplayComponentCtrl
};

commitChangesDisplayComponentCtrl.$inject = ['utilService', 'catalogManagerService'];

function commitChangesDisplayComponentCtrl(utilService, catalogManagerService) {
    var dvm = this;
    var cm = catalogManagerService;
    // dvm.size = 100;
    // dvm.index = 0;
    dvm.limit = 100;
    dvm.offset = 0;
    dvm.util = utilService;
    dvm.results = {};
    dvm.hasMoreResults = false;

    dvm.$onChanges = function() {
        // var adds = map(dvm.additions, '@id');
        // var deletes = map(dvm.deletions, '@id');
        // dvm.list = adds.concat(deletes.filter(i => adds.indexOf(i) == -1));
        dvm.limit = 100;
        dvm.offset = 0;
        dvm.results = getResults();
    }
    dvm.getMoreResults = function() {
        dvm.offset += dvm.limit;
        dvm.showMoreChangesFunc({limit: dvm.limit, offset: dvm.offset})
            .then(response => {
                var adds = map(response.data.additions, '@id');
                var deletes = map(response.data.deletions, '@id');
                var list = adds.concat(deletes.filter(i => adds.indexOf(i) == -1));
                forEach(list, id => {
                    addToResults(dvm.util.getChangesById(id, response.data.additions), dvm.util.getChangesById(id, response.data.deletions), id, dvm.results);
                });
                var headers = response.headers();
                dvm.hasMoreResults = get(headers, 'has-more-results', false) === 'true';
            }, errorMessage => dvm.error = errorMessage);
    }

    function getResults() {
        var results = {};
        dvm.showMoreChangesFunc({limit: dvm.limit, offset: dvm.offset})
            .then(response => {
                var adds = map(response.data.additions, '@id');
                var deletes = map(response.data.deletions, '@id');
                var list = adds.concat(deletes.filter(i => adds.indexOf(i) == -1));
                forEach(list, id => {
                    addToResults(dvm.util.getChangesById(id, response.data.additions), dvm.util.getChangesById(id, response.data.deletions), id, results);
                });
                var headers = response.headers();
                dvm.hasMoreResults = get(headers, 'has-more-results', false) === 'true';
            }, errorMessage => dvm.error = errorMessage);
        return results;
    }
    function addToResults(additions, deletions, id, results) {
        results[id] = { additions: additions, deletions: deletions };
    }
}

export default commitChangesDisplayComponent;