/*-
 * #%L
 * itests-web
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
var adminUsername = "admin"
var adminPassword = "admin"
var Onto1 = process.cwd()+ '/src/test/resources/ontologies/test-local-imports-1.ttl'
var Onto2 = process.cwd()+ '/src/test/resources/ontologies/test-local-imports-2.ttl'
var Onto3 = process.cwd()+ '/src/test/resources/ontologies/test-local-imports-3.ttl'

module.exports = {
    '@tags': ['sanity', "ontology-editor"],

    'Step 1: login as admin' : function(browser) {
        browser
            .url('https://localhost:8443/mobi/index.html#/home')
            .waitForElementVisible('input#username')
            .waitForElementVisible('input#password')
            .setValue('input#username', adminUsername)
            .setValue('input#password', adminPassword)
            .click('button[type=submit]')
    },

    'Step 2: check for visibility of home elements' : function(browser) {
        browser
            .waitForElementVisible('.home-page')
    },

    'Step 3: navigate to the Ontology Editor page' : function (browser) {
        browser
            .click('xpath', '//div//ul//a[@class="nav-link"][@href="#/ontology-editor"]')
    },

    'Step 4: click upload ontology' : function (browser) {
        browser
            .click('xpath', '//div[@class="btn-container"]//button[text()[contains(.,"Upload Ontology")]]')
    },

    'Step 5: Upload an Ontology' : function (browser) {
        browser
            .setValue('input[type=file]', Onto1)
            .click('upload-ontology-overlay div.modal-footer button.btn')
            .assert.elementNotPresent('upload-ontology-overlay div.modal-header button.close span')
            .setValue('input[type=file]', Onto2)
            .click('upload-ontology-overlay div.modal-footer button.btn')
            .assert.elementNotPresent('upload-ontology-overlay div.modal-header button.close span')
            .setValue('input[type=file]', Onto3)
    },

    'Step 6: Submit all ontology files' : function (browser) {
        browser
            .waitForElementVisible('upload-ontology-overlay')
            .click('xpath', '//button[text()[contains(.,"Submit All")]]')
    },

    'Step 7: Validate Ontology Appearance' : function (browser) {
        browser
            .waitForElementVisible('div.ontologies')
            .assert.elementNotPresent('div.modal-header')
            .waitForElementVisible('div.ontologies')
            .useXpath()
            .assert.visible('//div[contains(@class, "list-group")]//span[text()[contains(.,"test-local-imports-1.ttl")]]')
            .assert.visible('//div[contains(@class, "list-group")]//span[text()[contains(.,"test-local-imports-2.ttl")]]')
            .assert.visible('//div[contains(@class, "list-group")]//span[text()[contains(.,"test-local-imports-3.ttl")]]')
            .useCss()
    },

    'Step 8: Click on Ontology called “test-local-imports-1.ttl' : function (browser) {
        browser
            .click('xpath', '//div[contains(@class, "list-group")]//div//span[text()[contains(.,"test-local-imports-1.ttl")]]')
    },

    'Step 9: Click classes tab' : function (browser) {
        browser
            .waitForElementVisible('div.material-tabset li.nav-item')
            .click('xpath', '//div[contains(@class, "material-tabset")]//li[contains(@class, "nav-item")]//span[text()[contains(., "Classes")]]')
    },

    'Step 10: Check for Ontology classes' : function (browser) {
        browser
            .waitForElementVisible('div.tree')
            .useXpath()
            .waitForElementVisible({locateStrategy: 'xpath', selector: '//div[contains(@class, "tree-item-wrapper")]//span[text()[contains(., "Class 0")]]'})
            .waitForElementVisible({locateStrategy: 'xpath', selector: '//div[contains(@class, "tree-item-wrapper")]//span[text()[contains(., "Class 2")]]'})
            .click('xpath', '//div[contains(@class, "tree-item-wrapper")]//span[text()[contains(., "Class 2")]]//ancestor::a/i[contains(@class, "fa-plus-square-o")]')
            .waitForElementVisible({locateStrategy: 'xpath', selector: '//div[contains(@class, "tree-item-wrapper")]//span[text()[contains(., "Class 1")]]'})
            .assert.attributeEquals('//div[contains(@class, "tree-item-wrapper")]//span[text()[contains(., "Class 1")]]//ancestor::tree-item', 'data-path-to', 'https://mobi.com/records#93cf697a-6e7e-4f2b-b537-93d81fe9db6d.http://mobi.com/ontology/test-local-imports-2#Class2.http://mobi.com/ontology/test-local-imports-1#Class1')
            .waitForElementVisible({locateStrategy: 'xpath', selector: '//div[contains(@class, "tree-item-wrapper")]//span[text()[contains(., "Class 3")]]'})
    }


}
