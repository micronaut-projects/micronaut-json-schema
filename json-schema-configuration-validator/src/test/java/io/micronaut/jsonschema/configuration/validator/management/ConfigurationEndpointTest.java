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

import io.micronaut.context.annotation.Property;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.jsonschema.configuration.validator.ConfigurationValidatorConfiguration;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Property(name = "test.config.enabled", value = "not-a-bool")
@Property(name = "test.config.count", value = "1")
@Property(name = "endpoints.configurationerrors.sensitive", value = StringUtils.FALSE)
@MicronautTest
class ConfigurationEndpointTest {

    @Test
    void configurationEndpointExposesErrorsNamespace(@Client("/") HttpClient httpClient) {
        BlockingHttpClient client = httpClient.toBlocking();
        Map<String, Object> decoded = assertDoesNotThrow(() -> client.retrieve(HttpRequest.GET("/configurationerrors"), Argument.mapOf(String.class, Object.class)));
        assertTrue(decoded.containsKey("errors"), () -> "Unexpected response: " + decoded);
        Object errors = decoded.get("errors");
        assertInstanceOf(List.class, errors, () -> "Expected list, got: " + errors);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) errors;
        assertTrue(list.stream().anyMatch(e -> "test.config.enabled".equals(e.get("property"))));
    }
}
