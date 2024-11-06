import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
public record Fish(
    List<Fish> friends
) {
}
