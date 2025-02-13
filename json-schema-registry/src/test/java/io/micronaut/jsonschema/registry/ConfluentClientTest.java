package io.micronaut.jsonschema.registry;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.jsonschema.registry.basicauth.SchemaRegistryBasicAuthClient;
import io.micronaut.jsonschema.registry.types.ConfigKeys;
import io.micronaut.jsonschema.registry.types.Responses;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the ConfluentClient for Schema Registry.
 * Checking the response types from the client.
 */
@MicronautTest
@Property(name = ConfigKeys.ORIGIN, value = "http://144.24.55.159:8081/")
@Property(name = ConfigKeys.USERNAME, value = "micronaut")
@Property(name = ConfigKeys.PASSWORD, value = "test")
public class ConfluentClientTest {

    @Inject
    ResourceLoader resourceLoader;
    @Inject
    SchemaRegistryBasicAuthClient client;

    JsonMapper jsonMapper = new JsonMapper();

    @Test
    void testGetWithSchemas() {
        var response = client.getSchemaWithId(1);
        assertEquals(new Responses.SchemaJson("\"string\""), response);

        var response2 = client.getSchemaStringWithId(1);
        assertEquals("\"string\"", response2);

        var response3 = client.getSchemaVersionsWithId(1);
        assertEquals(List.of(new Responses.SubjectVersion("my_subject", 1)), response3);

        var response4 = client.getSchemaTypes();
        assertEquals(List.of(Responses.SchemaType.JSON, Responses.SchemaType.PROTOBUF, Responses.SchemaType.AVRO), response4);
    }

    @Test
    void testConfigGetters() {
        var response1 = client.getConfig();
        assertEquals(new Responses.Config(null, false, Responses.CompatibilityLevel.BACKWARD,
            null, null, null, null, null), response1);
    }

    @Test
    void testModeGetters() {
        var response1 = client.getMode();
        assertEquals(new Responses.Mode(Responses.Mode.ModeType.READWRITE), response1);
        assertNull(client.getModeForSubject("human"));
    }

    @Test
    void testGetSubject() throws IOException {
        var response1 = client.getSubjects();
        assertEquals(List.of("human", "my_subject"), response1);

        List<Integer> response2 = client.getSubjectVersions("human");
        assertEquals(List.of(1), response2);

        // prepare expected response
        String expected = getExpectedResponse("human.schema.json");
        Responses.Subject subjectExpected = jsonMapper.readValue(expected, Responses.Subject.class);

        Responses.Subject response3 = client.getSubjectWithVersion("human", "latest");
        assertEquals(subjectExpected, response3);

        String response4 = client.getSchemaWithSubjectAndVersion("human", "latest");
        assertEquals(subjectExpected.schema(), response4);

        var response5 = client.getSubjectVersionReferencedBy("human", "latest");
        assertEquals(List.of(), response5);

        Responses.Subject response6 = client.getSubjectMetadata("human");
        assertNull(response6);
    }

    private String getExpectedResponse(String filename) throws IOException {
        Optional<InputStream> expectedOptional = resourceLoader.getResourceAsStream(filename);
        assertTrue(expectedOptional.isPresent());
        String expected = new String(expectedOptional.get().readAllBytes(), StandardCharsets.UTF_8);
        return expected.replaceAll("\\s+", "").trim();
    }
}
