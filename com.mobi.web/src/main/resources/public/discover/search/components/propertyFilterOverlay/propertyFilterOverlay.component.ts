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
import { map, concat, intersection, forEach, get, assign, join, sortBy, has, keys } from 'lodash';

import './propertyFilterOverlay.component.scss';

const template = require('./propertyFilterOverlay.component.html');

/**
 * @ngdoc component
 * @name search.component:propertyFilterOverlay
 * @requires shared.service:discoverStateService
 * @requires shared.service:utilService
 * @requires search.service:searchService
 * @requires shared.service:prefixes
 * @requires shared.service:ontologyManagerService
 *
 * @description
 * `propertyFilterOverlay` is a component that creates content for a modal with a {@link search.component:propertySelector}
 * and {@link search.component:filterSelector} to create a property filter to be used in the search query.
 *
 * @param {Function} close A function that closes the modal
 * @param {Function} dismiss A function that dismisses the modal
 */
const propertyFilterOverlayComponent = {
    template,
    bindings: {
        close: '&',
        dismiss: '&'
    },
    controllerAs: 'dvm',
    controller: propertyFilterOverlayComponentCtrl
};

propertyFilterOverlayComponentCtrl.$inject = ['$timeout', 'discoverStateService', 'utilService', 'searchService', 'prefixes', 'ontologyManagerService']

function propertyFilterOverlayComponentCtrl($timeout, discoverStateService, utilService, searchService, prefixes, ontologyManagerService) {
    var dvm = this;
    var util = utilService;
    var ds = discoverStateService;
    var s = searchService;
    var defaultProperties = [];
    dvm.om = ontologyManagerService;
    dvm.property = undefined;
    dvm.range = undefined;
    dvm.keys = [];
    dvm.filterType = undefined;
    dvm.begin = undefined;
    dvm.end = undefined;
    dvm.value = undefined;
    dvm.regex = undefined;
    dvm.boolean = undefined;
    dvm.showFilter = false;
    dvm.path = [];

    dvm.$onInit = function() {
        defaultProperties = map([prefixes.rdfs + 'label', prefixes.rdfs + 'comment', prefixes.dcterms + 'title', prefixes.dcterms + 'description'], iri => ({'@id': iri}));
        dvm.keys = getKeys();
        setProperties();
    }
    dvm.submittable = function() {
        switch (dvm.filterType) {
            case 'Boolean':
                return dvm.boolean !== undefined;
            case 'Contains':
            case 'Exact':
            case 'Greater than':
            case 'Greater than or equal to':
            case 'Less than':
            case 'Less than or equal to':
                return dvm.value !== undefined;
            case 'Existence':
                return true;
            case 'Range':
                return dvm.begin !== undefined && dvm.end !== undefined;
            case 'Regex':
                return dvm.regex !== undefined;
            case undefined:
                return dvm.path.length && !dvm.showFilter;
            default:
                return false;
        }
    }
    dvm.submit = function() {
        var config: any = {};
        switch (dvm.filterType) {
            case 'Boolean':
                config.display = 'Is ' + dvm.boolean;
                config.boolean = dvm.boolean;
                break;
            case 'Contains':
                config.display = 'Contains "' + dvm.value + '"';
                config.value = dvm.value;
                break;
            case 'Exact':
                config.display = 'Exactly matches "' + dvm.value + '"';
                config.value = dvm.value;
                break;
            case 'Existence':
            case undefined:
                config.display = 'Existence';
                break;
            case 'Greater than':
                config.display = 'value > ' + dvm.value;
                config.value = dvm.value;
                break;
            case 'Greater than or equal to':
                config.display = 'value >= ' + dvm.value;
                config.value = dvm.value;
                break;
            case 'Less than':
                config.display = 'value < ' + dvm.value;
                config.value = dvm.value;
                break;
            case 'Less than or equal to':
                config.display = 'value <= ' + dvm.value;
                config.value = dvm.value;
                break;
            case 'Range':
                config.display = dvm.begin + ' <= value <= ' + dvm.end;
                config.begin = dvm.begin;
                config.end = dvm.end;
                break;
            case 'Regex':
                try {
                    var regex = new RegExp(dvm.regex);
                    config.display = 'Matches ' + dvm.regex;
                    config.regex = dvm.regex;
                    break;
                } catch (e) {
                    util.createErrorToast(e.message);
                    return;
                }
        }
        ds.search.queryConfig.filters.push(assign(config, {
            path: map(dvm.path, item => ({predicate: item.property['@id'], range: item.range})),
            title: join(map(dvm.path, item => dvm.om.getEntityName(item.property)), ' > '),
            type: dvm.filterType
        }));
        dvm.close();
    }
    dvm.rangeEvent = function(value) {
        dvm.range = value;
        $timeout(() => dvm.propertySelected());
    }
    dvm.propertySelected = function() {
        if (dvm.property && dvm.property !== {}) {
            dvm.path.push({property: angular.copy(dvm.property), range: dvm.range});
            if (dvm.om.isObjectProperty(dvm.property)) {
                dvm.keys = [dvm.range];
                dvm.property = undefined;
                dvm.range = undefined;
            } else {
                dvm.showFilter = true;
            }
        }
    }
    dvm.cancel = function() {
        dvm.dismiss();
    }

    function setProperties() {
        if (!ds.search.properties) {
            s.getPropertiesForDataset(ds.search.datasetRecordId)
                .then(response => {
                    ds.search.properties = {};
                    ds.search.noDomains = [];
                    forEach(response, property => {
                        var domains = get(property, prefixes.rdfs + 'domain');
                        forEach(domains, domain => {
                            var id = domain['@id'];
                            if (!has(ds.search.properties, id)) {
                                ds.search.properties[id] = [property];
                            } else {
                                ds.search.properties[id].push(property);
                            }
                        });
                        if (!domains) {
                            ds.search.noDomains.push(property);
                        }
                    });
                    ds.search.noDomains = concat(ds.search.noDomains, defaultProperties);
                    dvm.keys = getKeys();
                }, util.createErrorToast);
        }
    }

    function getKeys() {
        var selectedTypes = map(ds.search.queryConfig.types, 'classIRI');
        if (selectedTypes.length) {
            return sortBy(intersection(selectedTypes, keys(ds.search.properties)));
        }
        return sortBy(keys(ds.search.properties));
    }
}

export default propertyFilterOverlayComponent;