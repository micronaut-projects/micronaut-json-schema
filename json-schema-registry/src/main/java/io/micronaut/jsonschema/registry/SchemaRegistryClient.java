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
 *
 * <p> Supports the operations defined in the
 * <a href="https://docs.confluent.io/platform/current/schema-registry/develop/api.html">Schema Registry API</a>.
 *
 * <p> The client is configured with the {@link SchemaRegistryConfig} file for its host address
 * and authentication. the configuration parameters are expected to be configured in the application context.
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

//     SCHEMA OPERATIONS -----------------------------------------------------------

    /**
     * Get the schema string identified by the input ID.
     *
     * @param id The unique identifier of the schema
     * @return The schema string identified by the input ID
     */
    @Get("/schemas/ids/{id}")
    @SingleResult
    String getSchemaStringWithId(@PathVariable int id);

    /**
     * Retrieves only the schema identified by the input ID.
     *
     * @param id The unique identifier of the schema
     * @return Schema identified by the ID
     */
    @Get("/schemas/ids/{id}/schema")
    @SingleResult
    String getSchemaWithId(@PathVariable int id);

    /**
     * Get the subject-version pairs identified by the input ID.
     *
     * @param id The unique identifier of the schema
     * @return The subject-version pairs
     */
    @Get("/schemas/ids/{id}/versions")
    @SingleResult
    List<SubjectResponse> getSchemaVersionsWithId(@PathVariable int id);

    /**
     * Get the schema types that are registered with Schema Registry.
     *
     * @return The list of schema types
     */
    @Get("/schemas/types")
    @SingleResult
    List<String> getSchemaTypes();


    // SUBJECTS -----------------------------------------------------------

    /**
     * Get the list of subjects that are registered with Schema Registry.
     *
     * @return The list of subject names
     */
    @Get("/subjects")
    @SingleResult
    List<String> getSubjects();

    /**
     * Get the list of versions of the schema registered under this subject.
     *
     * @param subject The subject (topic name, e.g., user-data)
     * @return The list of versions
     */
    @Get("/subjects/{subject}/versions")
    @SingleResult
    List<Integer> getSubjectVersions(@PathVariable String subject);

    /**
     * Delete the subject and all its versions.
     *
     * @param subject The subject (topic name, e.g., user-data)
     * @return The list of versions that were deleted
     */
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

    /**
     * Get the schema string registered under this subject and version.
     *
     * @param subject The subject (topic name, e.g., user-data)
     * @param version  The version number or 'latest' as a string
     * @return The requested schema
     */
    @Get("/subjects/{subject}/versions/{version}/schema")
    @SingleResult
    String getSchemaWithSubjectAndVersion(@PathVariable String subject,
                                   @PathVariable String version);

    /**
     * Register a new schema under the specified subject. (Essentially, create a new schema.)
     * If successfully registered, this returns the unique identifier of this schema in the registry.
     *
     * @param subject The subject (topic name, e.g., user-data)
     * @param schemaBody  The new schema wished to be registered in the form of {@link SubjectRequestBody}
     * @return The globally unique identifier of the schema
     */
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
