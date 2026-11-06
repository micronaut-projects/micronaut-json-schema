package io.micronaut.jsonschema.validation.compat;

import io.micronaut.jsonschema.validation.JsonSchemaValidator;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * micronaut-json-schema-validation keeps validating with NetworkNT, as before 2.3.2.
 */
@MicronautTest(startApplication = false)
class DefaultValidatorTest {

    @Inject
    JsonSchemaValidator validator;

    @Test
    void validatesWithNetworkNt() {
        assertEquals("io.micronaut.jsonschema.validation.networknt.NetworkNtJsonSchemaValidator", validator.getClass().getName());
    }
}
