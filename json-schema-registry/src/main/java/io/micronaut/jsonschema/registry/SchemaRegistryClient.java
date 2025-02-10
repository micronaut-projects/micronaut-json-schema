/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jsonschema.registry;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.jsonschema.registry.types.CompatibilityResponse;
import io.micronaut.jsonschema.registry.types.ModeResponse;
import io.micronaut.jsonschema.registry.types.SubjectResponse;
import io.micronaut.jsonschema.registry.types.SubjectRequestBody;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.List;

/**
 * A client for the Confluent Schema Registry.
 * TODO: create schema response?
 *
 * @author Elif Kurtay
 * @since 1.5.0
 */
@Client(value = "${registry.url}")
@Requires(beans = SchemaRegistryConfig.class)
@Requires(property = "registry.url")
@Header(name = "Content-Type", value = "application/vnd.schemaregistry.v1+json")
@Singleton
public interface SchemaRegistryClient {

    /**
     * SCHEMAS. -----------------------------------------------------------
     * + GET /schemas/ids/{int: id}
     * + GET /schemas/ids/{int: id}/schema
     * + GET /schemas/types/
     * + GET /schemas/ids/{int: id}/versions
     */

    @Get("/schemas/ids/{id}")
    @SingleResult
    String getSchemaWithId(@PathVariable int id);

    @Get("/schemas/ids/{id}/schema")
    @SingleResult
    String getSchemaStringWithId(@PathVariable int id);

    @Get("/schemas/ids/{id}/versions")
    @SingleResult
    List<SubjectResponse> getSchemaVersionsWithId(@PathVariable int id);

    @Get("/schemas/types")
    @SingleResult
    List<String> getSchemaTypes();

    /**
     * SUBJECTS -----------------------------------------------------------
     * + GET /subjects
     * + GET /subjects/(string: subject)/versions
     * + DELETE /subjects/(string: subject)
     * + GET /subjects/(string: subject)/versions/(versionId: version)
     * + GET /subjects/(string: subject)/versions/(versionId: version)/schema
     * + POST /subjects/(string: subject)/versions
     * + POST /subjects/(string: subject)
     * + DELETE /subjects/(string: subject)/versions/(versionId: version)
     * + GET /subjects/(string: subject)/versions/{versionId: version}/referencedby
     * + GET /subjects/(string: subject)/metadata
     */

    @Get("/subjects")
    @SingleResult
    List<String> getSubjects();

    @Get("/subjects/{subject}/versions")
    @SingleResult
    List<Integer> getSubjectVersions(@PathVariable String subject);

    @Delete("/subjects/{subject}")
    @SingleResult
    List<Integer> deleteSubject(@PathVariable String subject);

    /**
     * Get a specific version of the schema registered under this subject.
     *
     * @param subject The subject (topic name, e.g., user-data)
     * @param version  The version number or 'latest' as a string
     * @return The requested schema
     */
    @Get("/subjects/{subject}/versions/{version}")
    @SingleResult
    SubjectResponse getSubjectWithVersion(@PathVariable String subject,
                                          @PathVariable String version);

    @Get("/subjects/{subject}/versions/{version}/schema")
    @SingleResult
    String getSchemaWithSubjectAndVersion(@PathVariable String subject,
                                   @PathVariable String version);

    @Post("/subjects/{subject}/versions")
    @SingleResult
    HashMap<String, Integer> registerNewVersion(@PathVariable String subject,
                               @Body SubjectRequestBody schemaBody);

    @Post("/subjects/{subject}")
    @SingleResult
    SubjectResponse createSubject(@PathVariable String subject,
                                  @Body SubjectRequestBody schemaBody);

    @Delete("/subjects/{subject}/versions/{version}")
    @SingleResult
    int deleteSubjectVersion(@PathVariable String subject,
                                          @PathVariable String version);

    @Get("/subjects/{subject}/versions/{version}/referencedby")
    @SingleResult
    List<Integer> getSubjectVersionReferencedBy(@PathVariable String subject,
                                               @PathVariable String version);

    @Get("/subjects/{subject}/metadata")
    @SingleResult
    SubjectResponse getSubjectMetadata(@PathVariable String subject);


    /**
     * MODE -----------------------------------------------------------
     * + GET /mode
     * + PUT /mode
     * + GET /mode/(string: subject)
     * + PUT /mode/(string: subject)
     * + DELETE /mode/(string: subject)
     */

    @Get("/mode")
    @SingleResult
    ModeResponse getMode();

    @Put("/mode")
    @SingleResult
    ModeResponse setMode(@Body ModeResponse mode);

    @Get("/mode/{subject}")
    @SingleResult
    ModeResponse getModeForSubject(@PathVariable String subject);

    @Put("/mode/{subject}")
    @SingleResult
    ModeResponse setModeForSubject(@PathVariable String subject, @Body ModeResponse mode);

    @Delete("/mode/{subject}")
    @SingleResult
    ModeResponse deleteModeForSubject(@PathVariable String subject);


    /**
     * COMPATIBILITY -----------------------------------------------------------
     * - POST /compatibility/subjects/(string: subject)/versions/(versionId: version)
     * - POST /compatibility/subjects/(string: subject)/versions
     */

    @Post("/compatibility/subjects/{subject}/versions/{version}")
    @SingleResult
    CompatibilityResponse checkCompatibilityForSubjectVersion(@PathVariable String subject,
                                                              @PathVariable String version,
                                                              @Body SubjectRequestBody compatibilityRequest);

    @Post("/compatibility/subjects/{subject}/versions")
    @SingleResult
    CompatibilityResponse checkCompatibilityForSubject(@PathVariable String subject,
                                        @Body SubjectRequestBody compatibilityRequest);

    /**
     * CONFIG -----------------------------------------------------------
     * TODO:
     * - PUT /config
     * - GET /config
     * - PUT /config/(string: subject)
     * - GET /config/(string: subject)
     * - DELETE /config/(string: subject)
     */

    @Put("/config")
    @SingleResult
    String setConfig(@Body String config);

    @Get("/config")
    @SingleResult
    String getConfig();

    @Put("/config/{subject}")
    @SingleResult
    String setConfigForSubject(@PathVariable String subject, @Body String config);

    @Get("/config/{subject}")
    @SingleResult
    String getConfigForSubject(@PathVariable String subject);

    @Delete("/config/{subject}")
    @SingleResult
    void deleteConfigForSubject(@PathVariable String subject);
}
