package com.example.project;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record Spike(
    float length
) {
}
