/*-
 * #%L
 * com.mobi.web
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2016 - 2020 iNovex Information Systems, Inc.
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
/**
 * Make sure not to include any deps from our main index file. That way, we can easily publish the publin as standalone build
 */
import { Plugin } from '@triply/yasr/src/plugins';
import Yasr from '@triply/yasr/build/yasr.min.js';

const CodeMirror = require("codemirror");
require("codemirror/addon/fold/foldcode.js");
require("codemirror/addon/fold/foldgutter.js");
require("codemirror/addon/fold/xml-fold.js");
require("codemirror/addon/fold/brace-fold.js");

require("codemirror/addon/edit/matchbrackets.js");
require("codemirror/mode/javascript/javascript.js");
require("codemirror/lib/codemirror.css");
import {drawFontAwesomeIconAsSvg, drawSvgStringAsElement, removeClass, addClass} from "../utils/yasguiUtil";
import * as faAlignLeft from "@fortawesome/free-solid-svg-icons/faBug";
import * as imgs from "@triply/yasr/src/imgs";

export interface PlugingConfig {
    maxLines: number
}

export default class JsonLD implements Plugin<PlugingConfig> {
    private config: PlugingConfig;
    private yasr : Yasr;
    private mode;
    private overLay: HTMLDivElement | undefined;
    //@create interface for cm.
    private cm = {
        getWrapperElement() {
            return undefined;
        },
        setValue(limitData: any) {
            
        },
        refresh() {

        }
    };
    // public attributes
    public priority = 11;
    public label = "JSON-LD";
    public getIcon() {
        return drawSvgStringAsElement(drawFontAwesomeIconAsSvg(faAlignLeft));
    }

    constructor(yasr: Yasr) {
        this.yasr = yasr;
        this.mode = 'application/ld+json';
        this.config = JsonLD.defaults;
        if(yasr.config.plugins['jsonLD'] && yasr.config.plugins['jsonLD'].dynamicConfig) {
            this.config = {
                ...this.config,
                ...yasr.config.plugins['jsonLD'].dynamicConfig
            }
        }
    }

    // Draw the resultset. This plugin simply draws the string 'True' or 'False'
    draw() {
        // When the original response is empty, use an empty string
        let value = this.yasr.results?.getOriginalResponseAsString() || "";
        let contentType = this.yasr.results?.getContentType();
        if ( contentType === 'application/ld+json') {
            value = JSON.stringify(value, null, 4);
        }
        const codemirrorOpts = {
            readOnly: true,
            lineNumbers: true,
            lineWrapping: true,
            foldGutter: true,
            gutters: ["CodeMirror-linenumbers", "CodeMirror-foldgutter"],
            value: value
        };

        //codemirrorOpts['mode']  = this.mode;
        codemirrorOpts['mode'] = contentType,
        this.cm = CodeMirror(this.yasr.resultsEl, codemirrorOpts);
       
    }



    download() {
        if (!this.yasr.results) return;
        const contentType = this.yasr.results.getContentType();
        const type = this.yasr.results.getType();
        return {
            getData: () => {
                return this.yasr.results?.getOriginalResponseAsString() || "";
            },
            filename: "queryResults" + (type ? "." + type : ""),
            contentType: contentType ? contentType : "text/plain",
            title: "Download result"
        };
    }

    // A required function, used to indicate whether this plugin can draw the current
    // resultset from yasr
    canHandleResults() {
        if (!this.yasr.results) return false;
        if (!this.yasr.results.getOriginalResponseAsString) return false;
        if (this.yasr.results?.getContentType() === 'application/json') return false;
        const response = this.yasr.results.getOriginalResponseAsString();
        if ((!response || response.length == 0) && this.yasr.results.getError()) return false; //in this case, show exception instead, as we have nothing to show anyway
        return true;
    }

   
    public static defaults: PlugingConfig = {
        maxLines: 30
    };
}

