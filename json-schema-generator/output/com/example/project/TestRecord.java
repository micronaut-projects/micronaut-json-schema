import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.lang.Float;
import java.util.List;
import java.util.Set;

@Serdeable
public record TestRecord(
    @Size(min = 1) List<@Size(min = 2) Set<@DecimalMin(10.0) Float>> arrayMulti
) {
}
