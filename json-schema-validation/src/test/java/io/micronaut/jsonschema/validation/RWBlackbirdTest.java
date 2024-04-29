package io.micronaut.jsonschema.validation;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.json.JsonMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(startApplication = false)
class RWBlackbirdTest {
    @Inject
    JsonSchemaValidator validator;

    @Test
    void validObjectWithChangedPath() throws IOException {
        var bird = new RWBlackbird("Clara", 1.2d);
        var assertions = validator.validate(bird, RWBlackbird.class);
        assertEquals(0, assertions.size());
    }
    @Test
    void llamaSchema(ResourceLoader resourceLoader, JsonMapper jsonMapper) throws IOException {
        Optional<InputStream> expectedOptional = resourceLoader.getResourceAsStream("expected-rwblackbird.json");
        assertTrue(expectedOptional.isPresent());
        String expected = new String(expectedOptional.get().readAllBytes(), StandardCharsets.UTF_8);
        Optional<InputStream> resultOptional = resourceLoader.getResourceAsStream("META-INF/schemas/red-winged-blackbird.schema.json");
        assertTrue(resultOptional.isPresent());
        String result = new String(resultOptional.get().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(jsonMapper.readValue(expected, Map.class), jsonMapper.readValue(result, Map.class));
    }
}
