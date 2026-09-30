from typing import Annotated

from jakarta.inject import Inject
from micronaut.http import HttpRequest
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@MicronautTest
class ServingSchemasTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def test_get_schemas(self) -> None:
        result = self.client.toBlocking().retrieve(HttpRequest.GET("/schemas/llama.schema.json"))
        assert result is not None
        assert "A llama." in result

        result = self.client.toBlocking().retrieve(HttpRequest.GET("/schemas/red-winged-blackbird.schema.json"))
        assert result is not None
        assert "A species of blackbird with red wings" in result
