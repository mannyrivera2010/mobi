/*-
 * #%L
 * org.matonto.web
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2016 - 2017 iNovex Information Systems, Inc.
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
describe('Search Service', function() {
    var searchSvc, scope, $q, httpSvc, sparqlManagerSvc, discoverStateSvc, prefixes, util, datasetManagerSvc, ontologyManagerSvc;

    beforeEach(function() {
        module('search');
        mockHttpService();
        mockSparqlManager();
        mockDiscoverState();
        mockPrefixes();
        mockDatasetManager();
        mockOntologyManager();
        mockUtil();

        module(function($provide) {
            $provide.constant('sparqljs', window.sparqljs);
        });

        inject(function(searchService, _$rootScope_, _$q_, _httpService_, _sparqlManagerService_, _discoverStateService_, _prefixes_, _utilService_, _datasetManagerService_, _ontologyManagerService_) {
            searchSvc = searchService;
            scope = _$rootScope_;
            $q = _$q_;
            httpSvc = _httpService_;
            sparqlManagerSvc = _sparqlManagerService_;
            discoverStateSvc = _discoverStateService_;
            prefixes = _prefixes_;
            util = _utilService_;
            datasetManagerSvc = _datasetManagerService_;
            ontologyManagerSvc = _ontologyManagerService_;
        });
    });

    describe('getPropertiesForDataset should return the correct list when getDataProperties', function() {
        beforeEach(function() {
            datasetManagerSvc.datasetRecords = [[
                {'@id': 'id', '@type': []},
                {prop: 'other'}
            ]];
            util.getPropertyId.and.returnValue('value');
        });
        it('resolves', function() {
            ontologyManagerSvc.getDataProperties.and.returnValue($q.when([{prop: 'prop1'}]));
            searchSvc.getPropertiesForDataset('id')
                .then(function(response) {
                    expect(util.getPropertyId).toHaveBeenCalledWith({prop: 'other'}, prefixes.dataset + 'linksToRecord');
                    expect(util.getPropertyId).toHaveBeenCalledWith({prop: 'other'}, prefixes.dataset + 'linksToBranch');
                    expect(util.getPropertyId).toHaveBeenCalledWith({prop: 'other'}, prefixes.dataset + 'linksToCommit');
                    expect(ontologyManagerSvc.getDataProperties).toHaveBeenCalledWith('value', 'value', 'value');
                    expect(response).toEqual([{prop: 'prop1'}]);
                }, function() {
                    fail('Promise should have resolved');
                });
            scope.$apply();
        });
        it('rejects', function() {
            ontologyManagerSvc.getDataProperties.and.returnValue($q.reject('error'));
            searchSvc.getPropertiesForDataset('id')
                .then(function() {
                    fail('Promise should have rejected');
                }, function(response) {
                    expect(util.getPropertyId).toHaveBeenCalledWith({prop: 'other'}, prefixes.dataset + 'linksToRecord');
                    expect(util.getPropertyId).toHaveBeenCalledWith({prop: 'other'}, prefixes.dataset + 'linksToBranch');
                    expect(util.getPropertyId).toHaveBeenCalledWith({prop: 'other'}, prefixes.dataset + 'linksToCommit');
                    expect(ontologyManagerSvc.getDataProperties).toHaveBeenCalledWith('value', 'value', 'value');
                    expect(response).toBe('error');
                });
            scope.$apply();
        });
    });
    describe('should submit a search query', function() {
        var query = 'query';
        beforeEach(function() {
            spyOn(searchSvc, 'createQueryString').and.returnValue(query);
        });
        it('unless an error occurs', function() {
            sparqlManagerSvc.query.and.returnValue($q.reject('Error Message'));
            searchSvc.submitSearch('', {}).then(function() {
                fail('Promise should have rejected');
            }, function(response) {
                expect(response).toEqual('Error Message');
                expect(searchSvc.createQueryString).toHaveBeenCalledWith({});
                expect(sparqlManagerSvc.query).toHaveBeenCalledWith(query, '', discoverStateSvc.search.targetedId);
                expect(httpSvc.cancel).toHaveBeenCalledWith(discoverStateSvc.search.targetedId);
            });
            scope.$apply();
        });
        it('successfully', function() {
            sparqlManagerSvc.query.and.returnValue($q.when({}));
            searchSvc.submitSearch('', {}).then(function(response) {
                expect(response).toEqual({});
                expect(searchSvc.createQueryString).toHaveBeenCalledWith({});
                expect(sparqlManagerSvc.query).toHaveBeenCalledWith(query, '', discoverStateSvc.search.targetedId);
                expect(httpSvc.cancel).toHaveBeenCalledWith(discoverStateSvc.search.targetedId);
            }, function() {
                fail('Promise should have rejected');
            });
            scope.$apply();
        });
    });
    describe('should create a keyword query', function() {
        it('with and', function() {
            var result = searchSvc.createQueryString({keywords: ['test1', 'test2']});
            expect(result).toEqual('SELECT DISTINCT ?Entity (GROUP_CONCAT(DISTINCT ?Keyword; SEPARATOR = "<br>") AS ?Keywords) WHERE {\n  {\n    ?Entity ?p ?Keyword.\n    FILTER(CONTAINS(LCASE(?Keyword), LCASE("test1")))\n  }\n  {\n    ?Entity ?p ?Keyword.\n    FILTER(CONTAINS(LCASE(?Keyword), LCASE("test2")))\n  }\n}\nGROUP BY ?Entity');
        });
        it('with or', function() {
            var result = searchSvc.createQueryString({keywords: ['test1', 'test2'], isOrKeywords: true});
            expect(result).toEqual('SELECT DISTINCT ?Entity (GROUP_CONCAT(DISTINCT ?Keyword; SEPARATOR = "<br>") AS ?Keywords) WHERE {\n  {\n    ?Entity ?p ?Keyword.\n    FILTER(CONTAINS(LCASE(?Keyword), LCASE("test1")))\n  }\n  UNION\n  {\n    ?Entity ?p ?Keyword.\n    FILTER(CONTAINS(LCASE(?Keyword), LCASE("test2")))\n  }\n}\nGROUP BY ?Entity');
        });
    });
    describe('should create a type query', function() {
        it('with and', function() {
            var result = searchSvc.createQueryString({types: [{classIRI: 'http://matonto.org/1'}, {classIRI: 'http://matonto.org/2'}]});
            expect(result).toEqual('SELECT DISTINCT ?Entity (GROUP_CONCAT(DISTINCT ?Type; SEPARATOR = "<br>") AS ?Types) WHERE {\n  {\n    ?Entity <' + prefixes.rdf + 'type> <http://matonto.org/1>.\n    ?Entity ?p ?Type.\n  }\n  {\n    ?Entity <' + prefixes.rdf + 'type> <http://matonto.org/2>.\n    ?Entity ?p ?Type.\n  }\n}\nGROUP BY ?Entity');
        });
        it('with or', function() {
            var result = searchSvc.createQueryString({types: [{classIRI: 'http://matonto.org/1'}, {classIRI: 'http://matonto.org/2'}], isOrTypes: true});
            expect(result).toEqual('SELECT DISTINCT ?Entity (GROUP_CONCAT(DISTINCT ?Type; SEPARATOR = "<br>") AS ?Types) WHERE {\n  {\n    ?Entity <' + prefixes.rdf + 'type> <http://matonto.org/1>.\n    ?Entity ?p ?Type.\n  }\n  UNION\n  {\n    ?Entity <' + prefixes.rdf + 'type> <http://matonto.org/2>.\n    ?Entity ?p ?Type.\n  }\n}\nGROUP BY ?Entity');
        });
    });
    it('should create a filtered query', function() {
        var result = searchSvc.createQueryString({filters: [{type: 'bgp', triples: [{subject: '?s', predicate: '?p', object: '?var0'}]}]});
        expect(result).toEqual('SELECT DISTINCT ?Entity WHERE { { ?s ?p ?var0. } }\nGROUP BY ?Entity');
    });
    it('createExistenceQuery should create the correct query part', function() {
        expect(searchSvc.createExistenceQuery('predicate')).toEqual({
            type: 'group',
            patterns: [{
                type: 'bgp',
                triples: [{
                    subject: '?Entity',
                    predicate: 'predicate',
                    object: '?var0'
                }]
            }]
        });
    });
    it('createContainsQuery should create the correct query part', function() {
        expect(searchSvc.createContainsQuery('predicate', 'keyword')).toEqual({
            type: 'group',
            patterns: [{
                type: 'bgp',
                triples: [{
                    subject: '?Entity',
                    predicate: 'predicate',
                    object: '?var0'
                }]
            }, {
                type: 'bgp',
                triples: [{
                    subject: '?Subject',
                    predicate: '?Predicate',
                    object: '?o'
                }]
            }, {
                type: 'filter',
                expression: {
                    type: 'operation',
                    operator: 'contains',
                    args: [{
                        type: 'operation',
                        operator: 'lcase',
                        args: ['?var0']
                    }, {
                        type: 'operation',
                        operator: 'lcase',
                        args: ['\"keyword\"']
                    }]
                }
            }]
        });
    });
    it('createExactQuery should create the correct query part', function() {
        expect(searchSvc.createExactQuery('predicate', 'keyword', 'range')).toEqual({
            type: 'group',
            patterns: [{
                type: 'bgp',
                triples: [{
                    subject: '?Entity',
                    predicate: 'predicate',
                    object: '"keyword"^^range'
                }]
            }, {
                type: 'operation',
                operator: 'bind',
                args: [{
                    type: 'operation',
                    operator: 'as',
                    args: ['"keyword"^^range', '?var0']
                }]
            }]
        });
    });
    it('createRegexQuery should create the correct query part', function() {
        expect(searchSvc.createRegexQuery('predicate', '/[A-Z]/')).toEqual({
            type: 'group',
            patterns: [{
                type: 'bgp',
                triples: [{
                    subject: '?Entity',
                    predicate: 'predicate',
                    object: '?var0'
                }]
            }, {
                type: 'filter',
                expression: {
                    type: 'operation',
                    operator: 'regex',
                    args: ['?var0', '\"/[A-Z]/\"']
                }
            }]
        });
    });
    describe('createRangeQuery should create the correct query part when rangeConfig contains', function() {
        it('lessThan', function() {
            expect(searchSvc.createRangeQuery('predicate', 'range', {lessThan: 1})).toEqual({
                type: 'group',
                patterns: [{
                    type: 'bgp',
                    triples: [{
                        subject: '?Entity',
                        predicate: 'predicate',
                        object: '?var0'
                    }]
                }, {
                    type: 'bgp',
                    triples: [{
                        subject: '?Subject',
                        predicate: '?Predicate',
                        object: '?o'
                    }]
                }, {
                    type: 'filter',
                    expression: '?var0 < 1'
                }]
            });
            expect(util.getInputType).toHaveBeenCalledWith('range');
        });
        it('lessThanOrEqualTo', function() {
            expect(searchSvc.createRangeQuery('predicate', 'range', {lessThanOrEqualTo: 1})).toEqual({
                type: 'group',
                patterns: [{
                    type: 'bgp',
                    triples: [{
                        subject: '?Entity',
                        predicate: 'predicate',
                        object: '?var0'
                    }]
                }, {
                    type: 'bgp',
                    triples: [{
                        subject: '?Subject',
                        predicate: '?Predicate',
                        object: '?o'
                    }]
                }, {
                    type: 'filter',
                    expression: '?var0 <= 1'
                }]
            });
            expect(util.getInputType).toHaveBeenCalledWith('range');
        });
        it('greaterThan', function() {
            expect(searchSvc.createRangeQuery('predicate', 'range', {greaterThan: 1})).toEqual({
                type: 'group',
                patterns: [{
                    type: 'bgp',
                    triples: [{
                        subject: '?Entity',
                        predicate: 'predicate',
                        object: '?var0'
                    }]
                }, {
                    type: 'bgp',
                    triples: [{
                        subject: '?Subject',
                        predicate: '?Predicate',
                        object: '?o'
                    }]
                }, {
                    type: 'filter',
                    expression: '?var0 > 1'
                }]
            });
            expect(util.getInputType).toHaveBeenCalledWith('range');
        });
        it('greaterThanOrEqualTo', function() {
            expect(searchSvc.createRangeQuery('predicate', 'range', {greaterThanOrEqualTo: 1})).toEqual({
                type: 'group',
                patterns: [{
                    type: 'bgp',
                    triples: [{
                        subject: '?Entity',
                        predicate: 'predicate',
                        object: '?var0'
                    }]
                }, {
                    type: 'bgp',
                    triples: [{
                        subject: '?Subject',
                        predicate: '?Predicate',
                        object: '?o'
                    }]
                }, {
                    type: 'filter',
                    expression: '?var0 >= 1'
                }]
            });
            expect(util.getInputType).toHaveBeenCalledWith('range');
        });
        it('lessThanOrEqualTo and greaterThanOrEqualTo', function() {
            expect(searchSvc.createRangeQuery('predicate', 'range', {lessThanOrEqualTo: 1, greaterThanOrEqualTo: 0})).toEqual({
                type: 'group',
                patterns: [{
                    type: 'bgp',
                    triples: [{
                        subject: '?Entity',
                        predicate: 'predicate',
                        object: '?var0'
                    }]
                }, {
                    type: 'bgp',
                    triples: [{
                        subject: '?Subject',
                        predicate: '?Predicate',
                        object: '?o'
                    }]
                }, {
                    type: 'filter',
                    expression: '?var0 <= 1'
                }, {
                    type: 'filter',
                    expression: '?var0 >= 0'
                }]
            });
            expect(util.getInputType).toHaveBeenCalledWith('range');
        });
    });
    it('createRangeQuery should create the correct query part when input type is datetime-local', function() {
        util.getInputType.and.returnValue('datetime-local');
        expect(searchSvc.createRangeQuery('predicate', 'range', {greaterThanOrEqualTo: 1})).toEqual({
            type: 'group',
            patterns: [{
                type: 'bgp',
                triples: [{
                    subject: '?Subject',
                    predicate: 'predicate',
                    object: '?o'
                }]
            }, {
                type: 'bgp',
                triples: [{
                    subject: '?Subject',
                    predicate: '?Predicate',
                    object: '?o'
                }]
            }, {
                type: 'filter',
                expression: '?o >= 1^^<' + prefixes.xsd + 'dateTime>'
            }]
        });
        expect(util.getInputType).toHaveBeenCalledWith('range');
    });
    describe('createBooleanQuery should create the correct query part when value is', function() {
        it('true', function() {
            expect(searchSvc.createBooleanQuery('predicate', true)).toEqual({
                type: 'group',
                patterns: [{
                    type: 'bgp',
                    triples: [{
                        subject: '?Entity',
                        predicate: 'predicate',
                        object: '?var0'
                    }]
                }, {
                    type: 'filter',
                    expression: {
                        type: 'operation',
                        operator: 'in',
                        args: ['?var0', ['"true"^^' + prefixes.xsd + 'boolean', '"1"^^' + prefixes.xsd + 'boolean']]
                    }
                }]
            });
        });
        it('false', function() {
            expect(searchSvc.createBooleanQuery('predicate', false)).toEqual({
                type: 'group',
                patterns: [{
                    type: 'bgp',
                    triples: [{
                        subject: '?Entity',
                        predicate: 'predicate',
                        object: '?var0'
                    }]
                }, {
                    type: 'filter',
                    expression: {
                        type: 'operation',
                        operator: 'in',
                        args: ['?var0', ['"false"^^' + prefixes.xsd + 'boolean', '"0"^^' + prefixes.xsd + 'boolean']]
                    }
                }]
            });
        });
    });
});
