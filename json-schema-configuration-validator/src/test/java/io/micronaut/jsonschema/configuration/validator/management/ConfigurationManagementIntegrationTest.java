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
package io.micronaut.jsonschema.configuration.validator.management;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.json.JsonMapper;
import io.micronaut.jsonschema.configuration.validator.ConfigurationValidatorConfiguration;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationManagementIntegrationTest {

    @Test
    void healthIndicatorReportsDownWhenErrorsPresent() throws Exception {
        try (EmbeddedServer server = startServer(Map.of(
            ConfigurationValidatorConfiguration.PREFIX + ".health.enabled", true,
            "test.config.enabled", "not-a-bool",
            "test.config.count", "1"
        ))) {
            try (HttpClient client = HttpClient.create(server.getURL())) {
                String json;
                try {
                    json = client.toBlocking().retrieve(HttpRequest.GET("/health"));
                } catch (HttpClientResponseException e) {
                    json = e.getResponse().getBody(String.class).orElse("");
                }
                JsonMapper mapper = server.getApplicationContext().getBean(JsonMapper.class);
                @SuppressWarnings("unchecked")
                Map<String, Object> decoded = (Map<String, Object>) mapper.readValue(json, Argument.of(Object.class));
                assertEquals("DOWN", decoded.get("status"), () -> "Unexpected response: " + decoded);
            }
        }
    }

    @Test
    void healthIndicatorCanBeDisabled() throws Exception {
        try (EmbeddedServer server = startServer(Map.of(
            ConfigurationValidatorConfiguration.PREFIX + ".health.enabled", false,
            "test.config.enabled", "not-a-bool",
            "test.config.count", "1"
        ))) {
            try (HttpClient client = HttpClient.create(server.getURL())) {
                String json = client.toBlocking().retrieve(HttpRequest.GET("/health"));
                JsonMapper mapper = server.getApplicationContext().getBean(JsonMapper.class);
                @SuppressWarnings("unchecked")
                Map<String, Object> decoded = (Map<String, Object>) mapper.readValue(json, Argument.of(Object.class));
                assertEquals("UP", decoded.get("status"), () -> "Unexpected response: " + decoded);
            }
        }
    }

    @Test
    void configurationEndpointExposesErrorsNamespace() throws Exception {
        try (EmbeddedServer server = startServer(Map.of(
            ConfigurationValidatorConfiguration.PREFIX + ".endpoint.enabled", true,
            "endpoints.configurationerrors.enabled", true,
            "test.config.enabled", "not-a-bool",
            "test.config.count", "1"
        ))) {
            try (HttpClient client = HttpClient.create(server.getURL())) {
                String json = client.toBlocking().retrieve(HttpRequest.GET("/configurationerrors"));
                JsonMapper mapper = server.getApplicationContext().getBean(JsonMapper.class);
                @SuppressWarnings("unchecked")
                Map<String, Object> decoded = (Map<String, Object>) mapper.readValue(json, Argument.of(Object.class));

                assertTrue(decoded.containsKey("errors"), () -> "Unexpected response: " + decoded);
                Object errors = decoded.get("errors");
                assertTrue(errors instanceof List, () -> "Expected list, got: " + errors);

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> list = (List<Map<String, Object>>) errors;
                assertTrue(list.stream().anyMatch(e -> "test.config.enabled".equals(e.get("property"))));
            }
        }
    }

    @Test
    void configurationEndpointCanBeDisabled() {
        try (EmbeddedServer server = startServer(Map.of(
            ConfigurationValidatorConfiguration.PREFIX + ".endpoint.enabled", false,
            "endpoints.configurationerrors.enabled", true,
            "test.config.enabled", "not-a-bool",
            "test.config.count", "1"
        ))) {
            try (HttpClient client = HttpClient.create(server.getURL())) {
                RuntimeException e = assertThrows(RuntimeException.class,
                    () -> client.toBlocking().retrieve(HttpRequest.GET("/configurationerrors")));
                assertTrue(e instanceof HttpClientResponseException || e instanceof io.micronaut.http.client.exceptions.ResponseClosedException,
                    () -> "Unexpected exception: " + e.getClass() + ": " + e.getMessage());
            }
        }
    }

    private static EmbeddedServer startServer(Map<String, Object> properties) {
        Map<String, Object> merged = new HashMap<>();
        merged.put("micronaut.server.port", -1);
        merged.put("endpoints.all.enabled", true);
        merged.put("endpoints.all.sensitive", false);
        merged.put("endpoints.health.enabled", true);
        merged.put("endpoints.health.sensitive", false);
        merged.put("endpoints.health.details-visible", "ANONYMOUS");
        merged.put("endpoints.configurationerrors.sensitive", false);
        merged.putAll(properties);

        return ApplicationContext.run(EmbeddedServer.class, merged);
    }
}
