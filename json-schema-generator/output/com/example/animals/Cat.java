import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record Cat(
    boolean hasMate
) {
}
