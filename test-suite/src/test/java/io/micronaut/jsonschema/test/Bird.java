package io.micronaut.jsonschema.test;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.micronaut.jsonschema.JsonSchema;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * A bird.
 */
@JsonTypeInfo(use = Id.NAME)
@JsonSubTypes({
    @Type(value = Bird.Ostrich.class, name = "ostrich-bird"),
    @Type(value = Bird.Eagle.class)
})
@Serdeable
@JsonSchema
public interface Bird {

    /**
     * An ostrich.
     *
     * @param name The name
     * @param runSpeed The run speed
     */
    @Serdeable
    record Ostrich(
        String name,
        @Positive
        float runSpeed
    ) implements Bird {
    }

    /**
     * The eagle.
     *
     * @param name The name
     * @param flySpeed The fly speed
     */
    @JsonTypeName("eagle-bird")
    record Eagle(
        String name,
        @Min(1)
        float flySpeed
    ) implements Bird {
    }

}


