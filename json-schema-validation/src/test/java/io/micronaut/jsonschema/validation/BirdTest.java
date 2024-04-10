package io.micronaut.jsonschema.validation;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(startApplication = false)
class BirdTest {
    @Inject
    JsonSchemaValidator validator;

    @ParameterizedTest
    @MethodSource("provideValid")
    void validObjectWithInheritance(Bird bird) throws IOException {
        var assertions = validator.validate(bird, Bird.class);
        assertEquals(0, assertions.size());
    }

    @ParameterizedTest
    @MethodSource("provideInvalid")
    void invalidRecord(Bird bird, String message1, String message2) throws IOException {
        var assertions = validator.validate(bird, Bird.class);
        assertEquals(3, assertions.size());
        assertTrue(assertions.stream().toList().stream().map(ValidationMessage::getMessage).anyMatch(m -> m.equals(": must be valid to one and only one schema, but 0 are valid")));
        assertTrue(assertions.stream().toList().stream().map(ValidationMessage::getMessage).anyMatch(m -> m.equals(message1)));
        assertTrue(assertions.stream().toList().stream().map(ValidationMessage::getMessage).anyMatch(m -> m.equals(message2)));
    }

    @ParameterizedTest
    @MethodSource("provideInvalidString")
    void invalidString(String bird, String message1, String message2) throws IOException {
        var assertions = validator.validate(bird, Bird.class);
        assertEquals(3, assertions.size());
        assertTrue(assertions.stream().toList().stream().map(ValidationMessage::getMessage).anyMatch(m -> m.equals(": must be valid to one and only one schema, but 0 are valid")));
        assertTrue(assertions.stream().toList().stream().map(ValidationMessage::getMessage).anyMatch(m -> m.equals(message1)));
        assertTrue(assertions.stream().toList().stream().map(ValidationMessage::getMessage).anyMatch(m -> m.equals(message2)));
    }

    private static Stream<Arguments> provideValid() {
        return Stream.of(
                Arguments.of(new Ostrich("Bob", 10.5f)),
                Arguments.of(new Eagle("Blob", 31.2f))
        );
    }

    private static Stream<Arguments> provideInvalid() {
        return Stream.of(
                Arguments.of(new Ostrich("Glob", -12), "/runSpeed: must have an exclusive minimum value of 0", "/@type: must be the constant value 'eagle-bird'"),
                Arguments.of(new Eagle("Blob", 0.5f), "/@type: must be the constant value 'ostrich-bird'", "/flySpeed: must have a minimum value of 1")
        );
    }

    private static Stream<Arguments> provideInvalidString() {
        return Stream.of(
                Arguments.of("{\"@type\":\"unknown-bird\"}","/@type: must be the constant value 'ostrich-bird'", "/@type: must be the constant value 'eagle-bird'")
        );
    }
}
