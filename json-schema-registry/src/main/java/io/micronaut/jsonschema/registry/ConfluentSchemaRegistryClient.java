/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.jsonschema.serialization.JsonSchemaMapperFactory;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal Confluent Schema Registry JSON Schema client for the registry POC.
 *
 * @since 2.0.0
 */
final class ConfluentSchemaRegistryClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    ConfluentSchemaRegistryClient(String baseUrl) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = JsonSchemaMapperFactory.createMapper();
        this.baseUrl = trimTrailingSlash(baseUrl);
    }

    List<String> subjects(String prefix) throws IOException, InterruptedException {
        HttpResponse<String> response = send(get("/subjects"));
        requireSuccess(response, "list subjects");
        List<?> values = objectMapper.readValue(response.body(), List.class);
        return values.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .filter(subject -> prefix == null || prefix.isBlank() || subject.startsWith(prefix))
            .toList();
    }

    Optional<String> latestSchema(String subject) throws IOException, InterruptedException {
        HttpResponse<String> response = send(get("/subjects/" + encodePath(subject) + "/versions/latest"));
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        requireSuccess(response, "read latest schema for subject " + subject);
        Map<?, ?> values = objectMapper.readValue(response.body(), Map.class);
        Object schema = values.get("schema");
        if (!(schema instanceof String schemaText) || schemaText.isBlank()) {
            throw new IOException("Schema Registry latest response does not contain schema text for subject " + subject);
        }
        return Optional.of(schemaText);
    }

    void register(String subject, String schemaText) throws IOException, InterruptedException {
        String body = objectMapper.writeValueAsString(Map.of(
            "schemaType", "JSON",
            "schema", schemaText
        ));
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/subjects/" + encodePath(subject) + "/versions"))
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/vnd.schemaregistry.v1+json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build());
        requireSuccess(response, "register schema for subject " + subject);
    }

    private HttpRequest get(String path) {
        return HttpRequest.newBuilder(uri(path))
            .timeout(REQUEST_TIMEOUT)
            .GET()
            .build();
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private URI uri(String path) {
        return URI.create(baseUrl + path);
    }

    private static void requireSuccess(HttpResponse<String> response, String action) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Schema Registry failed to " + action + ": HTTP " + response.statusCode() + " " + response.body());
        }
    }

    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8081";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
