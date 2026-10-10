package io.micronaut.jsonschema.validation;

import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
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

@MicronautTest(startApplication = false)
class JsonSchemaValidatorEngineTest {

    @Inject
    JsonSchemaValidator validator;

    @Inject
    JsonMapper jsonMapper;

    @Test
    void networkNtIsNotOnTheClasspath() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.networknt.schema.SchemaRegistry"));
    }

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
        // the default base URI of the generated schemas is classpath:META-INF/schemas (#429)
        assertEquals("classpath:META-INF/schemas/llama.schema.json#/properties/name/minLength", message.getSchemaLocation());
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
        assertThrows(IOException.class, () -> validator.validate("{not json", schema));
    }

    @Test
    void unknownTypesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(Map.of(), JsonSchemaValidatorEngineTest.class));
    }
}
