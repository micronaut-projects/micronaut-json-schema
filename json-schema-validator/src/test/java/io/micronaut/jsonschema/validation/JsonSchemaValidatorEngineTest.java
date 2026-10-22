package io.micronaut.jsonschema.validation;

import io.micronaut.json.tree.JsonNode;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour specific to the built-in validator; the behaviour shared with other implementations is in the TCK.
 */
@MicronautTest(startApplication = false)
class JsonSchemaValidatorEngineTest {

    @Inject
    JsonSchemaValidator validator;

    @Test
    void networkNtIsNotOnTheClasspath() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.networknt.schema.SchemaRegistry"));
    }

    @Test
    void theBuiltInValidatorIsUsed() {
        assertEquals("io.micronaut.jsonschema.validation.DefaultJsonSchemaValidator", validator.getClass().getName());
    }

    @Test
    void messagesExposeTheAbsoluteSchemaLocation() throws IOException {
        JsonNode invalid = JsonNode.createObjectNode(Map.of("name", JsonNode.createStringNode(""), "age", JsonNode.createNumberNode(3)));
        ValidationMessage message = validator.validate(invalid, Llama.class).iterator().next();
        // the default base URI of the generated schemas is classpath:META-INF/schemas (#429)
        assertEquals("classpath:META-INF/schemas/llama.schema.json#/properties/name/minLength", message.getSchemaLocation());
    }

    @Test
    void classpathReferencesOutsideTheSchemasFolderMustBeAllowed() {
        // tck-schemas/code.json is on the classpath, but its folder is not allowed by micronaut.jsonschema.validation.classpath-folders
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> validator.validate("\"ab\"", "{\"$ref\":\"classpath:tck-schemas/code.json\"}"));
        assertTrue(e.getMessage().contains("micronaut.jsonschema.validation.classpath-folders"), e.getMessage());
    }

    @Test
    void invalidJsonIsReportedAsAnIoException() {
        assertThrows(IOException.class, () -> validator.validate("{not json", "{\"type\":\"object\"}"));
    }
}
