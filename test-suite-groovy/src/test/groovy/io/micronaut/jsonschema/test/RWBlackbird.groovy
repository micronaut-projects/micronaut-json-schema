package io.micronaut.jsonschema.test

import io.micronaut.jsonschema.JsonSchema
import io.micronaut.serde.annotation.Serdeable

/**
 * A red-winged blackbird.
 */
// tag::clazz[]
@JsonSchema(
    title = "RedWingedBlackbird", // <1>
    description = "A species of blackbird with red wings",
    uri = "/red-winged-blackbird" // <2>
)
@Serdeable
class RWBlackbird {
    /**
     * The name.
     */
    String name

    /**
     * The wingspan.
     */
    Double wingSpan
}
// end::clazz[]
