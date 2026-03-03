package com.example.project;

import io.micronaut.serde.annotation.Serdeable;
import java.lang.Object;
import java.lang.String;
import java.util.Map;

@Serdeable
public record Hedgehog(
    Map<String, Spike> spikes,
    Map<String, String> aliases,
    Map<String, Object> properties
) {
}
