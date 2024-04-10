package io.micronaut.jsonschema.validation;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.json.JsonMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(startApplication = false)
class LlamaTest {
    @Inject
    JsonSchemaValidator validator;

    @Test
    void validRecord() throws IOException {
        var assertions = validator.validate(new Llama("John", 12), Llama.class);
        assertEquals(0, assertions.size());
    }

    @ParameterizedTest
    @MethodSource("provideInvalid")
    void invalidRecord(Llama llama, String message) throws IOException {
        var assertions = validator.validate(llama, Llama.class);
        assertEquals(1, assertions.size());
        assertEquals(message, assertions.stream().findFirst().get().getMessage());
    }

    @Test
    void llamaSchema(ResourceLoader resourceLoader, JsonMapper jsonMapper) throws IOException {
        Optional<InputStream> expectedOptional = resourceLoader.getResourceAsStream("expected-llama.schema.json");
        assertTrue(expectedOptional.isPresent());
        String expected = new String(expectedOptional.get().readAllBytes(), StandardCharsets.UTF_8);
        Optional<InputStream> resultOptional = resourceLoader.getResourceAsStream("META-INF/schemas/llama.schema.json");
        assertTrue(resultOptional.isPresent());
        String result = new String(resultOptional.get().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(jsonMapper.readValue(expected, Map.class), jsonMapper.readValue(result, Map.class));
    }

    private static Stream<Arguments> provideInvalid() {
        return Stream.of(
            Arguments.of(new Llama("", 12), "/name: must be at least 1 characters long"),
            Arguments.of(new Llama("John", -12), "/age: must have a minimum value of 0")
        );
    }
}