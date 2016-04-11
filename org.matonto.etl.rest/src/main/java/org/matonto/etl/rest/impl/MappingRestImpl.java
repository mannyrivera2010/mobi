package org.matonto.etl.rest.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Reference;
import net.sf.json.JSONArray;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.matonto.etl.api.csv.MappingManager;
import org.matonto.etl.rest.MappingRest;
import org.matonto.rdf.api.Resource;
import org.matonto.rest.util.ErrorUtils;
import org.openrdf.model.Model;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.Rio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.Response;

@Component(immediate = true)
public class MappingRestImpl implements MappingRest {

    private MappingManager manager;
    private final Logger logger = LoggerFactory.getLogger(MappingRestImpl.class);

    @Reference
    public void setManager(MappingManager manager) {
        this.manager = manager;
    }

    @Override
    public Response upload(InputStream fileInputStream, FormDataContentDisposition fileDetail,
                           String jsonld, String id) {
        if ((fileInputStream == null && jsonld == null) || (fileInputStream != null && jsonld != null)) {
            throw ErrorUtils.sendError("Must provide either a file or a string of JSON-LD",
                    Response.Status.BAD_REQUEST);
        }

        String fileName = generateUuid();
        Path filePath = Paths.get("data/tmp/" + fileName + ".jsonld");

        InputStream inputStream;
        if (fileInputStream != null) {
            inputStream = fileInputStream;
        } else {
            inputStream = new ByteArrayInputStream(jsonld.getBytes(StandardCharsets.UTF_8));
        }

        try {
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
            inputStream.close();
        } catch (FileNotFoundException e) {
            throw ErrorUtils.sendError("Error writing mapping file", Response.Status.BAD_REQUEST);
        } catch (IOException e) {
            throw ErrorUtils.sendError("Error parsing mapping file", Response.Status.BAD_REQUEST);
        }

        logger.info("Mapping File Uploaded: " + filePath);

        return Response.status(200).entity(fileName).build();
    }

    @Override
    public Response getFileNames() {
        File directory = new File("data/tmp/");
        File[] mappingFiles = directory.listFiles((dir, fileName) -> {
            return fileName.endsWith(".jsonld");
        });
        String[] fileNames = new String[mappingFiles.length];
        for (int i = 0; i < mappingFiles.length; i++) {
            fileNames[i] = mappingFiles[i].getName().replace(".jsonld", "");
        }
        Gson gson = new GsonBuilder().create();

        return Response.status(200).entity(gson.toJson(fileNames)).build();
    }

    @Override
    public Response getMapping(String fileName) {
        File file = new File("data/tmp/" + fileName + ".jsonld");
        logger.info("Getting mapping from " + file.getName());

        JSONArray json;
        try {
            String fileContent = new String(Files.readAllBytes(file.toPath()));
            json = JSONArray.fromObject(fileContent);
        } catch (IOException e) {
            throw ErrorUtils.sendError("Error reading mapping file", Response.Status.BAD_REQUEST);
        }

        return Response.status(200).entity(json.toString()).build();
    }

    @Override
    public Response downloadMapping(String fileName) {
        File file = new File("data/tmp/" + fileName + ".jsonld");
        logger.info("Downloading mapping from " + file.getName());

        if (file.exists()) {
            return Response.ok(file).header("Content-Disposition", "attachment; filename=" + file.getName()).build();
        } else {
            throw ErrorUtils.sendError("Error reading mapping file", Response.Status.BAD_REQUEST);
        }
    }

    /**
     * Creates a UUID string.
     *
     * @return a string with a UUID
     */
    public String generateUuid() {
        return UUID.randomUUID().toString();
    }
}
