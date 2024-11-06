import io.micronaut.serde.annotation.Serdeable;
import java.lang.String;
import java.util.List;

@Serdeable
public record Dog(
    boolean hasMate,
    String nickname,
    List<Cat> enemies
) {
}
