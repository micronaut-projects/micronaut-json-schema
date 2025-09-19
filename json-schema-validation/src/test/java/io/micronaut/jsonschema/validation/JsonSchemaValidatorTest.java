package io.micronaut.jsonschema.validation;

import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(startApplication = false)
class JsonSchemaValidatorTest {

    @Inject
    JsonSchemaValidator validator;

    // New: Inject ObjectMapper to parse schemaAsString into a Map
    @Inject
    JsonMapper objectMapper;

    private final String schemaAsString = """
        {"$schema":"https://json-schema.org/draft/2020-12/schema","$id":"http://localhost:8080/schemas/bird.schema.json","title":"Bird","description":"A bird.","type":"object","oneOf":[{"title":"Ostrich","description":"An ostrich.","type":"object","properties":{"@type":{"type":"string","const":"ostrich-bird"},"name":{"description":"The name","type":"string"},"runSpeed":{"description":"The run speed","type":"number","exclusiveMinimum":0}},"required":["@type"]},{"title":"Eagle","description":"The eagle.","type":"object","properties":{"@type":{"type":"string","const":"eagle-bird"},"flySpeed":{"description":"The fly speed","type":"number","minimum":1},"name":{"description":"The name","type":"string"}},"required":["@type"]}]}
        """;
    private Map<String, Object> schemaAsMap;

    @BeforeEach
    void setUpSchemaAsMap() throws IOException {
        schemaAsMap = objectMapper.readValue(schemaAsString, Argument.mapOf(String.class, Object.class));
    }

    @ParameterizedTest
    @MethodSource("provideValidBirds")
    void validateWithSchemaAsString(Bird bird) throws IOException {
        var assertions = validator.validate(bird, schemaAsString);
        assertEquals(0, assertions.size());
    }

    @ParameterizedTest
    @MethodSource("provideValidBirds")
    void validateWithSchemaAsMap(Bird bird) throws IOException {
        var assertions = validator.validate(bird, schemaAsMap);
        assertEquals(0, assertions.size());
    }

    private static Stream<Arguments> provideValidBirds() {
        return Stream.of(
            Arguments.of(new Ostrich("Bob", 10.5f)),
            Arguments.of(new Eagle("Blob", 31.2f))
        );
    }

}
