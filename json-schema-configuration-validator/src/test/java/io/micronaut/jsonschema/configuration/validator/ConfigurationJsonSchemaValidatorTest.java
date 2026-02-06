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
package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.ApplicationContextConfiguration;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.jsonschema.utils.JsonSchemaClassPathResourceLoader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationJsonSchemaValidatorTest {

    @Test
    void discoversTestSchemasViaJsonSchemasApi() {
        var schemas = JsonSchemaClassPathResourceLoader.createDefault(getClass().getClassLoader()).jsonSchemas();
        assertTrue(schemas.containsKey("test.config.TestConfig.json"));
        assertTrue(schemas.containsKey("test.executors.UserExecutorConfiguration.json"));
    }

    @Test
    void validatesConfigurationPropertiesSchemasAndCapturesOrigin() {
        Environment environment = createEnvironment(Map.of(
            "test.config.enabled", "not-a-bool",
            "test.config.count", "0",
            "test.config.ratio", "2",
            "test.config.mode", "C",
            "test.config.names", "a,b,c",
            "test.config.extra", "x"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(true);
        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);

        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.config.enabled") && e.message().contains("boolean")));
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.config.count") && e.message().contains(">=")));
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.config.ratio") && e.message().contains("<")));
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.config.mode") && e.message().contains("enum")));
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.config.extra") && e.message().contains("not present")));

        ConfigurationError enabled = errors.stream().filter(e -> e.property().equals("test.config.enabled")).findFirst().orElseThrow();
        assertEquals("test-origin", enabled.originLocation());
        assertEquals("test.config.enabled", enabled.rawPropertyName());
        assertEquals("not-a-bool", enabled.rawValue());
    }

    @Test
    void canIgnoreUnknownPropertiesWhenFailOnNotPresentIsFalse() {
        Environment environment = createEnvironment(Map.of(
            "test.config.enabled", "true",
            "test.config.count", "1",
            "test.config.extra", "x"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(false);

        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);
        assertFalse(errors.stream().anyMatch(e -> e.property().equals("test.config.extra")));
    }

    @Test
    void validatesEachPropertySchemasViaPropertyEntries() {
        Environment environment = createEnvironment(Map.of(
            "test.executors.alpha.n-threads", "0",
            "test.executors.alpha.type", "FIXED",
            "test.executors.beta.type", "CACHED",
            "test.executors.beta.unknown", "x"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(true);

        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);

        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.executors.alpha.n-threads") && e.message().contains(">=")));
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.executors.beta.n-threads") && e.message().contains("Missing required")));
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.executors.beta.unknown") && e.message().contains("not present")));
    }

    private static Environment createEnvironment(Map<String, Object> properties) {
        ClassLoader classLoader = ConfigurationJsonSchemaValidatorTest.class.getClassLoader();
        ApplicationContextConfiguration configuration = new ApplicationContextConfiguration() {
            @Override
            public List<String> getEnvironments() {
                return List.of("test");
            }

            @Override
            public ClassLoader getClassLoader() {
                return classLoader;
            }

            @Override
            public Optional<Boolean> getDeduceEnvironments() {
                return Optional.of(false);
            }

            @Override
            public boolean isEnableDefaultPropertySources() {
                return false;
            }
        };
        Environment environment = Environment.create(configuration);
        environment.addPropertySource(PropertySource.of("test", properties, PropertySource.Origin.of("test-origin")));
        return environment.start();
    }
}
