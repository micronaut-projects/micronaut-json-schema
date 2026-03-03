package com.example.project;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Min;
import java.util.List;

/**
 * A llama. &lt;4&gt;
 *
 * @param age The age
 * @param name This
 * @param hours Happy hours
 */
@Serdeable
public record Llama(
    @Min(0) int age,
    Llama name,
    List<Llama> hours
) {
}
