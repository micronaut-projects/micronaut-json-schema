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

import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.uri.UriBuilder;
import jakarta.inject.Singleton;

import java.net.URI;
import java.util.Map;

@Singleton
public class SchemaRegistryClient {

    private static final String HEADER_NAME = "Content-Type";
    private static final String HEADER_VALUE = "application/vnd.schemaregistry.v1+json";

    private final HttpClient httpClient;

    @Value("${schema_registry.url}")
    private String schemaRegistryUrl;

    public SchemaRegistryClient(@Client("/") HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Register a schema with the schema registry for a given subject.
     *
     * @param subject The subject (topic name, e.g., user-data)
     * @param schema  The Avro/JSON schema as a string
     * @return The ID of the registered schema
     */
    public int registerSchema(String subject, String schema) {
        URI url = UriBuilder.of(schemaRegistryUrl)
            .path("subjects")
            .path(subject)
            .path("versions")
            .build();

        Map<String, Object> body = Map.of("schema", schema);

        HttpRequest<Map<String, Object>> request = HttpRequest.POST(url, body)
            .header(HEADER_NAME, HEADER_VALUE)
            .contentType(MediaType.APPLICATION_JSON);

        HttpResponse<Map> response = httpClient.toBlocking().exchange(request, Map.class);

        if (response.getStatus().getCode() == 200) {
            Map<?, ?> responseBody = response.body();
            return (Integer) responseBody.get("id");
        } else {
            throw new RuntimeException("Error registering schema: " + response.getStatus());
        }
    }

    /**
     * Get a schema for a given subject and version.
     *
     * @param subject The subject (e.g., user-data)
     * @param version The version of the schema to retrieve (e.g., 1, 2, etc.)
     * @return The schema in JSON format
     */
    public String getSchema(String subject, int version) {
        String url = schemaRegistryUrl + "/subjects/" + subject + "/versions/" + version;

        HttpRequest<?> request = HttpRequest.GET(url)
            .contentType(MediaType.APPLICATION_JSON);

        HttpResponse<Map> response = httpClient.toBlocking().exchange(request, Map.class);

        if (response.getStatus().getCode() == 200) {
            Map responseBody = response.body();
            return (String) responseBody.get("schema");
        } else {
            throw new RuntimeException("Error retrieving schema: " + response.getStatus());
        }
    }

    /**
     * Check if a schema is compatible with the latest schema for a given subject.
     *
     * @param subject The subject (e.g., user-data)
     * @param schema  The Avro/JSON schema as a string
     * @return true if the schema is compatible, false otherwise
     */
    public boolean checkCompatibility(String subject, String schema) {
        String url = schemaRegistryUrl + "/compatibility/subjects/" + subject + "/versions/latest";

        Map<String, Object> body = Map.of("schema", schema);

        HttpRequest<Map<String, Object>> request = HttpRequest.POST(url, body)
            .contentType(MediaType.APPLICATION_JSON);

        HttpResponse<Map> response = httpClient.toBlocking().exchange(request, Map.class);

        if (response.getStatus().getCode() == 200) {
            Map responseBody = response.body();
            // Assuming response contains "is_compatible" field
            return (Boolean) responseBody.get("is_compatible");
        } else {
            throw new RuntimeException("Error checking compatibility: " + response.getStatus());
        }
    }

    public void deleteSchema(String subject) {
        // Delete the schema
    }

    public void deleteSchemaVersion(String subject, int version) {
        // Delete the schema version
    }

    public void deleteAllVersions(String subject) {
        // Delete all versions of the schema
    }

    public void deleteAllSubjects() {
        // Delete all subjects
    }
}
