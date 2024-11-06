import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.String;

@Serdeable
public record Animal(
    @Pattern(regexp = "^[A-Za-z0-9\\-\\.]{1,64}$") String id,
    @Pattern(regexp = "^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1]))?)?$") String birthdate,
    @Size(min = 1) String name
) {
}
