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
import io.micronaut.context.ConfigurableApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.jsonschema.configuration.validator.DefaultDependencyInjectionValidator;
import io.micronaut.jsonschema.configuration.validator.DependencyInjectionError;
import io.micronaut.jsonschema.configuration.validator.DependencyInjectionValidator;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * CLI-oriented dependency-injection validator that builds an application context from a supplied
 * runtime classpath and environment set, then validates bean wiring metadata.
 */
@Internal
public final class DependencyInjectionConfigurationValidator {
    private static final System.Logger LOG = System.getLogger(DependencyInjectionConfigurationValidator.class.getName());

    private final List<URL> classpath;
    private final List<String> environments;
    private final boolean deduceEnvironment;
    private final DependencyInjectionValidator validator;

    /**
     * @param classpath Runtime classpath URLs used to create the validation classloader
     * @param environments Environment names passed to {@link ApplicationContext#builder(String...)}
     * @param deduceEnvironment Whether Micronaut should deduce environments
     * @param validator The dependency-injection validator implementation
     */
    public DependencyInjectionConfigurationValidator(
        List<URL> classpath,
        List<String> environments,
        boolean deduceEnvironment,
        DependencyInjectionValidator validator
    ) {
        this.classpath = List.copyOf(classpath);
        this.environments = List.copyOf(environments);
        this.deduceEnvironment = deduceEnvironment;
        this.validator = validator;
    }

    /**
     * Creates a validator from a platform classpath string.
     *
     * @param classpath The classpath string using the platform path separator
     * @param environments The environment names to activate
     * @param deduceEnvironment Whether environment deduction is enabled
     * @return A configured validator instance
     */
    public static DependencyInjectionConfigurationValidator forClasspath(
        String classpath,
        List<String> environments,
        boolean deduceEnvironment
    ) {
        return forClasspath(classpath, environments, deduceEnvironment, List.of());
    }

    public static DependencyInjectionConfigurationValidator forClasspath(
        String classpath,
        List<String> environments,
        boolean deduceEnvironment,
        List<String> suppressedInjectionErrors
    ) {
        return new DependencyInjectionConfigurationValidator(
            parseClasspath(classpath),
            environments,
            deduceEnvironment,
            new DefaultDependencyInjectionValidator(suppressedInjectionErrors)
        );
    }

    /**
     * Executes dependency-injection validation for the configured classpath and environments.
     *
     * @return The set of detected dependency-injection errors
     */
    public Set<DependencyInjectionError> validate() {
        try (URLClassLoader classLoader = new URLClassLoader(classpath.toArray(URL[]::new), JsonSchemaConfigurationValidator.class.getClassLoader())) {
            try (ApplicationContext context = ApplicationContext.builder(environments.toArray(String[]::new))
                .classLoader(classLoader)
                .deduceEnvironment(deduceEnvironment)
                .build()) {
                ConfigurableApplicationContext configurableContext = (ConfigurableApplicationContext) context;
                try (Environment ignore = configurableContext.getEnvironment().start()) {
                    configurableContext.configure();
                    return validator.validate(configurableContext);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Dependency injection validation failed", e);
        }
    }

    private static List<URL> parseClasspath(String classpath) {
        String[] parts = classpath.split(Pattern.quote(File.pathSeparator));
        List<URL> urls = new ArrayList<>(parts.length);
        for (String part : parts) {
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
