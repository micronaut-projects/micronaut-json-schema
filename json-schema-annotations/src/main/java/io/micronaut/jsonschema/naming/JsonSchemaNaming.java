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
package io.micronaut.jsonschema.naming;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.net.URI;

/**
 * The naming of the JSON schemas generated for {@link io.micronaut.jsonschema.JsonSchema} types.
 *
 * <p>The annotation processor names the generated {@code META-INF/<outputLocation>/<name>.schema.json} files and
 * their {@code $id} with these methods, and the runtime uses the same methods to find the schema of a type.</p>
 *
 * @since 2.3.2
 */
@Internal
public final class JsonSchemaNaming {

    /**
     * The suffix of the generated schema files.
     */
    public static final String SUFFIX = ".schema.json";

    /**
     * The scheme of the default base URI, which NetworkNT and other validators resolve from the classpath.
     */
    public static final String CLASSPATH_SCHEME = "classpath:";

    /**
     * The folder of the generated schemas on the classpath.
     */
    public static final String META_INF = "META-INF";

    private static final String SLASH = "/";
    private static final String SCHEME_SEPARATOR = "://";

    private JsonSchemaNaming() {
    }

    /**
     * The default base URI of the schemas: {@code classpath:META-INF/<outputLocation>}, so that the {@code $ref}s
     * between generated schemas resolve from the classpath.
     *
     * @param outputLocation The location of the schemas inside the {@code META-INF/} directory
     * @return The base URI, without a trailing slash
     */
    public static String defaultBaseUri(String outputLocation) {
        return CLASSPATH_SCHEME + META_INF + SLASH + outputLocation;
    }

    /**
     * The title of a schema.
     *
     * @param title The title of the {@link io.micronaut.jsonschema.JsonSchema} annotation, if any
     * @param simpleName The simple name of the type, where nested types are separated by {@code $} or {@code .},
     *                   for example {@code Outer$Inner}
     * @return The title
     */
    public static String title(@Nullable String title, String simpleName) {
        if (title != null && !title.isEmpty()) {
            return title;
        }
        return simpleName.replace('$', '.');
    }

    /**
     * The simple name of a class including the names of the classes it is nested in, for example
     * {@code Outer$Inner}, as the annotation processor sees it.
     *
     * @param type The type
     * @return The simple name
     */
    public static String simpleName(Class<?> type) {
        String name = type.getName();
        int lastDot = name.lastIndexOf('.');
        return lastDot < 0 ? name : name.substring(lastDot + 1);
    }

    /**
     * The URI of a schema, relative to the base URI unless the {@code uri} member of the annotation is absolute.
     *
     * @param title The title of the schema, see {@link #title(String, String)}
     * @param uri The URI of the {@link io.micronaut.jsonschema.JsonSchema} annotation, if any
     * @return The URI
     */
    public static String uri(String title, @Nullable String uri) {
        if (uri != null && !uri.isEmpty()) {
            return isAbsolute(uri) ? uri : uri + SUFFIX;
        }
        return SLASH + camelCaseToKebabCase(title) + SUFFIX;
    }

    /**
     * The {@code $id} of a schema.
     *
     * @param uri The URI of the schema, see {@link #uri(String, String)}
     * @param baseUri The base URI, if any
     * @return The {@code $id}
     */
    public static String id(String uri, @Nullable String baseUri) {
        if (isAbsolute(uri) || baseUri == null) {
            return uri;
        }
        return baseUri + uri;
    }

    /**
     * Whether a schema URI is absolute.
     *
     * @param uri The URI
     * @return Whether it is absolute
     */
    public static boolean isAbsolute(String uri) {
        return uri.contains(SCHEME_SEPARATOR);
    }

    /**
     * The path of a schema file inside the output location.
     *
     * @param id The {@code $id} or the URI of the schema
     * @param baseUri The base URI, if known
     * @param outputLocation The location of the schemas inside the {@code META-INF/} directory
     * @return The path, for example {@code llama.schema.json}
     */
    public static String fileName(String id, @Nullable String baseUri, String outputLocation) {
        String path = id;
        if (baseUri != null && path.startsWith(baseUri)) {
            path = path.substring(baseUri.length());
        } else if (isAbsolute(path)) {
            path = URI.create(path).getPath().substring(1);
            if (path.startsWith(outputLocation)) {
                path = path.substring(outputLocation.length());
            }
        }
        if (path.startsWith(SLASH)) {
            path = path.substring(1);
        }
        return path;
    }

    /**
     * Convert from camel case to kebab case, skipping the dots between the names of nested types.
     *
     * @param value The value
     * @return The converted value
     */
    public static String camelCaseToKebabCase(String value) {
        StringBuilder result = new StringBuilder();
        boolean prevNewWord = true;
        for (int i = 0; i < value.length(); ++i) {
            if (value.charAt(i) == '.') {
                continue;
            }
            if (Character.isUpperCase(value.charAt(i))) {
                if (!prevNewWord) {
                    result.append("-");
                }
                prevNewWord = true;
            } else {
                prevNewWord = false;
            }
            result.append(Character.toLowerCase(value.charAt(i)));
        }
        return result.toString();
    }
}
