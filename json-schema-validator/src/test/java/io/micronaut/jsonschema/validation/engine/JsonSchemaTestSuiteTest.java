package io.micronaut.jsonschema.validation.engine;

import io.micronaut.core.type.Argument;
import io.micronaut.jackson.databind.JacksonDatabindMapper;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.jsonschema.validation.ValidationMessage;
import io.micronaut.jsonschema.validation.tck.TestSuiteFiles;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the official <a href="https://github.com/json-schema-org/JSON-Schema-Test-Suite">JSON-Schema-Test-Suite</a>
 * (vendored in the validator TCK) against {@link JsonSchemaEngine} with the JSON trees of Jackson Databind
 * (test-suite runs it with those of Micronaut Serialization).
 */
class JsonSchemaTestSuiteTest {

    private static final JsonMapper MAPPER = new JacksonDatabindMapper();
    private static final Argument<JsonNode> JSON_NODE = Argument.of(JsonNode.class);
    private static final String REMOTES_BASE = "http://localhost:1234/";

    @TestFactory
    Stream<DynamicNode> draft2020_12() {
        return suite("draft2020-12", Dialect.DRAFT_2020_12, MAPPER);
    }

    @TestFactory
    Stream<DynamicNode> draft2019_09() {
        return suite("draft2019-09", Dialect.DRAFT_2019_09, MAPPER);
    }

    @TestFactory
    Stream<DynamicNode> draft7() {
        return suite("draft7", Dialect.DRAFT_7, MAPPER);
    }

    @TestFactory
    Stream<DynamicNode> draft6() {
        return suite("draft6", Dialect.DRAFT_6, MAPPER);
    }

    @TestFactory
    Stream<DynamicNode> draft4() {
        return suite("draft4", Dialect.DRAFT_4, MAPPER);
    }

    private static JsonNode read(Path path, JsonMapper mapper) {
        try (InputStream in = Files.newInputStream(path)) {
            return mapper.readValue(in, JSON_NODE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Stream<DynamicNode> suite(String draft, Dialect dialect, JsonMapper mapper) {
        Path root = TestSuiteFiles.root();
        Path remotes = root.resolve("remotes");
        SchemaRetriever retriever = uri -> {
            if (!uri.startsWith(REMOTES_BASE)) {
                return null;
            }
            Path file = remotes.resolve(uri.substring(REMOTES_BASE.length())).normalize();
            if (!file.startsWith(remotes) || !Files.isRegularFile(file)) {
                return null;
            }
            return read(file, mapper);
        };
        // format is an annotation by default; the optional format tests check format assertions
        JsonSchemaEngine engine = new JsonSchemaEngine(mapper, retriever, dialect, null, false);
        JsonSchemaEngine formatEngine = new JsonSchemaEngine(mapper, retriever, dialect, null, true);
        Path tests = root.resolve("tests").resolve(draft);
        List<Path> files;
        try (Stream<Path> walk = Files.walk(tests)) {
            files = walk.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<DynamicNode> nodes = new ArrayList<>();
        for (Path file : files) {
            String fileKey = draft + "/" + tests.relativize(file).toString().replace('\\', '/');
            JsonSchemaEngine fileEngine = fileKey.contains("/optional/format/") ? formatEngine : engine;
            nodes.add(DynamicContainer.dynamicContainer(fileKey, file.toUri(), cases(fileEngine, fileKey, read(file, mapper))));
        }
        return nodes.stream();
    }

    private static Stream<DynamicNode> cases(JsonSchemaEngine engine, String fileKey, JsonNode cases) {
        List<DynamicNode> nodes = new ArrayList<>();
        for (JsonNode testCase : cases.values()) {
            String description = testCase.get("description").getStringValue();
            JsonNode schema = testCase.get("schema");
            CompiledJsonSchema[] compiled = new CompiledJsonSchema[1];
            List<DynamicNode> tests = new ArrayList<>();
            for (JsonNode test : testCase.get("tests").values()) {
                String testDescription = test.get("description").getStringValue();
                boolean valid = test.get("valid").getBooleanValue();
                JsonNode data = test.get("data");
                String key = fileKey + "#" + description + "#" + testDescription;
                tests.add(DynamicTest.dynamicTest(testDescription, () -> {
                    if (compiled[0] == null) {
                        compiled[0] = engine.compile(schema);
                    }
                    boolean actual = engine.isValid(compiled[0], data);
                    assertEquals(valid, actual, () -> key + "\nschema: " + JsonValues.toJson(schema) + "\ndata: " + JsonValues.toJson(data));
                    List<ValidationMessage> errors = engine.validate(compiled[0], data);
                    assertEquals(valid, errors.isEmpty(), () -> key + ": collecting evaluation disagrees, errors: " + errors);
                    if (!valid) {
                        assertTrue(errors.stream().allMatch(e -> e.getMessage() != null && !e.getMessage().isEmpty()));
                    }
                }));
            }
            if (!tests.isEmpty()) {
                nodes.add(DynamicContainer.dynamicContainer(description, tests.stream()));
            }
        }
        return nodes.stream();
    }
}
