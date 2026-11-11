package io.micronaut.jsonschema.validation.networknt;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import io.micronaut.jsonschema.utils.JsonSchemaClassPathResourceLoader;
import io.micronaut.jsonschema.validation.Order;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated schemas can be used by a validator that knows nothing of Micronaut, like the one of Camel's
 * json-validator component: their references are classpath: URIs.
 */
@MicronautTest(startApplication = false)
class ForeignValidatorTest {

    @Inject
    JsonSchemaClassPathResourceLoader resourceLoader;

    @Test
    void referencesResolveFromTheClasspathWithoutConfiguration() {
        String orderSchema = resourceLoader.jsonSchemaStringForClass(Order.class).orElseThrow();
        assertTrue(orderSchema.contains("classpath:META-INF/schemas/order-line.schema.json"), orderSchema);
        Schema schema = SchemaRegistry.withDialect(Dialects.getDraft202012()).getSchema(orderSchema, InputFormat.JSON);

        assertTrue(schema.validate("""
            {"lines":[{"product":"tea","quantity":2}]}""", InputFormat.JSON).isEmpty());
        assertEquals(1, schema.validate("""
            {"lines":[{"product":"tea","quantity":0}]}""", InputFormat.JSON).size());
    }
}
