package io.micronaut.jsonschema.registry;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.jsonschema.registry.types.ConfigKeys;
import io.micronaut.jsonschema.registry.types.Responses;
import io.micronaut.jsonschema.registry.types.SubjectRequestBody;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@Property(name = ConfigKeys.ORIGIN, value = "/test/")
public class ClientTest {

    @Inject
    ResourceLoader resourceLoader;
    @Inject
    SchemaRegistryClient client;

    Responses.Subject exampleSubject;

    @Test
    void testAddNewSubject() throws IOException {
        prepareTestData();
        // register new subject
        var body = new SubjectRequestBody(exampleSubject.schema(), Responses.SchemaType.JSON, List.of(), Map.of(), Set.of());
        var response = client.registerNewVersion(exampleSubject.subject(), body);

        // check subject is correctly registered
        assertEquals(2, response.id());
        assertEquals(1, client.getSubjects().size());
        assertEquals(List.of(1), client.getSubjectVersions(exampleSubject.subject()));
        assertEquals(exampleSubject, client.getSubjectWithVersion(exampleSubject.subject(), "1"));
        assertEquals(exampleSubject, client.getSubjectWithVersion(exampleSubject.subject(), "latest"));
        assertEquals(exampleSubject, client.checkSubject(exampleSubject.subject(), body));
        assertEquals(1, client.getSubjects().size());

        // delete non-existing version
        assertEquals(-1, client.deleteSubjectVersion(exampleSubject.subject(), "2"));

        // delete subject
        assertEquals(List.of(1), client.deleteSubject(exampleSubject.subject()));
        assertEquals(0, client.getSubjects().size());
    }

    @Test
    void testGetSubjects() {
        var response = client.getSubjects();
        assertEquals(0, response.size());
    }

    public void prepareTestData() throws IOException {
        JsonMapper jsonMapper = new JsonMapper();
        Optional<InputStream> expectedOptional = resourceLoader.getResourceAsStream("human.schema.json");
        assertTrue(expectedOptional.isPresent());
        String expected = new String(expectedOptional.get().readAllBytes(), StandardCharsets.UTF_8);
        expected = expected.replaceAll("\\s+", "").trim();
        exampleSubject = jsonMapper.readValue(expected, Responses.Subject.class);
    }
}
