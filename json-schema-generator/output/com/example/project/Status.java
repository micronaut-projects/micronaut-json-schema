package com.example.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.serde.annotation.Serdeable;
import java.lang.String;

/**
 * Status für mich
 */
@Serdeable
public enum Status {

  ACTIVE("active"),
  IN_PROGRESS("in progress"),
  DELETED("deleted"),
  NOT_ACTIVE("not_active"),
  NON_VALID("non-valid");

  public String value;

  private Status(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return this.value;
  }

  @JsonCreator
  public static Status statusOf(String value) {
    return switch (value) {
          case "active" -> ACTIVE;
          case "in progress" -> IN_PROGRESS;
          case "deleted" -> DELETED;
          case "not_active" -> NOT_ACTIVE;
          case "non-valid" -> NON_VALID;
          default -> null;
        };
  }
}
