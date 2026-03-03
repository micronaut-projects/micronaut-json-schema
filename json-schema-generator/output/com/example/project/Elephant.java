package com.example.project;

import io.micronaut.serde.annotation.Serdeable;
import java.lang.String;

/**
 * A elephant <a href="https://elephant.com">elephant URL</a>.
 *  Another line
 */
@Serdeable
public record Elephant(
    String name
) {
}
