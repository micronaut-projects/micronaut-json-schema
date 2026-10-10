package io.micronaut.jsonschema.validation;

import io.micronaut.jsonschema.utils.JsonSchemaClassPathResourceLoader;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(startApplication = false)
class OrderTest {
    @Inject
    JsonSchemaValidator validator;

    @Inject
    JsonSchemaClassPathResourceLoader resourceLoader;

    @Test
    void validOrder() throws IOException {
        assertTrue(validator.validate(new Order(List.of(new Order.Line("tea", 2))), Order.class).isEmpty());
    }

    @Test
    void invalidLineOfAnOrderIsValidatedWithTheReferencedSchema() throws IOException {
        // order.schema.json references order-line.schema.json with a classpath:META-INF/schemas/ $ref, which the
        // built-in validator resolves from the classpath
        var messages = validator.validate(new Order(List.of(new Order.Line("tea", 0))), Order.class);
        assertEquals(1, messages.size());
        assertEquals("/lines/0/quantity: must have an exclusive minimum value of 0", messages.iterator().next().getMessage());
    }

    @Test
    void nestedTypeIsValidatedByClass() throws IOException {
        assertTrue(validator.validate(new Order.Line("tea", 2), Order.Line.class).isEmpty());
        assertEquals(1, validator.validate("""
            {"product":"","quantity":2}""", Order.Line.class).size());
    }

    @Test
    void referencesResolveFromTheClasspathWithoutConfiguration() {
        // the reference is a classpath: URI that a validator which knows nothing of Micronaut, like NetworkNT in the
        // json-validator component of Apache Camel, resolves as is (NetworkNT is no longer on this classpath, #430)
        String orderSchema = resourceLoader.jsonSchemaStringForClass(Order.class).orElseThrow();
        assertTrue(orderSchema.contains("\"$ref\":\"classpath:META-INF/schemas/order-line.schema.json\""), orderSchema);
        assertNotNull(OrderTest.class.getClassLoader().getResource("META-INF/schemas/order-line.schema.json"));
    }
}
