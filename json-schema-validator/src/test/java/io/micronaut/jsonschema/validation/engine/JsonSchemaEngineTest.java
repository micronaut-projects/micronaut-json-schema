package io.micronaut.jsonschema.validation.engine;

import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.jsonschema.validation.ValidationMessage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void numbersAreComparedByValue() throws IOException {
        assertTrue(engine.isValid(engine.compile(json("{\"const\":1}")), json("1.0")));
        assertTrue(engine.isValid(engine.compile(json("{\"type\":\"integer\"}")), json("1.0")));
        assertTrue(engine.isValid(engine.compile(json("{\"multipleOf\":0.01}")), json("19.99")));
        assertFalse(engine.isValid(engine.compile(json("{\"uniqueItems\":true}")), json("[1, 1.0]")));
        assertTrue(engine.isValid(engine.compile(json("{\"maximum\":9007199254740993}")), json("9007199254740993")));
        assertFalse(engine.isValid(engine.compile(json("{\"maximum\":9007199254740992}")), json("9007199254740993")));
    }
}
