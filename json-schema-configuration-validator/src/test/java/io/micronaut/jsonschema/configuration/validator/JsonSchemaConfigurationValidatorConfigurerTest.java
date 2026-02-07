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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JsonSchemaConfigurationValidatorConfigurerTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void cleanup() {
        System.clearProperty(TestApplicationContextConfigurer.ENABLED_PROP);
        TestApplicationContextConfigurer.INVOKED.set(false);
    }

    @Test
    void applicationContextConfigurersAreApplied() throws Exception {
        Path cp = tempDir.resolve("cp");
        Files.createDirectories(cp);
        Files.writeString(cp.resolve("application-configured.properties"), String.join("\n",
            "test.config.enabled=not-a-bool",
            "test.config.count=1",
            ""
        ), StandardCharsets.UTF_8);

        String classpath = cp + File.pathSeparator + System.getProperty("java.class.path");

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        JsonSchemaConfigurationValidator facade = JsonSchemaConfigurationValidator.forClasspath(classpath, List.of("test"), validator);

        // Sanity: without the configurer enabled, there are no properties loaded -> no errors.
        Set<ConfigurationError> baseline = facade.validate();
        assertTrue(baseline.isEmpty(), () -> "Unexpected errors: " + baseline);

        System.setProperty(TestApplicationContextConfigurer.ENABLED_PROP, "true");
        Set<ConfigurationError> errors = facade.validate();

        assertTrue(TestApplicationContextConfigurer.INVOKED.get(), "Expected ApplicationContextConfigurer to be invoked");
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("test.config.enabled")),
            () -> "Expected validation error from application-configured.properties, got: " + errors);
    }
}
