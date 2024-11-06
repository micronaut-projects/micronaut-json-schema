import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.lang.Float;
import java.lang.String;
import java.util.List;

@Serdeable
public record Llama(
    @NotNull @Min(0) int age,
    @NotNull @Size(min = 1) String name,
    Status status,
    List<@Size(min = 1) List<@DecimalMin(18.0) @DecimalMax(22.5) Float>> hours
) {
  public enum Status {

    SINGLE("Single"),
    TAKEN("Taken");

    public String name;

    private Status(String name) {
      this.name = name;
    }

    @JsonValue
    public String getName() {
      return this.name;
    }

    @JsonCreator
    public Status statusOf(String name) {
      return switch (name) {
            case "Single" -> SINGLE;
            case "Taken" -> TAKEN;
          };
    }
  }
}
