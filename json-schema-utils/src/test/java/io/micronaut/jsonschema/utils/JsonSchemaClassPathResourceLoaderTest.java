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
    void theSchemaOfANestedTypeIsNamedAfterTheOuterType(JsonSchemaClassPathResourceLoader resourceLoader) {
        String schema = resourceLoader.jsonSchemaStringForClass(Catalog.Item.class).orElseThrow();
        assertTrue(schema.contains("catalog-item.schema.json"), schema);
        assertTrue(schema.contains("Catalog.Item"), schema);
    }

    @Test
    void theSchemaOfATitledTypeIsNamedAfterTheTitle(JsonSchemaClassPathResourceLoader resourceLoader) {
        String schema = resourceLoader.jsonSchemaStringForClass(Widget.class).orElseThrow();
        assertTrue(schema.contains("fancy-widget.schema.json"), schema);
        assertTrue(schema.contains("FancyWidget"), schema);
    }

    @Test
    void theSchemaOfANestedTitledTypeWithoutIntrospectionIsFound(JsonSchemaClassPathResourceLoader resourceLoader) {
        String schema = resourceLoader.jsonSchemaStringForClass(Catalog.Bin.class).orElseThrow();
        assertTrue(schema.contains("storage-bin.schema.json"), schema);
    }

    @Test
    void theSchemaOfATypeWithARelativeUriIsFoundInItsFolder(JsonSchemaClassPathResourceLoader resourceLoader) {
        String schema = resourceLoader.jsonSchemaStringForClass(Gadget.class).orElseThrow();
        assertTrue(schema.contains("shop/gadget.schema.json"), schema);
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
