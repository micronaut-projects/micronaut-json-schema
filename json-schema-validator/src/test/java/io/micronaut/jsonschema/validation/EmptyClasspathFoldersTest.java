package io.micronaut.jsonschema.validation;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An empty entry of {@code micronaut.jsonschema.validation.classpath-folders} does not allow the whole classpath.
 */
@Property(name = "micronaut.jsonschema.validation.classpath-folders", value = "/")
@MicronautTest(startApplication = false)
class EmptyClasspathFoldersTest {

    @Inject
    JsonSchemaValidator validator;

    @Test
    void emptyEntriesAreIgnored() {
        assertThrows(IllegalArgumentException.class,
            () -> validator.validate("\"ab\"", "{\"$ref\":\"classpath:tck-schemas/code.json\"}"));
    }
}
