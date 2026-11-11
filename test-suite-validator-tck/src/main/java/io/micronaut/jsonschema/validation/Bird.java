package io.micronaut.jsonschema.validation;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import io.micronaut.jsonschema.JsonSchema;

/**
 * A bird.
 */
@JsonTypeInfo(use = Id.NAME)
@JsonSubTypes({
    @Type(value = Ostrich.class, name = "ostrich-bird"),
    @Type(value = Eagle.class)
})
@JsonSchema
public interface Bird {
}


