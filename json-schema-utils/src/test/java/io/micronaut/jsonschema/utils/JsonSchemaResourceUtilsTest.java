package io.micronaut.jsonschema.utils;

import io.micronaut.core.io.scan.ClassPathResourceLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonSchemaResourceUtilsTest {

    @Test
    void generatedSchemasFolderUsesOutputLocation() {
        JsonSchemaConfiguration config = new JsonSchemaConfiguration() {
            @Override
            public String getOutputLocation() {
                return "custom-schemas";
            }
        };
        assertEquals("META-INF/custom-schemas/", JsonSchemaResourceUtils.generatedSchemasFolder(config));
    }

    @Test
    void configurationSchemasFolderIsConstant() {
        assertEquals("META-INF/micronaut-configuration-schemas/", JsonSchemaResourceUtils.configurationSchemasFolder());
    }

    @Test
    void resolvePathWithinFolderNormalizesAndRejectsTraversal() {
        String folder = "META-INF/schemas/";
        assertEquals("META-INF/schemas/a.json", JsonSchemaResourceUtils.resolvePathWithinFolder(folder, "a.json", "uri", folder));
        assertThrows(IllegalArgumentException.class, () -> JsonSchemaResourceUtils.resolvePathWithinFolder(folder, "../secret.json", "uri", folder));
    }

    @Test
    void resolveSchemasReturnsEmptyForMissingFolder() {
        ClassPathResourceLoader loader = ClassPathResourceLoader.defaultLoader(JsonSchemaResourceUtilsTest.class.getClassLoader());
        assertTrue(JsonSchemaResourceUtils.resolveSchemas(loader, loader.getClassLoader(), "META-INF/does-not-exist/").isEmpty());
    }
}
