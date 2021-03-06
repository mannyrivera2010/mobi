package com.mobi.etl.rest;

/*-
 * #%L
 * com.mobi.etl.rest
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2016 iNovex Information Systems, Inc.
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
import static com.mobi.rest.util.RestUtils.getObjectFromJsonld;
import static com.mobi.rest.util.RestUtils.getRDFFormat;
import static com.mobi.rest.util.RestUtils.groupedModelToString;
import static com.mobi.rest.util.RestUtils.jsonldToModel;

import com.mobi.catalog.api.CatalogManager;
import com.mobi.catalog.api.record.config.OperationConfig;
import com.mobi.catalog.api.record.config.RecordCreateSettings;
import com.mobi.catalog.api.record.config.RecordOperationConfig;
import com.mobi.catalog.api.record.config.VersionedRDFRecordCreateSettings;
import com.mobi.catalog.config.CatalogConfigProvider;
import com.mobi.etl.api.delimited.MappingManager;
import com.mobi.etl.api.delimited.MappingWrapper;
import com.mobi.etl.api.delimited.record.config.MappingRecordCreateSettings;
import com.mobi.etl.api.ontologies.delimited.MappingRecord;
import com.mobi.exception.MobiException;
import com.mobi.jaas.api.engines.EngineManager;
import com.mobi.jaas.api.ontologies.usermanagement.User;
import com.mobi.persistence.utils.api.SesameTransformer;
import com.mobi.rdf.api.Resource;
import com.mobi.rdf.api.ValueFactory;
import com.mobi.rest.security.annotations.ActionAttributes;
import com.mobi.rest.security.annotations.ActionId;
import com.mobi.rest.security.annotations.AttributeValue;
import com.mobi.rest.security.annotations.ResourceId;
import com.mobi.rest.security.annotations.ValueType;
import com.mobi.rest.util.ErrorUtils;
import com.mobi.security.policy.api.ontologies.policy.Delete;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

@Component(service = MappingRest.class, immediate = true)
@Path("/mappings")
@Api( value = "/mappings" )
public class MappingRest {

    private final Logger logger = LoggerFactory.getLogger(MappingRest.class);

    private MappingManager manager;
    private CatalogConfigProvider configProvider;
    private CatalogManager catalogManager;
    private ValueFactory vf;
    private EngineManager engineManager;
    private SesameTransformer transformer;

    @Reference
    void setManager(MappingManager manager) {
        this.manager = manager;
    }

    @Reference
    void setConfigProvider(CatalogConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    @Reference
    void setCatalogManager(CatalogManager catalogManager) {
        this.catalogManager = catalogManager;
    }

    @Reference
    void setVf(ValueFactory vf) {
        this.vf = vf;
    }

    @Reference
    void setEngineManager(EngineManager engineManager) {
        this.engineManager = engineManager;
    }

    @Reference
    void setTransformer(SesameTransformer transformer) {
        this.transformer = transformer;
    }

    
    /**
     * Uploads a mapping sent as form data or a JSON-LD string into a data store with a UUID local name and creates
     * a new MappingRecord in the catalog.
     *
     * @param title The required title for the new MappingRecord
     * @param description The optional description for the new MappingRecord
     * @param markdown The optional markdown abstract for the new MappingRecord.
     * @param keywords The optional list of keywords strings for the new MappingRecord
     * @param fileInputStream an InputStream of a mapping file passed as form data
     * @param fileDetail information about the file being uploaded, including the name
     * @param jsonld a mapping serialized as JSON-LD
     * @return a Response with the MappingRecord Resource ID
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("user")
    @ApiOperation("Upload mapping sent as form data.")
    @ActionAttributes(@AttributeValue(id = com.mobi.ontologies.rdfs.Resource.type_IRI, value = MappingRecord.TYPE))
    @ResourceId("http://mobi.com/catalog-local")
    public Response upload(@Context ContainerRequestContext context,
                    @FormDataParam("title") String title,
                    @FormDataParam("description") String description,
                    @FormDataParam("markdown") String markdown,
                    @FormDataParam("keywords") List<FormDataBodyPart> keywords,
                    @FormDataParam("file") InputStream fileInputStream,
                    @FormDataParam("file") FormDataContentDisposition fileDetail,
                    @FormDataParam("jsonld") String jsonld) {
        if ((fileInputStream == null && jsonld == null) || (fileInputStream != null && jsonld != null)) {
            throw ErrorUtils.sendError("Must provide either a file or a JSON-LD string", Response.Status.BAD_REQUEST);
        }
        checkStringParam(title, "Title is required");
        User user = getActiveUser(context, engineManager);
        Set<User> users = new LinkedHashSet<>();
        users.add(user);
        RecordOperationConfig config = new OperationConfig();
        Resource catalogId = configProvider.getLocalCatalogIRI();
        config.set(RecordCreateSettings.CATALOG_ID, catalogId.stringValue());
        config.set(RecordCreateSettings.RECORD_MARKDOWN, markdown);
        config.set(RecordCreateSettings.RECORD_PUBLISHERS, users);
        if (StringUtils.isNotEmpty(title)) {
            config.set(RecordCreateSettings.RECORD_TITLE, title);
        }
        if (StringUtils.isNotEmpty(description)) {
            config.set(RecordCreateSettings.RECORD_DESCRIPTION, description);
        }
        if (keywords != null && keywords.size() > 0) {
            config.set(RecordCreateSettings.RECORD_KEYWORDS, keywords.stream()
                    .map(FormDataBodyPart::getValue)
                    .collect(Collectors.toSet()));
        }
        MappingRecord record;
        try {
            if (fileInputStream != null) {
                RDFFormat format = Rio.getParserFormatForFileName(fileDetail.getFileName()).orElseThrow(() ->
                        new IllegalArgumentException("File is not in a valid RDF format"));
                config.set(MappingRecordCreateSettings.INPUT_STREAM, fileInputStream);
                config.set(MappingRecordCreateSettings.RDF_FORMAT, format);
            } else {
                config.set(VersionedRDFRecordCreateSettings.INITIAL_COMMIT_DATA, jsonldToModel(jsonld, transformer));
            }
            record = catalogManager.createRecord(user, config, MappingRecord.class);
            return Response.status(201).entity(record.getResource().stringValue()).build();
        } catch (IllegalArgumentException ex) {
            throw ErrorUtils.sendError(ex, ex.getMessage(), Response.Status.BAD_REQUEST);
        } catch (MobiException e) {
            throw ErrorUtils.sendError(e, e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Collects the JSON-LD from an uploaded mapping and returns it as JSON.
     *
     * @param recordId the id of an uploaded mapping
     * @return a response with the JSON-LD from the uploaded mapping
     */
    @GET
    @Path("{recordId}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("user")
    @ApiOperation("Retrieve JSON-LD of an uploaded mapping")
    @ResourceId(type = ValueType.PATH, value = "recordId")
    public Response getMapping(@PathParam("recordId") String recordId) {
        try {
            logger.info("Getting mapping " + recordId);
            MappingWrapper mapping = manager.retrieveMapping(vf.createIRI(recordId)).orElseThrow(() ->
                    ErrorUtils.sendError("Mapping not found", Response.Status.NOT_FOUND));
            String mappingJsonld = groupedModelToString(mapping.getModel(), getRDFFormat("jsonld"), transformer);
            return Response.ok(mappingJsonld).build();
        } catch (IllegalArgumentException e) {
            throw ErrorUtils.sendError(e, e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (MobiException e) {
            throw ErrorUtils.sendError(e, e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Downloads an uploaded mapping.
     *
     * @param recordId the id of an uploaded mapping
     * @return a response with mapping to download
     */
    @GET
    @Path("{recordId}")
    @Produces({MediaType.APPLICATION_OCTET_STREAM, "text/*", "application/*"})
    @RolesAllowed("user")
    @ApiOperation("Download an uploaded mapping")
    @ResourceId(type = ValueType.PATH, value = "recordId")
    public Response downloadMapping(@PathParam("recordId") String recordId,
                             @DefaultValue("jsonld") @QueryParam("format") String format) {
        try {
            logger.info("Downloading mapping " + recordId);
            MappingWrapper mapping = manager.retrieveMapping(vf.createIRI(recordId)).orElseThrow(() ->
                    ErrorUtils.sendError("Mapping not found", Response.Status.NOT_FOUND));
            RDFFormat rdfFormat = getRDFFormat(format);
            String mappingJsonld = groupedModelToString(mapping.getModel(), rdfFormat, transformer);
            StreamingOutput stream = os -> {
                Writer writer = new BufferedWriter(new OutputStreamWriter(os));
                writer.write(format.equalsIgnoreCase("jsonld") ? getObjectFromJsonld(mappingJsonld).toString() :
                        mappingJsonld);
                writer.flush();
                writer.close();
            };

            return Response.ok(stream).header("Content-Disposition", "attachment; filename="
                    + vf.createIRI(mapping.getId().getMappingIdentifier().stringValue()).getLocalName() + "."
                    + rdfFormat.getDefaultFileExtension()).header("Content-Type", rdfFormat.getDefaultMIMEType())
                    .build();
        } catch (IllegalArgumentException e) {
            throw ErrorUtils.sendError(e, e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (MobiException e) {
            throw ErrorUtils.sendError(e, e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Deletes an uploaded mapping from the data store.
     *
     * @param recordId the id of an uploaded mapping
     * @return a response indicating the success or failure of the request
     */
    @DELETE
    @Path("{recordId}")
    @RolesAllowed("user")
    @ApiOperation("Delete an uploaded mapping")
    @ActionId(Delete.TYPE)
    @ResourceId(type = ValueType.PATH, value = "recordId")
    public Response deleteMapping(@Context ContainerRequestContext context,
                           @PathParam("recordId") String recordId) {
        try {
            catalogManager.deleteRecord(getActiveUser(context, engineManager), vf.createIRI(recordId),
                    MappingRecord.class);
        } catch (MobiException e) {
            throw ErrorUtils.sendError(e, e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR);
        } catch (IllegalArgumentException e) {
            throw ErrorUtils.sendError(e, e.getMessage(), Response.Status.BAD_REQUEST);
        }
        return Response.ok().build();
    }
}
