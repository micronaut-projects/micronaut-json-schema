package io.micronaut.jsonschema.registry;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
public class ConfluentClientTest {

    @Inject
    ResourceLoader resourceLoader;

    JsonMapper jsonMapper = new JsonMapper();

    @Test
    void testSchemaRegistryClient() throws IOException {
        Map<String, Object> items = new HashMap<>();
        items.put("registry.url", "http://144.24.55.159:8081");

        ApplicationContext ctx = ApplicationContext.run(items);
        SchemaRegistryClient schemaRegistryClient = ctx.getBean(SchemaRegistryClient.class);

        String subject = "human";
        String version = "latest";
        String schema = schemaRegistryClient.getWithSubjectAndVersion(subject, version);

        String expected = getExpectedResponse("human.schema.json");
        Map<String, ?> mappedExpected = jsonMapper.readValue(expected, Map.class);
        assertEquals(mappedExpected.get("schema").toString(), schema);
        ctx.close();
    }

    private String getExpectedResponse(String filename) throws IOException {
        Optional<InputStream> expectedOptional = resourceLoader.getResourceAsStream(filename);
        assertTrue(expectedOptional.isPresent());
        String expected = new String(expectedOptional.get().readAllBytes(), StandardCharsets.UTF_8);
        return expected.replaceAll("\\s+", "").trim();
    }
}
