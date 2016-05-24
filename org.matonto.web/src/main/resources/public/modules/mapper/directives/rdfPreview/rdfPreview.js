(function() {
    'use strict';

    angular
        .module('rdfPreview', [])
        .directive('rdfPreview', rdfPreview)
        .directive('formatRdf', formatRdf);

        formatRdf.$inject = ['$filter'];
        rdfPreview.$inject = ['$window'];

        function formatRdf($filter) {
            return {
                restrict: 'A',
                require: 'ngModel',
                link: function(scope, element, attrs, modelCtrl) {
                    var formatJSON = function(data) {
                        var formatted = (typeof data === 'object') ? $filter('json')(data, 4) : (data || '');
                        return formatted;
                    }
                        
                    modelCtrl.$formatters.push(formatJSON);
                 }
               };
        }

        function rdfPreview($window) {
            return {
                restrict: 'E',
                controllerAs: 'dvm',
                replace: true,
                scope: {
                    preview: '=',
                    createPreview: '&'
                },
                controller: function() {
                    var dvm = this;
                    dvm.visible = true;
                    dvm.options = [
                        {
                            name: 'JSON-LD',
                            value: 'jsonld'
                        },
                        {
                            name: 'Turtle',
                            value: 'turtle'
                        },
                        {
                            name: 'RDF/XML',
                            value: 'rdf/xml'
                        }
                    ];
                },
                templateUrl: 'modules/mapper/directives/rdfPreview/rdfPreview.html'
            }
        }
})();
