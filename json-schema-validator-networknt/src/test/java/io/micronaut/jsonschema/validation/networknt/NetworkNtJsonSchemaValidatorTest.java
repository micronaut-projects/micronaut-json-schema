package io.micronaut.jsonschema.validation.networknt;

import io.micronaut.jsonschema.validation.JsonSchemaValidator;
import io.micronaut.jsonschema.validation.ValidationMessage;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@MicronautTest(startApplication = false)
class NetworkNtJsonSchemaValidatorTest {

    @Inject
    JsonSchemaValidator validator;

    @Test
    void networkNtReplacesTheBuiltInValidator() {
        assertInstanceOf(NetworkNtJsonSchemaValidator.class, validator);
    }

    @Test
    void messagesGiveAccessToTheNetworkNtError() throws IOException {
        Set<? extends ValidationMessage> messages = validator.validate("{\"n\":\"one\"}", "{\"properties\":{\"n\":{\"type\":\"integer\"}}}");
        NetworkNtValidationMessage message = assertInstanceOf(NetworkNtValidationMessage.class, messages.iterator().next());
        assertEquals("type", message.getError().getKeyword());
        assertEquals(message.getError().toString(), message.getMessage());
        assertEquals("/n", message.getInstanceLocation());
    }
}
