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

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest(startApplication = false)
class SalamanderTest {
    @Inject
    JsonSchemaValidator validator;

    @Test
    void validObject() throws IOException {
        var assertions = validator.validate(
                new Salamander().setColors(List.of("green", "red"))
                        .setEnvironments(List.of("pond", "river"))
                        .setSkinColor("green")
                .setSpecies("Pond Salamander")
                .setAge(1)
                .setNegative(-12)
                .setInteger(15L)
                .setNumber(20.25),
                Salamander.class
        );
        assertEquals(0, assertions.size());
    }

    @ParameterizedTest
    @MethodSource("provideInvalid")
    void invalidRecord(Salamander salamander, String message) throws IOException {
        var assertions = validator.validate(salamander, Salamander.class);
        assertEquals(1, assertions.size());
        assertEquals(message, assertions.stream().findFirst().get().getMessage());
    }

    private static Stream<Arguments> provideInvalid() {
        return Stream.of(
        Arguments.of(new Salamander().setColors(Collections.emptyList()), "/colors: must have at least 1 items but found 0"),
        Arguments.of(new Salamander().setEnvironments(Collections.singletonList("pond")), "/environments: must have at least 2 items but found 1"),
        Arguments.of(new Salamander().setSkinColor(""), "/skinColor: must be at least 1 characters long"),
        Arguments.of(new Salamander().setSpecies("a-very-long-species-name"), "/species: must be at most 20 characters long"),
        Arguments.of(new Salamander().setSpecies("invalidChar$"), "/species: does not match the regex pattern ^[a-zA-Z \\-]+$"),
        Arguments.of(new Salamander().setAge(-12), "/age: must have a minimum value of 0"),
        Arguments.of(new Salamander().setNegative(12), "/negative: must have an exclusive maximum value of 0"),
        Arguments.of(new Salamander().setInteger(1L), "/integer: must have a minimum value of 10"),
        Arguments.of(new Salamander().setInteger(120L), "/integer: must have a maximum value of 100"),
        Arguments.of(new Salamander().setNumber(10d), "/number: must have an exclusive minimum value of 10"),
        Arguments.of(new Salamander().setNumber(100.6), "/number: must have a maximum value of 100.5"));
    }

}