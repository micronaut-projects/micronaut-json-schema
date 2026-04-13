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

import io.micronaut.jsonschema.configuration.validator.cli.DependencyInjectionConfigurationValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyInjectionConfigurationValidatorConfigurerTest {

    @AfterEach
    void cleanup() {
        System.clearProperty(TestDependencyInjectionApplicationContextConfigurer.ENABLED_PROP);
        TestDependencyInjectionApplicationContextConfigurer.INVOKED.set(false);
    }

    @Test
    void applicationContextConfigurersAreApplied() {
        DependencyInjectionConfigurationValidator facade = DependencyInjectionConfigurationValidator.forClasspath(
            System.getProperty("java.class.path"),
            List.of(),
            false
        );

        Set<DependencyInjectionError> baseline = facade.validate();
        assertTrue(baseline.stream().noneMatch(e -> e.rootBean().contains("FixtureContextBean")),
            () -> "Unexpected baseline DI errors: " + baseline);

        System.setProperty(TestDependencyInjectionApplicationContextConfigurer.ENABLED_PROP, "true");

        Set<DependencyInjectionError> errors = facade.validate();

        assertTrue(TestDependencyInjectionApplicationContextConfigurer.INVOKED.get(), "Expected ApplicationContextConfigurer to be invoked");
        assertFalse(errors.isEmpty(), "Expected DI validation errors after configurer activated the test environment");
        assertTrue(errors.stream().anyMatch(e -> e.rootBean().contains("FixtureContextBean")),
            () -> "Expected FixtureContextBean DI error after configurer activation, got: " + errors);
    }
}
