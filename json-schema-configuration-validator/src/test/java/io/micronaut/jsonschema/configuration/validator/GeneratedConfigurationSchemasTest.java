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
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GeneratedConfigurationSchemasTest {

    @Test
    void validatesGeneratedConfigurationSchemaForCustomConfigurationProperties() {
        // These should be compiled with Micronaut's configuration schema generation,
        // producing META-INF/micronaut-configuration-schemas/<fqcn>.json on the test classpath.
        var schemas = io.micronaut.jsonschema.utils.JsonSchemaClassPathResourceLoader
            .createDefault(getClass().getClassLoader())
            .jsonSchemas();

        assertTrue(schemas.containsKey(ValidationTestConfiguration.class.getName() + ".json"));

        Environment env = createEnvironment(Map.of(
            "test.validation.enabled", "not-a-bool",
            "test.validation.count", "0",
            "test.validation.name", "",
            "test.validation.mode", "C",
            "test.validation.ints", "1,2,3",
            "test.validation.extra", "x"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(true);

        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), env);

        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.validation.enabled") && e.message().contains("boolean")));
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.validation.count") && e.message().contains(">=")));
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.validation.name") && (e.message().contains("Length") || e.message().contains("Missing"))));
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.validation.mode") && e.message().contains("enum")));
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.validation.extra") && e.message().contains("not present")));
    }

    @Test
    void validatesEnumConfigurationCaseInsensitively() {
        Environment env = createEnvironment(Map.of(
            "test.validation.mode", "a"
        ));

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(true);

        Set<ConfigurationError> errors = validator.validate(getClass().getClassLoader(), env);

        assertFalse(errors.stream().anyMatch(e -> e.property().equals("test.validation.mode")), () -> "Unexpected enum errors: " + errors);
    }

    private static Environment createEnvironment(Map<String, Object> properties) {
        ClassLoader classLoader = GeneratedConfigurationSchemasTest.class.getClassLoader();
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

    enum Mode {
        A,
        B
    }

    @ConfigurationProperties("test.validation")
    static final class ValidationTestConfiguration {
        private boolean enabled;

        @Min(1)
        @Max(10)
        private int count;

        @NotBlank
        @Size(min = 2, max = 5)
        private String name;

        private Mode mode;

        private List<Integer> ints;

        private Map<String, String> map;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Mode getMode() {
            return mode;
        }

        public void setMode(Mode mode) {
            this.mode = mode;
        }

        public List<Integer> getInts() {
            return ints;
        }

        public void setInts(List<Integer> ints) {
            this.ints = ints;
        }

        public Map<String, String> getMap() {
            return map;
        }

        public void setMap(Map<String, String> map) {
            this.map = map;
        }
    }
}
