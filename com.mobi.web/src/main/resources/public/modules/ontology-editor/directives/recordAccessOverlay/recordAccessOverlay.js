/*-
 * #%L
 * com.mobi.web
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
        /**
         * @ngdoc overview
         * @name recordAccessOverlay
         *
         * @description
         * TODO
         */
        .module('recordAccessOverlay', [])
        /** TODO
         */
        .directive('recordAccessOverlay', recordAccessOverlay);

        recordAccessOverlay.$inject = ['utilService', 'userManagerService', 'recordPermissionsManagerService', 'prefixes']

        function recordAccessOverlay(utilService, userManagerService, recordPermissionsManagerService, prefixes) {
            return {
                restrict: 'E',
                controllerAs: 'dvm',
                replace: true,
                scope: {

                },
                bindToController: {
                    overlayFlag: '=',
                    resource: '<',
                    ruleId: '@'
                },
                controller: function() {
                    var dvm = this;
                    var util = utilService;
                    var um = userManagerService;
                    var rp = recordPermissionsManagerService;
                    var recordPolicyId;

                    dvm.policy = '';
                    dvm.ruleTitle = '';

                    dvm.getPolicy = function(resourceId) {
                        recordPolicyId = 'http://mobi.com/policies/record/' + encodeURIComponent(resourceId);
                        rp.getRecordPolicy(recordPolicyId)
                            .then(result => {
                                dvm.policy = {
                                        policy: result,
                                        id: recordPolicyId,
                                        changed: false,
                                        everyone: false,
                                        users: [],
                                        groups: [],
                                        selectedUsers: [],
                                        selectedGroups: [],
                                        userSearchText: '',
                                        groupSearchText: '',
                                        selectedUser: undefined,
                                        selectedGroup: undefined
                                    };
                                setInfo(dvm.policy);
                                }, util.createErrorToast);
                    }

                    function setInfo(item) {
                        var ruleInfo = item.policy[dvm.ruleId];
                        if (ruleInfo.everyone) {
                            item.everyone = true;
                        } else {
                            item.selectedUsers = sortUsers(_.chain(ruleInfo.users)
                                .map(obj => _.find(um.users, {iri: obj}))
                                .reject(_.isNull)
                                .value());
                            item.selectedGroups = sortGroups(_.chain(ruleInfo.groups)
                                .map(obj => _.find(um.groups, {iri: obj}))
                                .reject(_.isNull)
                                .value());
                        }
                        item.users = sortUsers(_.difference(um.users, item.selectedUsers));
                        item.groups = sortGroups(_.difference(um.groups, item.selectedGroups));
                    }

                    dvm.cancel = function() {
                        dvm.overlayFlag = false;
                    }

                    dvm.save = function() {
                        dvm.overlayFlag = false;
                        dvm.policy.policy[dvm.ruleId] = {
                            everyone: dvm.policy.everyone,
                            users: _.map(dvm.policy.selectedUsers, user => user.iri),
                            groups: _.map(dvm.policy.selectedGroups, user => user.iri),
                        }
                        rp.updateRecordPolicy(recordPolicyId, dvm.policy.policy)
                            .then(() => {
                                dvm.policy.changed = false;
                                util.createSuccessToast('Permissions updated')
                            }, utilService.createErrorToast);
                    }

                    function sortUsers(users) {
                        return _.sortBy(users, 'username');
                    }

                    function sortGroups(groups) {
                        return _.sortBy(groups, 'title');
                    }

                    function getRuleTitle() {
                        switch (dvm.ruleId) {
                            case 'urn:read':
                                dvm.ruleTitle = "View Record";
                                break;
                            case 'urn:delete':
                                dvm.ruleTitle = "Delete Record";
                                break;
                            case 'urn:update': //TODO: MANAGE A RECORD?
                                break;
                            case 'urn:modify':
                                dvm.ruleTitle = 'Modify Record';
                                break;
                            case 'urn:modifyMaster':
                                dvm.ruleTitle = 'Modify Master Branch';
                                break;
                        }
                    }

                    dvm.getPolicy(dvm.resource);
                    getRuleTitle();
                },
                templateUrl: 'modules/ontology-editor/directives/recordAccessOverlay/recordAccessOverlay.html'
            }
        }
})();
