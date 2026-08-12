package com.mobi.prolly.record.rest;

/*-
 * #%L
 * com.mobi.prolly.record.rest
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2016 - 2026 iNovex Information Systems, Inc.
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

import static com.mobi.rest.util.RestUtils.checkStringParam;
import static com.mobi.rest.util.RestUtils.getActiveUser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mobi.catalog.api.RecordManager;
import com.mobi.catalog.api.ontologies.mcat.Branch;
import com.mobi.catalog.api.record.config.OperationConfig;
import com.mobi.catalog.api.record.config.RecordCreateSettings;
import com.mobi.catalog.api.record.config.RecordOperationConfig;
import com.mobi.catalog.api.record.config.VersionedRDFRecordCreateSettings;
import com.mobi.catalog.config.CatalogConfigProvider;
import com.mobi.jaas.api.engines.EngineManager;
import com.mobi.jaas.api.ontologies.usermanagement.User;
import com.mobi.prolly.record.api.ontologies.prollyrecord.ProllyVersionedRDFRecord;
import com.mobi.rest.security.annotations.ActionAttributes;
import com.mobi.rest.security.annotations.AttributeValue;
import com.mobi.rest.security.annotations.ResourceId;
import com.mobi.rest.util.FileUpload;
import com.mobi.rest.util.MobiNotFoundException;
import com.mobi.rest.util.RestUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.ValidatingValueFactory;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsResource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.security.RolesAllowed;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/prolly-records")
@Component(service = ProllyRecordRest.class, immediate = true)
@JaxrsResource
public class ProllyRecordRest {
    private static final ObjectMapper mapper = new ObjectMapper();
    final ValueFactory vf = new ValidatingValueFactory();

    @Reference
    CatalogConfigProvider configProvider;

    @Reference
    RecordManager recordManager;

    @Reference
    EngineManager engineManager;

    /**
     * Uploads an RDF file and creates a ProllyVersionedRDFRecord: a VersionedRDFRecord whose versioned
     * content is held in an out-of-process prolly-rdf4j repository. A master Branch is created with an
     * initial Commit containing the uploaded data.
     *
     * @param servletRequest Context of the request.
     * @return CREATED with the recordId, branchId, and commitId if persisted; BAD REQUEST if required
     *      form data is missing; INTERNAL SERVER ERROR on creation failure.
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            tags = "prolly-records",
            summary = "Uploads an RDF file and creates a prolly-backed versioned record",
            description = "Uploads an RDF file to the data store and creates an associated "
                    + "ProllyVersionedRDFRecord using the form data. A master Branch is created and stored "
                    + "with an initial Commit containing the uploaded data.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "ProllyVersionedRDFRecord created"),
                    @ApiResponse(responseCode = "400", description = "Required form data missing"),
                    @ApiResponse(responseCode = "403", description = "Permission Denied"),
                    @ApiResponse(responseCode = "500", description = "Problem creating record")
            },
            requestBody = @RequestBody(
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA,
                            schema = @Schema(implementation = ProllyRecordFileUpload.class))
            )
    )
    @RolesAllowed("user")
    @ActionAttributes(@AttributeValue(id = com.mobi.ontologies.rdfs.Resource.type_IRI,
            value = ProllyVersionedRDFRecord.TYPE))
    @ResourceId("http://mobi.com/catalog-local")
    public Response uploadFile(@Context HttpServletRequest servletRequest) {
        Map<String, List<Class<?>>> fields = new HashMap<>();
        fields.put("title", List.of(String.class));
        fields.put("description", List.of(String.class));
        fields.put("markdown", List.of(String.class));
        fields.put("keywords", List.of(Set.class, String.class));

        Map<String, Object> formData = RestUtils.getFormData(servletRequest, fields);
        String title = (String) formData.get("title");
        String description = (String) formData.get("description");
        String markdown = (String) formData.get("markdown");
        Set<String> keywords = (Set<String>) formData.get("keywords");
        FileUpload file = (FileUpload) formData.getOrDefault("file", new FileUpload());
        InputStream inputStream = file.getStream();
        String filename = file.getFilename();
        checkStringParam(title, "The title is missing.");
        if (inputStream == null) {
            throw RestUtils.getErrorObjBadRequest(new IllegalArgumentException("The RDF data is missing."));
        }
        RecordOperationConfig config = new OperationConfig();
        config.set(VersionedRDFRecordCreateSettings.INPUT_STREAM, inputStream);
        config.set(VersionedRDFRecordCreateSettings.FILE_NAME, filename);

        User user = getActiveUser(servletRequest, engineManager);
        Set<User> users = new LinkedHashSet<>();
        users.add(user);
        Resource catalogId = configProvider.getLocalCatalogIRI();
        config.set(RecordCreateSettings.CATALOG_ID, catalogId.stringValue());
        config.set(RecordCreateSettings.RECORD_TITLE, title);
        config.set(RecordCreateSettings.RECORD_DESCRIPTION, description);
        config.set(RecordCreateSettings.RECORD_MARKDOWN, markdown);
        config.set(RecordCreateSettings.RECORD_KEYWORDS, keywords);
        config.set(RecordCreateSettings.RECORD_PUBLISHERS, users);
        try (RepositoryConnection conn = configProvider.getRepository().getConnection()) {
            ProllyVersionedRDFRecord record = recordManager.createRecord(user, config,
                    ProllyVersionedRDFRecord.class, conn);
            Resource branchId = record.getMasterBranch_resource()
                    .orElseThrow(() -> new IllegalStateException("Record master branch resource not found."));
            Resource commitId;
            try (RepositoryResult<Statement> commitStmt = conn.getStatements(branchId,
                    vf.createIRI(Branch.head_IRI), null)) {
                if (!commitStmt.hasNext()) {
                    throw new MobiNotFoundException("The requested instance could not be found.");
                }
                commitId = (Resource) commitStmt.next().getObject();
            }

            ObjectNode objectNode = mapper.createObjectNode();
            objectNode.put("recordId", record.getResource().stringValue());
            objectNode.put("branchId", branchId.stringValue());
            objectNode.put("commitId", commitId.stringValue());
            return Response.status(Response.Status.CREATED).entity(objectNode.toString()).build();
        } catch (IllegalArgumentException ex) {
            throw RestUtils.getErrorObjBadRequest(ex);
        } catch (Exception ex) {
            throw RestUtils.getErrorObjInternalServerError(ex);
        }
    }

    /**
     * Class used for OpenAPI documentation for the file upload endpoint.
     */
    private static class ProllyRecordFileUpload {
        @Schema(type = "string", format = "binary", description = "RDF file to upload.")
        public String file;

        @Schema(type = "string", description = "Title for the ProllyVersionedRDFRecord", required = true)
        public String title;

        @Schema(type = "string", description = "Optional description for the ProllyVersionedRDFRecord")
        public String description;

        @Schema(type = "string", description = "Optional markdown abstract for the ProllyVersionedRDFRecord")
        public String markdown;
    }
}
