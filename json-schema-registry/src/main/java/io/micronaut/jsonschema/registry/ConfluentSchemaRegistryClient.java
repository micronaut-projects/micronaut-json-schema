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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal Confluent Schema Registry JSON Schema client for the registry POC.
 *
 * @since 2.0.0
 */
final class ConfluentSchemaRegistryClient implements AutoCloseable {
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MILLIS = 100;

    private final JsonSchemaRegistryConfiguration.SrConfiguration configuration;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String basePath;

    ConfluentSchemaRegistryClient(String baseUrl) {
        this(configuration(baseUrl));
    }

    ConfluentSchemaRegistryClient(JsonSchemaRegistryConfiguration.SrConfiguration configuration) {
        this.configuration = configuration;
        this.baseUrl = trimTrailingSlash(configuration.getUrl());
        URL url = toUrl(this.baseUrl);
        this.httpClient = HttpClient.create(origin(url));
        this.basePath = trimTrailingPathSlash(url.getPath());
        this.objectMapper = JsonSchemaMapperFactory.createMapper();
    }

    List<String> subjects(String prefix) throws IOException, InterruptedException {
        SrResponse response = send(get("/subjects"));
        requireSuccess(response, "list subjects");
        List<?> values = objectMapper.readValue(response.body(), List.class);
        return values.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .filter(subject -> prefix == null || prefix.isBlank() || subject.startsWith(prefix))
            .toList();
    }

    Optional<String> latestSchema(String subject) throws IOException, InterruptedException {
        SrResponse response = send(get("/subjects/" + encodePath(subject) + "/versions/latest"));
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        requireSuccess(response, "read latest schema for subject " + subject);
        Map<?, ?> values;
        try {
            values = objectMapper.readValue(response.body(), Map.class);
        } catch (Exception e) {
            throw new UnreadableSchemaException("Schema Registry latest response is not readable JSON for subject " + subject, e);
        }
        if (values == null) {
            throw new UnreadableSchemaException("Schema Registry latest response is not a JSON object for subject " + subject);
        }
        Object schema = values.get("schema");
        if (!(schema instanceof String schemaText) || schemaText.isBlank()) {
            throw new UnreadableSchemaException("Schema Registry latest response does not contain schema text for subject " + subject);
        }
        return Optional.of(schemaText);
    }

    Optional<String> compatibility(String subject) throws IOException, InterruptedException {
        SrResponse response = send(get("/config/" + encodePath(subject)));
        if (response.statusCode() == 404) {
            response = send(get("/config"));
        }
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        requireSuccess(response, "read compatibility configuration for subject " + subject);
        try {
            Object parsed = objectMapper.readValue(response.body(), Object.class);
            if (parsed instanceof Map<?, ?> values) {
                Object compatibility = values.get("compatibilityLevel");
                if (!(compatibility instanceof String)) {
                    compatibility = values.get("compatibility");
                }
                if (compatibility instanceof String compatibilityText && !compatibilityText.isBlank()) {
                    return Optional.of(compatibilityText);
                }
            }
        } catch (Exception e) {
            throw new IOException("Schema Registry compatibility response is not readable JSON for subject " + subject, e);
        }
        throw new IOException("Schema Registry compatibility response does not contain compatibility for subject " + subject);
    }

    String mode(String subject) throws IOException, InterruptedException {
        SrResponse response = send(get("/mode/" + encodePath(subject)));
        if (response.statusCode() == 404) {
            response = send(get("/mode"));
        }
        requireSuccess(response, "read mode for subject " + subject);
        return parseMode(response.body(), subject);
    }

    void register(String subject, String schemaText) throws IOException, InterruptedException {
        String body = objectMapper.writeValueAsString(Map.of(
            "schemaType", "JSON",
            "schema", schemaText
        ));
        SrResponse response = send(applyAuthentication(HttpRequest.POST(
                path("/subjects/" + encodePath(subject) + "/versions"),
                body
            ))
            .header("Content-Type", "application/vnd.schemaregistry.v1+json"));
        requireSuccess(response, "register schema for subject " + subject);
    }

    @Override
    public void close() {
        httpClient.close();
    }

    private MutableHttpRequest<?> get(String path) {
        return applyAuthentication(HttpRequest.GET(path(path)));
    }

    private SrResponse send(HttpRequest<?> request) throws IOException, InterruptedException {
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                io.micronaut.http.HttpResponse<String> response = httpClient.toBlocking().exchange(request, String.class);
                SrResponse srResponse = new SrResponse(
                    response.code(),
                    response.getBody(String.class).orElse("")
                );
                if (!shouldRetry(srResponse.statusCode()) || attempt == MAX_ATTEMPTS) {
                    return srResponse;
                }
            } catch (HttpClientResponseException e) {
                SrResponse response = new SrResponse(
                    e.getResponse().code(),
                    e.getResponse().getBody(String.class).orElse("")
                );
                if (!shouldRetry(response.statusCode()) || attempt == MAX_ATTEMPTS) {
                    return response;
                }
                lastFailure = e;
            } catch (RuntimeException e) {
                lastFailure = e;
                if (attempt == MAX_ATTEMPTS) {
                    throw new IOException("Schema Registry request failed", e);
                }
            }
            Thread.sleep(RETRY_BACKOFF_MILLIS * attempt);
        }
        if (lastFailure instanceof IOException ioException) {
            throw ioException;
        }
        throw lastFailure == null ? new IOException("Schema Registry request failed") : new IOException("Schema Registry request failed", lastFailure);
    }

    private static boolean shouldRetry(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private MutableHttpRequest<?> applyAuthentication(MutableHttpRequest<?> request) {
        configuration.getHeaders().forEach(request::header);
        String bearerToken = configuration.getBearerToken();
        if (bearerToken != null && !bearerToken.isBlank()) {
            return request.bearerAuth(bearerToken);
        }
        String username = configuration.getUsername();
        if (username != null && !username.isBlank()) {
            return request.basicAuth(username, configuration.getPassword() == null ? "" : configuration.getPassword());
        }
        return request;
    }

    private String path(String path) {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return basePath.isEmpty() ? normalizedPath : basePath + normalizedPath;
    }

    private static void requireSuccess(SrResponse response, String action) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Schema Registry failed to " + action + ": HTTP " + response.statusCode() + " " + response.body());
        }
    }

    private String parseMode(String body, String subject) throws IOException {
        try {
            Object parsed = objectMapper.readValue(body, Object.class);
            if (parsed instanceof Map<?, ?> values) {
                Object mode = values.get("mode");
                if (mode instanceof String modeText && !modeText.isBlank()) {
                    return modeText;
                }
            } else if (parsed instanceof String modeText && !modeText.isBlank()) {
                return modeText;
            }
        } catch (Exception e) {
            throw new IOException("Schema Registry mode response is not readable JSON for subject " + subject, e);
        }
        throw new IOException("Schema Registry mode response does not contain mode for subject " + subject);
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

    private static String trimTrailingPathSlash(String value) {
        if (value == null || value.isBlank() || "/".equals(value)) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static URL toUrl(String value) {
        try {
            return new URL(value);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid Schema Registry URL: " + value, e);
        }
    }

    private static URL origin(URL url) {
        try {
            return new URL(url.getProtocol(), url.getHost(), url.getPort(), "");
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid Schema Registry URL: " + url, e);
        }
    }

    private static JsonSchemaRegistryConfiguration.SrConfiguration configuration(String baseUrl) {
        JsonSchemaRegistryConfiguration.SrConfiguration configuration = new JsonSchemaRegistryConfiguration.SrConfiguration();
        configuration.setUrl(baseUrl);
        return configuration;
    }

    private record SrResponse(int statusCode, String body) {
    }

    static final class UnreadableSchemaException extends IOException {
        UnreadableSchemaException(String message) {
            super(message);
        }

        UnreadableSchemaException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
