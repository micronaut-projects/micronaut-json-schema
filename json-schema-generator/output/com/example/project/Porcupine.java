package com.example.project;

import io.micronaut.serde.annotation.Serdeable;
import java.lang.String;

/**
 * A porcupine
 */
@Serdeable
public class Porcupine {
  private String name;

  public String getName() {
    return this.name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
