package com.example.project;

import io.micronaut.serde.annotation.Serdeable;
import java.lang.String;

@Serdeable
public record MyLlamaNumberOne(
    String name
) {
}
