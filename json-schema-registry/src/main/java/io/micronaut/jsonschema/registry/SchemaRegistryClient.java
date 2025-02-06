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

import com.fasterxml.jackson.core.JsonProcessingException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.uri.UriBuilder;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.inject.Singleton;

import java.net.URI;
import java.util.HashMap;

/**
 * A client for the Confluent Schema Registry.
 *
 * @author Elif Kurtay
 * @since 1.5.0
 */
@Singleton
public class SchemaRegistryClient {

    private static final String HEADER_NAME = "Content-Type";
    private static final String HEADER_VALUE = "application/vnd.schemaregistry.v1+json";

    private final HttpClient client;
    private JsonMapper jsonMapper;

    //@Property(name="schema_registry.url")
    protected String schemaRegistryUrl = "http://144.24.55.159:8081";

    public SchemaRegistryClient(@Client() HttpClient httpClient) {
        this.client = httpClient;
        this.jsonMapper = new JsonMapper();
    }

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

    /**
     * Get a specific version of the schema registered under this subject.
     *
     * @param subject The subject (topic name, e.g., user-data)
     * @param version  The version number or 'latest' as a string
     * @return The requested schema
     */
    public String getWithSubjectAndVersion(String subject, String version) {
        URI url = UriBuilder.of(schemaRegistryUrl)
            .path("subjects")
            .path(subject)
            .path("versions")
            .path(version)
            .build();

        HttpRequest<?> request = HttpRequest.GET(url);

        HttpResponse<String> response = client.toBlocking().exchange(request, String.class);
        if (response.getStatus().getCode() == 200) {
            try {
                HashMap<String,?> HashMappedResponse = jsonMapper.readValue(response.body(), HashMap.class);
                return HashMappedResponse.get("schema").toString();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new RuntimeException("Error registering schema: " + response.getStatus());
        }
    }

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
