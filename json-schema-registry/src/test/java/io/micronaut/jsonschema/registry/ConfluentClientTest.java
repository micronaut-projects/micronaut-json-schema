package io.micronaut.jsonschema.registry;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
public class ConfluentClientTest {

    @Inject
    @Client("http://144.24.55.159:8081")
    HttpClient client; // (2)

    @Test
    void testConfluentResponse() {
        String subject = "human";
        String version = "latest";
        URI url = UriBuilder.of("/subjects")
            .path(subject)
            .path("versions")
            .path(version)
            .build();

        HttpRequest<?> request = HttpRequest.GET(url);
        //    .header("Content-Type", "application/vnd.schemaregistry.v1+json");

        String response = client.toBlocking().retrieve(request);
        assertEquals("""
            {"subject":"human","version":1,"id":2,"schemaType":"JSON","schema":"{\\"$schema\\":\\"http://json-schema.org/draft-07/schema#\\",\\"type\\":\\"object\\",\\"properties\\":{\\"name\\":{\\"type\\":\\"string\\"}}}"}""", response);
    }
}
