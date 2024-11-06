import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.lang.String;

public enum Status {

  ACTIVE("active"),
  IN_PROGRESS("in progress"),
  DELETED("deleted");

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
          case "active" -> ACTIVE;
          case "in progress" -> IN_PROGRESS;
          case "deleted" -> DELETED;
        };
  }
}
