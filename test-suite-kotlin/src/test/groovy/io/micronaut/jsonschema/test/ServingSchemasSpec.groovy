package io.micronaut.jsonschema.test

import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.Test
import spock.lang.Specification


@MicronautTest
class ServingSchemasSpec extends Specification {

    @Inject
    EmbeddedServer server

    @Inject
    HttpClient client

    @Test
    void "test get schemas"() {
        when:
        String result = client.toBlocking().retrieve(
                HttpRequest.GET(server.getURI().resolve("/schemas/llama.schema.json"))
        )

        then:
        result != null
        result.contains("A llama.")

        when:
        result = client.toBlocking().retrieve(
                HttpRequest.GET(server.getURI().resolve("/schemas/red-winged-blackbird.schema.json"))
        )

        then:
        result != null
        result.contains("A species of blackbird with red wings")
    }

}
