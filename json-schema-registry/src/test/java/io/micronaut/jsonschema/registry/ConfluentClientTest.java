package io.micronaut.jsonschema.registry;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.jsonschema.registry.types.Responses;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
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
    void testGetSubjectWithVersion() throws IOException {
        Map<String, Object> items = new HashMap<>();
        items.put("registry.url", "http://144.24.55.159:8081");
        items.put("registry.username", "micronaut");
        items.put("registry.password", "test");

        ApplicationContext ctx = ApplicationContext.run(items);
        SchemaRegistryClient client = ctx.getBean(SchemaRegistryClient.class);
        String subject = "human";
        String version = "latest";
        Responses.Subject response = client.getSubjectWithVersion(subject, version);

        String expected = getExpectedResponse("human.schema.json");
        Responses.Subject mappedExpected = jsonMapper.readValue(expected, Responses.Subject.class);
        assertEquals(mappedExpected, response);
        ctx.close();
    }

    @Test
    void testGetSchemaWithSubjectAndVersion() throws IOException {
        Map<String, Object> items = new HashMap<>();
        items.put("registry.url", "http://144.24.55.159:8081");
        items.put("registry.username", "micronaut");
        items.put("registry.password", "test");

        ApplicationContext ctx = ApplicationContext.run(items);
        SchemaRegistryClient client = ctx.getBean(SchemaRegistryClient.class);
        String subject = "human";
        String version = "latest";
        String response = client.getSchemaWithSubjectAndVersion(subject, version);

        String expected = getExpectedResponse("human.schema.json");
        Responses.Subject mappedExpected = jsonMapper.readValue(expected, Responses.Subject.class);
        assertEquals(mappedExpected.schema(), response);
        ctx.close();
    }

    @Test
    void testGetSubjectVersions() throws IOException {
        Map<String, Object> items = new HashMap<>();
        items.put("registry.url", "http://144.24.55.159:8081");
        items.put("registry.username", "micronaut");
        items.put("registry.password", "test");

        ApplicationContext ctx = ApplicationContext.run(items);
        SchemaRegistryClient client = ctx.getBean(SchemaRegistryClient.class);
        String subject = "human";
        List<Integer> response = client.getSubjectVersions(subject);

        assertEquals(List.of(1), response);
        ctx.close();
    }

    private String getExpectedResponse(String filename) throws IOException {
        Optional<InputStream> expectedOptional = resourceLoader.getResourceAsStream(filename);
        assertTrue(expectedOptional.isPresent());
        String expected = new String(expectedOptional.get().readAllBytes(), StandardCharsets.UTF_8);
        return expected.replaceAll("\\s+", "").trim();
    }
}
