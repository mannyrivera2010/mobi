package com.mobi.security.policy.impl.xacml;

/*-
 * #%L
 * security.policy.impl
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2016 - 2018 iNovex Information Systems, Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import com.mobi.ontologies.rdfs.Resource;
import com.mobi.rdf.api.IRI;
import com.mobi.rdf.api.Literal;
import com.mobi.rdf.orm.test.OrmEnabledTestCase;
import com.mobi.repository.api.Repository;
import com.mobi.repository.impl.sesame.SesameRepositoryWrapper;
import com.mobi.security.policy.api.AttributeDesignator;
import com.mobi.security.policy.api.Decision;
import com.mobi.security.policy.api.PIP;
import com.mobi.security.policy.api.Policy;
import com.mobi.security.policy.api.Request;
import com.mobi.security.policy.api.Response;
import com.mobi.security.policy.api.Status;
import com.mobi.security.policy.api.cache.PolicyCache;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.sail.memory.MemoryStore;
import org.w3c.dom.Document;
import org.wso2.balana.AbstractPolicy;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import javax.cache.Cache;
import javax.xml.parsers.DocumentBuilderFactory;

public class BalanaPDPTest extends OrmEnabledTestCase {
    private Repository repo;
    private BalanaPDP pdp;
    private BalanaPRP prp;

    private IRI policy1 = VALUE_FACTORY.createIRI("http://mobi.com/policies/policy1");
    private IRI policy2 = VALUE_FACTORY.createIRI("http://mobi.com/policies/policy2");
    private IRI policy3 = VALUE_FACTORY.createIRI("http://mobi.com/policies/policy3");
    private IRI userX = VALUE_FACTORY.createIRI("http://mobi.com/users/UserX");
    private IRI resource = VALUE_FACTORY.createIRI("http://mobi.com/catalog-local");
    private IRI createAction = VALUE_FACTORY.createIRI("http://mobi.com/ontologies/policy#Create");
    private Literal actionType = VALUE_FACTORY.createLiteral("http://mobi.com/ontologies/ontology-editor#OntologyRecord");

    @Mock
    private PIP pip;

    @Mock
    private PolicyCache policyCache;

    @Mock
    private Cache<String, Policy> cache;

    @Mock
    private Iterator<Cache.Entry<String, Policy>> it;

    @Mock
    private Cache.Entry<String, Policy> entry;

    @Before
    public void setUp() throws Exception {
        repo = new SesameRepositoryWrapper(new SailRepository(new MemoryStore()));
        repo.initialize();

        MockitoAnnotations.initMocks(this);
        when(pip.findAttribute(any(AttributeDesignator.class), any(Request.class))).thenReturn(Collections.emptyList());

        when(policyCache.getPolicyCache()).thenReturn(Optional.of(cache));
        when(cache.iterator()).thenReturn(it);
        when(it.hasNext()).thenReturn(true, false);
        when(it.next()).thenReturn(entry);

        prp = new BalanaPRP();
        prp.setVf(VALUE_FACTORY);
        prp.setPolicyCache(policyCache);
        pdp = new BalanaPDP();
        pdp.addPIP(pip);
        pdp.setBalanaPRP(prp);
        pdp.setVf(VALUE_FACTORY);
        pdp.setUp();
    }

    @Test
    public void simplePermitTest() throws Exception {
        // Setup:
        loadPolicy(policy1);
        BalanaRequest request = new BalanaRequest.Builder(userX, resource, createAction, OffsetDateTime.now(), VALUE_FACTORY).build();

        Response result = pdp.evaluate(request);
        System.out.println(result);
        assertEquals(Decision.PERMIT, result.getDecision());
        assertEquals(Status.OK, result.getStatus());
        assertTrue(result.getPolicyIds().contains(policy1));
    }

    @Test
    public void missingAttributeTest() throws Exception {
        // Setup:
        loadPolicy(policy2);
        BalanaRequest.Builder builder = new BalanaRequest.Builder(userX, resource, createAction, OffsetDateTime.now(), VALUE_FACTORY);
        builder.addActionAttr(Resource.type_IRI, actionType);

        Response result = pdp.evaluate(builder.build());
        System.out.println(result);
        assertEquals(Decision.DENY, result.getDecision());
        assertEquals(Status.OK, result.getStatus());
        assertTrue(result.getPolicyIds().contains(policy2));
    }

    @Test
    public void unsupportedCategoryInRuleTest() throws Exception {
        // Setup:
        loadPolicy(policy3);
        BalanaRequest.Builder builder = new BalanaRequest.Builder(userX, resource, createAction, OffsetDateTime.now(), VALUE_FACTORY);
        builder.addActionAttr(Resource.type_IRI, actionType);

        Response result = pdp.evaluate(builder.build());
        System.out.println(result);
        assertEquals(Decision.DENY, result.getDecision());
        assertEquals(Status.OK, result.getStatus());
        assertTrue(result.getPolicyIds().contains(policy3));
    }

    private void loadPolicy(IRI policyId) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/" + policyId.getLocalName() + ".xml")) {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            docFactory.setNamespaceAware(true);
            Document doc = docFactory.newDocumentBuilder().parse(in);
            AbstractPolicy abstractPolicy = org.wso2.balana.Policy.getInstance(doc.getDocumentElement());
            Policy policy = new BalanaPolicy(abstractPolicy, VALUE_FACTORY);
            when(entry.getKey()).thenReturn(policyId.stringValue());
            when(entry.getValue()).thenReturn(policy);
        }
    }
}
