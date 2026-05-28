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
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Minimal-classpath entry point that forwards execution to {@link ConfigurationJsonSchemaValidatorCli}
 * in a child JVM with the requested runtime classpath.
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
            System.exit(2);
            return;
        }

        String normalizedClasspath = normalizeClasspath(classpath);
        Process process = new ProcessBuilder(childCommand(args, normalizedClasspath))
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();
        System.exit(process.waitFor());
    }

    private static List<String> childCommand(String[] args, String classpath) {
        List<String> command = new ArrayList<>(args.length + 4);
        command.add(javaBinary());
        command.add("-cp");
        command.add(childClasspath(classpath));
        command.add(ConfigurationJsonSchemaValidatorCli.class.getName());
        java.util.Collections.addAll(command, rewriteClasspathArg(args, classpath));
        return command;
    }

    private static String javaBinary() {
        String executable = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).contains("win")
            ? "java.exe"
            : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private static String childClasspath(String classpath) {
        Set<String> entries = new LinkedHashSet<>();
        addSelfToClasspath(entries);
        entries.addAll(parseClasspath(classpath));
        return String.join(File.pathSeparator, entries);
    }

    private static void addSelfToClasspath(Set<String> entries) {
        try {
            CodeSource codeSource = ConfigurationJsonSchemaValidatorCliBootstrap.class.getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                return;
            }
            java.net.URL location = codeSource.getLocation();
            if (location != null) {
                entries.add(Path.of(location.toURI()).toString());
            }
        } catch (Exception e) {
            LOG.log(System.Logger.Level.DEBUG, "Failed to add bootstrap location to classpath", e);
        }
    }

    private static List<String> parseClasspath(String classpath) {
        String[] parts = classpath.split(Pattern.quote(File.pathSeparator));
        List<String> entries = new ArrayList<>(parts.length);
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            entries.add(trimmed);
        }
        return entries;
    }

    private static String normalizeClasspath(String classpath) {
        List<String> normalizedEntries = parseClasspath(classpath).stream()
            .map(ConfigurationJsonSchemaValidatorCliBootstrap::normalizePath)
            .toList();
        return String.join(File.pathSeparator, normalizedEntries);
    }

    private static String[] rewriteClasspathArg(String[] args, String normalizedClasspath) {
        String[] rewritten = args.clone();
        for (int i = 0; i < rewritten.length; i++) {
            String arg = rewritten[i];
            if ("--classpath".equals(arg) && i + 1 < rewritten.length) {
                rewritten[i + 1] = normalizedClasspath;
                break;
            }
            if (arg.startsWith("--classpath=")) {
                rewritten[i] = "--classpath=" + normalizedClasspath;
                break;
            }
        }
        return rewritten;
    }

    private static String normalizePath(String entry) {
        try {
            return Path.of(entry).toRealPath().toString();
        } catch (Exception e) {
            return Path.of(entry).toAbsolutePath().normalize().toString();
        }
    }

    @Nullable
    private static String parseClasspathArg(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

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
        if (next.startsWith("-")) {
            return null;
        }
        return next;
    }
}
