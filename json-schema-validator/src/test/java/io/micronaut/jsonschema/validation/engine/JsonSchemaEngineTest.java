package io.micronaut.jsonschema.validation.engine;

import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.jsonschema.validation.ValidationMessage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonSchemaEngineTest {

    private static final JsonMapper MAPPER = JsonMapper.createDefault();

    private final JsonSchemaEngine engine = new JsonSchemaEngine(MAPPER, SchemaRetriever.NONE, Dialect.DRAFT_2020_12, null);

    private static JsonNode json(String json) throws IOException {
        return MAPPER.readValue(json, Argument.of(JsonNode.class));
    }

    private List<String> messages(String schema, String instance) throws IOException {
        return engine.validate(engine.compile(json(schema)), json(instance)).stream().map(ValidationMessage::getMessage).toList();
    }

    @Test
    void reportsJsonPointerLocationsKeywordsAndSchemaLocations() throws IOException {
        CompiledJsonSchema schema = engine.compile(json("""
            {"$id":"https://example.com/order.json","type":"object","required":["id"],
             "properties":{"items":{"type":"array","items":{"$ref":"#/$defs/item"}}},
             "$defs":{"item":{"type":"object","properties":{"a/b~c":{"type":"integer","minimum":1}}}}}
            """));
        List<ValidationMessage> errors = engine.validate(schema, json("""
            {"items":[{"a/b~c":1},{"a/b~c":0}]}
            """));
        assertEquals(2, errors.size());
        ValidationMessage required = errors.get(0);
        assertEquals(": required property 'id' not found", required.getMessage());
        assertEquals("", required.getInstanceLocation());
        assertEquals("required", required.getKeyword());
        assertEquals("https://example.com/order.json#/required", required.getSchemaLocation());
        ValidationMessage minimum = errors.get(1);
        assertEquals("/items/1/a~1b~0c: must have a minimum value of 1", minimum.getMessage());
        assertEquals("/items/1/a~1b~0c", minimum.getInstanceLocation());
        assertEquals("minimum", minimum.getKeyword());
        assertEquals("https://example.com/order.json#/$defs/item/properties/a~1b~0c/minimum", minimum.getSchemaLocation());
    }

    @Test
    void messagesAreComparableToPreviousImplementation() throws IOException {
        assertEquals(List.of("/name: integer found, string expected"),
            messages("{\"properties\":{\"name\":{\"type\":\"string\"}}}", "{\"name\":12}"));
        assertEquals(List.of("/name: null found, [string, integer] expected"),
            messages("{\"properties\":{\"name\":{\"type\":[\"string\",\"integer\"]}}}", "{\"name\":null}"));
        assertEquals(List.of(": property 'extra' is not defined in the schema and the schema does not allow additional properties"),
            messages("{\"properties\":{\"a\":true},\"additionalProperties\":false}", "{\"a\":1,\"extra\":2}"));
        assertEquals(List.of(": property 'extra' is not evaluated and the schema does not allow unevaluated properties"),
            messages("{\"allOf\":[{\"properties\":{\"a\":true}}],\"unevaluatedProperties\":false}", "{\"a\":1,\"extra\":2}"));
        assertEquals(List.of("/date: does not match the date pattern must be a valid RFC 3339 full-date"),
            messages("{\"properties\":{\"date\":{\"type\":\"string\",\"format\":\"date\"}}}", "{\"date\":\"2024-02-30\"}"));
        assertEquals(List.of(": does not have a value in the enumeration [\"a\",\"b\"]"),
            messages("{\"enum\":[\"a\",\"b\"]}", "\"c\""));
        assertEquals(List.of(": must have at most 1 items but found 2"),
            messages("{\"maxItems\":1}", "[1,2]"));
        assertEquals(List.of(": has a missing property 'b' which is dependent required because 'a' is present"),
            messages("{\"dependentRequired\":{\"a\":[\"b\"]}}", "{\"a\":1}"));
        assertEquals(List.of(": property 'Bad' name is not valid: does not match the regex pattern ^[a-z]+$"),
            messages("{\"propertyNames\":{\"pattern\":\"^[a-z]+$\"}}", "{\"Bad\":1}"));
        assertEquals(List.of(": must be valid to one and only one schema, but 2 are valid with indexes '0, 1'"),
            messages("{\"oneOf\":[{\"type\":\"integer\"},{\"minimum\":0}]}", "1"));
    }

    @Test
    void anyOfAndOneOfReportNestedErrors() throws IOException {
        List<String> messages = messages("{\"anyOf\":[{\"type\":\"string\"},{\"type\":\"integer\"}]}", "true");
        assertEquals(3, messages.size());
        assertTrue(messages.get(0).startsWith(": must be valid to any of the schemas "));
        assertTrue(messages.contains(": boolean found, string expected"));
        assertTrue(messages.contains(": boolean found, integer expected"));
    }

    @Test
    void draft7RefIgnoresSiblingKeywords() throws IOException {
        String schema = """
            {"$schema":"http://json-schema.org/draft-07/schema#",
             "definitions":{"positive":{"type":"integer","minimum":1}},
             "properties":{"count":{"$ref":"#/definitions/positive","maximum":2}}}
            """;
        CompiledJsonSchema compiled = engine.compile(json(schema));
        assertEquals(Dialect.DRAFT_7, compiled.getDialect());
        assertTrue(engine.isValid(compiled, json("{\"count\":10}")));
        assertFalse(engine.isValid(compiled, json("{\"count\":0}")));
    }

    @Test
    void validatesAgainstBundledMetaSchemaWithoutNetworkAccess() throws IOException {
        CompiledJsonSchema metaSchema = engine.compile(json("{\"$ref\":\"https://json-schema.org/draft/2020-12/schema\"}"));
        assertTrue(engine.isValid(metaSchema, json("{\"type\":\"object\",\"properties\":{\"a\":{\"minLength\":1}}}")));
        assertFalse(engine.isValid(metaSchema, json("{\"type\":\"object\",\"properties\":{\"a\":{\"minLength\":-1}}}")));
        CompiledJsonSchema draft7 = engine.compile(json("{\"$ref\":\"http://json-schema.org/draft-07/schema#\"}"));
        assertFalse(engine.isValid(draft7, json("{\"type\":12}")));
    }

    @Test
    void resolvesReferencesThroughTheRetriever() throws IOException {
        Map<String, String> documents = Map.of(
            "https://example.com/schemas/address.json", "{\"type\":\"object\",\"required\":[\"city\"],\"properties\":{\"zip\":{\"$ref\":\"zip.json\"}}}",
            "https://example.com/schemas/zip.json", "{\"type\":\"string\",\"pattern\":\"^[0-9]{5}$\"}"
        );
        AtomicInteger loads = new AtomicInteger();
        JsonSchemaEngine remoteEngine = new JsonSchemaEngine(MAPPER, uri -> {
            String document = documents.get(uri);
            if (document == null) {
                return null;
            }
            loads.incrementAndGet();
            return json(document);
        }, Dialect.DRAFT_2020_12, "https://example.com/schemas/");
        CompiledJsonSchema schema = remoteEngine.compile(json("{\"properties\":{\"address\":{\"$ref\":\"address.json\"}}}"));
        assertTrue(remoteEngine.isValid(schema, json("{\"address\":{\"city\":\"Paris\",\"zip\":\"75001\"}}")));
        List<ValidationMessage> errors = remoteEngine.validate(schema, json("{\"address\":{\"zip\":\"7500\"}}"));
        assertEquals(List.of("/address: required property 'city' not found", "/address/zip: does not match the regex pattern ^[0-9]{5}$"),
            errors.stream().map(ValidationMessage::getMessage).toList());
        remoteEngine.compile(json("{\"$ref\":\"address.json\"}"));
        assertEquals(2, loads.get(), "referenced documents are loaded once and cached");
    }

    @Test
    void sharedDocumentsDoNotResolveIntoAnotherCompilation() throws IOException {
        Map<String, String> documents = Map.of(
            "http://localhost:8080/schemas/a.json", "{\"properties\":{\"b\":{\"$ref\":\"b.json\"}}}",
            "http://localhost:8080/schemas/b.json", "{\"properties\":{\"a\":{\"$ref\":\"a.json\"}}}",
            "http://localhost:8080/schemas/c.json", "{\"properties\":{\"b\":{\"$ref\":\"b.json\"}}}"
        );
        JsonSchemaEngine remoteEngine = new JsonSchemaEngine(MAPPER, uri -> documents.containsKey(uri) ? json(documents.get(uri)) : null,
            Dialect.DRAFT_2020_12, "http://localhost:8080/schemas/");
        // an ad-hoc schema that reuses the identifier of a classpath schema
        CompiledJsonSchema adHoc = remoteEngine.compile(json("""
            {"$id":"http://localhost:8080/schemas/a.json","required":["mustHave"],"properties":{"b":{"$ref":"b.json"}}}
            """));
        assertFalse(remoteEngine.isValid(adHoc, json("{}")));
        CompiledJsonSchema c = remoteEngine.compile(json("{\"$ref\":\"c.json\"}"));
        assertEquals(List.of(), remoteEngine.validate(c, json("{\"b\":{\"a\":{}}}")));
    }

    @Test
    void unusualNumericKeywordValues() throws IOException {
        CompiledJsonSchema hugeMax = engine.compile(json("{\"maxItems\":18446744073709551615,\"maxLength\":99999999999999999999999}"));
        assertTrue(engine.isValid(hugeMax, json("[1,2,3]")));
        assertTrue(engine.isValid(hugeMax, json("\"abc\"")));
        CompiledJsonSchema overflowedDivisor = engine.compile(json("{\"multipleOf\":1e400}"));
        assertFalse(engine.isValid(overflowedDivisor, json("5")));
        assertTrue(engine.isValid(overflowedDivisor, json("0")));
    }

    @Test
    void unresolvableReferencesFailWhenEvaluated() throws IOException {
        CompiledJsonSchema schema = engine.compile(json("{\"properties\":{\"a\":{\"$ref\":\"https://example.com/missing.json\"}}}"));
        assertTrue(engine.isValid(schema, json("{\"b\":1}")));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> engine.isValid(schema, json("{\"a\":1}")));
        assertTrue(e.getMessage().contains("https://example.com/missing.json"), e.getMessage());
    }

    @Test
    void invalidSchemasFailToCompile() {
        assertThrows(IllegalArgumentException.class, () -> engine.compile(json("{\"pattern\":\"[a-\"}")));
        assertThrows(IllegalArgumentException.class, () -> engine.compile(json("{\"properties\":{\"a\":12}}")));
        assertThrows(IllegalArgumentException.class, () -> engine.compile(json("{\"$schema\":\"https://example.com/unknown-meta-schema\"}")));
    }

    @Test
    void failedCompilationDoesNotAffectLaterCompilations() throws IOException {
        JsonSchemaEngine remoteEngine = new JsonSchemaEngine(MAPPER, uri -> uri.equals("https://example.com/broken.json")
            ? json("{\"properties\":{\"a\":{\"pattern\":\"[\"}}}")
            : null, Dialect.DRAFT_2020_12, null);
        assertThrows(IllegalArgumentException.class, () -> remoteEngine.compile(json("{\"$ref\":\"https://example.com/broken.json\"}")));
        assertThrows(IllegalArgumentException.class, () -> remoteEngine.compile(json("{\"$ref\":\"https://example.com/broken.json\"}")));
        CompiledJsonSchema valid = remoteEngine.compile(json("{\"type\":\"string\"}"));
        assertTrue(remoteEngine.isValid(valid, json("\"ok\"")));
    }

    @Test
    void detectsInfiniteRecursion() throws IOException {
        CompiledJsonSchema schema = engine.compile(json("{\"$defs\":{\"a\":{\"$ref\":\"#/$defs/b\"},\"b\":{\"$ref\":\"#/$defs/a\"}},\"$ref\":\"#/$defs/a\"}"));
        assertThrows(IllegalStateException.class, () -> engine.isValid(schema, json("1")));
    }

    @Test
    void formatAssertionsCanBeDisabled() throws IOException {
        JsonSchemaEngine annotationOnly = new JsonSchemaEngine(MAPPER, SchemaRetriever.NONE, Dialect.DRAFT_2020_12, null, false);
        JsonNode schema = json("{\"format\":\"email\"}");
        assertTrue(annotationOnly.isValid(annotationOnly.compile(schema), json("\"not an email\"")));
        assertFalse(engine.isValid(engine.compile(schema), json("\"not an email\"")));
    }

    @Test
    void compiledSchemasCanBeUsedConcurrently() throws Exception {
        CompiledJsonSchema schema = engine.compile(json("""
            {"type":"object","properties":{"n":{"type":"integer","multipleOf":2}},
             "unevaluatedProperties":false,"$defs":{"x":{"$dynamicAnchor":"x"}}}
            """));
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                int n = i;
                results.add(executor.submit(() -> engine.isValid(schema, json("{\"n\":" + n + "}")) == (n % 2 == 0)
                    && !engine.isValid(schema, json("{\"n\":2,\"x\":1}"))));
            }
            for (Future<Boolean> result : results) {
                assertTrue(result.get());
            }
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void patternsUseEcmaScriptSemanticsAndKeepJavaPropertyClasses() throws IOException {
        CompiledJsonSchema endAnchor = engine.compile(json("{\"pattern\":\"^abc$\"}"));
        assertTrue(engine.isValid(endAnchor, json("\"abc\"")));
        assertFalse(engine.isValid(endAnchor, json("\"abc\\n\"")));
        CompiledJsonSchema unicodeProperty = engine.compile(json("{\"pattern\":\"^\\\\p{Letter}+$\"}"));
        assertTrue(engine.isValid(unicodeProperty, json("\"héllo\"")));
        assertFalse(engine.isValid(unicodeProperty, json("\"h3llo\"")));
        CompiledJsonSchema javaProperty = engine.compile(json("{\"pattern\":\"^\\\\p{Alpha}+$\"}"));
        assertTrue(engine.isValid(javaProperty, json("\"abc\"")));
        assertFalse(engine.isValid(javaProperty, json("\"ab1\"")));
        CompiledJsonSchema whitespace = engine.compile(json("{\"pattern\":\"^\\\\s$\"}"));
        assertTrue(engine.isValid(whitespace, json("\"\\u00a0\"")));
    }

    @Test
    void multipleOfWithHugeExponentsIsCheap() throws IOException {
        CompiledJsonSchema schema = engine.compile(json("{\"multipleOf\":0.01}"));
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            assertTrue(engine.isValid(schema, JsonNode.createNumberNode(new BigDecimal("1e1000000"))));
            assertFalse(engine.isValid(schema, JsonNode.createNumberNode(new BigDecimal("1e-1000000"))));
            assertTrue(engine.isValid(schema, JsonNode.createNumberNode(new BigDecimal("123.45"))));
            assertFalse(engine.isValid(schema, JsonNode.createNumberNode(new BigDecimal("123.456"))));
        });
    }

    @Test
    void notANumberNeverSatisfiesNumericKeywords() throws IOException {
        JsonNode nan = JsonNode.createNumberNode(Double.NaN);
        assertFalse(engine.isValid(engine.compile(json("{\"minimum\":0}")), nan));
        assertFalse(engine.isValid(engine.compile(json("{\"maximum\":100}")), nan));
        assertFalse(engine.isValid(engine.compile(json("{\"enum\":[1, 2]}")), nan));
        assertTrue(engine.isValid(engine.compile(json("{\"uniqueItems\":true}")), JsonNode.createArrayNode(List.of(nan, JsonNode.createNumberNode(Double.NaN)))));
    }

    @Test
    void uniqueItemsHashesLargeArrays() throws IOException {
        CompiledJsonSchema schema = engine.compile(json("{\"uniqueItems\":true}"));
        List<JsonNode> items = new ArrayList<>();
        for (int i = 0; i < 50_000; i++) {
            items.add(JsonNode.createObjectNode(Map.of("id", JsonNode.createNumberNode(i))));
        }
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertTrue(engine.isValid(schema, JsonNode.createArrayNode(items))));
        List<JsonNode> duplicates = new ArrayList<>(items);
        duplicates.add(JsonNode.createObjectNode(Map.of("id", JsonNode.createNumberNode(42.0))));
        assertFalse(engine.isValid(schema, JsonNode.createArrayNode(duplicates)));
    }

    @Test
    void countsMustBeNonNegativeIntegers() {
        assertThrows(IllegalArgumentException.class, () -> engine.compile(json("{\"maxItems\":-1}")));
        assertThrows(IllegalArgumentException.class, () -> engine.compile(json("{\"minLength\":1.5}")));
        assertDoesNotThrow(() -> engine.compile(json("{\"maxLength\":2.0}")));
    }

    @Test
    void relativeReferencesResolveAgainstClasspathUris() throws IOException {
        Map<String, String> documents = Map.of(
            "classpath:META-INF/schemas/sub/a.json", "{\"properties\":{\"b\":{\"$ref\":\"b.json\"},\"c\":{\"$ref\":\"../c.json\"}}}",
            "classpath:META-INF/schemas/sub/b.json", "{\"type\":\"integer\"}",
            "classpath:META-INF/schemas/c.json", "{\"type\":\"string\"}"
        );
        JsonSchemaEngine classpathEngine = new JsonSchemaEngine(MAPPER, uri -> documents.containsKey(uri) ? json(documents.get(uri)) : null,
            Dialect.DRAFT_2020_12, null);
        CompiledJsonSchema schema = classpathEngine.compile(json("{\"$ref\":\"classpath:META-INF/schemas/sub/a.json\"}"));
        assertTrue(classpathEngine.isValid(schema, json("{\"b\":1,\"c\":\"x\"}")));
        List<ValidationMessage> errors = classpathEngine.validate(schema, json("{\"b\":\"x\",\"c\":1}"));
        assertEquals(List.of("classpath:META-INF/schemas/sub/b.json#/type", "classpath:META-INF/schemas/c.json#/type"),
            errors.stream().map(ValidationMessage::getSchemaLocation).toList());
    }

    @Test
    void relativeReferencesResolveAgainstTheDefaultBaseUri() throws IOException {
        JsonSchemaEngine defaultEngine = new JsonSchemaEngine(MAPPER,
            uri -> uri.equals("mem:/item.json") ? json("{\"type\":\"integer\"}") : null, Dialect.DRAFT_2020_12, null);
        CompiledJsonSchema schema = defaultEngine.compile(json("{\"items\":{\"$ref\":\"item.json\"}}"));
        assertTrue(defaultEngine.isValid(schema, json("[1, 2]")));
        assertFalse(defaultEngine.isValid(schema, json("[1, \"2\"]")));
    }

    @Test
    void retrievalErrorsFailTheCompilationAndAreNotCached() throws IOException {
        AtomicInteger attempts = new AtomicInteger();
        JsonSchemaEngine flakyEngine = new JsonSchemaEngine(MAPPER, uri -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IOException("connection reset");
            }
            return json("{\"type\":\"integer\"}");
        }, Dialect.DRAFT_2020_12, null);
        JsonNode schema = json("{\"$ref\":\"https://example.com/integer.json\"}");
        UncheckedIOException e = assertThrows(UncheckedIOException.class, () -> flakyEngine.compile(schema));
        assertEquals("connection reset", e.getCause().getMessage());
        CompiledJsonSchema compiled = flakyEngine.compile(schema);
        assertTrue(flakyEngine.isValid(compiled, json("1")));
    }

    @Test
    void requiredVocabulariesMustBeSupported() throws IOException {
        JsonSchemaEngine metaEngine = new JsonSchemaEngine(MAPPER, uri -> switch (uri) {
            case "https://example.com/unknown-vocabulary" -> json("""
                {"$schema":"https://json-schema.org/draft/2020-12/schema","$id":"https://example.com/unknown-vocabulary",
                 "$vocabulary":{"https://json-schema.org/draft/2020-12/vocab/core":true,"https://example.com/vocab/custom":true}}
                """);
            case "https://example.com/self-describing" -> json("""
                {"$schema":"https://example.com/self-describing","$id":"https://example.com/self-describing",
                 "$vocabulary":{"https://json-schema.org/draft/2020-12/vocab/core":true,"https://json-schema.org/draft/2020-12/vocab/applicator":true}}
                """);
            default -> null;
        }, Dialect.DRAFT_2020_12, null);
        assertThrows(IllegalArgumentException.class, () -> metaEngine.compile(json("{\"$schema\":\"https://example.com/unknown-vocabulary\"}")));
        // no validation vocabulary: minimum is not asserted, properties is
        CompiledJsonSchema schema = metaEngine.compile(json("""
            {"$schema":"https://example.com/self-describing","properties":{"n":{"minimum":10}},"additionalProperties":false}
            """));
        assertTrue(metaEngine.isValid(schema, json("{\"n\":1}")));
        assertFalse(metaEngine.isValid(schema, json("{\"other\":1}")));
    }

    @Test
    void compilationsSharingRetrievedDocumentsCanRunConcurrentlyWithValidation() throws Exception {
        JsonSchemaEngine sharedEngine = new JsonSchemaEngine(MAPPER, uri -> uri.equals("https://example.com/shared.json")
            ? json("{\"$defs\":{\"positive\":{\"type\":\"integer\",\"minimum\":1}},\"$ref\":\"#/$defs/positive\"}")
            : null, Dialect.DRAFT_2020_12, null);
        CompiledJsonSchema first = sharedEngine.compile(json("{\"items\":{\"$ref\":\"https://example.com/shared.json\"}}"));
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                int n = i;
                results.add(executor.submit(() -> {
                    CompiledJsonSchema other = sharedEngine.compile(json("{\"properties\":{\"p" + n + "\":{\"$ref\":\"https://example.com/shared.json\"}}}"));
                    return sharedEngine.isValid(first, json("[1, 2, 3]")) && !sharedEngine.isValid(first, json("[0]"))
                        && !sharedEngine.isValid(other, json("{\"p" + n + "\":0}"));
                }));
            }
            for (Future<Boolean> result : results) {
                assertTrue(result.get());
            }
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void catastrophicBacktrackingIsBounded() throws IOException {
        // Java handles ^(a+)+$ well, but nested repetitions of .* still backtrack polynomially (minutes for this input)
        String input = "\"" + "a".repeat(60) + "!\"";
        CompiledJsonSchema pattern = engine.compile(json("{\"pattern\":\"^(.*a){20}$\"}"));
        CompiledJsonSchema patternProperties = engine.compile(json("{\"patternProperties\":{\"^(.*a){20}$\":true},\"additionalProperties\":false}"));
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            assertFalse(engine.isValid(pattern, json(input)));
            List<ValidationMessage> errors = engine.validate(pattern, json(input));
            assertEquals(List.of(": could not be matched against the regex pattern ^(.*a){20}$ within the evaluation limit"),
                errors.stream().map(ValidationMessage::getMessage).toList());
            assertFalse(engine.isValid(patternProperties, json("{" + input + ":1}")));
        });
        assertTrue(engine.isValid(pattern, json("\"" + "a".repeat(10_000) + "\"")));
    }

    @Test
    void dotMatchesAnyCharacterButLineTerminators() throws IOException {
        CompiledJsonSchema schema = engine.compile(json("{\"pattern\":\"^.$\"}"));
        assertTrue(engine.isValid(schema, JsonNode.createStringNode("\u0085")));
        assertFalse(engine.isValid(schema, JsonNode.createStringNode("\n")));
        assertFalse(engine.isValid(schema, JsonNode.createStringNode(" ")));
    }

    @Test
    void floatsAreComparedByTheirDecimalValue() throws IOException {
        JsonNode floatValue = JsonNode.createNumberNode(0.1f);
        assertTrue(engine.isValid(engine.compile(json("{\"const\":0.1}")), floatValue));
        assertTrue(engine.isValid(engine.compile(json("{\"maximum\":0.1}")), floatValue));
        assertTrue(engine.isValid(engine.compile(json("{\"minimum\":0.1}")), floatValue));
    }

    @Test
    void deeplyNestedInstances() throws Exception {
        CompiledJsonSchema schema = engine.compile(json("{\"properties\":{\"c\":{\"$ref\":\"#\"},\"n\":{\"type\":\"integer\"}}}"));
        assertTrue(engine.isValid(schema, nested(1000, JsonNode.createNumberNode(1))));
        List<ValidationMessage> errors = engine.validate(schema, nested(1000, JsonNode.createStringNode("x")));
        assertEquals(1, errors.size());
        assertEquals("/c".repeat(1000) + "/n", errors.get(0).getInstanceLocation());
        // on a thread with a small stack, a deeper instance fails with an exception rather than a StackOverflowError
        Throwable[] failure = new Throwable[1];
        Thread thread = new Thread(null, () -> {
            try {
                engine.isValid(schema, nested(20_000, JsonNode.createNumberNode(1)));
            } catch (Throwable t) {
                failure[0] = t;
            }
        }, "small-stack", 256 * 1024);
        thread.start();
        thread.join();
        assertTrue(failure[0] == null || failure[0] instanceof IllegalStateException, () -> String.valueOf(failure[0]));
    }

    private static JsonNode nested(int depth, JsonNode leaf) {
        JsonNode node = JsonNode.createObjectNode(Map.of("n", leaf));
        for (int i = 0; i < depth; i++) {
            node = JsonNode.createObjectNode(Map.of("c", node));
        }
        // the innermost "n" is depth levels deep
        return node;
    }

    @Test
    void numbersAreComparedByValue() throws IOException {
        assertTrue(engine.isValid(engine.compile(json("{\"const\":1}")), json("1.0")));
        assertTrue(engine.isValid(engine.compile(json("{\"type\":\"integer\"}")), json("1.0")));
        assertTrue(engine.isValid(engine.compile(json("{\"multipleOf\":0.01}")), json("19.99")));
        assertFalse(engine.isValid(engine.compile(json("{\"uniqueItems\":true}")), json("[1, 1.0]")));
        assertTrue(engine.isValid(engine.compile(json("{\"maximum\":9007199254740993}")), json("9007199254740993")));
        assertFalse(engine.isValid(engine.compile(json("{\"maximum\":9007199254740992}")), json("9007199254740993")));
    }
}
