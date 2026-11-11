package io.micronaut.jsonschema.validation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.micronaut.jsonschema.JsonSchema;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * A salamander.
 */
@JsonSchema
public final class Salamander {
    @NotEmpty
    @JsonInclude(Include.NON_NULL)
    List<String> colors;

    @Size(min = 2, max = 10)
    List<@Size(min = 3) String> environments;

    @NotBlank
    @JsonInclude(Include.NON_NULL)
    String skinColor;

    @Size(min = 3, max = 20)
    @Pattern(regexp = "^[a-zA-Z \\-]+$")
    String species;

    @PositiveOrZero
    Integer age;

    @Negative
    Integer negative;

    @Min(10)
    @Max(100)
    Long integer;

    @DecimalMin(value = "10", inclusive = false)
    @DecimalMax("100.5")
    Double number;

    public List<String> getColors() {
        return colors;
    }

    public Salamander setColors(List<String> colors) {
        this.colors = colors;
        return this;
    }

    public List<String> getEnvironments() {
        return environments;
    }

    public Salamander setEnvironments(List<String> environments) {
        this.environments = environments;
        return this;
    }

    public String getSkinColor() {
        return skinColor;
    }

    public Salamander setSkinColor(String skinColor) {
        this.skinColor = skinColor;
        return this;
    }

    public String getSpecies() {
        return species;
    }

    public Salamander setSpecies(String species) {
        this.species = species;
        return this;
    }

    public Integer getAge() {
        return age;
    }

    public Salamander setAge(Integer age) {
        this.age = age;
        return this;
    }

    public Integer getNegative() {
        return negative;
    }

    public Salamander setNegative(Integer negative) {
        this.negative = negative;
        return this;
    }

    public Long getInteger() {
        return integer;
    }

    public Salamander setInteger(Long integer) {
        this.integer = integer;
        return this;
    }

    public Double getNumber() {
        return number;
    }

    public Salamander setNumber(Double number) {
        this.number = number;
        return this;
    }
}
