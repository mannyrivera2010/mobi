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

import { includes, filter, remove, concat, set, get, find, sortBy } from 'lodash';

import './userAccessControls.component.scss';

const template = require('./userAccessControls.component.html');

/**
 * @ngdoc component
 * @name shared.component:userAccessControls
 * @requires shared.service:policyManagerService
 * @requires shared.service:utilService
 * @requires shared.service:loginManagerService
 * @requires shared.service:prefixes
 *
 * @description
 * `userAccessControls` is a component that creates a Bootstrap `row` div with a single column containing a
 * {@link shared.component:block block} for viewing and updating permissions on a Rule of a Policy. The Rule is
 * represented by the provided `item`. A `ruleTitle` can be provided to provide context on the Rule. The IRI of the
 * Rule is set with `ruleId`. The behavior when the Rule is updated is controlled by the provided `updateItem`
 * function which is expected to update the value of `item`. The Rule should be in the format:
 * ```
 * {
 *     everyone: false,
 *     users: [],
 *     selectedUsers: [],
 *     userSearchText: '',
 *     selectedUser: {},
 *     groups: [],
 *     selectedGroups: [],
 *     groupSearchText: '',
 *     selectedGroup: {}
 * }
 * ```
 * 
 * @param {Object} item A representation of a Rule in a Policy
 * @param {string} ruleTitle A string representing the display title of the Rule
 * @param {string} ruleId The IRI id of the Rule
 * @param {Function} updateItem A function to update the Rule. Should update teh value of `item`. Expects an
 * argument called `item`.
 */
const userAccessControlsComponent = {
    template,
    bindings: {
        item: '<',
        ruleTitle: '<',
        ruleId: '<',
        updateItem: '&'
    },
    controllerAs: 'dvm',
    controller: userAccessControlsComponentCtrl
};

userAccessControlsComponentCtrl.$inject = ['$scope', 'policyManagerService', 'loginManagerService', 'prefixes'];

function userAccessControlsComponentCtrl($scope, policyManagerService, loginManagerService, prefixes) {
    var dvm = this;
    dvm.lm = loginManagerService;
    var pm = policyManagerService;
    var groupAttributeId = 'http://mobi.com/policy/prop-path(' + encodeURIComponent('^<' + prefixes.foaf + 'member' + '>') + ')';
    var userRole = 'http://mobi.com/roles/user';

    dvm.filterUsers = function(users, searchText) {
        return filter(users, user => includes(user.username.toLowerCase(), searchText.toLowerCase()));
    }
    dvm.filterGroups = function(groups, searchText) {
        return filter(groups, group => includes(group.title.toLowerCase(), searchText.toLowerCase()));
    }
    dvm.addUser = function(user) {
        if (user) {
            dvm.item.selectedUsers.push(user);
            dvm.item.selectedUsers = sortUsers(dvm.item.selectedUsers);
            remove(dvm.item.users, user);
            dvm.item.selectedUser = undefined;
            dvm.item.userSearchText = '';
            $scope.$$childTail.userSearchText = '';
            $scope.$$childTail.selectedUser = undefined;
            if (document.activeElement instanceof HTMLElement) {
                document.activeElement.blur();
            }
            // document.activeElement.blur();
            if (!dvm.ruleId) {
                addUserMatch(user.iri, dvm.item.policy);
            }
            dvm.updateItem({item: dvm.item});
        }
    }
    dvm.removeUser = function(user) {
        dvm.item.users.push(user);
        dvm.item.users = sortUsers(dvm.item.users);
        remove(dvm.item.selectedUsers, user);
        if (!dvm.ruleId) {
            removeMatch(user.iri, dvm.item.policy);
        }
        dvm.updateItem({item: dvm.item});
    }
    dvm.addGroup = function(group) {
        if (group) {
            dvm.item.selectedGroups.push(group);
            dvm.item.selectedGroups = sortGroups(dvm.item.selectedGroups);
            remove(dvm.item.groups, group);
            dvm.item.selectedGroup = undefined;
            dvm.item.groupSearchText = '';
            $scope.$$childTail.groupSearchText = '';
            $scope.$$childTail.selectedGroup = undefined;
            if (document.activeElement instanceof HTMLElement) {
                document.activeElement.blur();
            }
            // document.activeElement.blur();
            if (!dvm.ruleId) {
                addGroupMatch(group.iri, dvm.item.policy);
            }
            dvm.updateItem({item: dvm.item});
        }
    }
    dvm.removeGroup = function(group) {
        dvm.item.groups.push(group);
        dvm.item.groups = sortGroups(dvm.item.groups);
        remove(dvm.item.selectedGroups, group);
        if (!dvm.ruleId) {
            removeMatch(group.iri, dvm.item.policy);
        }
        dvm.updateItem({item: dvm.item});
    }
    dvm.toggleEveryone = function() {
        if (dvm.item.everyone) {
            if (!dvm.ruleId) {
                set(dvm.item.policy, 'Rule[0].Target.AnyOf[0].AllOf', []);
                addMatch(userRole, prefixes.user + 'hasUserRole', dvm.item.policy);
            }
            dvm.item.users = sortUsers(concat(dvm.item.users, dvm.item.selectedUsers));
            dvm.item.selectedUsers = [];
            dvm.item.groups = sortGroups(concat(dvm.item.groups, dvm.item.selectedGroups));
            dvm.item.selectedGroups = [];
        } else {
            if (!dvm.ruleId) {
                removeMatch(userRole, dvm.item.policy);
            }
            dvm.addUser(find(dvm.item.users, {iri: dvm.lm.currentUserIRI}));
        }
        dvm.updateItem({item: dvm.item});
    }

    function removeMatch(value, policy) {
        remove(get(policy, 'Rule[0].Target.AnyOf[0].AllOf', []), ['Match[0].AttributeValue.content[0]', value]);
    }
    function addUserMatch(value, policy) {
        addMatch(value, pm.subjectId, policy);
    }
    function addGroupMatch(value, policy) {
        addMatch(value, groupAttributeId, policy);
    }
    function addMatch(value, id, policy) {
        var newMatch = {
            Match: [{
                AttributeValue: {
                    content: [value],
                    otherAttributes: {},
                    DataType: prefixes.xsd + 'string'
                },
                AttributeDesignator: {
                    Category: pm.subjectCategory,
                    AttributeId: id,
                    DataType: prefixes.xsd + 'string',
                    MustBePresent: true
                },
                MatchId: pm.stringEqual
            }]
        };
        get(policy, 'Rule[0].Target.AnyOf[0].AllOf', []).push(newMatch);
    }
    function sortUsers(users) {
        return sortBy(users, 'username');
    }
    function sortGroups(groups) {
        return sortBy(groups, 'title');
    }
}

export default userAccessControlsComponent;