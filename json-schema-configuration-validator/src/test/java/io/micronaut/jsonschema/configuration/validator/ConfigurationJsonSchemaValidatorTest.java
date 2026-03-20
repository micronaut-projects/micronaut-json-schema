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
import io.micronaut.core.util.StringUtils;
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
        assertTrue(schemas.containsKey("test.bindable.BindableDefaultsConfig.json"));
    }

    @Test
    void doesNotReportMissingRequiredForPropertiesWithSchemaDefault() {
        Environment environment = createEnvironment(Map.of(
            "test.bindable.enabled", StringUtils.TRUE
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(true);

        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);

        assertFalse(errors.stream().anyMatch(e -> e.property().equals("test.bindable.initial-pool-size")
            && e.message().contains("Missing required")), () -> "Unexpected missing required error for initial-pool-size: " + errors);
        assertFalse(errors.stream().anyMatch(e -> e.property().equals("test.bindable.max-pool-size")
            && e.message().contains("Missing required")), () -> "Unexpected missing required error for max-pool-size: " + errors);
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
            "test.config.enabled", StringUtils.TRUE,
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
            "source.enabled", StringUtils.TRUE,
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
    void unresolvedPlaceholderProducesWarningWithoutException() {
        Environment environment = createEnvironment(Map.of(
            "test.config.enabled", "${nonexistent.placeholder}",
            "test.config.count", "1"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(true);

        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);
        assertTrue(errors.stream().noneMatch(e -> e.type() == ConfigurationError.Type.ERROR),
            () -> "Did not expect validation errors, got: " + errors);
        assertTrue(errors.stream().anyMatch(e ->
            e.type() == ConfigurationError.Type.WARNING
                && e.property() != null
                && e.property().startsWith("test.config")
                && e.message().contains("Could not resolve placeholder")),
            () -> "Expected unresolved placeholder warning for test.config, got: " + errors);
    }

    @Test
    void unresolvedPlaceholderInEachPropertyProducesWarningWithoutException() {
        Environment environment = createEnvironment(Map.of(
            "test.executors.alpha.n-threads", "${nonexistent.threads}",
            "test.executors.alpha.type", "FIXED"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(true);

        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);
        assertTrue(errors.stream().noneMatch(e -> e.type() == ConfigurationError.Type.ERROR),
            () -> "Did not expect validation errors, got: " + errors);
        assertTrue(errors.stream().anyMatch(e ->
            e.type() == ConfigurationError.Type.WARNING
                && e.property() != null
                && e.property().startsWith("test.executors.alpha")
                && e.message().contains("Could not resolve placeholder")),
            () -> "Expected unresolved placeholder warning for test.executors.alpha, got: " + errors);
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
    void validatesMicronautSqlDatasourceConfigurationFromPublishedRelease() {
        Environment environment = createEnvironment(Map.of(
            "datasources.default.driver-class-name", "org.h2.Driver",
            "datasources.default.url", "jdbc:h2:mem:devDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE",
            "datasources.default.username", "sa",
            "datasources.default.password", ""
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(true);

        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);

        assertTrue(errors.isEmpty(), () -> "Expected no errors, got: " + errors);
    }

    @Test
    void validatesIssue316ApplicationPropertiesSnippetWithDataJdbcFixture() {
        Environment environment = createEnvironment(Map.of(
            "datasources.default.dialect", "H2",
            "datasources.default.driver-class-name", "org.h2.Driver",
            "datasources.default.url", "jdbc:h2:mem:devDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE",
            "datasources.default.username", "sa",
            "datasources.default.password", ""
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(true);

        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);

        assertTrue(errors.isEmpty(), () -> "Expected no errors, got: " + errors);
    }

    @Test
    void validatesAdditionalPropertiesSchemaEvenWhenFailOnNotPresentIsFalse() {
        Environment environment = createEnvironment(Map.of(
            "test.executors.alpha.n-threads", "0"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(false);

        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);

        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.executors.alpha.n-threads")
            && e.message().contains(">=")), () -> "Expected minimum validation error, got: " + errors);
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
            "test.suggest.enabeld", StringUtils.TRUE,
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
    void resolvesLineNumbersAndSnippetsFromClasspathOrigins() {
        Environment environment = createEnvironment(
            Map.of(
                "test.config.enabled", "not-a-bool",
                "test.config.count", "1"
            ),
            "classpath:origin-test.properties"
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
            "test.config.enabled", StringUtils.TRUE,
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
            "test.config.enabled", StringUtils.TRUE,
            "test.config.count", "1",
            "test.config.max-allowed.foo", "x",
            "test.config.max-allowed.bar", "y"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);

        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.config.max-allowed") && e.message().contains("at most")));
    }

    @Test
    void supportsOverlappingConfigurationPropertiesSchemasForSamePrefix() {
        Environment environment = createEnvironment(Map.of(
            "test.overlap.foo", "not-a-bool",
            "test.overlap.bar", "not-an-int",
            "test.overlap.extra", "x"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(true);

        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), environment);

        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.overlap.foo") && e.message().contains("boolean"))
            , () -> "Expected boolean validation error, got: " + errors);
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.overlap.bar") && e.message().contains("integer"))
            , () -> "Expected integer validation error, got: " + errors);

        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.overlap.extra") && e.message().contains("not present"))
            , () -> "Expected unknown property error for extra, got: " + errors);

        // When multiple schemas share the same prefix, keys defined in any schema must not be
        // reported as unknown just because they are not present in the current schema.
        assertFalse(errors.stream().anyMatch(e -> e.property().equals("test.overlap.foo") && e.message().contains("not present"))
            , () -> "Unexpected unknown-property error for foo, got: " + errors);
        assertFalse(errors.stream().anyMatch(e -> e.property().equals("test.overlap.bar") && e.message().contains("not present"))
            , () -> "Unexpected unknown-property error for bar, got: " + errors);
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
