import json
from typing import Annotated

from java.lang import String
from java.nio.charset import StandardCharsets
from jakarta.inject import Inject
from micronaut.core.io import ResourceLoader
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@MicronautTest(startApplication=False)
class SchemaGenerationTest:
    resource_loader: Annotated[ResourceLoader, Inject]

    @Test
    def test_build_json_schema(self) -> None:
        for name in ["llama", "red-winged-blackbird"]:
            assert self.resource_loader.getResource("META-INF/schemas/" + name + ".schema.json").isPresent()

    @Test
    def test_llama_schema(self) -> None:
        expected = self.read_resource("expected-llama.schema.json")
        result = self.read_resource("META-INF/schemas/llama.schema.json")
        assert json.loads(expected) == json.loads(result)

    def read_resource(self, path: str) -> str:
        stream = self.resource_loader.getResourceAsStream(path)
        assert stream.isPresent()
        return str(String(stream.get().readAllBytes(), StandardCharsets.UTF_8))
