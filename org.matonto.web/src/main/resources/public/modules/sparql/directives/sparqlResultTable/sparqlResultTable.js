/*-
 * #%L
 * org.matonto.web
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2016 iNovex Information Systems, Inc.
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
(function() {
    'use strict';

    angular
        .module('sparqlResultTable', ['sparqlManager'])
        .directive('sparqlResultTable', sparqlResultTable);

        sparqlResultTable.$inject = ['$window', '$timeout'];

        function sparqlResultTable($window, $timeout) {
            return {
                restrict: 'E',
                templateUrl: 'modules/sparql/directives/sparqlResultTable/sparqlResultTable.html',
                replace: true,
                scope: {},
                controllerAs: 'dvm',
                controller: ['sparqlManagerService', function(sparqlManagerService) {
                    var dvm = this;

                    dvm.sparql = sparqlManagerService;

                    dvm.getPage = function(direction) {
                        if(direction === 'next') {
                            dvm.sparql.currentPage += 1;
                            sparqlManagerService.getResults(dvm.sparql.data.paginatedResults.links.base + dvm.sparql.data.paginatedResults.links.next);
                        } else {
                            dvm.sparql.currentPage -= 1;
                            sparqlManagerService.getResults(dvm.sparql.data.paginatedResults.links.base + dvm.sparql.data.paginatedResults.links.prev);
                        }
                    }
                }],
                link: function(scope, element) {
                    var resize = function() {
                        var totalHeight = document.getElementsByClassName('sparql')[0].clientHeight;
                        var topHeight = document.getElementsByClassName('sparql-editor')[0].clientHeight;
                        var pagingHeight = element[0].querySelector('.paging-details').clientHeight;
                        element.css({'height': (totalHeight - topHeight) + 'px', 'padding-bottom': (pagingHeight + 10) + 'px'});
                    }

                    angular.element($window).bind('resize', function() {
                        resize();
                    });

                    $timeout(function() {
                        resize();
                    });

                    element.on('$destroy', function() {
                        angular.element($window).off('resize');
                    });
                }
            }
        }
})();
