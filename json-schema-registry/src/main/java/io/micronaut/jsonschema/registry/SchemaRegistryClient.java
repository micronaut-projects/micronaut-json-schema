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
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotBlank;

/**
 * A client for the Confluent Schema Registry.
 *
 * @author Elif Kurtay
 * @since 1.5.0
 */
@Client("${registry.url}")
@Requires(beans = SchemaRegistryConfig.class)
@Header(name = "Content-Type", value = "application/vnd.schemaregistry.v1+json")
@Singleton
public interface SchemaRegistryClient {

    /**
     * SCHEMAS -----------------------------------------------------------
     * TODO:
     * - GET /schemas/ids/{int: id}
     * - GET /schemas/ids/{int: id}/schema
     * - GET /schemas/types/
     * - GET /schemas/ids/{int: id}/versions
     */

    /**
     * SUBJECTS -----------------------------------------------------------
     * TODO:
     * - GET /subjects
     * - GET /subjects/(string: subject)/versions
     * - DELETE /subjects/(string: subject)
     * + GET /subjects/(string: subject)/versions/(versionId: version)
     * - GET /subjects/(string: subject)/versions/(versionId: version)/schema
     * - POST /subjects/(string: subject)/versions
     * - POST /subjects/(string: subject)
     * - DELETE /subjects/(string: subject)/versions/(versionId: version)
     * - GET /subjects/(string: subject)/versions/{versionId: version}/referencedby
     * - GET /subjects/(string: subject)/metadata
     */

    @Get("/subjects/{subject}/versions/latest")
    @SingleResult
    RegistryResponse getSubjectWithVersion(@PathVariable @NotBlank String subject,
                                           @Header String authorization);

    /**
     * MODE -----------------------------------------------------------
     * TODO:
     * - GET /mode
     * - PUT /mode
     * - GET /mode/(string: subject)
     * - PUT /mode/(string: subject)
     * - DELETE /mode/(string: subject)
     */

    /**
     * COMPATIBILITY -----------------------------------------------------------
     * TODO:
     * - POST /compatibility/subjects/(string: subject)/versions/(versionId: version)
     * - POST /compatibility/subjects/(string: subject)/versions
     */

    /**
     * CONFIG -----------------------------------------------------------
     * TODO:
     * - PUT /config
     * - GET /config
     * - PUT /config/(string: subject)
     * - GET /config/(string: subject)
     * - DELETE /config/(string: subject)
     */
}
