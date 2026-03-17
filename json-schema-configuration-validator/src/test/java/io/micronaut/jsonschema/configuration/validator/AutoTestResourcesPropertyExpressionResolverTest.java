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
import io.micronaut.core.convert.MutableConversionService;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoTestResourcesPropertyExpressionResolverTest {

    private final AutoTestResourcesPropertyExpressionResolver resolver = new AutoTestResourcesPropertyExpressionResolver();

    @Test
    void nonAutoTestResourcesExpressionsRemainUnresolved() {
        try (Environment environment = newEnvironment()) {
            assertTrue(resolver.resolve(environment, MutableConversionService.create(), "datasources.default.username", String.class).isEmpty());
        }
    }

    @Test
    void autoTestResourcesExpressionsResolveToDummyString() {
        try (Environment environment = newEnvironment()) {
            assertEquals("test-resource", resolver.resolve(environment, MutableConversionService.create(), "auto.test.resources.datasources.default.username", String.class).orElseThrow());
        }
    }

    @Test
    void resolverUsesPrimaryConversionResultWhenAvailable() {
        try (Environment environment = newEnvironment()) {
            URI uri = resolver.resolve(environment, MutableConversionService.create(), "auto.test.resources.datasources.default.url", URI.class).orElseThrow();
            assertEquals(URI.create("test-resource"), uri);
        }
    }

    @Test
    void resolverFallsBackToFalseLiteralWhenRequired() {
        try (Environment environment = newEnvironment()) {
            MutableConversionService conversionService = MutableConversionService.create();
            conversionService.addConverter(String.class, FalseOnlyValue.class, value -> "false".equals(value) ? new FalseOnlyValue(value) : null);

            FalseOnlyValue value = resolver.resolve(environment, conversionService, "auto.test.resources.flag", FalseOnlyValue.class).orElseThrow();
            assertEquals("false", value.value());
            assertFalse("0".equals(value.value()));
        }
    }

    @Test
    void resolverFallsBackToZeroLiteralWhenRequired() {
        try (Environment environment = newEnvironment()) {
            MutableConversionService conversionService = MutableConversionService.create();
            conversionService.addConverter(String.class, ZeroOnlyValue.class, value -> "0".equals(value) ? new ZeroOnlyValue(value) : null);

            ZeroOnlyValue value = resolver.resolve(environment, conversionService, "auto.test.resources.port", ZeroOnlyValue.class).orElseThrow();
            assertEquals("0", value.value());
        }
    }

    record FalseOnlyValue(String value) {
    }

    record ZeroOnlyValue(String value) {
    }

    private static Environment newEnvironment() {
        ClassLoader classLoader = AutoTestResourcesPropertyExpressionResolverTest.class.getClassLoader();
        return Environment.create(new io.micronaut.context.ApplicationContextConfiguration() {
            @Override
            public Optional<Boolean> getDeduceEnvironments() {
                return Optional.of(false);
            }

            @Override
            public List<String> getEnvironments() {
                return List.of();
            }

            @Override
            public ClassLoader getClassLoader() {
                return classLoader;
            }
        }).start();
    }
}
