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
import { get } from 'lodash';

import './mergeRequestTabset.component.scss';

const template = require('./mergeRequestTabset.component.html');

/**
 * @ngdoc component
 * @name merge-requests.component:mergeRequestTabset
 * @requires shared.service:mergeRequestsStateService
 *
 * @description
 * `mergeRequestTabset` is a component which creates a div containing a
 * {@link shared.component:materialTabset tabset} with tabs for the
 * {@link merge-requests.component:mergeRequestDiscussion},
 * {@link shared.component:commitChangesDisplay changes}, and
 * {@link shared.component:commitHistoryTable commits} of the provided Merge Request.
 *
 * @param {Object} request An object representing a Merge Request
 * @param {Function} updateRequest A function to be called when the value of `request` changes. Expects an argument
 * called `value` and should update the value of `request`.
 */
const mergeRequestTabsetComponent = {
    template,
    bindings: {
        request: '<',
        updateRequest: '&'
    },
    controllerAs: 'dvm',
    controller: mergeRequestTabsetComponentCtrl
};

mergeRequestTabsetComponentCtrl.$inject = ['mergeRequestsStateService'];

function mergeRequestTabsetComponentCtrl(mergeRequestsStateService) {
    var dvm = this;
    dvm.state = mergeRequestsStateService;

    dvm.tabs = {
        discussion: true,
        changes: false,
        commits: false
    };
}

export default mergeRequestTabsetComponent;