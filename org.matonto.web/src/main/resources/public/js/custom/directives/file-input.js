(function() {
    'use strict';

    angular
        .module('file-input', [])
        .directive('fileInput', fileInput);

    fileInput.$inject = ['$parse'];

        function fileInput($parse) {
            var directive = {
                    restrict: 'EA',
                    template: '<input type="file" />',
                    replace: true,
                    link: link
                };
            return directive;

            function link(scope, element, attrs) {
                var modelGet = $parse(attrs.fileInput),
                    modelSet = modelGet.assign,
                    onChange = $parse(attrs.onChange),
                    updateModel = function() {
                        scope.$apply(function() {
                            modelSet(scope, element[0].files[0]);
                            onChange(scope);
                        });
                    };
                // binds the change event to the element
                element.bind('change', updateModel);
            }
        }
})();
