package io.micronaut.jsonschema.utils;

import io.micronaut.core.io.Readable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(startApplication = false)
class JsonSchemaClassPathResourceLoaderTest {

    @Test
    void itIsPossibleToGetTheJsonSchema(JsonSchemaClassPathResourceLoader resourceLoader) {
        assertTrue(resourceLoader.jsonSchemaStringForClass(Product.class).isPresent());
        assertFalse(resourceLoader.jsonSchemaStringForClass(Test.class).isPresent());
    }

    @Test
    void itIsPossibleToLoadAllJsonSchemas(JsonSchemaClassPathResourceLoader resourceLoader) throws IOException {
        var schemas = resourceLoader.jsonSchemas();
        assertFalse(schemas.isEmpty());

        assertTrue(schemas.containsKey("product.schema.json"));
        Readable productSchema = schemas.get("product.schema.json");
        assertNotNull(productSchema);
        assertTrue(productSchema.exists());

        try (InputStream inputStream = productSchema.asInputStream()) {
            String schema = new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(schema.contains("\"$schema\""));
        }

        // Validate at least one known Micronaut configuration schema is present
        assertTrue(schemas.containsKey("io.micronaut.http.ssl.ServerSslConfiguration.json"));

        for (var entry : schemas.entrySet()) {
            Readable readable = entry.getValue();
            assertNotNull(readable, entry.getKey());
            assertTrue(readable.exists(), entry.getKey());
            try (InputStream inputStream = readable.asInputStream()) {
                assertNotEquals(-1, inputStream.read(), entry.getKey());
            }
        }
    }

}
