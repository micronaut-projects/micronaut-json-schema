package io.micronaut.jsonschema.validation.tck;

import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.jsonschema.validation.JsonSchemaValidator;
import io.micronaut.jsonschema.validation.ValidationMessage;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs the tests (required and optional) of the official <a href="https://github.com/json-schema-org/JSON-Schema-Test-Suite">JSON-Schema-Test-Suite</a>
 * through the public {@link JsonSchemaValidator} API ({@link JsonSchemaValidator#validate(Object, String)}), so that every
 * implementation is held to the same expectations.
 *
 * <p>Tests that the public API cannot express are excluded for every implementation (see {@link #COMMON_EXCLUSIONS}).
 * Anything else an implementation does not support must be listed in its {@value #EXCLUSIONS} classpath resource,
 * which documents where implementations differ. Each line is a {@code <draft>} directory or a {@code <draft>/<file>}, optionally followed by
 * {@code  > <case description>} and {@code  > <test description>}; lines starting with {@code #} are comments.</p>
 */
@MicronautTest(startApplication = false)
class OfficialTestSuiteTest {

    /**
     * The classpath resource listing the tests an implementation does not support.
     */
    static final String EXCLUSIONS = "json-schema-validator-tck-exclusions.txt";

    /**
     * Tests excluded for every implementation: references to the test suite's remote documents
     * ({@code http://localhost:1234/...}) cannot be served through the public API, and the validators assert
     * {@code format} by default whereas the 2020-12 suite expects it to be an annotation only.
     */
    static final Map<String, String> COMMON_EXCLUSIONS = Map.of(
        "draft2020-12/refRemote.json", "needs the remote documents of the test suite",
        "draft2019-09/refRemote.json", "needs the remote documents of the test suite",
        "draft7/refRemote.json", "needs the remote documents of the test suite"
    );

    private static final String SEPARATOR = " > ";
    private static final String REMOTES = "http://localhost:1234";
    private static final String FORMAT_ANNOTATION = "only an annotation by default";
    private static final Map<String, String> DRAFTS = new LinkedHashMap<>();

    static {
        // the dialect of each draft, declared with $schema in the schemas of the test suite that do not declare it
        DRAFTS.put("draft2020-12", "https://json-schema.org/draft/2020-12/schema");
        DRAFTS.put("draft2019-09", "https://json-schema.org/draft/2019-09/schema");
        DRAFTS.put("draft7", "http://json-schema.org/draft-07/schema#");
    }
    private static final Argument<JsonNode> JSON_NODE = Argument.of(JsonNode.class);

    @Inject
    JsonSchemaValidator validator;

    @Inject
    JsonMapper jsonMapper;

    @TestFactory
    Stream<DynamicNode> jsonSchemaTestSuite() throws IOException {
        Set<String> exclusions = exclusions();
        Path tests = TestSuiteFiles.root().resolve("tests");
        List<DynamicNode> drafts = new ArrayList<>();
        for (Map.Entry<String, String> entry : DRAFTS.entrySet()) {
            String draft = entry.getKey();
            if (exclusions.contains(draft)) {
                continue;
            }
            List<Path> files;
            Path dir = tests.resolve(draft);
            try (Stream<Path> walk = Files.walk(dir)) {
                files = walk.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
            }
            List<DynamicNode> nodes = new ArrayList<>();
            for (Path file : files) {
                String fileKey = draft + "/" + dir.relativize(file).toString().replace('\\', '/');
                if (!COMMON_EXCLUSIONS.containsKey(fileKey) && !exclusions.contains(fileKey)) {
                    nodes.add(DynamicContainer.dynamicContainer(fileKey, cases(fileKey, read(file), entry.getValue(), exclusions)));
                }
            }
            drafts.add(DynamicContainer.dynamicContainer(draft, nodes.stream()));
        }
        return drafts.stream();
    }

    private Stream<DynamicNode> cases(String fileKey, JsonNode cases, String metaSchema, Set<String> exclusions) throws IOException {
        List<DynamicNode> nodes = new ArrayList<>();
        for (JsonNode testCase : cases.values()) {
            String description = testCase.get("description").getStringValue();
            String caseKey = fileKey + SEPARATOR + description;
            String schema = jsonMapper.writeValueAsString(withMetaSchema(testCase.get("schema"), metaSchema));
            if (exclusions.contains(caseKey) || schema.contains(REMOTES)) {
                continue;
            }
            List<DynamicNode> tests = new ArrayList<>();
            for (JsonNode test : testCase.get("tests").values()) {
                String testDescription = test.get("description").getStringValue();
                String key = caseKey + SEPARATOR + testDescription;
                if (exclusions.contains(key) || testDescription.contains(FORMAT_ANNOTATION)) {
                    continue;
                }
                boolean valid = test.get("valid").getBooleanValue();
                String data = jsonMapper.writeValueAsString(test.get("data"));
                tests.add(DynamicTest.dynamicTest(testDescription, () -> {
                    Set<? extends ValidationMessage> messages = validator.validate(data, schema);
                    assertEquals(valid, messages.isEmpty(), () -> key + "\nschema: " + schema + "\ndata: " + data + "\nmessages: " + messages);
                }));
            }
            if (!tests.isEmpty()) {
                nodes.add(DynamicContainer.dynamicContainer(description, tests.stream()));
            }
        }
        return nodes.stream();
    }

    private static JsonNode withMetaSchema(JsonNode schema, String metaSchema) {
        if (!schema.isObject() || schema.get("$schema") != null) {
            return schema;
        }
        Map<String, JsonNode> values = new LinkedHashMap<>();
        values.put("$schema", JsonNode.createStringNode(metaSchema));
        schema.entries().forEach(e -> values.put(e.getKey(), e.getValue()));
        return JsonNode.createObjectNode(values);
    }

    private JsonNode read(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return jsonMapper.readValue(in, JSON_NODE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Set<String> exclusions() throws IOException {
        Set<String> exclusions = new HashSet<>();
        InputStream in = OfficialTestSuiteTest.class.getClassLoader().getResourceAsStream(EXCLUSIONS);
        if (in == null) {
            return exclusions;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    exclusions.add(trimmed);
                }
            }
        }
        return exclusions;
    }
}
