package com.mobi.catalog.rest.impl;

/*-
 * #%L
 * com.mobi.catalog.rest
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2016 - 2017 iNovex Information Systems, Inc.
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

import static com.mobi.rest.util.RestUtils.encode;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import com.mobi.catalog.api.mergerequest.MergeRequestConfig;
import com.mobi.catalog.api.mergerequest.MergeRequestManager;
import com.mobi.catalog.api.ontologies.mergerequests.MergeRequest;
import com.mobi.catalog.api.ontologies.mergerequests.MergeRequestFactory;
import com.mobi.exception.MobiException;
import com.mobi.jaas.api.engines.EngineManager;
import com.mobi.jaas.api.ontologies.usermanagement.User;
import com.mobi.jaas.api.ontologies.usermanagement.UserFactory;
import com.mobi.persistence.utils.api.SesameTransformer;
import com.mobi.rdf.api.Model;
import com.mobi.rdf.api.ModelFactory;
import com.mobi.rdf.api.Resource;
import com.mobi.rdf.api.Statement;
import com.mobi.rdf.api.ValueFactory;
import com.mobi.rdf.core.impl.sesame.LinkedHashModelFactory;
import com.mobi.rdf.core.impl.sesame.SimpleValueFactory;
import com.mobi.rdf.core.utils.Values;
import com.mobi.rdf.orm.conversion.ValueConverterRegistry;
import com.mobi.rdf.orm.conversion.impl.DefaultValueConverterRegistry;
import com.mobi.rdf.orm.conversion.impl.DoubleValueConverter;
import com.mobi.rdf.orm.conversion.impl.FloatValueConverter;
import com.mobi.rdf.orm.conversion.impl.IRIValueConverter;
import com.mobi.rdf.orm.conversion.impl.IntegerValueConverter;
import com.mobi.rdf.orm.conversion.impl.LiteralValueConverter;
import com.mobi.rdf.orm.conversion.impl.ResourceValueConverter;
import com.mobi.rdf.orm.conversion.impl.ShortValueConverter;
import com.mobi.rdf.orm.conversion.impl.StringValueConverter;
import com.mobi.rdf.orm.conversion.impl.ValueValueConverter;
import com.mobi.rest.util.MobiRestTestNg;
import com.mobi.rest.util.UsernameTestFilter;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.Optional;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public class MergeRequestRestImplTest extends MobiRestTestNg {
    private MergeRequestRestImpl rest;
    private MergeRequestFactory mergeRequestFactory;
    private UserFactory userFactory;
    private ValueFactory vf;
    private ModelFactory mf;
    private ValueConverterRegistry vcr;
    private MergeRequest request;
    private User user;

    private final String RECORD_ID = "http://mobi.com/records#record";
    private final String BRANCH_ID = "http://mobi.com/branches#branch";

    @Mock
    private MergeRequestManager requestManager;

    @Mock
    private EngineManager engineManager;

    @Mock
    private SesameTransformer transformer;

    @Override
    protected Application configureApp() throws Exception {
        vf = SimpleValueFactory.getInstance();
        mf = LinkedHashModelFactory.getInstance();
        vcr = new DefaultValueConverterRegistry();

        mergeRequestFactory = new MergeRequestFactory();
        mergeRequestFactory.setModelFactory(mf);
        mergeRequestFactory.setValueFactory(vf);
        mergeRequestFactory.setValueConverterRegistry(vcr);
        vcr.registerValueConverter(mergeRequestFactory);

        userFactory = new UserFactory();
        userFactory.setModelFactory(mf);
        userFactory.setValueFactory(vf);
        userFactory.setValueConverterRegistry(vcr);
        vcr.registerValueConverter(userFactory);

        vcr.registerValueConverter(new ResourceValueConverter());
        vcr.registerValueConverter(new IRIValueConverter());
        vcr.registerValueConverter(new DoubleValueConverter());
        vcr.registerValueConverter(new IntegerValueConverter());
        vcr.registerValueConverter(new FloatValueConverter());
        vcr.registerValueConverter(new ShortValueConverter());
        vcr.registerValueConverter(new StringValueConverter());
        vcr.registerValueConverter(new ValueValueConverter());
        vcr.registerValueConverter(new LiteralValueConverter());

        MockitoAnnotations.initMocks(this);
        when(transformer.sesameModel(any(Model.class)))
                .thenAnswer(i -> Values.sesameModel(i.getArgumentAt(0, Model.class)));
        when(transformer.sesameStatement(any(Statement.class)))
                .thenAnswer(i -> Values.sesameStatement(i.getArgumentAt(0, Statement.class)));
        when(transformer.mobiModel(any(org.openrdf.model.Model.class)))
                .thenAnswer(i -> Values.mobiModel(i.getArgumentAt(0, org.openrdf.model.Model.class)));

        request = mergeRequestFactory.createNew(vf.createIRI("http://mobi.com/merge-requests#1"));
        Model contextModel = mf.createModel();
        request.getModel().forEach(statement -> contextModel.add(statement.getSubject(), statement.getPredicate(), statement.getObject(), request.getResource()));
        request = mergeRequestFactory.getExisting(request.getResource(), contextModel).get();
        user = userFactory.createNew(vf.createIRI("http://test.org/" + UsernameTestFilter.USERNAME));

        rest = new MergeRequestRestImpl();
        rest.setManager(requestManager);
        rest.setEngineManager(engineManager);
        rest.setTransformer(transformer);
        rest.setVf(vf);
        rest.setMf(mf);

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
        when(requestManager.getMergeRequests()).thenReturn(Collections.singleton(request));
        when(requestManager.createMergeRequest(any(MergeRequestConfig.class))).thenReturn(request);
        when(requestManager.getMergeRequest(any(Resource.class))).thenReturn(Optional.empty());
        when(requestManager.getMergeRequest(request.getResource())).thenReturn(Optional.of(request));

        when(engineManager.retrieveUser(anyString())).thenReturn(Optional.empty());
        when(engineManager.retrieveUser(UsernameTestFilter.USERNAME)).thenReturn(Optional.of(user));
    }

    @AfterMethod
    public void resetMocks() {
        reset(requestManager, engineManager);
    }

    /* GET merge-requests */

    @Test
    public void getMergeRequestsTest() {
        Response response = target().path("merge-requests").request().get();
        assertEquals(response.getStatus(), 200);
        verify(requestManager).getMergeRequests();
        try {
            JSONArray result = JSONArray.fromObject(response.readEntity(String.class));
            assertEquals(result.size(), 1);
            JSONArray requestArr = result.getJSONArray(0);
            assertEquals(requestArr.size(), 1);
            JSONObject requestObj = requestArr.getJSONObject(0);
            assertFalse(requestObj.containsKey("@graph"));
            assertTrue(requestObj.containsKey("@id"));
            assertEquals(requestObj.getString("@id"), request.getResource().stringValue());
        } catch (Exception e) {
            fail("Expected no exception, but got: " + e.getMessage());
        }
    }

    @Test
    public void getMergeRequestsWithMissingRepoTest() {
        // Setup
        doThrow(new IllegalStateException()).when(requestManager).getMergeRequests();

        Response response = target().path("merge-requests").request().get();
        assertEquals(response.getStatus(), 500);
        verify(requestManager).getMergeRequests();
    }

    @Test
    public void getMergeRequestsWithErrorTest() {
        // Setup
        doThrow(new MobiException()).when(requestManager).getMergeRequests();

        Response response = target().path("merge-requests").request().get();
        assertEquals(response.getStatus(), 500);
        verify(requestManager).getMergeRequests();
    }

    /* POST merge-requests */

    @Test
    public void createMergeRequestTest() {
        // Setup:
        FormDataMultiPart fd = new FormDataMultiPart();
        fd.field("title", "Title");
        fd.field("description", "Description");
        fd.field("recordId", RECORD_ID);
        fd.field("sourceBranchId", BRANCH_ID);
        fd.field("targetBranchId", BRANCH_ID);
        fd.field("assignees", UsernameTestFilter.USERNAME);

        Response response = target().path("merge-requests").request().post(Entity.entity(fd, MediaType.MULTIPART_FORM_DATA));
        assertEquals(response.getStatus(), 201);
        verify(engineManager, atLeastOnce()).retrieveUser(UsernameTestFilter.USERNAME);
        verify(requestManager).createMergeRequest(any(MergeRequestConfig.class));
        verify(requestManager).addMergeRequest(any(MergeRequest.class));
        assertEquals(request.getResource().stringValue(), response.readEntity(String.class));
    }

    @Test
    public void createMergeRequestWithInvalidAssigneeTest() {
        // Setup:
        FormDataMultiPart fd = new FormDataMultiPart();
        fd.field("title", "Title");
        fd.field("recordId", RECORD_ID);
        fd.field("sourceBranchId", BRANCH_ID);
        fd.field("targetBranchId", BRANCH_ID);
        fd.field("assignees", "error");

        Response response = target().path("merge-requests").request().post(Entity.entity(fd, MediaType.MULTIPART_FORM_DATA));
        assertEquals(response.getStatus(), 400);
        verify(engineManager, atLeastOnce()).retrieveUser(UsernameTestFilter.USERNAME);
        verify(requestManager, times(0)).createMergeRequest(any(MergeRequestConfig.class));
        verify(requestManager, times(0)).addMergeRequest(any(MergeRequest.class));
    }

    @Test
    public void createMergeRequestWithoutTitleTest() {
        // Setup:
        FormDataMultiPart fd = new FormDataMultiPart();
        fd.field("recordId", RECORD_ID);
        fd.field("sourceBranchId", BRANCH_ID);
        fd.field("targetBranchId", BRANCH_ID);

        Response response = target().path("merge-requests").request().post(Entity.entity(fd, MediaType.MULTIPART_FORM_DATA));
        assertEquals(response.getStatus(), 400);
        verify(engineManager, times(0)).retrieveUser(UsernameTestFilter.USERNAME);
        verify(requestManager, times(0)).createMergeRequest(any(MergeRequestConfig.class));
        verify(requestManager, times(0)).addMergeRequest(any(MergeRequest.class));
    }

    @Test
    public void createMergeRequestWithoutRecordTest() {
        // Setup:
        FormDataMultiPart fd = new FormDataMultiPart();
        fd.field("title", "Title");
        fd.field("sourceBranchId", BRANCH_ID);
        fd.field("targetBranchId", BRANCH_ID);

        Response response = target().path("merge-requests").request().post(Entity.entity(fd, MediaType.MULTIPART_FORM_DATA));
        assertEquals(response.getStatus(), 400);
        verify(engineManager, times(0)).retrieveUser(UsernameTestFilter.USERNAME);
        verify(requestManager, times(0)).createMergeRequest(any(MergeRequestConfig.class));
        verify(requestManager, times(0)).addMergeRequest(any(MergeRequest.class));
    }

    @Test
    public void createMergeRequestWithoutSourceTest() {
        // Setup:
        FormDataMultiPart fd = new FormDataMultiPart();
        fd.field("title", "Title");
        fd.field("recordId", RECORD_ID);
        fd.field("targetBranchId", BRANCH_ID);

        Response response = target().path("merge-requests").request().post(Entity.entity(fd, MediaType.MULTIPART_FORM_DATA));
        assertEquals(response.getStatus(), 400);
        verify(engineManager, times(0)).retrieveUser(UsernameTestFilter.USERNAME);
        verify(requestManager, times(0)).createMergeRequest(any(MergeRequestConfig.class));
        verify(requestManager, times(0)).addMergeRequest(any(MergeRequest.class));
    }

    @Test
    public void createMergeRequestWithoutTargetTest() {
        // Setup:
        FormDataMultiPart fd = new FormDataMultiPart();
        fd.field("title", "Title");
        fd.field("recordId", RECORD_ID);
        fd.field("sourceBranchId", BRANCH_ID);

        Response response = target().path("merge-requests").request().post(Entity.entity(fd, MediaType.MULTIPART_FORM_DATA));
        assertEquals(response.getStatus(), 400);
        verify(engineManager, times(0)).retrieveUser(UsernameTestFilter.USERNAME);
        verify(requestManager, times(0)).createMergeRequest(any(MergeRequestConfig.class));
        verify(requestManager, times(0)).addMergeRequest(any(MergeRequest.class));
    }

    @Test
    public void createMergeRequestWithInvalidPathTest() {
        // Setup:
        FormDataMultiPart fd = new FormDataMultiPart();
        fd.field("title", "Title");
        fd.field("recordId", RECORD_ID);
        fd.field("sourceBranchId", BRANCH_ID);
        fd.field("targetBranchId", BRANCH_ID);
        doThrow(new IllegalArgumentException()).when(requestManager).createMergeRequest(any(MergeRequestConfig.class));

        Response response = target().path("merge-requests").request().post(Entity.entity(fd, MediaType.MULTIPART_FORM_DATA));
        assertEquals(response.getStatus(), 400);
        verify(engineManager, atLeastOnce()).retrieveUser(UsernameTestFilter.USERNAME);
        verify(requestManager).createMergeRequest(any(MergeRequestConfig.class));
        verify(requestManager, times(0)).addMergeRequest(any(MergeRequest.class));
    }

    @Test
    public void createMergeRequestWithMissingRepoTest() {
        // Setup:
        FormDataMultiPart fd = new FormDataMultiPart();
        fd.field("title", "Title");
        fd.field("recordId", RECORD_ID);
        fd.field("sourceBranchId", BRANCH_ID);
        fd.field("targetBranchId", BRANCH_ID);
        doThrow(new IllegalStateException()).when(requestManager).createMergeRequest(any(MergeRequestConfig.class));

        Response response = target().path("merge-requests").request().post(Entity.entity(fd, MediaType.MULTIPART_FORM_DATA));
        assertEquals(response.getStatus(), 500);
        verify(engineManager, atLeastOnce()).retrieveUser(UsernameTestFilter.USERNAME);
        verify(requestManager).createMergeRequest(any(MergeRequestConfig.class));
        verify(requestManager, times(0)).addMergeRequest(any(MergeRequest.class));
    }

    @Test
    public void createMergeRequestWithErrorTest() {
        // Setup:
        FormDataMultiPart fd = new FormDataMultiPart();
        fd.field("title", "Title");
        fd.field("recordId", RECORD_ID);
        fd.field("sourceBranchId", BRANCH_ID);
        fd.field("targetBranchId", BRANCH_ID);
        doThrow(new MobiException()).when(requestManager).createMergeRequest(any(MergeRequestConfig.class));

        Response response = target().path("merge-requests").request().post(Entity.entity(fd, MediaType.MULTIPART_FORM_DATA));
        assertEquals(response.getStatus(), 500);
        verify(engineManager, atLeastOnce()).retrieveUser(UsernameTestFilter.USERNAME);
        verify(requestManager).createMergeRequest(any(MergeRequestConfig.class));
        verify(requestManager, times(0)).addMergeRequest(any(MergeRequest.class));
    }

    /* GET merge-requests/{requestId} */

    @Test
    public void getMergeRequestTest() {
        Response response = target().path("merge-requests/" + encode(request.getResource().stringValue())).request().get();
        assertEquals(response.getStatus(), 200);
        verify(requestManager).getMergeRequest(request.getResource());
        try {
            JSONArray result = JSONArray.fromObject(response.readEntity(String.class));
            assertEquals(result.size(), 1);
            JSONObject requestObj = result.getJSONObject(0);
            assertFalse(requestObj.containsKey("@graph"));
            assertTrue(requestObj.containsKey("@id"));
            assertEquals(requestObj.getString("@id"), request.getResource().stringValue());
        } catch (Exception e) {
            fail("Expected no exception, but got: " + e.getMessage());
        }
    }

    @Test
    public void getMissingMergeRequestTest() {
        Response response = target().path("merge-requests/" + encode("http://mobi.com/error")).request().get();
        assertEquals(response.getStatus(), 404);
        verify(requestManager).getMergeRequest(vf.createIRI("http://mobi.com/error"));
    }

    @Test
    public void getMergeRequestWithMissingRepoTest() {
        // Setup:
        doThrow(new IllegalStateException()).when(requestManager).getMergeRequest(request.getResource());

        Response response = target().path("merge-requests/" + encode(request.getResource().stringValue())).request().get();
        assertEquals(response.getStatus(), 500);
        verify(requestManager).getMergeRequest(request.getResource());
    }

    @Test
    public void getMergeRequestWithErrorTest() {
        // Setup:
        doThrow(new MobiException()).when(requestManager).getMergeRequest(request.getResource());

        Response response = target().path("merge-requests/" + encode(request.getResource().stringValue())).request().get();
        assertEquals(response.getStatus(), 500);
        verify(requestManager).getMergeRequest(request.getResource());
    }
}
