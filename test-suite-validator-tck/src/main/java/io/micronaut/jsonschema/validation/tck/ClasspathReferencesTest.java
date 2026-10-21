package io.micronaut.jsonschema.validation.tck;

import io.micronaut.context.annotation.Property;
import io.micronaut.jsonschema.validation.JsonSchemaValidator;
import io.micronaut.jsonschema.validation.ValidationMessage;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * References with the {@code classpath:} scheme, inside and outside the folder of the generated schemas.
 */
// the built-in validator only loads classpath: references from the generated schemas folder and the allowed folders
@Property(name = "micronaut.jsonschema.validation.classpath-folders", value = "tck-schemas")
@MicronautTest(startApplication = false)
class ClasspathReferencesTest {

    @Inject
    JsonSchemaValidator validator;

    @Test
    void referencesOutsideTheGeneratedSchemasFolder() throws IOException {
        String schema = "{\"properties\":{\"code\":{\"$ref\":\"classpath:tck-schemas/code.json\"}}}";
        assertTrue(validator.validate("{\"code\":\"ab\"}", schema).isEmpty());
        assertEquals(List.of("/code: must be at least 2 characters long"), messages(validator.validate("{\"code\":\"a\"}", schema)));
    }

    @Test
    void relativeReferencesResolveAgainstTheClasspathLocationOfTheSchema() throws IOException {
        String schema = "{\"$ref\":\"classpath:META-INF/schemas/tck/sub/parcel.json\"}";
        assertTrue(validator.validate(Map.of("weight", 2, "code", "AB"), schema).isEmpty());
        // weight.json is next to parcel.json, ../code.json is in the parent folder
        assertEquals(List.of("/code: does not match the regex pattern ^[A-Z]+$", "/weight: must have a minimum value of 0"),
            messages(validator.validate(Map.of("weight", -1, "code", "ab"), schema)));
    }

    @Test
    void notANumberIsValidatedLikeItsJsonText() throws IOException {
        // JSON has no NaN: the mappers write it as the string "NaN"
        String schema = "{\"properties\":{\"n\":{\"type\":\"number\",\"minimum\":0,\"maximum\":100}}}";
        assertEquals(1, validator.validate(Map.of("n", Double.NaN), schema).size());
        assertEquals(1, validator.validate(Map.of("n", Double.POSITIVE_INFINITY), schema).size());
        assertTrue(validator.validate(Map.of("n", 50.5), schema).isEmpty());
    }

    private static List<String> messages(Set<? extends ValidationMessage> messages) {
        return messages.stream().map(ValidationMessage::getMessage).sorted().toList();
    }
}
