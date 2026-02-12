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

class MicronautShippedSchemasTest {

    @Test
    void discoversCommonMicronautConfigurationSchemas() {
        var schemas = JsonSchemaClassPathResourceLoader.createDefault(getClass().getClassLoader()).jsonSchemas();

        assertTrue(schemas.containsKey("io.micronaut.http.ssl.ServerSslConfiguration.json"));
        assertTrue(schemas.containsKey("io.micronaut.scheduling.executor.UserExecutorConfiguration.json"));
        assertTrue(schemas.containsKey("io.micronaut.http.server.HttpServerConfiguration.json"));
        assertTrue(schemas.containsKey("io.micronaut.web.router.resource.StaticResourceConfiguration.json"));
        assertTrue(schemas.containsKey("io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration$NettyListenerConfiguration.json"));
        assertTrue(schemas.containsKey("io.micronaut.http.client.DefaultHttpClientConfiguration.json"));
        assertTrue(schemas.containsKey("io.micronaut.jackson.JacksonConfiguration.json"));
    }

    @Test
    void validatesAgainstMicronautEachPropertyExecutorSchema() {
        Environment environment = createEnvironment(Map.of(
            // valid entry
            "micronaut.executors.alpha.type", "FIXED",
            // invalid minimum
            "micronaut.executors.alpha.parallelism", "0",
            // invalid enum
            "micronaut.executors.beta.type", "INVALID",
            // invalid boolean
            "micronaut.executors.beta.virtual", "not-a-bool",
            // invalid field
            "micronaut.executors.beta.unknown", "x"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(true);

        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("micronaut.executors.alpha.parallelism") && e.message().contains(">=")));
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("micronaut.executors.beta.type") && e.message().contains("enum")));
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("micronaut.executors.beta.virtual") && e.message().contains("boolean")));
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("micronaut.executors.beta.unknown") && e.message().contains("not present")));
    }

    @Test
    void validatesAgainstMicronautServerSslSchema() {
        Environment environment = createEnvironment(Map.of(
            // boolean
            "micronaut.server.ssl.enabled", "not-a-bool",
            // integer
            "micronaut.server.ssl.port", "not-a-number",
            // duration format
            "micronaut.server.ssl.handshake-timeout", "not-a-duration",
            // unknown
            "micronaut.server.ssl.extra", "x"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(true);

        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);

        assertTrue(errors.stream().anyMatch(e -> e.property().equals("micronaut.server.ssl.enabled") && e.message().contains("boolean")));
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("micronaut.server.ssl.port") && (e.message().contains("number") || e.message().contains("integer"))));
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("micronaut.server.ssl.handshake-timeout") && e.message().toLowerCase().contains("duration")));
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("micronaut.server.ssl.extra") && e.message().contains("not present")));
    }

    @Test
    void reportsDeprecatedSchemaPropertiesAsWarnings() {
        Environment environment = createEnvironment(Map.of(
            "micronaut.server.read-timeout", "1"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(true);

        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);

        assertTrue(errors.stream().anyMatch(e -> e.property().equals("micronaut.server.read-timeout") && e.type() == ConfigurationError.Type.WARNING));
        assertFalse(errors.stream().anyMatch(e -> e.type() == ConfigurationError.Type.ERROR));
    }

    @Test
    void validatesAgainstMicronautStaticResourceEachPropertySchema() {
        Environment environment = createEnvironment(Map.of(
            "micronaut.router.static-resources.assets.paths", "classpath:public",
            "micronaut.router.static-resources.assets.unknown", "x"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(true);

        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);

        assertTrue(errors.stream().anyMatch(e -> e.property().equals("micronaut.router.static-resources.assets.unknown") && e.message().contains("not present")));
    }

    @Test
    void validatesAgainstMicronautNettyListenerEachPropertySchema() {
        Environment environment = createEnvironment(Map.of(
            "micronaut.server.netty.listeners.main.family", "INVALID",
            "micronaut.server.netty.listeners.main.ssl", "not-a-bool",
            "micronaut.server.netty.listeners.main.unknown", "x"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(true);

        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);

        assertTrue(errors.stream().anyMatch(e -> e.property().equals("micronaut.server.netty.listeners.main.family") && e.message().contains("enum")));
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("micronaut.server.netty.listeners.main.ssl") && e.message().contains("boolean")));
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("micronaut.server.netty.listeners.main.unknown") && e.message().contains("not present")));
    }

    @Test
    void validatesAgainstMicronautDefaultHttpClientSchema() {
        Environment environment = createEnvironment(Map.of(
            "micronaut.http.client.read-timeout", "not-a-duration",
            "micronaut.http.client.log-level", "INVALID",
            "micronaut.http.client.channel-options", "not-an-object"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(true);

        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);

        assertTrue(errors.stream().anyMatch(e -> e.property().equals("micronaut.http.client.read-timeout") && e.message().toLowerCase().contains("duration")));
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("micronaut.http.client.log-level") && e.message().contains("enum")));
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("micronaut.http.client.channel-options") && e.message().contains("object")));
    }

    @Test
    void canSuppressErrorsWithWildcardPatterns() {
        Environment environment = createEnvironment(Map.of(
            "micronaut.http.client.unknown", "x"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(true);
        validator.setSuppressionPatterns(List.of("micronaut.http.*"));

        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);

        assertTrue(errors.stream().anyMatch(e -> e.property().equals("micronaut.http.client.unknown") && e.type() == ConfigurationError.Type.WARNING));
        assertFalse(errors.stream().anyMatch(e -> e.type() == ConfigurationError.Type.ERROR));
    }

    @Test
    void canSuppressErrorsWithPrefixPatterns() {
        Environment environment = createEnvironment(Map.of(
            "micronaut.http.client.unknown", "x"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(true);
        validator.setSuppressionPatterns(List.of("micronaut.http"));

        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);

        assertTrue(errors.stream().anyMatch(e -> e.property().equals("micronaut.http.client.unknown") && e.type() == ConfigurationError.Type.WARNING));
        assertFalse(errors.stream().anyMatch(e -> e.type() == ConfigurationError.Type.ERROR));
    }

    @Test
    void validatesAgainstMicronautJacksonSchemaAdditionalProperties() {
        Environment environment = createEnvironment(Map.of(
            "jackson.serialization-features.FAIL_ON_EMPTY_BEANS", "not-a-bool"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(true);

        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);

        assertTrue(errors.stream().anyMatch(e -> e.property().startsWith("jackson.serialization-features.") && e.message().contains("boolean")));
    }

    private static Environment createEnvironment(Map<String, Object> properties) {
        ClassLoader classLoader = MicronautShippedSchemasTest.class.getClassLoader();
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
