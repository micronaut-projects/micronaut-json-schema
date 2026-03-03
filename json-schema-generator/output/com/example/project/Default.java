package com.example.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.lang.String;

/**
 * A record with inner record.
 *
 * @param age The age
 */
@Serdeable
public record Default(
    @Min(0) int age,
    Defaults defaults
) {
  @Serdeable
  public record Defaults(
      Run run
  ) {
    @Serdeable
    public record Run(
        Shell shell,
        @JsonProperty("working-directory") @Pattern(regexp = "^[a-zA-Z]*") String workingDirectory
    ) {
      @Serdeable
      public enum Shell {

        BASH("bash"),
        PWSH("pwsh"),
        PYTHON("python"),
        SH("sh"),
        CMD("cmd"),
        POWERSHELL("powershell");

        public String value;

        private Shell(String value) {
          this.value = value;
        }

        @JsonValue
        public String getValue() {
          return this.value;
        }

        @JsonCreator
        public static Run.Shell statusOf(String value) {
          return switch (value) {
                case "bash" -> BASH;
                case "pwsh" -> PWSH;
                case "python" -> PYTHON;
                case "sh" -> SH;
                case "cmd" -> CMD;
                case "powershell" -> POWERSHELL;
                default -> null;
              };
        }
      }
    }
  }
}
