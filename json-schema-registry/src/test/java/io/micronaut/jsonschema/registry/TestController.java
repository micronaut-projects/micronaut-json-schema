package io.micronaut.jsonschema.registry;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.jsonschema.registry.types.Responses;
import io.micronaut.jsonschema.registry.types.SubjectRequestBody;

import java.util.ArrayList;
import java.util.List;

@Controller("/test")
@Consumes(MediaType.APPLICATION_JSON)
public class TestController {
    private final List<Responses.Subject> subjects = new ArrayList<>();
    private int idCounter = 2;

    @Post("/subjects/{subject}/versions")
    @SingleResult
    Responses.Id registerNewVersion(@PathVariable String subject,
                                    @Body SubjectRequestBody schemaBody) {
        // check if subject already exists and get its latest version
        var existingSubject = getExistingLatestSubject(subject);
        var version = 1;
        if (existingSubject != null) {
            if (existingSubject.schema().equals(schemaBody.schema())) {
                return new Responses.Id(existingSubject.id());
            }
            version = existingSubject.version() + 1;
        }
        var newSubject = new Responses.Subject(subject, idCounter, version, schemaBody.schemaType(), schemaBody.schema());
        subjects.add(newSubject);
        idCounter++;
        return new Responses.Id(newSubject.id());
    }

    @Get("/subjects")
    @SingleResult
    List<String> getSubjects() {
        return subjects.stream()
                .map(Responses.Subject::subject)
                .distinct()
                .toList();
    }

    @Get("/subjects/{subject}/versions")
    @SingleResult
    List<Integer> getSubjectVersions(@PathVariable String subject) {
        return subjects.stream()
                .filter(s -> s.subject().equals(subject))
                .map(Responses.Subject::version)
                .toList();
    }

    @Delete("/subjects/{subject}")
    @SingleResult
    List<Integer> deleteSubject(@PathVariable String subject) {
        var deletedVersions = subjects.stream()
                .filter(s -> s.subject().equals(subject))
                .map(Responses.Subject::version)
                .toList();
        subjects.removeIf(s -> s.subject().equals(subject));
        return deletedVersions;
    }

    @Get("/subjects/{subject}/versions/{version}")
    @SingleResult
    Responses.Subject getSubjectWithVersion(@PathVariable String subject,
                                            @PathVariable String version) {
        if (version.equals("latest")) {
            return getExistingLatestSubject(subject);
        }
        return subjects.stream()
                .filter(s -> s.subject().equals(subject) && s.version() == Integer.parseInt(version))
                .findFirst()
                .orElse(null);
    }

    @Get("/subjects/{subject}/versions/{version}/schema")
    @SingleResult
    String getSchemaWithSubjectAndVersion(@PathVariable String subject,
                                          @PathVariable String version) {
        var subjectResponse = getSubjectWithVersion(subject, version);
        return subjectResponse != null ? subjectResponse.schema() : null;
    }

    @Post("/subjects/{subject}")
    @SingleResult
    Responses.Subject checkSubject(@PathVariable String subject,
                                    @Body SubjectRequestBody schemaBody) {
        return subjects.stream()
            .filter(s -> s.subject().equals(subject) && s.schema().equals(schemaBody.schema()))
            .findFirst().orElse(null);
    }

    @Delete("/subjects/{subject}/versions/{version}")
    @SingleResult
    int deleteSubjectVersion(@PathVariable String subject,
                             @PathVariable String version) {
        if (version.equals("latest")) {
            var latest = getExistingLatestSubject(subject);
            if (latest != null) {
                subjects.remove(latest);
                return latest.version();
            }
            return -1;
        }
        int versionInt = Integer.parseInt(version);
        if (!subjects.removeIf(s -> s.subject().equals(subject) && s.version() == versionInt)) {
            return -1;
        }
        return versionInt;
    }

    private Responses.Subject getExistingLatestSubject(String subject) {
        return subjects.stream()
            .filter(s -> s.subject().equals(subject))
            .max((s1, s2) -> Integer.compare(s2.version(), s1.version()))
            .orElse(null);
    }
}
