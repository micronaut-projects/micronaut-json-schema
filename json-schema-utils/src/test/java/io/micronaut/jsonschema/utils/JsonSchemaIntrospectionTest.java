package io.micronaut.jsonschema.utils;

import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.jsonschema.JsonSchemaMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonSchemaIntrospectionTest {

    @Test
    void beanIntrospectorFindsPlainJsonSchemaType() {
        assertTrue(BeanIntrospector.SHARED.findIntrospections(io.micronaut.jsonschema.JsonSchema.class).stream()
            .anyMatch(introspection -> PlainProduct.class.equals(introspection.getBeanType())));
    }

    @Test
    void runtimeSchemaMapperLoadsSchemaForPlainJsonSchemaType() {
        assertTrue(JsonSchemaMapper.generateSchemaFor(PlainProduct.class).isPresent());
    }
}
