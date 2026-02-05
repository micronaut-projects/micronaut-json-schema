/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.jsonschema.utils;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.IOUtils;
import io.micronaut.core.io.Readable;
import io.micronaut.core.io.ResourceLoader;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal utilities for resolving JSON schema resources on the classpath.
 */
@Internal
public final class JsonSchemaResourceUtils {

    public static final String CLASSPATH_PREFIX = "classpath:";
    public static final String META_INF = "META-INF";
    public static final String SLASH = "/";

    private static final Logger LOG = LoggerFactory.getLogger(JsonSchemaResourceUtils.class);
    private static final String MICRONAUT_CONFIGURATION_SCHEMAS = "micronaut-configuration-schemas";

    private JsonSchemaResourceUtils() {
    }

    /**
     * @param jsonSchemaConfiguration The JSON schema configuration
     * @return {@code META-INF/<outputLocation>/}
     */
    @NonNull
    public static String generatedSchemasFolder(@NonNull JsonSchemaConfiguration jsonSchemaConfiguration) {
        return META_INF + SLASH + jsonSchemaConfiguration.getOutputLocation() + SLASH;
    }

    /**
     * @return {@code META-INF/micronaut-configuration-schemas/}
     */
    @NonNull
    public static String configurationSchemasFolder() {
        return META_INF + SLASH + MICRONAUT_CONFIGURATION_SCHEMAS + SLASH;
    }

    /**
     * Resolve and normalize a relative schema path within the given folder.
     *
     * @param schemaFolder The schema folder (must end with a slash)
     * @param relativePath The relative path
     * @param uriForError The URI (string form) used for error messages
     * @param configuredFolderForError The configured folder (string form) used for error messages
     * @return The normalized file path
     */
    @NonNull
    public static String resolvePathWithinFolder(
        @NonNull String schemaFolder,
        @NonNull String relativePath,
        @NonNull String uriForError,
        @NonNull String configuredFolderForError
    ) {
        String filePath = Path.of(schemaFolder + relativePath).normalize().toString();
        if (!filePath.startsWith(schemaFolder)) {
            throw new IllegalArgumentException(
                "Schema for URI " + uriForError + " is not inside the required folder " + configuredFolderForError + " at path: " + relativePath
            );
        }
        return filePath;
    }

    /**
     * Resolve the available schemas and return a map of schema names to resource.
     * @param resourceLoader The resource loader
     * @param classLoader The classloader
     * @param schemaFolder The schema folder
     * @return The schemas
     */
    static @NonNull Map<String, io.micronaut.core.io.Readable> resolveSchemas(ResourceLoader resourceLoader, ClassLoader classLoader, String schemaFolder) {
        List<URI> roots = findRoots(classLoader, schemaFolder);
        if (roots.isEmpty()) {
            return Map.of();
        }

        Map<String, Readable> schemas = new LinkedHashMap<>();
        List<Closeable> toClose = new ArrayList<>();
        try {
            for (URI uri : roots) {
                collectFromRoot(resourceLoader, schemaFolder, uri, toClose, schemas);
            }
        } finally {
            closeAll(toClose);
        }
        return Collections.unmodifiableMap(schemas);
    }

    @NonNull
    private static List<URI> findRoots(@NonNull ClassLoader classLoader, @NonNull String schemaFolder) {
        try {
            return IOUtils.getResources(classLoader, schemaFolder);
        } catch (IOException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Error scanning JSON schema resources under {}", schemaFolder, e);
            }
            return List.of();
        }
    }

    private static void collectFromRoot(
        @NonNull ResourceLoader resourceLoader,
        @NonNull String schemaFolder,
        @NonNull URI uri,
        @NonNull List<Closeable> toClose,
        @NonNull Map<String, Readable> schemas
    ) {
        try {
            Path basePath = IOUtils.resolvePath(uri, schemaFolder, toClose);
            if (basePath == null) {
                return;
            }
            Files.walkFileTree(basePath, new SchemaCollectingVisitor(resourceLoader, schemaFolder, basePath, schemas));
        } catch (IOException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Error scanning JSON schema resources", e);
            }
        }
    }

    private static void closeAll(@NonNull List<Closeable> toClose) {
        for (Closeable closeable : toClose) {
            try {
                closeable.close();
            } catch (IOException e) {
                // Ignore close failures
            }
        }
    }

    private static final class SchemaCollectingVisitor extends SimpleFileVisitor<Path> {
        private final ResourceLoader resourceLoader;
        private final String schemaFolder;
        private final Path basePath;
        private final Map<String, Readable> schemas;

        private SchemaCollectingVisitor(ResourceLoader resourceLoader,
                                       String schemaFolder,
                                       Path basePath,
                                       Map<String, Readable> schemas) {
            this.resourceLoader = resourceLoader;
            this.schemaFolder = schemaFolder;
            this.basePath = basePath;
            this.schemas = schemas;
        }

        @Override
        public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (!shouldInclude(file)) {
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            String relativePath = basePath.relativize(file).toString().replace('\\', '/');
            String resourcePath = CLASSPATH_PREFIX + schemaFolder + relativePath;
            schemas.putIfAbsent(relativePath, new LazyJsonSchemaReadable(resourceLoader, relativePath, resourcePath));
            return java.nio.file.FileVisitResult.CONTINUE;
        }

        private static boolean shouldInclude(Path file) throws IOException {
            if (Files.isHidden(file)) {
                return false;
            }
            Path fileName = file.getFileName();
            if (fileName == null) {
                return false;
            }
            String name = fileName.toString();
            if (name.startsWith(".")) {
                return false;
            }
            return file.toString().endsWith(".json");
        }
    }
}
