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
        .module('customHeader', [])
        .directive('customHeader', customHeader);

    customHeader.$inject = ['loginManagerService', 'catalogStateService', 'catalogManagerService', 'ontologyStateService', 'ontologyManagerService', 'mapperStateService', 'mappingManagerService', 'delimitedManagerService', 'sparqlManagerService', 'userStateService', 'userManagerService'];

    function customHeader(loginManagerService, catalogStateService, catalogManagerService, ontologyStateService, ontologyManagerService, mapperStateService, mappingManagerService, delimitedManagerService, sparqlManagerService, userStateService, userManagerService) {
        return {
            restrict: 'E',
            replace: true,
            scope: {
                pageTitle: '<'
            },
            controllerAs: 'dvm',
            controller: function() {
                var dvm = this;

                dvm.um = userManagerService;
                dvm.lm = loginManagerService;

                dvm.logout = function() {
                    catalogStateService.reset();
                    ontologyStateService.reset();
                    ontologyManagerService.reset();
                    mapperStateService.initialize();
                    mapperStateService.resetEdit();
                    mappingManagerService.reset();
                    delimitedManagerService.reset();
                    sparqlManagerService.reset();
                    loginManagerService.logout();
                    userStateService.reset();
                    userManagerService.reset();
                }
            },
            templateUrl: 'directives/customHeader/customHeader.html'
        }
    }

})();
