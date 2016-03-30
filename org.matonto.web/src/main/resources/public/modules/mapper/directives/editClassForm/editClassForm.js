(function() {
    'use strict';

    angular
        .module('editClassForm', ['prefixes', 'mappingManager', 'ontologyManager'])
        .directive('editClassForm', editClassForm);

        editClassForm.$inject = ['prefixes', 'mappingManagerService', 'ontologyManagerService'];

        function editClassForm(prefixes, mappingManagerService, ontologyManagerService) {
            return {
                restrict: 'E',
                controllerAs: 'dvm',
                replace: true,
                scope: {
                    props: '=',
                    isLastClass: '=',
                    setBase: '&',
                    clickDelete: '&',
                    openProp: '&'
                },
                bindToController: {
                    mapping: '=',
                    classMappingId: '='
                },
                controller: function($scope) {
                    var dvm = this;

                    dvm.openProperty = function(propId) {
                        $scope.openProp({propId: propId});
                    }
                    dvm.getTitle = function() {
                        var ontologyId = mappingManagerService.getSourceOntologyId(dvm.mapping);
                        var classId = mappingManagerService.getClassIdByMappingId(dvm.mapping, dvm.classMappingId);
                        return ontologyManagerService.getEntityName(ontologyManagerService.getClass(ontologyId, classId));
                    }
                },
                templateUrl: 'modules/mapper/directives/editClassForm/editClassForm.html'
            }
        }
})();
