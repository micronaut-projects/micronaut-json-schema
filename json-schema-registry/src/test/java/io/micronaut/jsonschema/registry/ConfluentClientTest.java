package io.micronaut.jsonschema.registry;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
public class ConfluentClientTest {

    @Inject
    ResourceLoader resourceLoader;

    @Inject
    SchemaRegistryClient schemaRegistryClient;

    JsonMapper jsonMapper = new JsonMapper();

    @Test
    void testSchemaRegistryClient() throws IOException {
        String subject = "human";
        String version = "latest";
        String schema = schemaRegistryClient.getWithSubjectAndVersion(subject, version);

        String expected = getExpectedResponse("human.schema.json");
        Map<String, ?> mappedExpected = jsonMapper.readValue(expected, Map.class);
        assertEquals(mappedExpected.get("schema").toString(), schema);
    }

    private String getExpectedResponse(String filename) throws IOException {
        Optional<InputStream> expectedOptional = resourceLoader.getResourceAsStream(filename);
        assertTrue(expectedOptional.isPresent());
        String expected = new String(expectedOptional.get().readAllBytes(), StandardCharsets.UTF_8);
        return expected.replaceAll("\\s+", "").trim();
    }
}
