/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertyEntry;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.jsonschema.configuration.validator.model.JsonSchema;
import io.micronaut.jsonschema.configuration.validator.model.JsonSchemaProperty;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Internal
final class SchemaContext {
    private static final Argument<JsonSchemaProperty> JSON_SCHEMA_PROPERTY_ARGUMENT = Argument.of(JsonSchemaProperty.class);

    private final JsonSchema root;
    private final ClassLoader classLoader;
    private final Environment environment;
    private final JsonMapper jsonMapper;
    private final boolean failOnNotPresent;
    private final RefResolver refResolver;

    SchemaContext(
        JsonSchema root,
        ClassLoader classLoader,
        Environment environment,
        JsonMapper jsonMapper,
        boolean failOnNotPresent
    ) {
        this.root = Objects.requireNonNull(root, "root");
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
        this.failOnNotPresent = failOnNotPresent;
        this.refResolver = new RefResolver();
    }

    JsonSchema root() {
        return root;
    }

    ClassLoader classLoader() {
        return classLoader;
    }

    Environment environment() {
        return environment;
    }

    JsonMapper jsonMapper() {
        return jsonMapper;
    }

    boolean failOnNotPresent() {
        return failOnNotPresent;
    }

    RefResolver refResolver() {
        return refResolver;
    }

    ConfigurationError error(String property, String message) {
        return create(ConfigurationError.Type.ERROR, property, message);
    }

    ConfigurationError warning(String property, String message) {
        return create(ConfigurationError.Type.WARNING, property, message);
    }

    private ConfigurationError create(ConfigurationError.Type type, String property, String message) {
        Optional<PropertyEntry> entry = environment.getPropertyEntry(property);
        if (entry.isPresent()) {
            PropertyEntry propertyEntry = entry.get();
            String originLocation = propertyEntry.origin() != null ? propertyEntry.origin().location() : null;
            OriginSnippet snippet = OriginSnippetResolver.resolve(
                classLoader,
                originLocation,
                propertyEntry.raw() != null ? propertyEntry.raw() : property,
                propertyEntry.value()
            );
            return new ConfigurationError(
                property,
                type,
                message,
                originLocation,
                propertyEntry.raw(),
                propertyEntry.value(),
                snippet.lineNumber(),
                snippet.snippet(),
                snippet.language()
            );
        }
        OriginSnippet snippet = OriginSnippetResolver.resolve(classLoader, null, property, null);
        return new ConfigurationError(property, type, message, null, null, null, snippet.lineNumber(), snippet.snippet(), snippet.language());
    }

    String resolvedPropertyName(String computedPropertyName, @Nullable String micronautPath, @Nullable String wildcardReplacement) {
        if (micronautPath != null) {
            if (wildcardReplacement != null) {
                return micronautPath.replace("*", wildcardReplacement);
            }
            return micronautPath;
        }
        return computedPropertyName;
    }

    private record OriginSnippet(int lineNumber, @Nullable String snippet, @Nullable String language) {
    }

    private static final class OriginSnippetResolver {
        private static final Pattern PROPERTIES_LINE = Pattern.compile("^\\s*([^#;!][^=:\\s]*?)\\s*[=:].*$");

        private OriginSnippetResolver() {
        }

        static OriginSnippet resolve(
            ClassLoader classLoader,
            @Nullable String originLocation,
            String rawPropertyName,
            @Nullable Object rawValue
        ) {
            if (originLocation == null || originLocation.isBlank()) {
                return new OriginSnippet(-1, inferredSnippet(rawPropertyName, rawValue), null);
            }

            ResolvedText resolved = readOriginText(classLoader, originLocation);
            if (resolved == null || resolved.text().isBlank()) {
                return new OriginSnippet(-1, inferredSnippet(rawPropertyName, rawValue), languageFor(originLocation));
            }

            List<String> lines = splitLines(resolved.text());
            int idx = findLineIndex(lines, rawPropertyName);
            if (idx < 0) {
                // Try best-effort match on the last segment (common when YAML is used).
                int dot = rawPropertyName.lastIndexOf('.');
                if (dot > -1 && dot + 1 < rawPropertyName.length()) {
                    idx = findLineIndex(lines, rawPropertyName.substring(dot + 1));
                }
            }

            if (idx < 0) {
                return new OriginSnippet(-1, inferredSnippet(rawPropertyName, rawValue), languageFor(originLocation));
            }

            String snippet = sliceSnippet(lines, idx, 2);
            return new OriginSnippet(idx + 1, snippet, languageFor(originLocation));
        }

        @Nullable
        private static ResolvedText readOriginText(ClassLoader classLoader, String originLocation) {
            try {
                if (originLocation.startsWith("file:")) {
                    URI uri = URI.create(originLocation);
                    Path path = Path.of(uri);
                    if (!Files.exists(path)) {
                        return null;
                    }
                    return new ResolvedText(Files.readString(path, StandardCharsets.UTF_8));
                }

                String location = originLocation;
                if (location.startsWith("classpath:")) {
                    location = location.substring("classpath:".length());
                    if (location.startsWith("/")) {
                        location = location.substring(1);
                    }
                }

                try (InputStream is = classLoader.getResourceAsStream(location)) {
                    if (is == null) {
                        return null;
                    }
                    return new ResolvedText(new String(is.readAllBytes(), StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                return null;
            }
        }

        private static List<String> splitLines(String text) {
            String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
            String[] parts = normalized.split("\n", -1);
            List<String> lines = new ArrayList<>(parts.length);
            for (String p : parts) {
                lines.add(p);
            }
            return lines;
        }

        private static int findLineIndex(List<String> lines, String needle) {
            if (needle.isBlank()) {
                return -1;
            }

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line == null) {
                    continue;
                }
                if (matchesPropertiesKey(line, needle)) {
                    return i;
                }
                if (line.contains(needle)) {
                    return i;
                }
            }
            return -1;
        }

        private static boolean matchesPropertiesKey(String line, String key) {
            Matcher m = PROPERTIES_LINE.matcher(line);
            if (!m.matches()) {
                return false;
            }
            return Objects.equals(m.group(1), key);
        }

        private static String sliceSnippet(List<String> lines, int lineIndex, int contextLines) {
            int start = Math.max(0, lineIndex - contextLines);
            int end = Math.min(lines.size() - 1, lineIndex + contextLines);

            StringBuilder sb = new StringBuilder(256);
            for (int i = start; i <= end; i++) {
                if (i > start) {
                    sb.append('\n');
                }
                sb.append(lines.get(i));
            }
            return sb.toString();
        }

        private static String inferredSnippet(String rawPropertyName, @Nullable Object rawValue) {
            String value = rawValue == null ? "" : String.valueOf(rawValue);
            return rawPropertyName + "=" + value;
        }

        @Nullable
        private static String languageFor(String originLocation) {
            String lower = originLocation.toLowerCase(java.util.Locale.ENGLISH);
            if (lower.endsWith(".properties")) {
                return "properties";
            }
            if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
                return "yaml";
            }
            if (lower.endsWith(".toml")) {
                return "toml";
            }
            if (lower.endsWith(".json")) {
                return "json";
            }
            return null;
        }

        private record ResolvedText(String text) {
        }
    }

    @Internal
    final class RefResolver {
        @Nullable
        JsonSchemaProperty resolveAdditionalPropertiesSchema(JsonSchemaProperty schema) {
            Object additionalProperties = schema.additionalProperties();
            if (additionalProperties == null) {
                return null;
            }
            if (additionalProperties instanceof Boolean) {
                return null;
            }
            if (additionalProperties instanceof JsonSchemaProperty prop) {
                return resolveRef(prop);
            }
            if (additionalProperties instanceof Map) {
                JsonSchemaProperty parsed = parseProperty(additionalProperties);
                return parsed != null ? resolveRef(parsed) : null;
            }
            return null;
        }

        @Nullable
        JsonSchemaProperty resolveRef(JsonSchemaProperty schema) {
            if (schema.ref() == null) {
                return schema;
            }
            return resolveRef(schema.ref());
        }

        @Nullable
        JsonSchemaProperty resolveRef(String ref) {
            if (!ref.startsWith("#/")) {
                return null;
            }
            List<String> tokens = JsonPointer.parse(ref);
            Object current = root;
            for (String token : tokens) {
                if (current instanceof JsonSchema jsonSchema) {
                    current = resolveFromRoot(jsonSchema, token);
                } else if (current instanceof JsonSchemaProperty property) {
                    current = resolveFromProperty(property, token);
                } else if (current instanceof Map<?, ?> map) {
                    current = map.get(token);
                } else {
                    return null;
                }
                if (current == null) {
                    return null;
                }
            }
            if (current instanceof JsonSchemaProperty prop) {
                return prop.ref() != null ? resolveRef(prop.ref()) : prop;
            }
            return null;
        }

        private static Object resolveFromRoot(JsonSchema schema, String token) {
            if ("$defs".equals(token)) {
                return schema.defs();
            }
            if ("properties".equals(token)) {
                return schema.properties();
            }
            return null;
        }

        private static Object resolveFromProperty(JsonSchemaProperty schema, String token) {
            if ("$defs".equals(token)) {
                return schema.defs();
            }
            if ("properties".equals(token)) {
                return schema.properties();
            }
            return null;
        }

        @Nullable
        private JsonSchemaProperty parseProperty(Object mapLike) {
            try {
                String json = jsonMapper.writeValueAsString(mapLike);
                return jsonMapper.readValue(json, JSON_SCHEMA_PROPERTY_ARGUMENT);
            } catch (IOException e) {
                return null;
            }
        }
    }
}
