package com.example.project;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import java.lang.Float;
import java.util.List;

@Serdeable
public record TestRecord(
    @NotNull List<Float> array
) {
}
