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
package io.micronaut.jsonschema.configuration.validator.cli;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextConfiguration;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.jsonschema.configuration.validator.ConfigurationError;
import io.micronaut.jsonschema.configuration.validator.ConfigurationJsonSchemaValidator;
import io.micronaut.jsonschema.configuration.validator.report.ConfigurationErrorReporter;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * CLI-oriented facade that constructs a classloader and environment and delegates to
 * {@link ConfigurationJsonSchemaValidator}.
 */
@Internal
public final class JsonSchemaConfigurationValidator {
    private static final System.Logger LOG = System.getLogger(JsonSchemaConfigurationValidator.class.getName());

    private final List<URL> classpath;
    private final List<String> environments;
    private final boolean deduceEnvironment;
    private final ConfigurationJsonSchemaValidator validator;

    /**
     * @param classpath The classpath to use to discover configuration schemas
     * @param environments The environments to enable
     * @param deduceEnvironment Whether to deduce environments
     * @param validator The validator
     */
    public JsonSchemaConfigurationValidator(
        List<URL> classpath,
        List<String> environments,
        boolean deduceEnvironment,
        ConfigurationJsonSchemaValidator validator
    ) {
        this.classpath = List.copyOf(classpath);
        this.environments = List.copyOf(environments);
        this.deduceEnvironment = deduceEnvironment;
        this.validator = validator;
    }

    public static JsonSchemaConfigurationValidator forClasspath(
        String classpath,
        List<String> environments,
        boolean deduceEnvironment,
        ConfigurationJsonSchemaValidator validator
    ) {
        return new JsonSchemaConfigurationValidator(parseClasspath(classpath), environments, deduceEnvironment, validator);
    }

    public static JsonSchemaConfigurationValidator forClasspath(
        String classpath,
        List<String> environments,
        ConfigurationJsonSchemaValidator validator
    ) {
        return forClasspath(classpath, environments, false, validator);
    }

    /**
     * Validate the configuration found on the configured classpath.
     *
     * @return A set of validation errors/warnings (empty if valid)
     * @throws IOException If validation fails
     */
    public Set<ConfigurationError> validate() throws IOException {
        try (URLClassLoader classLoader = new URLClassLoader(classpath.toArray(URL[]::new), JsonSchemaConfigurationValidator.class.getClassLoader());
             Environment environment = Environment.create(createEnvironmentConfiguration(classLoader, environments, deduceEnvironment)).start()) {
            try {
                return validator.validate(classLoader, environment);
            } finally {
                environment.stop();
            }
        }
    }

    /**
     * Validate and report using the given reporter.
     *
     * @param reporter The reporter
     * @return The validation errors/warnings
     * @throws IOException If validation or reporting fails
     */
    public Set<ConfigurationError> validateAndReport(ConfigurationErrorReporter reporter) throws IOException {
        Set<ConfigurationError> errors = validate();
        reporter.report(errors);
        return errors;
    }

    private static ApplicationContextConfiguration createEnvironmentConfiguration(ClassLoader classLoader, List<String> environments, boolean deduceEnvironment) {
        // Use ApplicationContext.builder(...) so ApplicationContextConfigurer instances are applied.
        // The builder implementation also implements ApplicationContextConfiguration.
        return (ApplicationContextConfiguration) ApplicationContext.builder(environments.toArray(String[]::new))
            .classLoader(classLoader)
            .deduceEnvironment(deduceEnvironment);
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
                LOG.log(System.Logger.Level.DEBUG, "Invalid classpath entry: " + trimmed, e);
            }
        }
        return urls;
    }
}
