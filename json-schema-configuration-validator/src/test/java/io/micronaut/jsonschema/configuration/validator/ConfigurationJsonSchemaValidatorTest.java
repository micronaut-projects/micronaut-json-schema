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
import org.junit.jupiter.api.io.TempDir;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationJsonSchemaValidatorTest {

    @Test
    void discoversTestSchemasViaJsonSchemasApi() {
        var schemas = JsonSchemaClassPathResourceLoader.createDefault(getClass().getClassLoader()).jsonSchemas();
        assertTrue(schemas.containsKey("test.config.TestConfig.json"));
        assertTrue(schemas.containsKey("test.executors.UserExecutorConfiguration.json"));
        assertTrue(schemas.containsKey("test.edge.EdgeCases.json"));
        assertTrue(schemas.containsKey("test.suggest.SuggestConfig.json"));
        assertTrue(schemas.containsKey("test.enum.EnumConfig.json"));
        assertTrue(schemas.containsKey("test.pattern.PatternConfig.json"));
        assertTrue(schemas.containsKey("test.url.UrlConfig.json"));
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
    void resolvesPropertyPlaceholdersBeforeValidation() {
        Environment environment = createEnvironment(Map.of(
            "source.enabled", "true",
            "source.count", "2",
            "test.config.enabled", "${source.enabled}",
            "test.config.count", "${source.count}"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(true);

        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);
        assertTrue(errors.isEmpty(), () -> "Expected no errors, got: " + errors);
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

    @Test
    void validatesArrayOfObjectsAndNestedConstraints() {
        Environment environment = createEnvironment(Map.of(
            "test.edge.servers[0].port", "0",
            "test.edge.servers[0].name", "",
            "test.edge.servers[0].extra", "x",
            "test.edge.servers[1].name", "b"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(true);

        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);

        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.edge.servers[0].port") && e.message().contains(">=")));
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.edge.servers[0].name") && e.message().toLowerCase().contains("length")));
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.edge.servers[0].extra") && e.message().contains("not present")));
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.edge.servers[1].port") && e.message().contains("Missing required")));
    }

    @Test
    void suggestsSimilarPropertiesForUnknownKeys() {
        Environment environment = createEnvironment(Map.of(
            "test.suggest.enabeld", "true",
            "test.suggest.read-timeout", "1s"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(true);

        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);

        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.suggest.enabeld")
            && e.message().contains("Did you mean")
            && e.message().contains("test.suggest.enabled")));
    }

    @Test
    void listsAllowedValuesForEnumValidationErrors() {
        Environment environment = createEnvironment(Map.of(
            "test.enum.mode", "C"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);

        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.enum.mode")
            && e.message().contains("enum")
            && e.message().contains("'A'")
            && e.message().contains("'B'")));
    }

    @Test
    void validatesRegexPatternJavaTypeValues() {
        Environment environment = createEnvironment(Map.of(
            "test.pattern.regex", "["
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);

        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.pattern.regex")
            && e.message().toLowerCase().contains("regex")));
    }

    @Test
    void validatesUrlAndUriJavaTypeValues() {
        Environment environment = createEnvironment(Map.of(
            "test.url.endpoint", "ht!tp://bad",
            "test.url.location", "http://bad uri"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);

        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.url.endpoint")
            && e.message().toLowerCase().contains("url")));
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.url.location")
            && e.message().toLowerCase().contains("uri")));
    }

    @Test
    void resolvesLineNumbersAndSnippetsFromFileOrigins(@TempDir Path tempDir) throws Exception {
        Path propertiesFile = tempDir.resolve("application.properties");
        Files.writeString(propertiesFile, String.join("\n",
            "test.config.enabled=not-a-bool",
            "test.config.count=1",
            ""
        ), StandardCharsets.UTF_8);

        Environment environment = createEnvironment(
            Map.of(
                "test.config.enabled", "not-a-bool",
                "test.config.count", "1"
            ),
            propertiesFile.toUri().toString()
        );

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(true);
        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);

        ConfigurationError enabled = errors.stream().filter(e -> e.property().equals("test.config.enabled")).findFirst().orElseThrow();
        assertEquals(1, enabled.lineNumber());
        assertNotNull(enabled.snippet());
        assertTrue(enabled.snippet().contains("test.config.enabled=not-a-bool"));
        assertEquals("properties", enabled.snippetLanguage());
    }

    @Test
    void validatesMinPropertiesForObjects() {
        Environment environment = createEnvironment(Map.of(
            "test.config.enabled", "true",
            "test.config.count", "1",
            "test.config.min-required.foo", "x"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);

        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.config.min-required") && e.message().contains("at least")));
    }

    @Test
    void validatesMaxPropertiesForObjects() {
        Environment environment = createEnvironment(Map.of(
            "test.config.enabled", "true",
            "test.config.count", "1",
            "test.config.max-allowed.foo", "x",
            "test.config.max-allowed.bar", "y"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);

        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.config.max-allowed") && e.message().contains("at most")));
    }

    private static Environment createEnvironment(Map<String, Object> properties) {
        return createEnvironment(properties, "test-origin");
    }

    private static Environment createEnvironment(Map<String, Object> properties, String originLocation) {
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
        environment.addPropertySource(PropertySource.of("test", properties, PropertySource.Origin.of(originLocation)));
        return environment.start();
    }
}
