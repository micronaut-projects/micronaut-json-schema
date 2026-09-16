from dataclasses import dataclass
from typing import Annotated

from com.fasterxml.jackson.annotation import JsonInclude
from jakarta.validation.constraints import NotBlank, PositiveOrZero
from micronaut.jsonschema import JsonSchema
from micronaut.serde.annotation import Serdeable


@JsonSchema  # <1>
@Serdeable  # <2>
@dataclass
class Llama:
    """A llama."""  # <4>

    name: Annotated[str, NotBlank, JsonInclude(JsonInclude.Include.NON_NULL)]  # <3>
    """The name"""

    age: Annotated[int | None, PositiveOrZero] = None  # <3>
    """The age"""
