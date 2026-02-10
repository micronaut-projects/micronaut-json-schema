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

import io.micronaut.context.env.Environment;
import io.micronaut.context.ApplicationContextConfiguration;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.util.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationRuleIntegrationTest {

    @AfterEach
    void cleanup() {
        System.clearProperty(TestConfigurationRule.ENABLED_PROP);
    }

    @Test
    void configurationRuleCanReportDependentConfigurationErrors() {
        System.setProperty(TestConfigurationRule.ENABLED_PROP, StringUtils.TRUE);

        Environment env = createEnvironment(Map.of(
            "jpa.default.properties.hibernate.hbm2ddl.auto", "validate"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), env);

        assertTrue(errors.stream().anyMatch(e -> e.property().equals("datasources.default.url")
            && e.message().contains("Required when jpa.default.properties")),
            () -> "Expected rule error, got: " + errors);
    }

    @Test
    void supportsPrefixFiltersRuleExecution() {
        System.setProperty(TestConfigurationRule.ENABLED_PROP, StringUtils.TRUE);

        Environment env = createEnvironment(Map.of(
            // Ensure a schema is validated so rules would run if not filtered.
            "test.config.enabled", StringUtils.TRUE,
            "test.config.count", "1",
            // Add the dependent config to avoid the jpa error.
            "datasources.default.url", "jdbc:h2:mem:test"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), env);

        assertTrue(errors.isEmpty(), () -> "Expected no rule errors due to prefix filtering, got: " + errors);
    }

    @Test
    void configurationRuleSeesFullyQualifiedEachPropertyPrefix() {
        System.setProperty(TestConfigurationRule.ENABLED_PROP, StringUtils.TRUE);

        Environment env = createEnvironment(Map.of(
            "test.executors.alpha.n-threads", "1"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(false);

        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), env);

        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.executors.alpha.rule")
            && e.message().contains("prefix=test.executors.alpha")
            && e.message().contains("n-threads=1")),
            () -> "Expected each-property rule error, got: " + errors);
    }

    private static Environment createEnvironment(Map<String, Object> properties) {
        ClassLoader classLoader = ConfigurationRuleIntegrationTest.class.getClassLoader();
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
