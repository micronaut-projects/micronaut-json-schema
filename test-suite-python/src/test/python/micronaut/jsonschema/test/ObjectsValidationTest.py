from typing import Annotated

from jakarta.inject import Inject
from micronaut.jsonschema.validation import JsonSchemaValidator
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .Llama import Llama
from .RWBlackbird import RWBlackbird


@MicronautTest(startApplication=False)
class ObjectsValidationTest:
    json_schema_validator: Annotated[JsonSchemaValidator, Inject]

    @Test
    def test_valid_record(self) -> None:
        assertions = self.json_schema_validator.validate(Llama("John", 12), Llama)
        assert assertions.size() == 0

    @Test
    def test_invalid_record(self) -> None:
        assertions = self.json_schema_validator.validate(Llama("", 12), Llama)
        assert assertions.size() == 1
        assert list(assertions)[0].getMessage() == "/name: must be at least 1 characters long"

        assertions = self.json_schema_validator.validate('{"name":null}', Llama)
        assert assertions.size() == 1
        assert list(assertions)[0].getMessage() == "/name: null found, string expected"

        assertions = self.json_schema_validator.validate(Llama("John", -12), Llama)
        assert assertions.size() == 1
        assert list(assertions)[0].getMessage() == "/age: must have a minimum value of 0"

    @Test
    def test_valid_object_with_changed_path(self) -> None:
        assertions = self.json_schema_validator.validate(RWBlackbird("Clara", 1.2), RWBlackbird)
        assert assertions.size() == 0

    @Test
    def test_invalid_object_with_changed_path(self) -> None:
        assertions = self.json_schema_validator.validate('{"name":12}', RWBlackbird)
        assert assertions.size() == 1
        assert list(assertions)[0].getMessage() == "/name: integer found, string expected"
