package com.example.project;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.lang.Float;
import java.lang.String;
import java.util.HashMap;
import java.util.List;

/**
 * A llama. &lt;4&gt;
 */
@Serdeable
public class Llama2 {
  /**
   * The age
   */
  private @NotNull @Min(0) int age;

  /**
   * The name
   */
  private @NotNull @Size(min = 1) String name;

  /**
   * Happy hours
   */
  private List<@DecimalMin("0.0") Float> hours;

  HashMap<String, String> unknownFields;

  public @NotNull @Min(0) int getAge() {
    return this.age;
  }

  public void setAge(@NotNull @Min(0) int age) {
    this.age = age;
  }

  public @NotNull @Size(min = 1) String getName() {
    return this.name;
  }

  public void setName(@NotNull @Size(min = 1) String name) {
    this.name = name;
  }

  public List<@DecimalMin("0.0") Float> getHours() {
    return this.hours;
  }

  public void setHours(List<@DecimalMin("0.0") Float> hours) {
    this.hours = hours;
  }

  @JsonAnyGetter
  public HashMap<String, String> getUnknownFields() {
    return this.unknownFields;
  }

  @JsonAnySetter
  public void setUnknownFields(String name, String value) {
    if (this.unknownFields == null) {
      this.unknownFields = new java.util.HashMap();
    }
    this.unknownFields.put(name, value);
  }
}
