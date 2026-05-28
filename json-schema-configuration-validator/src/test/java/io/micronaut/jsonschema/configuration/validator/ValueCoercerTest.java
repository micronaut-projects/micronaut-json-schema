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
import io.micronaut.json.JsonMapper;
import io.micronaut.jsonschema.configuration.validator.model.ConfigurationSchema;
import io.micronaut.jsonschema.configuration.validator.model.ConfigurationSchemaProperty;
import io.micronaut.jsonschema.configuration.validator.model.ConfigurationSchemaType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ValueCoercerTest {

    @Test
    void schemaTypesToTypeHandlesNullAndStringsAndLists() {
        assertNull(SchemaTypes.toType(null));
        assertEquals(ConfigurationSchemaType.STRING, SchemaTypes.toType("string"));
        assertEquals(ConfigurationSchemaType.BOOLEAN, SchemaTypes.toType(List.of("boolean")));
        assertNull(SchemaTypes.toType(List.of()));
        assertNull(SchemaTypes.toType(List.of(1)));
    }

    @Test
    void coerceReturnsNullWhenValueIsNull() {
        SchemaContext ctx = newContext();
        Set<ConfigurationError> errors = new LinkedHashSet<>();

        assertNull(ValueCoercer.coerce(ctx, schemaProperty("string", null), "p", null, null, errors));
    }

    @Test
    void coerceDoesNotConvertObjects() {
        SchemaContext ctx = newContext();
        Set<ConfigurationError> errors = new LinkedHashSet<>();

        Map<String, Object> value = Map.of("a", "b");
        Object result = ValueCoercer.coerce(ctx, schemaProperty("object", null), "p", null, value, errors);
        assertSame(value, result);
    }

    @Test
    void coerceSkipsBooleanConversionForNonBooleanLiterals() {
        SchemaContext ctx = newContext();
        Set<ConfigurationError> errors = new LinkedHashSet<>();

        Object result = ValueCoercer.coerce(ctx, schemaProperty("boolean", null), "p", null, "yes", errors);
        assertEquals("yes", result);
    }

    @Test
    void coerceSkipsNumericConversionForNonNumericLiterals() {
        SchemaContext ctx = newContext();
        Set<ConfigurationError> errors = new LinkedHashSet<>();

        Object result = ValueCoercer.coerce(ctx, schemaProperty("integer", null), "p", null, "abc", errors);
        assertEquals("abc", result);
    }

    @Test
    void coerceConvertsIntegerUsingConversionService() {
        SchemaContext ctx = newContext();
        Set<ConfigurationError> errors = new LinkedHashSet<>();

        Object result = ValueCoercer.coerce(ctx, schemaProperty("integer", null), "p", null, "42", errors);
        assertTrue(result instanceof Number);
        assertEquals(42L, ((Number) result).longValue());
    }

    @Test
    void coerceUsesJavaTypeConversionWhenSchemaTypeIsMissing() {
        SchemaContext ctx = newContext();
        Set<ConfigurationError> errors = new LinkedHashSet<>();

        Object result = ValueCoercer.coerce(ctx, schemaProperty(null, "int"), "p", null, "7", errors);
        assertTrue(result instanceof Number);
        assertEquals(7, ((Number) result).intValue());
    }

    @Test
    void coerceDoesNotUseIncompatibleJavaTypeConversion() {
        SchemaContext ctx = newContext();
        Set<ConfigurationError> errors = new LinkedHashSet<>();

        Object result = ValueCoercer.coerce(ctx, schemaProperty("array", "java.time.Duration"), "p", null, "1s", errors);
        assertTrue(result instanceof List);
        assertEquals(List.of("1s"), result);
    }

    @Test
    void coerceFallsBackToCommaSplitForArrays() {
        SchemaContext ctx = newContext();
        Set<ConfigurationError> errors = new LinkedHashSet<>();

        Object result = ValueCoercer.coerce(ctx, schemaProperty("array", null), "p", null, "a, b,,c", errors);
        assertTrue(result instanceof List);
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) result;
        assertTrue(list.contains("a"));
        assertTrue(list.contains("c"));
    }

    @Test
    void splitCommaSeparatedTrimsAndDropsEmptyValues() {
        List<String> result = ValueCoercer.splitCommaSeparated("a, b,,c");
        assertEquals(List.of("a", "b", "c"), result);
    }

    @Test
    void coerceReturnsOriginalValueWhenIntegerHasFractionalPart() {
        SchemaContext ctx = newContext();
        Set<ConfigurationError> errors = new LinkedHashSet<>();

        Object result = ValueCoercer.coerce(ctx, schemaProperty("integer", null), "p", null, "1.5", errors);
        assertEquals("1.5", result);
    }

    @Test
    void coerceIgnoresUnknownJavaType() {
        SchemaContext ctx = newContext();
        Set<ConfigurationError> errors = new LinkedHashSet<>();

        Object result = ValueCoercer.coerce(ctx, schemaProperty(null, "com.example.DoesNotExist"), "p", null, "1", errors);
        assertEquals("1", result);
    }

    private static SchemaContext newContext() {
        Environment environment = createEnvironment(Map.of());
        return new SchemaContext(emptyRootSchema(), ValueCoercerTest.class.getClassLoader(), environment, JsonMapper.createDefault(), true);
    }

    private static ConfigurationSchemaProperty schemaProperty(Object type, String javaType) {
        return new ConfigurationSchemaProperty(
            type,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            javaType,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    private static ConfigurationSchema emptyRootSchema() {
        return new ConfigurationSchema(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    private static Environment createEnvironment(Map<String, Object> properties) {
        ClassLoader classLoader = ValueCoercerTest.class.getClassLoader();
        ApplicationContextConfiguration configuration = new ApplicationContextConfiguration() {
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
        };
        Environment environment = Environment.create(configuration);
        environment.addPropertySource(PropertySource.of("test", properties, PropertySource.Origin.of("test-origin")));
        return environment.start();
    }
}
