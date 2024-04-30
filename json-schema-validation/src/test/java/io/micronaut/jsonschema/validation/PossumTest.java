package io.micronaut.jsonschema.validation;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(startApplication = false)
class PossumTest {
    @Inject
    JsonSchemaValidator validator;

    @Test
    void validObjectWithReference() throws IOException {
        var possum = new Possum("Bob",
                List.of(new Possum("Alice", Collections.emptyList(), new Environment("field"))),
                new Environment("marshland")
        );
        var messages = validator.validate(possum, Possum.class);
        assertTrue(messages.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("provideInvalid")
    void invalidObjectWithReferences(Possum posum, String message) throws IOException {
        var assertions = validator.validate(posum, Possum.class);
        assertEquals(1, assertions.size());
        assertEquals(message, assertions.stream().findFirst().get().getMessage());
    }

    private static Stream<Arguments> provideInvalid() {
        return Stream.of(
                Arguments.of(new Possum("", Collections.emptyList(), new Environment("forest")) , "/name: must be at least 1 characters long"),
                Arguments.of(new Possum("Bob", Collections.emptyList(), new Environment("f")) , "/environment/name: must be at least 2 characters long"),
                Arguments.of(new Possum("Bob", List.of(new Possum("Alice", null, new Environment("f"))), null) , "/children/0/environment/name: must be at least 2 characters long"),
                Arguments.of(new Possum("Bob", List.of(new Possum("", null, null)), null) , "/children/0/name: must be at least 1 characters long")
                );
    }
}
