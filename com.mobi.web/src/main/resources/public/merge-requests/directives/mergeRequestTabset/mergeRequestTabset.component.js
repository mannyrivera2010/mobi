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
(function() {
    'use strict';

    angular
        /**
         * @ngdoc overview
         * @name mergeRequestTabset
         *
         * @description
         * The `mergeRequestTabset` module only provides the `mergeRequestTabset` component
         * which creates a {@link shared.directive:materialTabset} with tabs related to the discussion and
         * difference of a Merge Request.
         */
        .module('mergeRequestTabset', [])
        /**
         * @ngdoc component
         * @name mergeRequestTabset.component:mergeRequestTabset
         *
         * @description
         * `mergeRequestTabset` is a component which creates a div containing a
         * {@link shared.directive:materialTabset tabset} with tabs for the
         * {@link mergeRequestDiscussion.directive:mergeRequestDiscussion},
         * {@link shared.directive:commitChangesDisplay changes}, and
         * {@link shared.directive:commitHistoryTable commits} of the provided Merge Request.
         *
         * @param {Object} request An object representing a Merge Request
         */
        .component('mergeRequestTabset', {
            bindings: {
                request: '=',
            },
            controllerAs: 'dvm',
            controller: MergeRequestTabsetController,
            templateUrl: 'merge-requests/directives/mergeRequestTabset/mergeRequestTabset.component.html',
        });

    function MergeRequestTabsetController() {
        var dvm = this;
        dvm.tabs = {
            discussion: true,
            changes: false,
            commits: false
        };
    }
})();