package io.micronaut.jsonschema.validation;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The folders allowed with {@code micronaut.jsonschema.validation.classpath-folders}.
 */
@Property(name = "micronaut.jsonschema.validation.classpath-folders", value = "tck-schemas")
@MicronautTest(startApplication = false)
class ClasspathFoldersTest {

    @Inject
    JsonSchemaValidator validator;

    @Test
    void allowedFoldersAreLoaded() throws IOException {
        assertTrue(validator.validate("\"ab\"", "{\"$ref\":\"classpath:tck-schemas/code.json\"}").isEmpty());
    }

    @Test
    void parentSegmentsDoNotEscapeAnAllowedFolder() {
        assertThrows(IllegalArgumentException.class,
            () -> validator.validate("\"ab\"", "{\"$ref\":\"classpath:tck-schemas/../not-allowed/code.json\"}"));
    }

    @Test
    void parentSegmentsDoNotEscapeTheGeneratedSchemasFolder() {
        assertThrows(IllegalArgumentException.class,
            () -> validator.validate("\"ab\"", "{\"$ref\":\"classpath:META-INF/schemas/../../not-allowed/code.json\"}"));
    }

    @Test
    void parentSegmentsOfUnparsableReferencesDoNotEscapeAnAllowedFolder() {
        // ^ is not valid in a URI, so the reference cannot be normalized before it is loaded
        String schema = "{\"$id\":\"classpath:tck-schemas/schema.json\",\"$ref\":\"../not-allowed/code.json#^\"}";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> validator.validate("\"ab\"", schema));
        assertTrue(e.getMessage().contains("classpath-folders"), e.getMessage());
    }
}
