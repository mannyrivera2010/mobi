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
import * as Yasgui from '@triply/yasgui/build/yasgui.min.js';
import * as YasrTurtlePlugin from '../../vendor/YASGUI/plugins/turtle/turtle';
import * as YasrRdfXmlPlugin from '../../vendor/YASGUI/plugins/rdfXml/rdfXml';
import * as YasrJsonLDlPlugin from '../../vendor/YASGUI/plugins/jsonLD/jsonLD';
import { hasClass } from "../../vendor/YASGUI/plugins/utils/yasguiUtil";

import { merge } from 'lodash';
import { format } from 'path';

/**
 * @ngdoc service
 * @name shared.service:yasguiService
 * @requires shared.service:prefixes
 *
 */
yasguiService.$inject = ['REST_PREFIX'];

function yasguiService(REST_PREFIX) {
    const self = this;
    const defaultUrl : URL =  new URL(REST_PREFIX + 'sparql/limited-results', document.location.origin);
    let hasInitialized = false
    let mimetype = 'application/json';
    let yasrContainerSelector = '.yasr .CodeMirror-scroll, .yasr .dataTables_wrapper ';

    let yasrRootElement : HTMLElement = <any>{};
    const initPlugins = () => {
        //
        if (Yasgui.Yasr) {
            Yasgui.Yasr.registerPlugin("turtle", YasrTurtlePlugin.default as any);
            Yasgui.Yasr.registerPlugin("rdfXml", YasrRdfXmlPlugin.default as any);
            Yasgui.Yasr.registerPlugin("jsonLD", YasrJsonLDlPlugin.default as any);
        } else {
            if (window) {
                (window as any).Yasr.registerPlugin("turtle", YasrTurtlePlugin.default as any);
                (window as any).Yasr.registerPlugin("rdfXml", YasrRdfXmlPlugin.default as any);
                (window as any).Yasr.registerPlugin("jsonLD", YasrJsonLDlPlugin.default as any);
            }
        }
        Yasgui.Yasr.defaults.pluginOrder = [ "table", "turtle" , "rdfXml", "jsonLD", "response"];
    }

    const initEvents = () => {

        self.yasgui.getTab().yasr.on('change',(instance: Yasgui.Yasr, plugin: Plugin) => {
            if(isPluginEnabled(instance?.selectedPlugin)) {
                refreshPluginData();
            }
        });

        self.yasgui.getTab().yasqe.on("resize",(element) => {
           let yasrContentElement = <HTMLElement>document.querySelector(yasrContainerSelector);
           yasrContentElement.style.height = getYasContainerHeight();
        });

        self.yasgui.getTab().yasr.on("drawn",(instance: Yasgui.yasr, plugin: Plugin) => {
            let yasrCodeMirrorElement = <HTMLElement>document.querySelector(yasrContainerSelector);
            yasrCodeMirrorElement.style.height = getYasContainerHeight();
        });

    }


    const getYasContainerHeight = () =>  {
        let yasqeWrapper = <HTMLElement>document.querySelector('.yasqe')
        let style  = `calc( ${window.innerHeight - 280}px - ${yasqeWrapper.offsetHeight}px)`;
        return style;
    }

    const getFormat = (type) => {
       const formatType =  {
           'turtle': 'text/turtle',
           'rdfXml': 'application/rdf+xml',
           'jsonLD': 'application/ld+json',
           'table': 'application/json'
       }
       return formatType?.[type] || formatType.jsonLD;
    }

    const isPluginEnabled = (plugin) => {
        let pluginElement = document.querySelector(`.select_${plugin}`);
        if(pluginElement) {
            return !hasClass(pluginElement, 'disabled');
        } else {
            return false;
        }
    }
    const getSelectedMimeType =  () => getFormat(self.yasgui.getTab().yasr.selectedPlugin);

    const getEndpointConfig= () => {
        let format = getSelectedMimeType();
        let url = getUrl(format);
       return url;
    }

    const setRequestConfig = () => {
        let url = getEndpointConfig()
        const { headers } = self.yasgui.getTab().getRequestConfig();

        headers.Accept = getSelectedMimeType();
        self.yasgui.getTab().setRequestConfig({
            endpoint: url, 
            headers: headers
        });
    }

    const getUrl = (format: string = mimetype) => {
        const searchValue = 'returnFormat';
        if (!defaultUrl.searchParams.has(searchValue)) {
            defaultUrl.searchParams.append(searchValue, format)
        } else {
            defaultUrl.searchParams.set(searchValue, format)
        }

        return defaultUrl.href;
    }

    const refreshPluginData = () => {
        let selectedPlugin = self.yasgui.getTab().yasr.selectedPlugin;
        if (self.yasgui.getTab().yasr.drawnPlugin && selectedPlugin) {
            setRequestConfig();
            self.submitQuery();
        }
    }

    const getDefaultConfig =  () => {
        return {
            requestConfig : {
                method: 'GET',
                endpoint: getUrl()
            },
            persistencyExpire: 0,
            populateFromUrl: false,
            copyEndpointOnNewTab: false
        };

    }

    const handleYasrVisivility = () => {
        let className = 'hide'
        let isElementHidden = hasClass(yasrRootElement,className);
        let method = isElementHidden ? 'remove' : 'add'
        let hasResults = !!self.yasgui.getTab().yasr.results;

        if(method === 'add' && !hasResults) {
            yasrRootElement.classList.add(className);
        } else {
            if(isElementHidden) {
                yasrRootElement.classList.remove(className);
            }
        }
    }

    const overwritePlugins = () => {
        //overwrite table plugin
        // update canHandleResults
        // render plugin only when content type is EQ to json
        self.yasgui.getTab().getYasr().plugins['table'].canHandleResults = function() {
            return !!this.yasr.results
                && this.yasr.results?.getVariables()
                && this.yasr.results?.getVariables().length > 0
                && this.yasr.results.getContentType() === 'application/json';
        }
    }

    self.initYasgui = (element, config = {}) => {
        const localConfig = getDefaultConfig();
        const configuration = merge({}, localConfig, config );
        // Init YASGUI
        initPlugins();
        self.yasgui = new Yasgui(element, configuration);

        hasInitialized = true;
        overwritePlugins();
        // Init UI events
        initEvents();
        yasrRootElement =  self.yasgui.getTab().yasr.rootEl;

        handleYasrVisivility();
        document.querySelector(`.select_response`).classList.add('hide');
        return self.yasgui;
    }

    // fire a new query
    self.submitQuery  = (queryConfig = {}) => {
        if(hasInitialized) {
            handleYasrVisivility();
            self.yasgui.getTab().yasqe.query(queryConfig);
        } else {
            throw 'Yasgui has not ben inizialize!';
        }
    }
}

export default yasguiService;
