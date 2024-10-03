import io.micronaut.serde.annotation.Serdeable;
import java.lang.String;

@Serdeable
public record LlamaRecord(
    int age,
    String name
) {
}
