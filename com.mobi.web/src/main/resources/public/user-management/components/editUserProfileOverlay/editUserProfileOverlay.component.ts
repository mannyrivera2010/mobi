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
import { find } from 'lodash';

const template = require('./editUserProfileOverlay.component.html');

/**
 * @ngdoc component
 * @name user-management.component:editUserProfileOverlay
 * @requires shared.service:userManagerService
 * @requires shared.service:userStateService
 * @requires shared.service:prefixes
 *
 * @description
 * `editUserProfileOverlay` is a component that creates content for a modal with a form to change the
 * {@link shared.service:userStateService#selectedUser selected user's} profile information in Mobi. The
 * form contains fields to edit the user's first name, last name, and email. Meant to be used in conjunction
 * with the {@link shared.service:modalService}.
 *
 * @param {Function} close A function that closes the modal
 * @param {Function} dismiss A function that dismisses the modal
 */
const editUserProfileOverlayComponent = {
    template,
    bindings: {
        close: '&',
        dismiss: '&'
    },
    controllerAs: 'dvm',
    controller: editUserProfileOverlayComponentCtrl,
};

editUserProfileOverlayComponentCtrl.$inject = ['userStateService', 'userManagerService', 'prefixes'];

function editUserProfileOverlayComponentCtrl(userStateService, userManagerService, prefixes) {
    var dvm = this;
    dvm.state = userStateService;
    dvm.um = userManagerService;
    dvm.newUser = {};

    dvm.$onInit = function() {
        dvm.newUser = angular.copy(dvm.state.selectedUser);
    }
    dvm.set = function() {
        dvm.newUser.jsonld[prefixes.foaf + 'firstName'] = [{'@value': dvm.newUser.firstName}];
        dvm.newUser.jsonld[prefixes.foaf + 'lastName'] = [{'@value': dvm.newUser.lastName}];
        dvm.newUser.jsonld[prefixes.foaf + 'mbox'] = [{'@id': dvm.newUser.email}];
        dvm.um.updateUser(dvm.state.selectedUser.username, dvm.newUser).then(response => {
            dvm.errorMessage = '';
            dvm.state.selectedUser = find(dvm.um.users, {username: dvm.newUser.username});
            dvm.close();
        }, error => dvm.errorMessage = error);
    }
    dvm.cancel = function() {
        dvm.dismiss();
    }
}

export default editUserProfileOverlayComponent;