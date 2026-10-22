package io.micronaut.jsonschema.validation;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import io.micronaut.jsonschema.utils.JsonSchemaClassPathResourceLoader;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        // a validator that knows nothing of Micronaut, like the one of Camel's json-validator component
        String orderSchema = resourceLoader.jsonSchemaStringForClass(Order.class).orElseThrow();
        assertTrue(orderSchema.contains("classpath:META-INF/schemas/order-line.schema.json"), orderSchema);
        Schema schema = SchemaRegistry.withDialect(Dialects.getDraft202012()).getSchema(orderSchema, InputFormat.JSON);

        assertTrue(schema.validate("""
            {"lines":[{"product":"tea","quantity":2}]}""", InputFormat.JSON).isEmpty());
        assertEquals(1, schema.validate("""
            {"lines":[{"product":"tea","quantity":0}]}""", InputFormat.JSON).size());
    }
}
