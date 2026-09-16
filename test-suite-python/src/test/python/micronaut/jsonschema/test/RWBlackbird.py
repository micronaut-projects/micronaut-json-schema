from dataclasses import dataclass

from micronaut.jsonschema import JsonSchema
from micronaut.serde.annotation import Serdeable


# tag::clazz[]
@JsonSchema(
    title="RedWingedBlackbird",  # <1>
    description="A species of blackbird with red wings",
    uri="/red-winged-blackbird",  # <2>
)
@Serdeable
@dataclass
class RWBlackbird:
    """A red-winged blackbird."""

    name: str | None = None
    """The name"""

    wingSpan: float | None = None
    """The wing span of the bird"""
# end::clazz[]
