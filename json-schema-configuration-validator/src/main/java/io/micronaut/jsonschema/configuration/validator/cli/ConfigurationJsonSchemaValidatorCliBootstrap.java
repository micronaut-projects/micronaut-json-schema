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

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Minimal-classpath entry point that bootstraps {@link ConfigurationJsonSchemaValidatorCli} using
 * a dedicated classloader.
 * <p>
 * This is intended to allow invoking the CLI with a classpath that initially contains only the
 * {@code micronaut-json-schema-configuration-validator} module, while providing the full runtime
 * classpath (including transitive dependencies) via {@code --classpath}.
 */
@Internal
public final class ConfigurationJsonSchemaValidatorCliBootstrap {
    private static final System.Logger LOG = System.getLogger(ConfigurationJsonSchemaValidatorCliBootstrap.class.getName());

    private ConfigurationJsonSchemaValidatorCliBootstrap() {
    }

    /**
     * Bootstrap entry point.
     *
     * @param args CLI args (must include {@code --classpath})
     */
    public static void main(String[] args) throws Exception {
        String classpath = parseClasspathArg(args);
        if (classpath == null || classpath.isBlank()) {
            System.err.println("Missing required argument: --classpath");
            return;
        }

        List<URL> urls = new ArrayList<>();
        addSelfToClasspath(urls);
        urls.addAll(parseClasspath(classpath));

        try (URLClassLoader cl = new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            Thread current = Thread.currentThread();
            ClassLoader previous = current.getContextClassLoader();
            current.setContextClassLoader(cl);
            try {
                Class<?> cli = Class.forName(ConfigurationJsonSchemaValidatorCli.class.getName(), true, cl);
                Method main = cli.getMethod("main", String[].class);
                main.invoke(null, (Object) args);
            } finally {
                current.setContextClassLoader(previous);
            }
        }
    }

    private static void addSelfToClasspath(List<URL> urls) {
        try {
            CodeSource codeSource = ConfigurationJsonSchemaValidatorCliBootstrap.class.getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                return;
            }
            URL location = codeSource.getLocation();
            if (location != null) {
                urls.add(location);
            }
        } catch (Exception e) {
            LOG.log(System.Logger.Level.DEBUG, "Failed to add bootstrap location to classpath", e);
        }
    }

    private static List<URL> parseClasspath(String classpath) {
        String[] parts = classpath.split(Pattern.quote(File.pathSeparator));
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
            } catch (Exception ignored) {
                // ignore invalid entries
            }
        }
        return urls;
    }

    @Nullable
    private static String parseClasspathArg(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg == null) {
                continue;
            }

            String equalsValue = parseEqualsSyntax(arg);
            if (equalsValue != null) {
                return equalsValue;
            }

            if ("--classpath".equals(arg)) {
                return parseNextValue(args, i);
            }
        }
        return null;
    }

    @Nullable
    private static String parseEqualsSyntax(String arg) {
        int eq = arg.indexOf('=');
        if (eq < 0) {
            return null;
        }
        String key = arg.substring(0, eq);
        if (!"--classpath".equals(key)) {
            return null;
        }
        return arg.substring(eq + 1);
    }

    @Nullable
    private static String parseNextValue(String[] args, int index) {
        if (index + 1 >= args.length) {
            return null;
        }
        String next = args[index + 1];
        if (next == null) {
            return "";
        }
        if (next.startsWith("-")) {
            return null;
        }
        return next;
    }
}
