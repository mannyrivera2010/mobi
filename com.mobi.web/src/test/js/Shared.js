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
export function mockComponent(moduleName, componentName) {
    angular.mock.module(moduleName, function($provide) {
        $provide.factory(componentName + 'Directive', function() { return {}; });
    });
}
  
export function createQueryString(obj) {
    var queryString = '';
    var keys = Object.keys(obj);
    keys.forEach(function(key) {
        if (keys.indexOf(key) === 0) {
            queryString = queryString.concat('?');
        }
        queryString = queryString.concat(key + '=' + obj[key]);
        if (keys.indexOf(key) !== keys.length - 1) {
            queryString = queryString.concat('&');
        }
    });
    return queryString;
}

export function injectChromaConstant() {
    angular.mock.module(function($provide) {
        $provide.constant('chroma', {
            scale: jasmine.createSpy('scale').and.returnValue({
                colors: jasmine.createSpy('colors').and.callFake(function(num) {
                    return _.fill(Array(num), '');
                })
            }),
            brewer: {
                Set1: ['']
            }
        });
    });
}

export function injectSparqljsConstant() {
    angular.mock.module(function($provide) {
        $provide.constant('sparqljs', {
            Generator: jasmine.createSpy('Generator').and.returnValue({
                stringify: jasmine.createSpy('stringify').and.returnValue('')
            })
        })
    });
}

export function injectRegexConstant() {
    angular.mock.module(function($provide) {
        $provide.constant('REGEX', {
            'IRI': /[a-zA-Z]/,
            'LOCALNAME': /[a-zA-Z]+/,
            'FILENAME': /[a-zA-Z]/,
            'UUID': /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/,
            'DATETIME': /[a-zA-Z]/,
            'INTEGER': /[a-zA-Z]/,
            'DECIMAL': /[a-zA-Z]/,
            'ANYTHING': /[a-zA-Z]/
        });
    });
}

export function injectRestPathConstant() {
    angular.mock.module(function($provide) {
        $provide.constant('REST_PREFIX', '/mobirest/');
    });
}

export function injectAdminUserIRIConstant() {
    angular.mock.module(function($provide) {
        $provide.constant('ADMIN_USER_IRI', 'http://mobi.com/users/d033e22ae348aeb5660fc2140aec35850c4da997');
    });
}

export function injectIndentConstant() {
    angular.mock.module(function($provide) {
        $provide.constant('INDENT', 1);
    });
}

export function injectShowdownConstant() {
    angular.mock.module(function($provide) {
        $provide.constant('showdown', {
            Converter: jasmine.createSpy('Converter').and.returnValue({
                setFlavor: jasmine.createSpy('setFlavor'),
                makeHtml: jasmine.createSpy('makeHtml').and.returnValue('')
            })
        });
    });
}

export function injectBeautifyFilter() {
    angular.mock.module(function($provide) {
        $provide.value('beautifyFilter', jasmine.createSpy('beautifyFilter').and.callFake(_.identity));
    });
}

export function injectBranchesToDisplayFilter() {
    angular.mock.module(function($provide) {
        $provide.value('branchesToDisplayFilter', jasmine.createSpy('branchesToDisplay').and.callFake(_.identity));
    });
}

export function injectSplitIRIFilter() {
    angular.mock.module(function($provide) {
        $provide.value('splitIRIFilter', jasmine.createSpy('splitIRIFilter').and.callFake(function(iri) {
            return {
                begin: '',
                then: '',
                end: ''
            }
        }));
    });
}

export function injectTrustedFilter() {
    angular.mock.module(function($provide) {
        $provide.value('trustedFilter', jasmine.createSpy('trustedFilter'));
    });
}

export function injectHighlightFilter() {
    angular.mock.module(function($provide) {
        $provide.value('highlightFilter', jasmine.createSpy('highlightFilter'));
    });
}

export function injectCamelCaseFilter() {
    angular.mock.module(function($provide) {
        $provide.value('camelCaseFilter', jasmine.createSpy('camelCaseFilter').and.callFake(function(str) {
            return str;
        }));
    });
}

export function injectShowPropertiesFilter() {
    angular.mock.module(function($provide) {
        var properties = ['prop1', 'prop2'];
        $provide.value('showPropertiesFilter', jasmine.createSpy('showPropertiesFilter').and.callFake(function(entity, arr) {
            return entity ? properties : [];
        }));
    });
}

export function injectRemoveIriFromArrayFilter() {
    angular.mock.module(function($provide) {
        $provide.value('removeIriFromArrayFilter', jasmine.createSpy('removeIriFromArrayFilter').and.callFake(_.identity));
    });
}

export function injectPrefixationFilter() {
    angular.mock.module(function($provide) {
        $provide.value('prefixationFilter', jasmine.createSpy('prefixationFilter').and.callFake(_.identity));
    });
}

export function injectInArrayFilter() {
    angular.mock.module(function($provide) {
        $provide.value('inArrayFilter', jasmine.createSpy('inArrayFilter').and.callFake(_.identity));
    });
}

export function injectUsernameSearchFilter() {
    angular.mock.module(function($provide) {
        $provide.value('usernameSearchFilter', jasmine.createSpy('usernameSearchFilter').and.callFake(_.identity));
    });
}

export function injectUniqueKeyFilter() {
    angular.mock.module(function($provide) {
        $provide.value('uniqueKeyFilter', jasmine.createSpy('uniqueKeyFilter').and.callFake(_.identity));
    });
}

export function mockStateManager() {
    angular.mock.module(function($provide, $qProvider) {
        $qProvider.errorOnUnhandledRejections(false);
        $provide.service('stateManagerService', function($q) {
            this.states = [];
            this.initialize = jasmine.createSpy('initialize');
            this.getStates = jasmine.createSpy('getStates').and.returnValue($q.when());
            this.createState = jasmine.createSpy('createStates').and.returnValue($q.when());
            this.getState = jasmine.createSpy('getState').and.returnValue($q.when());
            this.updateState = jasmine.createSpy('updateState').and.returnValue($q.when());
            this.deleteState = jasmine.createSpy('deleteState').and.returnValue($q.when());
        });
    });
}

export function mockOntologyManager() {
    angular.mock.module(function($provide, $qProvider) {
        $qProvider.errorOnUnhandledRejections(false);
        $provide.service('ontologyManagerService', function($q) {
            this.ontologyRecords = [];
            this.entityNameProps = [];
            this.reset = jasmine.createSpy('reset');
            this.initialize = jasmine.createSpy('initialize');
            this.uploadFile = jasmine.createSpy('uploadFile').and.returnValue($q.when({}));
            this.uploadJson = jasmine.createSpy('uploadJson').and.returnValue($q.when({}));
            this.getOntology = jasmine.createSpy('getOntology').and.returnValue($q.when({}));
            this.getVocabularyStuff = jasmine.createSpy('getVocabularyStuff').and.returnValue($q.when({}));
            this.getOntologyStuff = jasmine.createSpy('getOntologyStuff').and.returnValue($q.when({}));
            this.getIris = jasmine.createSpy('getIris').and.returnValue($q.when({}));
            this.getImportedIris = jasmine.createSpy('getImportedIris').and.returnValue($q.when([]));
            this.getClassHierarchies = jasmine.createSpy('getClassHierarchies').and.returnValue($q.when({}));
            this.getClassesWithIndividuals = jasmine.createSpy('getClassesWithIndividuals').and.returnValue($q.when({}));
            this.getDataPropertyHierarchies = jasmine.createSpy('getDataPropertyHierarchies').and.returnValue($q.when({}));
            this.getObjectPropertyHierarchies = jasmine.createSpy('getObjectPropertyHierarchies').and.returnValue($q.when({}));
            this.getConceptHierarchies = jasmine.createSpy('getConceptHierarchies').and.returnValue($q.when({}));
            this.getConceptSchemeHierarchies = jasmine.createSpy('getConceptSchemeHierarchies').and.returnValue($q.when({}));
            this.getImportedOntologies = jasmine.createSpy('getImportedOntologies').and.returnValue($q.when([]));
            this.getEntityUsages = jasmine.createSpy('getEntityUsages').and.returnValue($q.when({}));
            this.getOntologyEntityNames = jasmine.createSpy('getOntologyEntityNames').and.returnValue($q.when({}));
            this.getSearchResults = jasmine.createSpy('getSearchResults');
            this.getQueryResults = jasmine.createSpy('getQueryResults').and.returnValue($q.when({}));
            this.getEntityAndBlankNodes = jasmine.createSpy('getEntityAndBlankNodes').and.returnValue($q.when([]));
            this.isDeprecated = jasmine.createSpy('isDeprecated');
            this.isOntology = jasmine.createSpy('isOntology');
            this.isOntologyRecord = jasmine.createSpy('isOntologyRecord');
            this.hasOntologyEntity = jasmine.createSpy('hasOntologyEntity');
            this.getOntologyEntity = jasmine.createSpy('getOntologyEntity').and.returnValue({});
            this.getOntologyIRI = jasmine.createSpy('getOntologyIRI').and.returnValue('');
            this.isDatatype = jasmine.createSpy('isDatatype');
            this.isClass = jasmine.createSpy('isClass');
            this.hasClasses = jasmine.createSpy('hasClasses').and.returnValue(true);
            this.getClasses = jasmine.createSpy('getClasses').and.returnValue([]);
            this.getClassIRIs = jasmine.createSpy('getClassIRIs').and.returnValue([]);
            this.getClassProperties = jasmine.createSpy('getClassProperties').and.returnValue([]);
            this.getClassPropertyIRIs = jasmine.createSpy('getClassPropertyIRIs').and.returnValue([]);
            this.getClassProperty = jasmine.createSpy('getClassProperty').and.returnValue({});
            this.getOntologyClasses = jasmine.createSpy('getOntologyClasses').and.returnValue($q.when([]));

            this.getOntologyById = jasmine.createSpy('getOntologyById').and.returnValue([]);
            this.isObjectProperty = jasmine.createSpy('isObjectProperty');
            this.hasObjectProperties = jasmine.createSpy('hasObjectProperties').and.returnValue(true);
            this.getObjectProperties = jasmine.createSpy('getObjectProperties').and.returnValue([]);
            this.getObjectPropertyIRIs = jasmine.createSpy('getObjectPropertyIRIs').and.returnValue([]);
            this.isDataTypeProperty = jasmine.createSpy('isDataTypeProperty');
            this.hasDataTypeProperties = jasmine.createSpy('hasDataTypeProperties').and.returnValue(true);
            this.getDataTypeProperties = jasmine.createSpy('getDataTypeProperties').and.returnValue([]);
            this.getDataTypePropertyIRIs = jasmine.createSpy('getDataTypePropertyIRIs').and.returnValue([]);
            this.isProperty = jasmine.createSpy('isProperty').and.returnValue(true);
            this.hasNoDomainProperties = jasmine.createSpy('hasNoDomainProperties').and.returnValue(true);
            this.getNoDomainProperties = jasmine.createSpy('getNoDomainProperties').and.returnValue([]);
            this.getNoDomainPropertyIRIs = jasmine.createSpy('getNoDomainPropertyIRIs').and.returnValue([]);
            this.isAnnotation = jasmine.createSpy('isAnnotation');
            this.hasAnnotations = jasmine.createSpy('hasAnnotations').and.returnValue(true);
            this.getAnnotations = jasmine.createSpy('getAnnotations').and.returnValue([]);
            this.getAnnotationIRIs = jasmine.createSpy('getAnnotationIRIs').and.returnValue([]);
            this.isIndividual = jasmine.createSpy('isIndividual').and.returnValue(true);
            this.hasIndividuals = jasmine.createSpy('hasIndividuals').and.returnValue(true);
            this.getIndividuals = jasmine.createSpy('getIndividuals').and.returnValue([]);
            this.hasNoTypeIndividuals = jasmine.createSpy('hasIndividuals').and.returnValue(true);
            this.getNoTypeIndividuals = jasmine.createSpy('getIndividuals').and.returnValue([]);
            this.hasClassIndividuals = jasmine.createSpy('hasClassIndividuals').and.returnValue(true);
            this.getClassIndividuals = jasmine.createSpy('getClassIndividuals').and.returnValue([]);
            this.isRestriction = jasmine.createSpy('isRestriction').and.returnValue(true);
            this.getRestrictions = jasmine.createSpy('getRestrictions').and.returnValue([]);
            this.isBlankNode = jasmine.createSpy('isBlankNode').and.returnValue(true);
            this.isBlankNodeId = jasmine.createSpy('isBlankNodeId').and.returnValue(false);
            this.getBlankNodes = jasmine.createSpy('getBlankNodes').and.returnValue([]);
            this.getEntity = jasmine.createSpy('getEntity').and.returnValue({});
            this.getEntityName = jasmine.createSpy('getEntityName').and.callFake((ontology, entity) => _.has(entity, '@id') ? entity['@id'] : '');
            this.getEntityNames = jasmine.createSpy('getEntityNames').and.callFake((ontology, entity) => _.has(entity, '@id') ? [entity['@id']] : ['']);
            this.getEntityDescription = jasmine.createSpy('getEntityDescription').and.returnValue('');
            this.isConcept = jasmine.createSpy('isConcept').and.returnValue(true);
            this.hasConcepts = jasmine.createSpy('hasConcepts').and.returnValue(true);
            this.getConcepts = jasmine.createSpy('getConcepts').and.returnValue([]);
            this.getConceptIRIs = jasmine.createSpy('getConceptIRIs').and.returnValue([]);
            this.isConceptScheme = jasmine.createSpy('isConceptScheme').and.returnValue(true);
            this.hasConceptSchemes = jasmine.createSpy('hasConceptSchemes').and.returnValue(true);
            this.getConceptSchemes = jasmine.createSpy('getConceptSchemes').and.returnValue([]);
            this.getConceptSchemeIRIs = jasmine.createSpy('getConceptSchemeIRIs').and.returnValue([]);
            this.downloadOntology = jasmine.createSpy('downloadOntology');
            this.deleteOntology = jasmine.createSpy('deleteOntology').and.returnValue($q.when());
            this.deleteOntologyBranch = jasmine.createSpy('deleteOntologyBranch').and.returnValue($q.when());
            this.getAnnotationPropertyHierarchies = jasmine.createSpy('getAnnotationPropertyHierarchies');
            this.uploadChangesFile = jasmine.createSpy('uploadChangesFile').and.returnValue($q.when({}));
            this.getFailedImports = jasmine.createSpy('getFailedImports').and.returnValue($q.when([]));
            this.getDataProperties = jasmine.createSpy('getDataProperties').and.returnValue($q.when([]));
            this.getObjProperties = jasmine.createSpy('getObjProperties').and.returnValue($q.when([]));
        });
    });
}

export function mockMappingManager() {
    angular.mock.module(function($provide, $qProvider) {
        $qProvider.errorOnUnhandledRejections(false);
        $provide.service('mappingManagerService', function($q) {
            this.annotationProperties = [];

            this.upload = jasmine.createSpy('upload').and.returnValue($q.when());
            this.getMappingRecords = jasmine.createSpy("getMappingRecords").and.returnValue($q.when([]));
            this.getMapping = jasmine.createSpy('getMapping').and.returnValue($q.when([]));
            this.downloadMapping = jasmine.createSpy('downloadMapping');
            this.deleteMapping = jasmine.createSpy('deleteMapping').and.returnValue($q.when());
            this.getMappingId = jasmine.createSpy('getMappingId').and.returnValue('');
            this.createNewMapping = jasmine.createSpy('createNewMapping').and.returnValue([]);
            this.setSourceOntologyInfo = jasmine.createSpy('setSourceOntologyInfo').and.returnValue([]);
            this.copyMapping = jasmine.createSpy('copyMapping').and.returnValue([]);
            this.renameMapping = jasmine.createSpy('renameMapping').and.returnValue([]);
            this.addClass = jasmine.createSpy('addClass').and.returnValue([]);
            this.editIriTemplate = jasmine.createSpy('editIriTemplate').and.returnValue([]);
            this.addDataProp = jasmine.createSpy('addDataProp').and.returnValue([]);
            this.addObjectProp = jasmine.createSpy('addObjectProp').and.returnValue([]);
            this.removeProp = jasmine.createSpy('removeProp').and.returnValue([]);
            this.removeClass = jasmine.createSpy('removeClass').and.returnValue([]);
            this.isPropertyMapping = jasmine.createSpy('isPropertyMapping').and.returnValue(true);
            this.isObjectMapping = jasmine.createSpy('isObjectMapping').and.returnValue(true);
            this.isDataMapping = jasmine.createSpy('isDataMapping').and.returnValue(true);
            this.isClassMapping = jasmine.createSpy('isClassMapping').and.returnValue(true);
            this.getMappingEntity = jasmine.createSpy('getMappingEntity').and.returnValue({});
            this.getPropMappingsByClass = jasmine.createSpy('getPropMappingsByClass').and.returnValue([]);
            this.getOntology = jasmine.createSpy('getOntology').and.returnValue($q.when({}));
            this.getSourceOntologies = jasmine.createSpy('getSourceOntologies').and.returnValue($q.when([]));
            this.findSourceOntologyWithClass = jasmine.createSpy('findSourceOntologyWithClass').and.returnValue({});
            this.findSourceOntologyWithProp = jasmine.createSpy('findSourceOntologyWithProp').and.returnValue({});
            this.getSourceOntologyId = jasmine.createSpy('getSourceOntologyId').and.returnValue('');
            this.getSourceOntologyInfo = jasmine.createSpy('getSourceOntologyInfo').and.returnValue({});
            this.getSourceOntology = jasmine.createSpy('getSourceOntologyId').and.returnValue({});
            this.areCompatible = jasmine.createSpy('areCompatible').and.returnValue(true);
            this.findIncompatibleMappings = jasmine.createSpy('findIncompatibleMappings').and.returnValue([]);
            this.findClassWithObjectMapping = jasmine.createSpy('findClassWithObjectMapping').and.returnValue({});
            this.findClassWithDataMapping = jasmine.createSpy('findClassWithDataMapping').and.returnValue({});
            this.getClassIdByMapping = jasmine.createSpy('getClassIdByMapping').and.returnValue('');
            this.getPropIdByMapping = jasmine.createSpy('getPropIdByMapping').and.returnValue('');
            this.getClassIdByMappingId = jasmine.createSpy('getClassIdByMappingId').and.returnValue('');
            this.getPropIdByMappingId = jasmine.createSpy('getPropIdByMappingId').and.returnValue('');
            this.getAllClassMappings = jasmine.createSpy('getAllClassMappings').and.returnValue([]);
            this.getAllDataMappings = jasmine.createSpy('getAllDataMappings').and.returnValue([]);
            this.getPropsLinkingToClass = jasmine.createSpy('getPropsLinkingToClass').and.returnValue([]);
            this.getPropMappingTitle = jasmine.createSpy('getPropMappingTitle').and.returnValue('');
            this.getBaseClass = jasmine.createSpy('getBaseClass').and.returnValue({});
            this.getClassMappingsByClassId = jasmine.createSpy('getClassMappingsByClassId').and.returnValue([]);
        });
    });
}

export function mockDelimitedManager() {
    angular.mock.module(function($provide, $qProvider) {
        $qProvider.errorOnUnhandledRejections(false);
        $provide.service('delimitedManagerService', function($q) {
            this.dataRows = undefined;
            this.fileName = '';
            this.fileObj = undefined;
            this.separator = ',';
            this.containsHeaders = true;
            this.preview = '';

            this.upload = jasmine.createSpy('upload').and.returnValue($q.when(''));
            this.previewFile = jasmine.createSpy('previewFile').and.callFake(function(rowCount) {
                return rowCount ? $q.when() : $q.reject('Something went wrong');
            });
            this.previewMap = jasmine.createSpy('previewMap').and.callFake((jsonld, format) => {
                if (jsonld) {
                    return format === 'jsonld' ? $q.when([]) : $q.when('');
                } else {
                    return $q.reject('Something went wrong');
                }
            });
            this.mapAndDownload = jasmine.createSpy('mapAndDownload');
            this.mapAndUpload = jasmine.createSpy('mapAndUpload').and.returnValue($q.when());
            this.mapAndCommit = jasmine.createSpy('mapAndCommit').and.returnValue($q.when());
            this.reset = jasmine.createSpy('reset');
            this.getHeader = jasmine.createSpy('getHeader').and.returnValue('');
        });
    });
}

export function mockMapperState() {
    angular.mock.module(function($provide, $qProvider) {
        $qProvider.errorOnUnhandledRejections(false);
        $provide.service('mapperStateService', function($q) {
            this.selectMappingStep = 0;
            this.fileUploadStep = 1;
            this.editMappingStep = 2;
            this.mapping = undefined;
            this.sourceOntologies = [];
            this.mappingSearchString = '';
            this.propsByClass = {};
            this.editMapping = false;
            this.newMapping = false;
            this.step = 0;
            this.editTabs = {
                edit: true,
                commits: false
            };
            this.invalidProps = [];
            this.availableClasses = [];
            this.selectedClassMappingId = '';
            this.selectedPropMappingId = '';
            this.newProp = false;
            this.highlightIndexes = [];

            this.initialize = jasmine.createSpy('initialize');
            this.resetEdit = jasmine.createSpy('resetEdit');
            this.createMapping = jasmine.createSpy('createMapping').and.returnValue({record: {}, ontology: undefined, jsonld: [], difference: {additions: [], deletions: []}});
            this.selectMapping = jasmine.createSpy('selectMapping');
            this.isMappingChanged = jasmine.createSpy('isMappingChanged').and.returnValue(false);
            this.saveMapping = jasmine.createSpy('saveMapping').and.returnValue($q.when());
            this.setMasterBranch = jasmine.createSpy('setMasterBranch').and.returnValue($q.when());
            this.setInvalidProps = jasmine.createSpy('setInvalidProps');
            this.getProps = jasmine.createSpy('getProps').and.returnValue([]);
            this.getPropsByClassMappingId = jasmine.createSpy('getPropsByClassMappingId').and.returnValue([]);
            this.setProps = jasmine.createSpy('setProps');
            this.setPropsByClassMappingId = jasmine.createSpy('setPropsByClassMappingId');
            this.hasProps = jasmine.createSpy('hasProps');
            this.hasPropsByClassMappingId = jasmine.createSpy('hasPropsByClassMappingId');
            this.hasPropsSet = jasmine.createSpy('hasPropsSet');
            this.hasPropsSetByClassMappingId = jasmine.createSpy('hasPropsSetByClassMappingId');
            this.removeProps = jasmine.createSpy('removeProps');
            this.removePropsByClassMappingId = jasmine.createSpy('removePropsByClassMappingId');
            this.getClassProps = jasmine.createSpy('getClassProps').and.returnValue([]);
            this.getClasses = jasmine.createSpy('getClasses').and.returnValue([]);
            this.getMappedColumns = jasmine.createSpy('getMappedColumns').and.returnValue([]);
            this.changeProp = jasmine.createSpy('changeProp');
            this.addClassMapping = jasmine.createSpy('addClassMapping').and.returnValue({});
            this.addDataMapping = jasmine.createSpy('addDataMapping').and.returnValue({});
            this.addObjectMapping = jasmine.createSpy('addObjectMapping').and.returnValue({});
            this.deleteEntity = jasmine.createSpy('deleteEntity');
            this.deleteClass = jasmine.createSpy('deleteClass');
            this.deleteProp = jasmine.createSpy('deleteProp');
        });
    });
}

export function mockHttpService() {
    angular.mock.module(function($provide, $qProvider) {
        $qProvider.errorOnUnhandledRejections(false);
        $provide.service('httpService', function() {
            this.pending = [];
            this.isPending = jasmine.createSpy('isPending');
            this.cancel = jasmine.createSpy('cancel');
            this.get = jasmine.createSpy('get');
            this.post = jasmine.createSpy('post');
        });
    });
}

export function mockPrefixes() {
    angular.mock.module(function($provide) {
        $provide.service('prefixes', function() {
            this.owl = this.delim = this.data = this.mappings = '';
            this.rdfs = 'rdfs:';
            this.dc = 'dc:';
            this.dcterms = 'dcterms:';
            this.rdf = 'rdf:';
            this.ontologyState = 'ontologyState:';
            this.catalog = 'catalog:';
            this.skos = 'skos:';
            this.xsd = 'xsd:';
            this.ontologyEditor = 'ontEdit:';
            this.dataset = 'dataset:';
            this.matprov = 'matprov:';
            this.prov = 'prov:';
            this.matprov = 'matprov:';
            this.mergereq = 'mergereq:';
            this.user = 'user:';
            this.policy = 'policy:';
            this.roles = "roles:";
            this.foaf = "foaf:";
        });
    });
}

export function mockUpdateRefs() {
    angular.mock.module(function($provide) {
        $provide.service('updateRefsService', function() {
            this.update = jasmine.createSpy('update');
            this.remove = jasmine.createSpy('remove');
        });
    });
}

export function mockSparqlManager() {
    angular.mock.module(function($provide, $qProvider) {
        $qProvider.errorOnUnhandledRejections(false);
        $provide.service('sparqlManagerService', function($q) {
            this.data = undefined;
            this.bindings = [];
            this.limit = 100;
            this.links = {
                next: '',
                prev: ''
            };
            this.currentPage = 0;
            this.totalSize = 0;
            this.bindings = [];
            this.queryString = '';
            this.datasetRecordIRI = '';
            this.errorMessage = '';
            this.infoMessage = '';
            this.reset = jasmine.createSpy('reset');
            this.query = jasmine.createSpy('query').and.returnValue($q.when({}));
            this.queryRdf = jasmine.createSpy('queryRdf');
            this.downloadResults = jasmine.createSpy('downloadResults');
            this.setResults = jasmine.createSpy('setResults');
            this.pagedQuery = jasmine.createSpy('pagedQuery').and.returnValue($q.when({}));
        });
    });
}

export function mockSettingsManager() {
    angular.mock.module(function($provide) {
        $provide.service('settingsManagerService', function() {
            this.getSettings = jasmine.createSpy('getSettings').and.returnValue({});
            this.setSettings = jasmine.createSpy('setSettings').and.callFake(_.identity);
            this.getTreeDisplay = jasmine.createSpy('getTreeDisplay').and.returnValue('');
            this.getTooltipDisplay = jasmine.createSpy('getTooltipDisplay').and.returnValue('');
        });
    });
}

export function mockOntologyState() {
    angular.mock.module(function($provide, $qProvider) {
        $qProvider.errorOnUnhandledRejections(false);
        $provide.service('ontologyStateService', function($q) {
            this.recordIdToClose = 'recordIdToClose';
            this.annotationSelect = 'select';
            this.annotationValue = 'value';
            this.annotationType = {namespace: '', localName: ''};
            this.key = 'key';
            this.index = 0;
            this.annotationIndex = 0;
            this.listItem = {
                selected: {
                    '@id': 'id'
                },
                selectedBlankNodes: [],
                active: true,
                upToDate: true,
                isVocabulary: false,
                editorTabStates: {
                   project: {
                       active: true,
                       entityIRI: '',
                       targetedSpinnerId: 'project'
                   },
                   overview: {
                       active: false,
                       searchText: '',
                       open: {},
                       targetedSpinnerId: 'overview'
                   },
                   classes: {
                       active: false,
                       searchText: '',
                       index: 0,
                       open: {},
                       targetedSpinnerId: 'classes'
                   },
                   properties: {
                       active: false,
                       searchText: '',
                       index: 0,
                       open: {},
                       targetedSpinnerId: 'properties'
                   },
                   individuals: {
                       active: false,
                       searchText: '',
                       index: 0,
                       open: {},
                       targetedSpinnerId: 'individuals'
                   },
                   concepts: {
                       active: false,
                       searchText: '',
                       index: 0,
                       open: {},
                       targetedSpinnerId: 'concepts'
                   },
                   schemes: {
                       active: false,
                       searchText: '',
                       index: 0,
                       open: {},
                       targetedSpinnerId: 'schemes'
                   },
                   search: {
                       active: false
                   },
                   savedChanges: {
                       active: false
                   },
                   commits: {
                       active: false
                   }
                },
                userBranch: false,
                createdFromExists: true,
                userCanModify: false,
                userCanModifyMaster: false,
                masterBranchIRI: '',
                ontologyRecord: {
                    title: '',
                    recordId: '',
                    branchId: '',
                    commitId: ''
                },
                merge: {
                    active: false,
                    target: undefined,
                    checkbox: false,
                    difference: undefined,
                    conflicts: [],
                    resolutions: {
                        additions: [],
                        deletions: []
                    }
                },
                dataPropertyRange: {},
                derivedConcepts: [],
                derivedConceptSchemes: [],
                derivedSemanticRelations: [],
                classes: {
                    iris: {},
                    parentMap: {},
                    childMap: {},
                    flat: []
                },
                objectProperties: {
                    iris: {},
                    parentMap: {},
                    childMap: {},
                    flat: []
                },
                dataProperties: {
                    iris: {},
                    parentMap: {},
                    childMap: {},
                    flat: []
                },
                annotations: {
                    iris: {},
                    parentMap: {},
                    childMap: {},
                    flat: []
                },
                individuals: {
                    iris: {},
                    flat: []
                },
                concepts: {
                    iris: {},
                    parentMap: {},
                    childMap: {},
                    flat: []
                },
                conceptSchemes: {
                    iris: {},
                    parentMap: {},
                    childMap: {},
                    flat: []
                },
                blankNodes: {},
                index: {},
                ontologyId: 'ontologyId',
                additions: [],
                deletions: [],
                inProgressCommit: {
                    additions: [],
                    deletions: []
                },
                branches: [],
                tags: [],
                ontology: [{
                    '@id': 'id'
                }],
                individualsParentPath: [],
                classesAndIndividuals: {},
                classesWithIndividuals: [],
                importedOntologies: [],
                importedOntologyIds: [],
                iriList: [],
                failedImports: [],
                goTo: {
                    entityIRI: '',
                    active: false
                }
            };
            this.states = [];
            this.list = [];
            this.uploadList = [];
            this.initialize = jasmine.createSpy('initialize');
            this.reset = jasmine.createSpy('reset');
            this.getOntologyCatalogDetails = jasmine.createSpy('getOntologyCatalogDetails').and.returnValue({});
            this.createOntology = jasmine.createSpy('createOntology').and.returnValue($q.resolve({}));
            this.uploadThenGet = jasmine.createSpy('uploadThenGet').and.returnValue($q.resolve(''));
            this.uploadChanges = jasmine.createSpy('uploadChanges').and.returnValue($q.resolve(''));
            this.updateOntology = jasmine.createSpy('updateOntology');
            this.updateOntologyWithCommit = jasmine.createSpy('updateOntologyWithCommit');
            this.addOntologyToList = jasmine.createSpy('addOntologyToList').and.returnValue($q.when([]));
            this.createOntologyListItem = jasmine.createSpy('createOntologyListItem').and.returnValue($q.when([]));
            this.addEntity = jasmine.createSpy('addEntity');
            this.removeEntity = jasmine.createSpy('removeEntity');
            this.getListItemByRecordId = jasmine.createSpy('getListItemByRecordId').and.returnValue({});
            this.getEntityByRecordId = jasmine.createSpy('getEntityByRecordId');
            this.getEntity = jasmine.createSpy('getEntity').and.returnValue($q.when([]));
            this.getEntityNoBlankNodes = jasmine.createSpy('getEntityNoBlankNodes').and.returnValue($q.when({}));
            this.existsInListItem = jasmine.createSpy('existsInListItem').and.returnValue(true);
            this.getFromListItem = jasmine.createSpy('getFromListItem').and.returnValue({});
            this.getOntologyByRecordId = jasmine.createSpy('getOntologyByRecordId');
            this.getEntityNameByListItem = jasmine.createSpy('getEntityNameByListItem');
            this.saveChanges = jasmine.createSpy('saveChanges').and.returnValue($q.resolve({}));
            this.addToAdditions = jasmine.createSpy('addToAdditions');
            this.addToDeletions = jasmine.createSpy('addToDeletions');
            this.openOntology = jasmine.createSpy('openOntology').and.returnValue($q.resolve({}));
            this.closeOntology = jasmine.createSpy('closeOntology');
            this.removeBranch = jasmine.createSpy('removeBranch');
            this.afterSave = jasmine.createSpy('afterSave').and.returnValue($q.when([]));
            this.clearInProgressCommit = jasmine.createSpy('clearInProgressCommit');
            this.setNoDomainsOpened = jasmine.createSpy('setNoDomainsOpened');
            this.getNoDomainsOpened = jasmine.createSpy('getNoDomainsOpened').and.returnValue(true);
            this.getUnsavedEntities = jasmine.createSpy('getUnsavedEntities');
            this.getDataPropertiesOpened = jasmine.createSpy('getDataPropertiesOpened');
            this.setDataPropertiesOpened = jasmine.createSpy('setDataPropertiesOpened');
            this.getObjectPropertiesOpened = jasmine.createSpy('getObjectPropertiesOpened');
            this.setObjectPropertiesOpened = jasmine.createSpy('setObjectPropertiesOpened');
            this.getAnnotationPropertiesOpened = jasmine.createSpy('getAnnotationPropertiesOpened');
            this.setAnnotationPropertiesOpened = jasmine.createSpy('setAnnotationPropertiesOpened');
            this.onEdit = jasmine.createSpy('onEdit');
            this.setCommonIriParts = jasmine.createSpy('setCommonIriParts');
            this.setSelected = jasmine.createSpy('setSelected');
            this.setEntityUsages = jasmine.createSpy('setEntityUsages');
            this.resetStateTabs = jasmine.createSpy('resetStateTabs');
            this.getActiveKey = jasmine.createSpy('getActiveKey').and.returnValue('');
            this.getActivePage = jasmine.createSpy('getActivePage').and.returnValue({});
            this.getActiveEntityIRI = jasmine.createSpy('getActiveEntityIRI');
            this.selectItem = jasmine.createSpy('selectItem').and.returnValue($q.when());
            this.unSelectItem = jasmine.createSpy('unSelectItem');
            this.hasChanges = jasmine.createSpy('hasChanges').and.returnValue(true);
            this.isCommittable = jasmine.createSpy('isCommittable');
            this.updateIsSaved = jasmine.createSpy('updateIsSaved');
            this.addEntityToHierarchy = jasmine.createSpy('addEntityToHierarchy');
            this.deleteEntityFromParentInHierarchy = jasmine.createSpy('deleteEntityFromParentInHierarchy');
            this.deleteEntityFromHierarchy = jasmine.createSpy('deleteEntityFromHierarchy');
            this.joinPath = jasmine.createSpy('joinPath').and.returnValue('');
            this.getPathsTo = jasmine.createSpy('getPathsTo');
            this.goTo = jasmine.createSpy('goTo');
            this.openAt = jasmine.createSpy('openAt');
            this.getDefaultPrefix = jasmine.createSpy('getDefaultPrefix');
            this.retrieveClassesWithIndividuals = jasmine.createSpy('retrieveClassesWithIndividuals');
            this.getIndividualsParentPath = jasmine.createSpy('getIndividualsParentPath');
            this.setVocabularyStuff = jasmine.createSpy('setVocabularyStuff');
            this.flattenHierarchy = jasmine.createSpy('flattenHierarchy');
            this.areParentsOpen = jasmine.createSpy('areParentsOpen');
            this.createFlatEverythingTree = jasmine.createSpy('createFlatEverythingTree');
            this.createFlatIndividualTree = jasmine.createSpy('createFlatIndividualTree');
            this.updatePropertyIcon = jasmine.createSpy('updatePropertyIcon');
            this.isDerivedConcept = jasmine.createSpy('isDerivedConcept');
            this.isDerivedConceptScheme = jasmine.createSpy('isDerivedConceptScheme');
            this.hasInProgressCommit = jasmine.createSpy('hasInProgressCommit').and.returnValue(false);
            this.addToClassIRIs = jasmine.createSpy('addToClassIRIs');
            this.removeFromClassIRIs = jasmine.createSpy('removeFromClassIRIs');
            this.addErrorToUploadItem = jasmine.createSpy('addErrorToUploadItem');
            this.attemptMerge = jasmine.createSpy('attemptMerge').and.returnValue($q.when());
            this.checkConflicts = jasmine.createSpy('checkConflicts').and.returnValue($q.when());
            this.getMergeDifferences = jasmine.createSpy('getMergeDifferences').and.returnValue($q.when());
            this.merge = jasmine.createSpy('merge').and.returnValue($q.when());
            this.cancelMerge = jasmine.createSpy('cancelMerge');
            this.canModify = jasmine.createSpy('canModify');
            this.createOntologyState = jasmine.createSpy('createOntologyState').and.returnValue($q.when());
            this.getOntologyStateByRecordId = jasmine.createSpy('getOntologyStateByRecordId').and.returnValue({});
            this.updateOntologyState = jasmine.createSpy('updateOntologyState').and.returnValue($q.when());
            this.deleteOntologyBranchState = jasmine.createSpy('deleteOntologyBranchState').and.returnValue($q.when());
            this.deleteOntologyState = jasmine.createSpy('deleteOntologyState').and.returnValue($q.when());
            this.getCurrentStateByRecordId = jasmine.createSpy('getCurrentStateByRecordId').and.returnValue({});
            this.getCurrentStateIdByRecordId = jasmine.createSpy('getCurrentStateIdByRecordId').and.returnValue('');
            this.getCurrentStateId = jasmine.createSpy('getCurrentStateId').and.returnValue('');
            this.getCurrentState = jasmine.createSpy('getCurrentState').and.returnValue({});
            this.isStateTag = jasmine.createSpy('isStateTag').and.returnValue(false);
            this.isStateBranch = jasmine.createSpy('isStateBranch').and.returnValue(false);
            this.isImported = jasmine.createSpy('isImported').and.returnValue(false);
            this.isSelectedImported = jasmine.createSpy('isSelectedImported').and.returnValue(false);
            this.handleNewProperty = jasmine.createSpy('handleNewProperty');
            this.handleDeletedProperty = jasmine.createSpy('handleDeletedProperty');
            this.addPropertyToClasses = jasmine.createSpy('addPropertyToClasses');
            this.handleDeletedClass = jasmine.createSpy('handleDeletedClass');
            this.removePropertyFromClass = jasmine.createSpy('removePropertyFromClass');
            this.getBnodeIndex = jasmine.createSpy('getBnodeIndex');
        });
    });
}

export function mockOntologyUtilsManager() {
    angular.mock.module(function($provide, $qProvider) {
        $qProvider.errorOnUnhandledRejections(false);
        $provide.service('ontologyUtilsManagerService', function($q) {
            this.containsDerivedConcept = jasmine.createSpy('containsDerivedConcept');
            this.containsDerivedSemanticRelation = jasmine.createSpy('containsDerivedSemanticRelation');
            this.containsDerivedConceptScheme = jasmine.createSpy('containsDerivedConceptScheme');
            this.updateVocabularyHierarchies = jasmine.createSpy('updateVocabularyHierarchies');
            this.removeFromVocabularyHierarchies = jasmine.createSpy('removeFromVocabularyHierarchies');
            this.addIndividual = jasmine.createSpy('addIndividual');
            this.addConcept = jasmine.createSpy('addConcept');
            this.addConceptScheme = jasmine.createSpy('addConceptScheme');
            this.commonDelete = jasmine.createSpy('commonDelete');
            this.deleteClass = jasmine.createSpy('deleteClass');
            this.deleteObjectProperty = jasmine.createSpy('deleteObjectProperty');
            this.deleteDataTypeProperty = jasmine.createSpy('deleteDataTypeProperty');
            this.deleteAnnotationProperty = jasmine.createSpy('deleteAnnotationProperty');
            this.deleteIndividual = jasmine.createSpy('deleteIndividual');
            this.deleteConcept = jasmine.createSpy('deleteConcept');
            this.deleteConceptScheme = jasmine.createSpy('deleteConceptScheme');
            this.getBlankNodeValue = jasmine.createSpy('getBlankNodeValue');
            this.isLinkable = jasmine.createSpy('isLinkable');
            this.getNameByNode = jasmine.createSpy('getNameByNode');
            this.addLanguageToNewEntity = jasmine.createSpy('addLanguageToNewEntity');
            this.saveCurrentChanges = jasmine.createSpy('saveCurrentChanges');
            this.updateLabel = jasmine.createSpy('updateLabel');
            this.getLabelForIRI = jasmine.createSpy('getLabelForIRI');
            this.getDropDownText = jasmine.createSpy('getDropDownText');
            this.setSuperClasses = jasmine.createSpy('setSuperClasses');
            this.setSuperProperties = jasmine.createSpy('setSuperProperties');
            this.updateflatIndividualsHierarchy = jasmine.createSpy('updateflatIndividualsHierarchy');
            this.checkIri = jasmine.createSpy('checkIri');
            this.getSelectList = jasmine.createSpy('getSelectList');
            this.getRemovePropOverlayMessage = jasmine.createSpy('getRemovePropOverlayMessage').and.returnValue('');
            this.getPropValueDisplay = jasmine.createSpy('getPropValueDisplay').and.returnValue('');
            this.removeProperty = jasmine.createSpy('removeProperty').and.returnValue($q.when({}));
        });
    });
}

export function mockPropertyManager() {
    angular.mock.module(function($provide, $qProvider) {
        $qProvider.errorOnUnhandledRejections(false);
        $provide.service('propertyManagerService', function($q) {
            this.defaultAnnotations = [];
            this.owlAnnotations = [];
            this.skosAnnotations = [];
            this.ontologyProperties = [];
            this.conceptSchemeRelationshipList = [];
            this.conceptRelationshipList = [];
            this.schemeRelationshipList = [];
            this.defaultDatatypes = [];
            this.getValuesKey = jasmine.createSpy('getValuesKey').and.returnValue('');
            this.getDefaultAnnotations = jasmine.createSpy('getDefaultAnnotations').and.returnValue([]);
            this.remove = jasmine.createSpy('remove');
            this.addValue = jasmine.createSpy('addValue');
            this.addId = jasmine.createSpy('addId');
            this.editValue = jasmine.createSpy('editValue');
            this.editId = jasmine.createSpy('editId');
            this.createValueObj = jasmine.createSpy('createValueObj').and.returnValue({});
            this.create = jasmine.createSpy('create').and.returnValue($q.resolve({}));
            this.getDatatypeMap = jasmine.createSpy('getDatatypeMap').and.returnValue({});
        });
    });
}

export function mockLoginManager() {
    angular.mock.module(function($provide, $qProvider) {
        $qProvider.errorOnUnhandledRejections(false);
        $provide.service('loginManagerService', function($q) {
            this.currentUser = '';
            this.login = jasmine.createSpy('login').and.returnValue($q.when());
            this.logout = jasmine.createSpy('logout');
            this.isAuthenticated = jasmine.createSpy('isAuthenticated').and.returnValue($q.when());
            this.getCurrentLogin = jasmine.createSpy('getCurrentLogin').and.returnValue($q.when());
        });
    });
}

export function mockUserManager() {
    angular.mock.module(function($provide, $qProvider) {
        $qProvider.errorOnUnhandledRejections(false);
        $provide.service('userManagerService', function($q) {
            this.users = [];
            this.groups = [];
            this.reset = jasmine.createSpy('reset');
            this.initialize = jasmine.createSpy('initialize');
            this.getUsername = jasmine.createSpy('getUsername').and.returnValue($q.when(''));
            this.setUsers = jasmine.createSpy('setUsers').and.returnValue($q.when());
            this.setGroups = jasmine.createSpy('setGroups').and.returnValue($q.when());
            this.addUser = jasmine.createSpy('addUser').and.returnValue($q.when());
            this.getUser = jasmine.createSpy('getUser').and.returnValue($q.when());
            this.updateUser = jasmine.createSpy('updateUser').and.returnValue($q.when());
            this.changePassword = jasmine.createSpy('changePassword').and.returnValue($q.when());
            this.resetPassword = jasmine.createSpy('resetPassword').and.returnValue($q.when());
            this.deleteUser = jasmine.createSpy('deleteUser').and.returnValue($q.when());
            this.addUserRoles = jasmine.createSpy('addUserRoles').and.returnValue($q.when());
            this.deleteUserRole = jasmine.createSpy('deleteUserRole').and.returnValue($q.when());
            this.addUserGroup = jasmine.createSpy('addUserGroup').and.returnValue($q.when());
            this.deleteUserGroup = jasmine.createSpy('deleteUserGroup').and.returnValue($q.when());
            this.addGroup = jasmine.createSpy('addGroup').and.returnValue($q.when());
            this.getGroup = jasmine.createSpy('getGroup').and.returnValue($q.when());
            this.updateGroup = jasmine.createSpy('updateGroup').and.returnValue($q.when());
            this.deleteGroup = jasmine.createSpy('deleteGroup').and.returnValue($q.when());
            this.addGroupRoles = jasmine.createSpy('addGroupRoles').and.returnValue($q.when());
            this.deleteGroupRole = jasmine.createSpy('deleteGroupRole').and.returnValue($q.when());
            this.getGroupUsers = jasmine.createSpy('getGroupUsers').and.returnValue($q.when([]));
            this.addGroupUsers = jasmine.createSpy('addGroupUsers').and.returnValue($q.when());
            this.deleteGroupUser = jasmine.createSpy('deleteGroupUser').and.returnValue($q.when());
            this.getUserObj = jasmine.createSpy('getUserObj').and.returnValue({});
            this.getGroupObj = jasmine.createSpy('getGroupObj').and.returnValue({});
            this.isAdmin = jasmine.createSpy('isAdmin');
        });
    });
}

export function mockUserState() {
    angular.mock.module(function($provide) {
        $provide.service('userStateService', function() {
            this.userSearchString = '';
            this.groupSearchString = '';
            this.showGroups = false;
            this.showUsers = false;
            this.selectedGroup = undefined;
            this.selectedUser = undefined;
            this.reset = jasmine.createSpy('reset');
        });
    });
}

export function mockCatalogManager() {
    angular.mock.module(function($provide, $qProvider) {
        $qProvider.errorOnUnhandledRejections(false);
        $provide.service('catalogManagerService', function($q) {
            this.coreRecordTypes = [];
            this.sortOptions = [];
            this.recordTypes = [];
            this.localCatalog = undefined;
            this.distributedCatalog = undefined;
            this.initialize = jasmine.createSpy('initialize').and.returnValue($q.when());
            this.getSortOptions = jasmine.createSpy('getSortOptions').and.returnValue($q.when([]));
            this.getRecordTypes = jasmine.createSpy('getRecordTypes').and.returnValue($q.when([]));
            this.getResultsPage = jasmine.createSpy('getResultsPage').and.returnValue($q.when({}));
            this.getRecords = jasmine.createSpy('getRecords').and.returnValue($q.when({}));
            this.getRecord = jasmine.createSpy('getRecord').and.returnValue($q.when({}));
            this.createRecord = jasmine.createSpy('createRecord').and.returnValue($q.when());
            this.updateRecord = jasmine.createSpy('updateRecord').and.returnValue($q.when());
            this.deleteRecord = jasmine.createSpy('deleteRecord').and.returnValue($q.when());
            this.getRecordDistributions = jasmine.createSpy('getRecordDistributions').and.returnValue($q.when({}));
            this.getRecordDistribution = jasmine.createSpy('getRecordDistribution').and.returnValue($q.when({}));
            this.createRecordDistribution = jasmine.createSpy('createRecordDistribution').and.returnValue($q.when());
            this.updateRecordDistribution = jasmine.createSpy('updateRecordDistribution').and.returnValue($q.when());
            this.deleteRecordDistribution = jasmine.createSpy('deleteRecordDistribution').and.returnValue($q.when());
            this.getRecordVersions = jasmine.createSpy('getRecordVersions').and.returnValue($q.when({}));
            this.getRecordLatestVersion = jasmine.createSpy('getRecordLatestVersion').and.returnValue($q.when({}));
            this.getRecordVersion = jasmine.createSpy('getRecordVersion').and.returnValue($q.when({}));
            this.createRecordVersion = jasmine.createSpy('createRecordVersion').and.returnValue($q.when());
            this.createRecordTag = jasmine.createSpy('createRecordTag').and.returnValue($q.when());
            this.updateRecordVersion = jasmine.createSpy('updateRecordVersion').and.returnValue($q.when());
            this.deleteRecordVersion = jasmine.createSpy('deleteRecordVersion').and.returnValue($q.when());
            this.getVersionCommit = jasmine.createSpy('getVersionCommit').and.returnValue($q.when({}));
            this.getVersionDistributions = jasmine.createSpy('getVersionDistributions').and.returnValue($q.when({}));
            this.getVersionDistribution = jasmine.createSpy('getVersionDistribution').and.returnValue($q.when({}));
            this.createVersionDistribution = jasmine.createSpy('createVersionDistribution').and.returnValue($q.when());
            this.updateVersionDistribution = jasmine.createSpy('updateVersionDistribution').and.returnValue($q.when());
            this.deleteVersionDistribution = jasmine.createSpy('deleteVersionDistribution').and.returnValue($q.when());
            this.getRecordBranches = jasmine.createSpy('getRecordBranches').and.returnValue($q.when({}));
            this.getRecordMasterBranch = jasmine.createSpy('getRecordMasterBranch').and.returnValue($q.when({}));
            this.getRecordBranch = jasmine.createSpy('getRecordBranch').and.returnValue($q.when({}));
            this.createRecordBranch = jasmine.createSpy('createRecordBranch').and.returnValue($q.when());
            this.createRecordUserBranch = jasmine.createSpy('createRecordUserBranch').and.returnValue($q.when());
            this.updateRecordBranch = jasmine.createSpy('updateRecordBranch').and.returnValue($q.when());
            this.deleteRecordBranch = jasmine.createSpy('deleteRecordBranch').and.returnValue($q.when());
            this.getCommit = jasmine.createSpy('getCommit').and.returnValue($q.when([]));
            this.getCommitHistory = jasmine.createSpy('getCommitHistory').and.returnValue($q.when([]));
            this.getCompiledResource = jasmine.createSpy('getCompiledResource').and.returnValue($q.when([]));
            this.getDifference = jasmine.createSpy('getDifference').and.returnValue($q.when([]));
            this.getBranchCommits = jasmine.createSpy('getBranchCommits').and.returnValue($q.when([]));
            this.createBranchCommit = jasmine.createSpy('createBranchCommit').and.returnValue($q.when());
            this.getBranchHeadCommit = jasmine.createSpy('getBranchHeadCommit').and.returnValue($q.when({}));
            this.getBranchCommit = jasmine.createSpy('getBranchCommit').and.returnValue($q.when({}));
            this.getBranchDifference = jasmine.createSpy('getBranchDifference').and.returnValue($q.when({}));
            this.getBranchConflicts = jasmine.createSpy('getBranchConflicts').and.returnValue($q.when([]));
            this.mergeBranches = jasmine.createSpy('mergeBranches').and.returnValue($q.when(''));
            this.getResource = jasmine.createSpy('getResource').and.returnValue($q.when(''));
            this.downloadResource = jasmine.createSpy('downloadResource');
            this.createInProgressCommit = jasmine.createSpy('createInProgressCommit').and.returnValue($q.when());
            this.getInProgressCommit = jasmine.createSpy('getInProgressCommit').and.returnValue($q.when({}));
            this.updateInProgressCommit = jasmine.createSpy('updateInProgressCommit').and.returnValue($q.when());
            this.deleteInProgressCommit = jasmine.createSpy('deleteInProgressCommit').and.returnValue($q.when());
            this.getEntityName = jasmine.createSpy('getEntityName');
            this.isRecord = jasmine.createSpy('isRecord');
            this.isVersionedRDFRecord = jasmine.createSpy('isVersionedRDFRecord');
            this.isDistribution = jasmine.createSpy('isDistribution');
            this.isBranch = jasmine.createSpy('isBranch');
            this.isUserBranch = jasmine.createSpy('isUserBranch');
            this.isVersion = jasmine.createSpy('isVersion');
            this.isTag = jasmine.createSpy('isTag');
            this.isCommit = jasmine.createSpy('isCommit');
        });
    });
}

export function mockCatalogState() {
    angular.mock.module(function($provide) {
        $provide.service('catalogStateService', function() {
            this.selectedRecord = undefined;
            this.totalRecordSize = 0;
            this.currentRecordPage = 1;
            this.recordLimit = 10;
            this.recordSortOption = undefined;
            this.recordFilterType = '';
            this.recordSearchText = '';
            this.recordIcons = {};
            this.initialize = jasmine.createSpy('initialize');
            this.reset = jasmine.createSpy('reset');
            this.getRecordType = jasmine.createSpy('getRecordType').and.returnValue('');
            this.getRecordIcon = jasmine.createSpy('getRecordIcon').and.returnValue('');
        });
    });
}

export function mockUtil() {
    angular.mock.module(function($provide, $qProvider) {
        $qProvider.errorOnUnhandledRejections(false);
        $provide.service('utilService', function($q) {
            this.getBeautifulIRI = jasmine.createSpy('getBeautifulIRI').and.callFake(_.identity);
            this.getPropertyValue = jasmine.createSpy('getPropertyValue').and.returnValue('');
            this.setPropertyValue = jasmine.createSpy('setPropertyValue').and.returnValue({});
            this.hasPropertyValue = jasmine.createSpy('hasPropertyValue').and.returnValue(false);
            this.removePropertyValue = jasmine.createSpy('removePropertyValue');
            this.replacePropertyValue = jasmine.createSpy('replacePropertyValue');
            this.setPropertyId = jasmine.createSpy('setPropertyId').and.returnValue({});
            this.getPropertyId = jasmine.createSpy('getPropertyId').and.returnValue('');
            this.hasPropertyId = jasmine.createSpy('hasPropertyId').and.returnValue(false);
            this.removePropertyId = jasmine.createSpy('removePropertyId');
            this.replacePropertyId = jasmine.createSpy('replacePropertyId');
            this.getDctermsValue = jasmine.createSpy('getDctermsValue').and.returnValue('');
            this.removeDctermsValue = jasmine.createSpy('removeDctermsValue');
            this.setDctermsValue = jasmine.createSpy('setDctermsValue').and.returnValue({});
            this.updateDctermsValue = jasmine.createSpy('updateDctermsValue').and.returnValue({});
            this.mergingArrays = jasmine.createSpy('mergingArrays');
            this.getDctermsId = jasmine.createSpy('getDctermsId').and.returnValue('');
            this.parseLinks = jasmine.createSpy('parseLinks').and.returnValue({});
            this.createErrorToast = jasmine.createSpy('createErrorToast');
            this.createSuccessToast = jasmine.createSpy('createSuccessToast');
            this.createWarningToast = jasmine.createSpy('createWarningToast');
            this.createJson = jasmine.createSpy('createJson').and.returnValue({});
            this.getIRINamespace = jasmine.createSpy('getIRINamespace').and.returnValue('');
            this.getIRILocalName = jasmine.createSpy('getIRILocalName').and.returnValue('');
            this.getDate = jasmine.createSpy('getDate').and.returnValue(new Date());
            this.condenseCommitId = jasmine.createSpy('condenseCommitId');
            this.paginatedConfigToParams = jasmine.createSpy('paginatedConfigToParams').and.returnValue({});
            this.onError = jasmine.createSpy('onError').and.callFake(function(error, deferred) {
                deferred.reject(_.get(error, 'statusText', ''));
            });
            this.rejectError = jasmine.createSpy("rejectError").and.returnValue($q.reject(''));
            this.getErrorMessage = jasmine.createSpy('getErrorMessage').and.returnValue('');
            this.rejectErrorObject = jasmine.createSpy("rejectErrorObject").and.returnValue($q.reject(''));
            this.getErrorDataObject = jasmine.createSpy('getErrorDataObject').and.returnValue('');
            this.getResultsPage = jasmine.createSpy('getResultsPage').and.returnValue($q.when({}));
            this.getChangesById = jasmine.createSpy('getChangesById');
            this.getPredicatesAndObjects = jasmine.createSpy('getPredicatesAndObjects');
            this.getObjIrisFromDifference = jasmine.createSpy('getObjIrisFromDifference');
            this.getPredicateLocalName = jasmine.createSpy('getPredicateLocalName');
            this.getIdForBlankNode = jasmine.createSpy('getIdForBlankNode').and.returnValue('');
            this.getSkolemizedIRI = jasmine.createSpy('getSkolemizedIRI').and.returnValue('');
            this.getInputType = jasmine.createSpy('getInputType').and.returnValue('');
            this.getPattern = jasmine.createSpy('getPattern').and.returnValue(/[a-zA-Z]/);
            this.startDownload = jasmine.createSpy('startDownload');
        });
    });
}

export function mockDatasetManager() {
    angular.mock.module(function($provide, $qProvider) {
        $qProvider.errorOnUnhandledRejections(false);
        $provide.service('datasetManagerService', function($q) {
            this.datasetRecords = [];
            this.getResultsPage = jasmine.createSpy('getResultsPage').and.returnValue($q.when({}));
            this.getDatasetRecords = jasmine.createSpy('getDatasetRecords').and.returnValue($q.when({}));
            this.createDatasetRecord = jasmine.createSpy('createDatasetRecord').and.returnValue($q.when(''));
            this.deleteDatasetRecord = jasmine.createSpy('deleteDatasetRecord').and.returnValue($q.when());
            this.clearDatasetRecord = jasmine.createSpy('clearDatasetRecord').and.returnValue($q.when());
            this.updateDatasetRecord = jasmine.createSpy('updateDatasetRecord').and.returnValue($q.when());
            this.uploadData = jasmine.createSpy('uploadData').and.returnValue($q.when());
            this.initialize = jasmine.createSpy('initialize');
            this.getOntologyIdentifiers = jasmine.createSpy('getOntologyIdentifiers').and.returnValue([]);
            this.getRecordFromArray = jasmine.createSpy('getRecordFromArray').and.returnValue({});
            this.splitDatasetArray = jasmine.createSpy('splitDatasetArray').and.returnValue({});
        });
    });
}

export function mockDatasetState() {
    angular.mock.module(function($provide) {
        $provide.service('datasetStateService', function() {
            this.paginationConfig = {
                limit: 0,
                sortOption: {field: '', asc: true},
                pageIndex: 0,
                searchText: ''
            };
            this.totalSize = 0;
            this.links = {
                prev: '',
                next: ''
            };
            this.results = [];
            this.setResults = jasmine.createSpy('setResults');
            this.reset = jasmine.createSpy('reset');
            this.resetPagination = jasmine.createSpy('resetPagination');
            this.setPagination = jasmine.createSpy('setPagination');
            this.selectedDataset = '';
            this.openedDatasetId = '';
        })
    })
}

export function mockManchesterConverter() {
    angular.mock.module(function($provide) {
        $provide.service('manchesterConverterService', function() {
            this.getKeywords = jasmine.createSpy('getKeywords').and.returnValue([]);
            this.jsonldToManchester = jasmine.createSpy('jsonldToManchester').and.returnValue('');
            this.manchesterToJsonld = jasmine.createSpy('jsonldToManchester').and.returnValue({errorMessage: '', jsonld: []});
        });
    })
}

export function mockToastr() {
    angular.mock.module(function($provide) {
        $provide.service('toastr', function() {
            this.error = jasmine.createSpy('error');
            this.success = jasmine.createSpy('success');
            this.info = jasmine.createSpy('info');
            this.warning = jasmine.createSpy('warning');
        });
    });
}

export function mockDiscoverState() {
    angular.mock.module(function($provide) {
        $provide.service('discoverStateService', function() {
            this.explore = {
                active: true,
                breadcrumbs: ['Classes'],
                classDetails: [],
                classId: '',
                creating: false,
                editing: false,
                instance: {
                    changed: [],
                    entity: [{}],
                    metadata: {},
                    original: []
                },
                instanceDetails: {
                    currentPage: 1,
                    data: [],
                    limit: 99,
                    links: {
                        next: '',
                        prev: ''
                    },
                    total: 0
                },
                recordId: ''
            };
            this.query = {
                active: false
            };
            this.search = {
                active: false,
                datasetRecordId: '',
                filterMeta: [],
                noDomains: undefined,
                properties: undefined,
                queryConfig: {
                    isOrKeywords: false,
                    isOrTypes: false,
                    keywords: [],
                    types: [],
                    filters: []
                },
                results: undefined,
                targetedId: 'discover-search-results'
            };
            this.reset = jasmine.createSpy('reset');
            this.resetPagedInstanceDetails = jasmine.createSpy('resetPagedInstanceDetails');
            this.cleanUpOnDatasetDelete = jasmine.createSpy('cleanUpOnDatasetDelete');
            this.cleanUpOnDatasetClear = jasmine.createSpy('cleanUpOnDatasetClear');
            this.clickCrumb = jasmine.createSpy('clickCrumb');
            this.getInstance = jasmine.createSpy('getInstance').and.returnValue({});
            this.resetSearchQueryConfig = jasmine.createSpy('resetSearchQueryConfig');
        });
    });
}

export function mockExplore() {
    angular.mock.module(function($provide, $qProvider) {
        $qProvider.errorOnUnhandledRejections(false);
        $provide.service('exploreService', function($q) {
            this.getClassDetails = jasmine.createSpy('getClassDetails').and.returnValue($q.when([]));
            this.getClassInstanceDetails = jasmine.createSpy('getClassInstanceDetails').and.returnValue($q.when([]));
            this.getClassPropertyDetails = jasmine.createSpy('getClassPropertyDetails').and.returnValue($q.when([]));
            this.createInstance = jasmine.createSpy('createInstance').and.returnValue($q.when(''));
            this.getInstance = jasmine.createSpy('getInstance').and.returnValue($q.when({}));
            this.updateInstance = jasmine.createSpy('updateInstance').and.returnValue($q.when({}));
            this.deleteInstance = jasmine.createSpy('deleteInstance').and.returnValue($q.when());
            this.createPagedResultsObject = jasmine.createSpy('createPagedResultsObject').and.returnValue({});
        });
    });
}

export function mockExploreUtils() {
    angular.mock.module(function($provide, $qProvider) {
        $qProvider.errorOnUnhandledRejections(false);
        $provide.service('exploreUtilsService', function($q) {
            this.getInputType = jasmine.createSpy('getInputType').and.returnValue('');
            this.getPattern = jasmine.createSpy('getPattern').and.returnValue(/[a-zA-Z]/);
            this.isPropertyOfType = jasmine.createSpy('isPropertyOfType').and.returnValue(true);
            this.isBoolean = jasmine.createSpy('isBoolean').and.returnValue(true);
            this.createIdObj = jasmine.createSpy('createIdObj').and.returnValue({});
            this.createValueObj = jasmine.createSpy('createValueObj').and.returnValue({});
            this.getRange = jasmine.createSpy('getRange').and.returnValue('');
            this.contains = jasmine.createSpy('contains').and.returnValue(true);
            this.getNewProperties = jasmine.createSpy('getNewProperties').and.returnValue([]);
            this.removeEmptyProperties = jasmine.createSpy('removeEmptyProperties').and.returnValue({});
            this.removeEmptyPropertiesFromArray = jasmine.createSpy('removeEmptyPropertiesFromArray').and.returnValue([]);
            this.getReification = jasmine.createSpy('getReification');
            this.getClasses = jasmine.createSpy('getClasses').and.returnValue($q.when([]));
            this.getReferencedTitles = jasmine.createSpy('getReferencedTitles').and.returnValue($q.when());
        });
    });
}

export function mockSearch() {
    angular.mock.module(function($provide, $qProvider) {
        $qProvider.errorOnUnhandledRejections(false);
        $provide.service('searchService', function($q) {
            this.getPropertiesForDataset = jasmine.createSpy('getPropertiesForDataset').and.returnValue($q.when([]));
            this.createQueryString = jasmine.createSpy("createQueryString").and.returnValue('');
            this.submitSearch = jasmine.createSpy('submitSearch').and.returnValue($q.when({}));
            this.createExistenceQuery = jasmine.createSpy('createExistenceQuery').and.returnValue({});
            this.createContainsQuery = jasmine.createSpy('createContainsQuery').and.returnValue({});
            this.createExactQuery = jasmine.createSpy('createExactQuery').and.returnValue({});
            this.createRegexQuery = jasmine.createSpy('createRegexQuery').and.returnValue({});
            this.createRangeQuery = jasmine.createSpy('createRangeQuery').and.returnValue({});
            this.createBooleanQuery = jasmine.createSpy('createBooleanQuery').and.returnValue({});
        });
    });
}

export function mockProvManager() {
    angular.mock.module(function($provide, $qProvider) {
        $qProvider.errorOnUnhandledRejections(false);
        $provide.service('provManagerService', function($q) {
            this.activityTypes = [];
            this.getActivities = jasmine.createSpy('getActivities').and.returnValue($q.when({}));
        });
    });
}

export function mockMergeRequestManager() {
    angular.mock.module(function($provide, $qProvider) {
        $qProvider.errorOnUnhandledRejections(false);
        $provide.service('mergeRequestManagerService', function($q) {
            this.getRequests = jasmine.createSpy('getRequests').and.returnValue($q.when([]));
            this.createRequest = jasmine.createSpy('createRequest').and.returnValue($q.when());
            this.getRequest = jasmine.createSpy('getRequest').and.returnValue($q.when({}));
            this.deleteRequest = jasmine.createSpy('deleteRequest').and.returnValue($q.when());
            this.acceptRequest = jasmine.createSpy('acceptRequest').and.returnValue($q.when());
            this.updateRequest = jasmine.createSpy('updateRequest').and.returnValue($q.when());
            this.isAccepted = jasmine.createSpy('isAccepted').and.returnValue(false);
            this.getComments = jasmine.createSpy('getComments').and.returnValue($q.when([]));
            this.createComment = jasmine.createSpy('createComment').and.returnValue($q.when(''));
            this.deleteComment = jasmine.createSpy('deleteComment').and.returnValue($q.when());
        });
    });
}

export function mockMergeRequestsState() {
    angular.mock.module(function($provide, $qProvider) {
        $qProvider.errorOnUnhandledRejections(false);
        $provide.service('mergeRequestsStateService', function($q) {
            this.selected = undefined;
            this.acceptedFilter = false;
            this.requests = [];
            this.createRequest = false;
            this.createRequestStep = 0;
            this.requestConfig = {};
            this.initialize = jasmine.createSpy('initialize');
            this.reset = jasmine.createSpy('reset');
            this.setRequests = jasmine.createSpy('setRequests');
            this.setRequestDetails = jasmine.createSpy('setRequestDetails');
            this.startCreate = jasmine.createSpy('startCreate');
            this.resolveRequestConflicts = jasmine.createSpy('resolveRequestConflicts').and.returnValue($q.when());
            this.removeSource = jasmine.createSpy('removeSource').and.returnValue(true);
            this.getSourceEntityNames = jasmine.createSpy('getSourceEntityNames').and.returnValue($q.when());
            this.updateRequestConfigDifference = jasmine.createSpy('updateRequestConfigDifference').and.returnValue($q.when());
            this.updateRequestConfigBranch = jasmine.createSpy('updateRequestConfigBranch');
        });
    });
}

export function mockPolicyManager() {
    angular.mock.module(function($provide, $qProvider) {
        $qProvider.errorOnUnhandledRejections(false);
        $provide.service('policyManagerService', function($q) {
            this.actionCreate = 'create';
            this.actionRead = 'read';
            this.actionUpdate = 'update';
            this.actionDelete = 'delete';
            this.subjectId = 'subjectId';
            this.resourceId = 'resourceId';
            this.actionId = 'actionId';
            this.subjectCategory = 'actionCategory';
            this.resourceCategory = 'resourceCategory';
            this.actionCategory = 'actionCategory';
            this.stringEqual = 'stringEqual';
            this.getPolicies = jasmine.createSpy('getPolicies').and.returnValue($q.when([]));
            this.getPolicy = jasmine.createSpy('getPolicy').and.returnValue($q.when({}));
            this.updatePolicy = jasmine.createSpy('updatePolicy').and.returnValue($q.when());
        });
    });
}

export function mockPolicyEnforcement() {
    angular.mock.module(function($provide, $qProvider) {
        $qProvider.errorOnUnhandledRejections(false);
        $provide.service('policyEnforcementService', function($q) {
            this.permit = 'Permit';
            this.deny = 'Deny';
            this.indeterminate = 'Indeterminate';
            this.evaluateRequest = jasmine.createSpy('evaulateRequest').and.returnValue($q.when());
        });
    });
}

export function mockModal() {
    angular.mock.module(function($provide) {
        $provide.service('modalService', function() {
            this.openModal = jasmine.createSpy('openModal');
            this.openConfirmModal = jasmine.createSpy('openConfirmModal');
        });
    });
}

export function mockRecordPermissionsManager() {
    angular.mock.module(function($provide, $qProvider) {
        $qProvider.errorOnUnhandledRejections(false);
        $provide.service('recordPermissionsManagerService', function($q) {
            this.getRecordPolicy = jasmine.createSpy('getRecordPolicy').and.returnValue($q.when({}));
            this.updateRecordPolicy = jasmine.createSpy('getRecordPolicy').and.returnValue($q.when());
        });
    });
}

export function flushAndVerify($httpBackend) {
    $httpBackend.flush();
    $httpBackend.verifyNoOutstandingExpectation();
    $httpBackend.verifyNoOutstandingRequest();
}

export function mockYasguiService() {
    angular.mock.module(function($provide, $qProvider) {
        $qProvider.errorOnUnhandledRejections(false);
        $provide.service('yasguiService', function($q) {
            this.reset = jasmine.createSpy('reset');
            this.initYasgui = jasmine.createSpy('initYasgui');
            this.getYasgui = jasmine.createSpy('getYasgui').and.returnValue({});
        });
    });
}
