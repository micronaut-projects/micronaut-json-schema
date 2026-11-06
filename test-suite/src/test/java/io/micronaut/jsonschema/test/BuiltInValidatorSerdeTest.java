package io.micronaut.jsonschema.test;

import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.jsonschema.validation.engine.CompiledJsonSchema;
import io.micronaut.jsonschema.validation.engine.Dialect;
import io.micronaut.jsonschema.validation.engine.JsonSchemaEngine;
import io.micronaut.jsonschema.validation.engine.SchemaRetriever;
import io.micronaut.jsonschema.validation.tck.TestSuiteFiles;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
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

/**
 * Runs the 2020-12 JSON-Schema-Test-Suite against the built-in validator with the JSON trees of Micronaut
 * Serialization (the JsonMapper of this test suite), whose number representations differ from Jackson Databind's.
 */
@MicronautTest(startApplication = false)
class BuiltInValidatorSerdeTest {

    private static final Argument<JsonNode> JSON_NODE = Argument.of(JsonNode.class);
    private static final String REMOTES_BASE = "http://localhost:1234/";

    @Inject
    JsonMapper jsonMapper;

    @TestFactory
    Stream<DynamicNode> draft2020_12() throws IOException {
        Path root = TestSuiteFiles.root();
        Path remotes = root.resolve("remotes");
        SchemaRetriever retriever = uri -> {
            if (!uri.startsWith(REMOTES_BASE)) {
                return null;
            }
            Path file = remotes.resolve(uri.substring(REMOTES_BASE.length())).normalize();
            return file.startsWith(remotes) && Files.isRegularFile(file) ? read(file) : null;
        };
        // format is an annotation by default; the optional format tests check format assertions
        JsonSchemaEngine engine = new JsonSchemaEngine(jsonMapper, retriever, Dialect.DRAFT_2020_12, null, false);
        JsonSchemaEngine formatEngine = new JsonSchemaEngine(jsonMapper, retriever, Dialect.DRAFT_2020_12, null, true);
        Path tests = root.resolve("tests").resolve("draft2020-12");
        List<Path> files;
        try (Stream<Path> walk = Files.walk(tests)) {
            files = walk.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        List<DynamicNode> nodes = new ArrayList<>();
        for (Path file : files) {
            String fileKey = tests.relativize(file).toString();
            JsonSchemaEngine fileEngine = fileKey.contains("format/") ? formatEngine : engine;
            List<DynamicNode> tests1 = new ArrayList<>();
            for (JsonNode testCase : read(file).values()) {
                JsonNode schema = testCase.get("schema");
                CompiledJsonSchema[] compiled = new CompiledJsonSchema[1];
                for (JsonNode test : testCase.get("tests").values()) {
                    String key = fileKey + " > " + testCase.get("description").getStringValue() + " > " + test.get("description").getStringValue();
                    boolean valid = test.get("valid").getBooleanValue();
                    JsonNode data = test.get("data");
                    tests1.add(DynamicTest.dynamicTest(key, () -> {
                        if (compiled[0] == null) {
                            compiled[0] = fileEngine.compile(schema);
                        }
                        assertEquals(valid, fileEngine.isValid(compiled[0], data), key);
                        assertEquals(valid, fileEngine.validate(compiled[0], data).isEmpty(), key);
                    }));
                }
            }
            nodes.add(DynamicContainer.dynamicContainer(fileKey, tests1.stream()));
        }
        return nodes.stream();
    }

    private JsonNode read(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return jsonMapper.readValue(in, JSON_NODE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
