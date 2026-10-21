package io.micronaut.jsonschema.validation.tck;

import io.micronaut.json.tree.JsonNode;
import io.micronaut.jsonschema.validation.JsonSchemaValidator;
import io.micronaut.jsonschema.validation.Llama;
import io.micronaut.jsonschema.validation.ValidationMessage;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The kinds of values and schemas accepted by {@link JsonSchemaValidator} and the details of the messages.
 */
@MicronautTest(startApplication = false)
class ValidationInputsTest {

    @Inject
    JsonSchemaValidator validator;

    @Test
    void validatesMicronautJsonTrees() throws IOException {
        JsonNode valid = JsonNode.createObjectNode(Map.of("name", JsonNode.createStringNode("John"), "age", JsonNode.createNumberNode(3)));
        assertTrue(validator.validate(valid, Llama.class).isEmpty());
        JsonNode invalid = JsonNode.createObjectNode(Map.of("name", JsonNode.createStringNode(""), "age", JsonNode.createNumberNode(3)));
        Set<? extends ValidationMessage> messages = validator.validate(invalid, Llama.class);
        assertEquals(1, messages.size());
        ValidationMessage message = messages.iterator().next();
        assertEquals("/name: must be at least 1 characters long", message.getMessage());
        assertEquals("/name", message.getInstanceLocation());
        assertEquals("minLength", message.getKeyword());
        assertTrue(message.getSchemaLocation() != null && message.getSchemaLocation().endsWith("#/properties/name/minLength"),
            message::getSchemaLocation);
        assertEquals("ValidationMessageAdapter{message=/name: must be at least 1 characters long}", message.toString());
    }

    @Test
    void mapSchemasResolveRelativeReferencesFromTheClasspath() throws IOException {
        Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", Map.of("environment", Map.of("$ref", "environment.schema.json"))
        );
        assertTrue(validator.validate(Map.of("environment", Map.of("name", "forest")), schema).isEmpty());
        List<String> messages = validator.validate(Map.of("environment", Map.of("name", "f")), schema).stream()
            .map(ValidationMessage::getMessage)
            .toList();
        assertEquals(List.of("/environment/name: must be at least 2 characters long"), messages);
    }

    @Test
    void stringValuesAreParsedAsJson() throws IOException {
        String schema = "{\"type\":\"object\",\"properties\":{\"n\":{\"type\":\"integer\"}}}";
        assertTrue(validator.validate("{\"n\":1}", schema).isEmpty());
        assertEquals(1, validator.validate("{\"n\":\"one\"}", schema).size());
        assertThrows(Exception.class, () -> validator.validate("{not json", schema));
    }

    @Test
    void messagesUseJsonPointers() throws IOException {
        String schema = """
            {"type":"object","required":["id"],"properties":{"items":{"type":"array","items":{"properties":{"a/b~c":{"type":"integer","minimum":1}}}}}}
            """;
        List<String> messages = validator.validate("{\"items\":[{\"a/b~c\":1},{\"a/b~c\":0}]}", schema).stream()
            .map(ValidationMessage::getMessage)
            .sorted()
            .toList();
        assertEquals(List.of("/items/1/a~1b~0c: must have a minimum value of 1", ": required property 'id' not found"), messages);
    }

    @Test
    void numericallyEqualItemsAreNotUnique() throws IOException {
        String schema = "{\"uniqueItems\":true}";
        assertEquals(1, validator.validate("[1000000000000000000,1000000000000000000.0]", schema).size());
        assertEquals(1, validator.validate("[-1000000000000000000,-1000000000000000000.0]", schema).size());
        assertEquals(1, validator.validate("[[1000000000000000000],[1000000000000000000.0]]", schema).size());
        assertEquals(1, validator.validate("[{\"a\":1000000000000000000},{\"a\":1000000000000000000.0}]", schema).size());
        assertEquals(1, validator.validate("[9223372036854775807,9223372036854775807]", schema).size());
        assertTrue(validator.validate("[1000000000000000000,1000000000000000001]", schema).isEmpty());
    }

    @Test
    void unknownTypesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(Map.of(), ValidationInputsTest.class));
    }
}
