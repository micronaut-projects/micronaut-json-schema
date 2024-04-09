package io.micronaut.jsonschema.test;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
class ServingSchemasTest  {
    @Test
    void testGetSchemas(@Client("/") HttpClient client) {
        String result = client.toBlocking().retrieve(HttpRequest.GET("/schemas/llama.schema.json"));
        assertNotNull(result);
        assertTrue(result.contains("A llama."));

        result = client.toBlocking().retrieve(HttpRequest.GET("/schemas/red-winged-blackbird.schema.json"));
        assertNotNull(result);
        assertTrue(result.contains("A species of blackbird with red wings"));
    }
}
