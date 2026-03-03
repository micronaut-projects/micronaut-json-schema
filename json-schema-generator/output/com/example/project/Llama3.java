package com.example.project;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.lang.String;

/**
 * A llama. &lt;4&gt;
 *
 * @param name The name
 */
@Serdeable
public record Llama3(
    @NotNull @Size(min = 1) String name,
    @NotNull String foo,
    @NotNull @DecimalMin("18") float bar
) {
}
