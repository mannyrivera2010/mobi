package com.mobi.security.policy.rest.impl;

import static com.mobi.persistence.utils.ResourceUtils.encode;
import static com.mobi.rdf.orm.test.OrmEnabledTestCase.getValueFactory;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import com.mobi.rdf.api.IRI;
import com.mobi.rdf.api.ValueFactory;
import com.mobi.rest.util.MobiRestTestNg;
import com.mobi.rest.util.UsernameTestFilter;
import com.mobi.security.policy.api.xacml.XACMLPolicy;
import com.mobi.security.policy.api.xacml.XACMLPolicyManager;
import com.mobi.security.policy.api.xacml.jaxb.PolicyType;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Optional;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;

public class RecordPermissionsRestImplTest extends MobiRestTestNg {
    private RecordPermissionsRestImpl rest;
    private ValueFactory vf;

    private String recordJson;
    private XACMLPolicy recordPolicy;
    private IRI recordPolicyId;
    private IRI policyPolicyId;

    @Mock
    private XACMLPolicyManager policyManager;

    @Override
    protected Application configureApp() throws Exception {
        MockitoAnnotations.initMocks(this);
        vf = getValueFactory();

        recordJson = IOUtils.toString(getClass().getResourceAsStream("/recordPolicy.json"), "UTF-8");
        recordPolicy = new XACMLPolicy(IOUtils.toString(getClass().getResourceAsStream("/recordPolicy.xml"), "UTF-8"), vf);
        recordPolicyId = vf.createIRI("http://mobi.com/policies/record/https%3A%2F%2Fmobi.com%2Frecords%testRecord1");
        policyPolicyId = vf.createIRI("http://mobi.com/policies/policy/record/https%3A%2F%2Fmobi.com%2Frecords%testRecord1");

        rest = new RecordPermissionsRestImpl();
        rest.setVf(vf);
        rest.setPolicyManager(policyManager);

        return new ResourceConfig()
                .register(rest)
                .register(UsernameTestFilter.class)
                .register(MultiPartFeature.class);
    }

    @Override
    protected void configureClient(ClientConfig config) {
        config.register(MultiPartFeature.class);
    }

    @BeforeMethod
    public void setUpMocks() throws Exception {
        reset(policyManager);
        when(policyManager.getPolicy(recordPolicyId)).thenReturn(Optional.of(recordPolicy));

    }
    /* GET /policies/record-permissions/{policyId} */

    @Test
    public void retrieveRecordPolicyTest() {
        Response response = target().path("record-permissions/" + encode(recordPolicyId.stringValue())).request().get();
        assertEquals(response.getStatus(), 200);
        verify(policyManager).getPolicy(recordPolicyId);
        try {
            JSONObject result = JSONObject.fromObject(response.readEntity(String.class));
            assertEquals(result, JSONObject.fromObject(recordJson));

        } catch (Exception e) {
            fail("Expected no exception, but got: " + e.getMessage());
        }
    }

    /* PUT /policies/record-permissions/{policyId} */

    @Test
    public void updateRecordPolicyTest() {
        // Setup
        when(policyManager.createPolicy(any(PolicyType.class))).thenReturn(recordPolicy);

        Response response = target().path("record-permissions/" + encode(recordPolicyId.stringValue())).request().put(Entity.json(recordJson));
        assertEquals(response.getStatus(), 200);
        verify(policyManager).getPolicy(recordPolicyId);
        verify(policyManager).createPolicy(any(PolicyType.class));
        verify(policyManager).updatePolicy(any(XACMLPolicy.class));
    }
}
