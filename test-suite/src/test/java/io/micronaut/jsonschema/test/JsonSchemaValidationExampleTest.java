package io.micronaut.jsonschema.test;

import io.micronaut.jsonschema.validation.JsonSchemaValidator;
import io.micronaut.jsonschema.validation.ValidationMessage;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(startApplication = false)
class JsonSchemaValidationExampleTest {

    @Test
    void validateAgainstGeneratedSchema(JsonSchemaValidator validator) throws IOException {
        // tag::validate-type[]
        Set<? extends ValidationMessage> messages = validator.validate(new Llama("", -1), Llama.class); // <1>
        for (ValidationMessage message : messages) {
            System.out.println(message.getMessage()); // <2>
            System.out.println(message.getInstanceLocation() + " " + message.getKeyword()); // <3>
        }
        // end::validate-type[]
        assertEquals(Set.of("/name: must be at least 1 characters long", "/age: must have a minimum value of 0"),
            messages.stream().map(ValidationMessage::getMessage).collect(Collectors.toSet()));
    }

    @Test
    void validateAgainstSchema(JsonSchemaValidator validator) throws IOException {
        // tag::validate-schema[]
        Map<String, Object> schema = Map.of(
            "type", "object",
            "required", List.of("city"),
            "properties", Map.of("zip", Map.of("type", "string", "pattern", "^[0-9]{5}$"))
        );
        Set<? extends ValidationMessage> messages = validator.validate(Map.of("zip", "1234"), schema); // <1>
        // end::validate-schema[]
        assertEquals(Set.of(": required property 'city' not found", "/zip: does not match the regex pattern ^[0-9]{5}$"),
            messages.stream().map(ValidationMessage::getMessage).collect(Collectors.toSet()));
        assertTrue(validator.validate("{\"city\":\"Paris\",\"zip\":\"75001\"}", schema).isEmpty());
    }

    @Test
    void relativeReferencesResolveAgainstTheBaseUri(JsonSchemaValidator validator) throws IOException {
        // the base URI of this test suite is configured without a trailing slash
        Map<String, Object> schema = Map.of("$ref", "llama.schema.json");
        assertTrue(validator.validate(new Llama("John", 3), schema).isEmpty());
        assertEquals(Set.of("/name: must be at least 1 characters long"),
            validator.validate(new Llama("", 3), schema).stream().map(ValidationMessage::getMessage).collect(Collectors.toSet()));
    }
}
