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
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.jsonschema.registry.types.Responses;
import io.micronaut.jsonschema.registry.types.SubjectRequestBody;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * A client for the Confluent SchemaJson Registry.
 *
 * <p> Supports the operations defined in the
 * <a href="https://docs.confluent.io/platform/current/schema-registry/develop/api.html">SchemaJson Registry API</a>.
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
//@Header(name = "Content-Type", value = "application/vnd.schemaregistry.v1+json")
@Consumes(MediaType.APPLICATION_JSON)
@Singleton
public interface SchemaRegistryClient {

// SCHEMA OPERATIONS ---------------------------------------------------------------------------

    /**
     * Get the schema string identified by the input ID.
     *
     * @param id The unique identifier of the schema
     * @return The schema string identified by the input ID
     */
    @Get("/schemas/ids/{id}")
    @SingleResult
    Responses.SchemaJson getSchemaWithId(@PathVariable int id);

    /**
     * Retrieves only the schema identified by the input ID.
     *
     * @param id The unique identifier of the schema
     * @return SchemaJson identified by the ID
     */
    @Get("/schemas/ids/{id}/schema")
    @SingleResult
    String getSchemaStringWithId(@PathVariable int id);

    /**
     * Get the subject-version pairs identified by the input ID.
     *
     * @param id The unique identifier of the schema
     * @return The subject-version pairs
     */
    @Get("/schemas/ids/{id}/versions")
    @SingleResult
    List<Responses.SubjectVersion> getSchemaVersionsWithId(@PathVariable int id);

    /**
     * Get the schema types that are registered with SchemaJson Registry.
     *
     * @return The list of schema types
     */
    @Get("/schemas/types")
    @SingleResult
    List<Responses.SchemaType> getSchemaTypes();


    // SUBJECTS ------------------------------------------------------------------------------------

    /**
     * Get the list of subjects that are registered with SchemaJson Registry.
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
    Responses.Subject getSubjectWithVersion(@PathVariable String subject,
                                            @PathVariable String version);

    /**
     * Get the schema string registered under this subject and version.
     *
     * @param subject The subject (topic name, e.g., user-data)
     * @param version  The version number or 'latest' as a string
     * @return The requested schema string (unescaped)
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
    Responses.Id registerNewVersion(@PathVariable String subject,
                                         @Body SubjectRequestBody schemaBody);

    /**
     * Checks if a schema has already been registered under the specified subject.
     *
     * @param subject Subject under which the schema will be registered
     * @param schemaBody The new schema wished to be registered in the form of {@link SubjectRequestBody}
     * @return the schema string along with its globally unique identifier, its version under this subject and the subject name.
     */
    @Post("/subjects/{subject}")
    @SingleResult
    Responses.Subject checkSubject(@PathVariable String subject,
                               @Body SubjectRequestBody schemaBody);

    /**
     * Deletes a specific version of the schema registered under this subject.
     *
     * @param subject Subject under which the schema is registered
     * @param version The version number or 'latest' as a string
     * @return The version number that was deleted
     */
    @Delete("/subjects/{subject}/versions/{version}")
    @SingleResult
    int deleteSubjectVersion(@PathVariable String subject,
                                          @PathVariable String version);

    /**
     * Get the list of versions that reference this schema.
     *
     * @param subject Subject under which the schema is registered
     * @param version The version number or 'latest' as a string
     * @return The list of versions that reference this schema
     */
    @Get("/subjects/{subject}/versions/{version}/referencedby")
    @SingleResult
    List<Integer> getSubjectVersionReferencedBy(@PathVariable String subject,
                                               @PathVariable String version);

    /**
     * Get the metadata for the specified subject.
     *
     * @param subject Subject under which the schema is registered
     * @return The metadata for the specified subject
     */
    @Get("/subjects/{subject}/metadata")
    @SingleResult
    Responses.Subject getSubjectMetadata(@PathVariable String subject);

    // MODE ----------------------------------------------------------------------------------------

    /**
     * Get the global compatibility mode.
     *
     * @return The global compatibility mode
     */
    @Get("/mode")
    @SingleResult
    Responses.Mode getMode();

    /**
     * Set the global compatibility mode.
     *
     * @param mode The new global compatibility mode
     * @return The new global compatibility mode
     */
    @Put("/mode")
    @SingleResult
    Responses.Mode setMode(@Body Responses.Mode mode);

    /**
     * Get the compatibility mode for the specified subject.
     *
     * @param subject Subject under which the schema is registered
     * @return The compatibility mode for the specified subject
     */
    @Get("/mode/{subject}")
    @SingleResult
    Responses.Mode getModeForSubject(@PathVariable String subject);

    /**
     * Set the compatibility mode for the specified subject.
     *
     * @param subject Subject under which the schema is registered
     * @param mode The new compatibility mode for the specified subject
     * @return The new compatibility mode for the specified subject
     */
    @Put("/mode/{subject}")
    @SingleResult
    Responses.Mode setModeForSubject(@PathVariable String subject, @Body Responses.Mode mode);

    /**
     * Delete the compatibility mode for the specified subject.
     *
     * @param subject Subject under which the schema is registered
     * @return The compatibility mode that was deleted
     */
    @Delete("/mode/{subject}")
    @SingleResult
    Responses.Mode deleteModeForSubject(@PathVariable String subject);

    // COMPATIBILITY ---------------------------------------------------------------------------

    /**
     * Test input schema against a particular version of a subject&apos;s schema for compatibility.
     *
     * @param subject Subject under which the schema is registered
     * @param version The version number or &apos;latest&apos; as a string
     * @param compatibilityRequest The new schema wished to be registered in the form of {@link SubjectRequestBody}
     * @return Whether the new schema is compatible with the specified subject and version
     */
    @Post("/compatibility/subjects/{subject}/versions/{version}")
    @SingleResult
    Responses.Compatibility checkCompatibilityForSubjectVersion(@PathVariable String subject,
                                                                @PathVariable String version,
                                                                @Body SubjectRequestBody compatibilityRequest);

    /**
     * Perform a compatibility check on the schema against one or more versions in the subject,
     * depending on how the compatibility is set.
     *
     * @param subject Subject under which the schema is registered
     * @param compatibilityRequest The new schema to be checked in the form of {@link SubjectRequestBody}
     * @return Whether the new schema is compatible with the specified subject
     */
    @Post("/compatibility/subjects/{subject}/versions")
    @SingleResult
    Responses.Compatibility checkCompatibilityForSubject(@PathVariable String subject,
                                                         @Body SubjectRequestBody compatibilityRequest);

    // CONFIG ----------------------------------------------------------------------------------

    /**
     * Get the global configuration.
     *
     * @return The global configuration
     */
    @Get("/config")
    @SingleResult
    Responses.Config getConfig();

    /**
     * Set the global configuration.
     *
     * @param config The new global configuration
     * @return The new global configuration
     */
    @Put("/config")
    @SingleResult
    Responses.Config setConfig(@Body Responses.Config config);

    /**
     * Get the configuration for the specified subject.
     *
     * @param subject Subject under which the schema is registered
     * @return The configuration for the specified subject
     */
    @Get("/config/{subject}")
    @SingleResult
    Responses.Config getConfigForSubject(@PathVariable String subject);

    /**
     * Set the configuration for the specified subject.
     *
     * @param subject Subject under which the schema is registered
     * @param config The new configuration for the specified subject
     * @return The new configuration for the specified subject
     */
    @Put("/config/{subject}")
    @SingleResult
    Responses.Config setConfigForSubject(@PathVariable String subject, @Body Responses.Config config);

    /**
     * Delete the configuration for the specified subject.
     *
     * @param subject Subject under which the schema is registered
     * @return The configuration that was deleted
     */
    @Delete("/config/{subject}")
    @SingleResult
    Responses.Config deleteConfigForSubject(@PathVariable String subject);
}
