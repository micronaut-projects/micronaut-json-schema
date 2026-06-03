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

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.jsonschema.serialization.JsonSchemaMapperFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal Confluent Schema Registry JSON Schema client for runtime registry reconciliation.
 *
 * @since 2.0.0
 */
final class ConfluentSchemaRegistryClient implements AutoCloseable {
    private static final String JSON_CONTENT_TYPE = "application/vnd.schemaregistry.v1+json";
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MS = 100L;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final JsonSchemaRegistryConfiguration.SrConfiguration configuration;
    private final String basePath;
    private final Optional<MeterRegistry> meterRegistry;

    ConfluentSchemaRegistryClient(String baseUrl) {
        this(srConfiguration(baseUrl));
    }

    ConfluentSchemaRegistryClient(JsonSchemaRegistryConfiguration.SrConfiguration configuration) {
        this(configuration, Optional.empty());
    }

    ConfluentSchemaRegistryClient(JsonSchemaRegistryConfiguration.SrConfiguration configuration, Optional<MeterRegistry> meterRegistry) {
        this.configuration = configuration;
        this.objectMapper = JsonSchemaMapperFactory.createMapper();
        this.meterRegistry = meterRegistry;
        URI uri = URI.create(trimTrailingSlash(configuration.getUrl()));
        URI origin = URI.create(uri.getScheme() + "://" + uri.getAuthority());
        this.basePath = trimBasePath(uri.getRawPath());
        try {
            this.httpClient = HttpClient.create(origin.toURL());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid Schema Registry URL: " + configuration.getUrl(), e);
        }
    }

    List<String> subjects(String prefix) throws IOException {
        return recordOperation("subjects", () -> {
            HttpResponse<String> response = send(get("/subjects"));
            List<?> values = objectMapper.readValue(body(response), List.class);
            return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(subject -> prefix == null || prefix.isBlank() || subject.startsWith(prefix))
                .toList();
        });
    }

    Optional<String> latestSchema(String subject) throws IOException {
        return recordOperation("latest", () -> {
            try {
                HttpResponse<String> response = send(get("/subjects/" + encodePath(subject) + "/versions/latest"));
                Map<?, ?> values = objectMapper.readValue(body(response), Map.class);
                Object schema = values.get("schema");
                if (!(schema instanceof String schemaText) || schemaText.isBlank()) {
                    throw new IOException("Schema Registry latest response does not contain schema text for subject " + subject);
                }
                return Optional.of(schemaText);
            } catch (HttpClientResponseException e) {
                if (e.getStatus().getCode() == 404) {
                    return Optional.empty();
                }
                throw schemaRegistryException("read latest schema for subject " + subject, e);
            }
        });
    }

    Optional<String> mode(String subject) throws IOException {
        return recordOperation("mode", () -> {
            try {
                HttpResponse<String> response = send(get(subject == null ? "/mode" : "/mode/" + encodePath(subject)));
                return mapValue(response, "mode");
            } catch (HttpClientResponseException e) {
                if (e.getStatus().getCode() == 404 && subject != null) {
                    return mode(null);
                }
                if (e.getStatus().getCode() == 404) {
                    return Optional.empty();
                }
                throw schemaRegistryException("read mode for subject " + subject, e);
            }
        });
    }

    Optional<String> compatibility(String subject, String schemaText) throws IOException {
        return recordOperation("compatibility", () -> {
            String body = objectMapper.writeValueAsString(Map.of(
                "schemaType", "JSON",
                "schema", schemaText
            ));
            try {
                HttpResponse<String> response = send(post("/compatibility/subjects/" + encodePath(subject) + "/versions/latest", body));
                return mapValue(response, "is_compatible");
            } catch (HttpClientResponseException e) {
                if (e.getStatus().getCode() == 404) {
                    return Optional.empty();
                }
                throw schemaRegistryException("check compatibility for subject " + subject, e);
            }
        });
    }

    void register(String subject, String schemaText) throws IOException {
        recordOperation("register", () -> {
            String body = objectMapper.writeValueAsString(Map.of(
                "schemaType", "JSON",
                "schema", schemaText
            ));
            try {
                send(post("/subjects/" + encodePath(subject) + "/versions", body));
                return null;
            } catch (HttpClientResponseException e) {
                throw schemaRegistryException("register schema for subject " + subject, e);
            }
        });
    }

    @Override
    public void close() {
        httpClient.close();
    }

    private MutableHttpRequest<?> get(String path) {
        return withHeaders(HttpRequest.GET(path(path)));
    }

    private MutableHttpRequest<?> post(String path, String body) {
        return withHeaders(HttpRequest.POST(path(path), body).contentType(JSON_CONTENT_TYPE));
    }

    private MutableHttpRequest<?> withHeaders(MutableHttpRequest<?> request) {
        request.accept(JSON_CONTENT_TYPE);
        if (configuration.getUsername() != null && configuration.getPassword() != null) {
            request.basicAuth(configuration.getUsername(), configuration.getPassword());
        }
        if (configuration.getBearerToken() != null) {
            request.bearerAuth(configuration.getBearerToken());
        }
        configuration.getHeaders().forEach(request::header);
        return request;
    }

    private HttpResponse<String> send(MutableHttpRequest<?> request) throws IOException {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return httpClient.toBlocking().exchange(request, String.class);
            } catch (HttpClientResponseException e) {
                if (!isRetryable(e.getStatus().getCode()) || attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                backoff(e);
            } catch (RuntimeException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                backoff(e);
            }
        }
        throw new IOException("Schema Registry request failed");
    }

    private Optional<String> mapValue(HttpResponse<String> response, String key) throws IOException {
        Map<?, ?> values = objectMapper.readValue(body(response), Map.class);
        Object value = values.get(key);
        return value == null ? Optional.empty() : Optional.of(value.toString());
    }

    private <T> T recordOperation(String operation, IoSupplier<T> supplier) throws IOException {
        Optional<MeterRegistry> registry = meterRegistry;
        if (registry.isEmpty()) {
            return supplier.get();
        }
        Timer.Sample sample = Timer.start(registry.get());
        try {
            T result = supplier.get();
            sample.stop(operationTimer(registry.get(), operation, false));
            return result;
        } catch (IOException | RuntimeException e) {
            sample.stop(operationTimer(registry.get(), operation, true));
            throw e;
        }
    }

    private static Timer operationTimer(MeterRegistry meterRegistry, String operation, boolean failure) {
        return Timer.builder("json.schema.registry.operation.duration")
            .description("JSON Schema Registry target operation duration")
            .tag("target", "sr")
            .tag("operation", operation)
            .tag("failure", Boolean.toString(failure))
            .register(meterRegistry);
    }

    private String path(String path) {
        if (basePath == null || basePath.isBlank()) {
            return path;
        }
        return basePath + path;
    }

    private static IOException schemaRegistryException(String action, HttpClientResponseException e) {
        return new IOException("Schema Registry failed to " + action + ": HTTP "
            + e.getStatus().getCode() + " " + responseBody(e), e);
    }

    private static String body(HttpResponse<String> response) {
        return response.getBody().orElse("");
    }

    private static String responseBody(HttpClientResponseException e) {
        return e.getResponse().getBody(String.class).orElse("");
    }

    private static boolean isRetryable(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private static void backoff(Exception e) throws IOException {
        try {
            Thread.sleep(RETRY_BACKOFF_MS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while retrying Schema Registry request", interrupted);
        }
    }

    private static String encodePath(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8081";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String trimBasePath(String value) {
        if (value == null || value.isBlank() || "/".equals(value)) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static JsonSchemaRegistryConfiguration.SrConfiguration srConfiguration(String baseUrl) {
        JsonSchemaRegistryConfiguration.SrConfiguration configuration = new JsonSchemaRegistryConfiguration.SrConfiguration();
        configuration.setUrl(baseUrl);
        return configuration;
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }
}
