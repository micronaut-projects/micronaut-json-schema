import io.micronaut.serde.annotation.Serdeable;
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
    List<@DecimalMin(0.0) Float> hours
) {
}
