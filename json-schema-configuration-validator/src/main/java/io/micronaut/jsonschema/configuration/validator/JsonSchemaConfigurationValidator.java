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

import io.micronaut.context.ApplicationContextConfiguration;
import io.micronaut.context.env.Environment;
import io.micronaut.jsonschema.configuration.validator.report.ConfigurationErrorReporter;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * CLI-oriented facade that constructs a classloader and environment and delegates to
 * {@link ConfigurationJsonSchemaValidator}.
 */
public final class JsonSchemaConfigurationValidator {
    private final List<URL> classpath;
    private final List<String> environments;
    private final ConfigurationJsonSchemaValidator validator;

    public JsonSchemaConfigurationValidator(
        @NonNull List<URL> classpath,
        @NonNull List<String> environments,
        @NonNull ConfigurationJsonSchemaValidator validator
    ) {
        this.classpath = List.copyOf(classpath);
        this.environments = List.copyOf(environments);
        this.validator = validator;
    }

    public static JsonSchemaConfigurationValidator forClasspath(
        @NonNull String classpath,
        @NonNull List<String> environments,
        @NonNull ConfigurationJsonSchemaValidator validator
    ) {
        return new JsonSchemaConfigurationValidator(parseClasspath(classpath), environments, validator);
    }

    @NonNull
    public Set<ConfigurationError> validate() throws IOException {
        try (URLClassLoader classLoader = new URLClassLoader(classpath.toArray(URL[]::new), ClassLoader.getSystemClassLoader())) {
            Environment environment = createEnvironment(classLoader, environments);
            try {
                return validator.validate(classLoader, environment);
            } finally {
                closeIfPossible(environment);
            }
        }
    }

    public Set<ConfigurationError> validateAndReport(ConfigurationErrorReporter reporter) throws IOException {
        Set<ConfigurationError> errors = validate();
        reporter.report(errors);
        return errors;
    }

    private static Environment createEnvironment(ClassLoader classLoader, List<String> environments) {
        ApplicationContextConfiguration configuration = new ApplicationContextConfiguration() {
            @Override
            public List<String> getEnvironments() {
                return environments;
            }

            @Override
            public ClassLoader getClassLoader() {
                return classLoader;
            }

            @Override
            public Optional<Boolean> getDeduceEnvironments() {
                return Optional.of(false);
            }
        };

        return Environment.create(configuration).start();
    }

    private static void closeIfPossible(Object maybeCloseable) {
        if (maybeCloseable instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    private static List<URL> parseClasspath(String classpath) {
        String[] parts = classpath.split(java.util.regex.Pattern.quote(File.pathSeparator));
        List<URL> urls = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                urls.add(Path.of(trimmed).toUri().toURL());
            } catch (MalformedURLException e) {
                // ignore invalid classpath entries
            }
        }
        return urls;
    }
}
