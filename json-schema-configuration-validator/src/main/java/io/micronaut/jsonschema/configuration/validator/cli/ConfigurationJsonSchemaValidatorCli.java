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
import io.micronaut.json.JsonMapper;
import io.micronaut.jsonschema.configuration.validator.ConfigurationError;
import io.micronaut.jsonschema.configuration.validator.ConfigurationJsonSchemaValidator;
import io.micronaut.jsonschema.configuration.validator.report.HtmlConfigurationErrorReporter;
import io.micronaut.jsonschema.configuration.validator.report.JsonConfigurationErrorReporter;
import io.micronaut.jsonschema.configuration.validator.report.SystemErrConfigurationErrorReporter;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Command line entry point for {@link ConfigurationJsonSchemaValidator}.
 * <p>
 * Supported arguments:
 * <ul>
 *     <li>{@code --classpath <pathSepSeparatedClasspath>} (required)</li>
 *     <li>{@code --env <name>} (repeatable) or {@code --environments <csv>}</li>
 *     <li>{@code --suppress <pattern>} (repeatable) or {@code --suppressions <csv>}</li>
 *     <li>{@code --fail-on-not-present <true|false>} (defaults to {@code true})</li>
 *     <li>{@code --deduce-environments <true|false>} (defaults to {@code false})</li>
 *     <li>{@code --out <directory>} (required)</li>
 *     <li>{@code --format <json|html|both>} (defaults to {@code both})</li>
 * </ul>
 */
@Internal
public final class ConfigurationJsonSchemaValidatorCli {
    private static final System.Logger LOG = System.getLogger(ConfigurationJsonSchemaValidatorCli.class.getName());

    private ConfigurationJsonSchemaValidatorCli() {
    }

    /**
     * CLI entry point.
     *
     * @param args The CLI arguments
     */
    @SuppressWarnings("java:S1147") // CLI entry point: exit code must be propagated to the process.
    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            LOG.log(System.Logger.Level.ERROR, "Validation failed with exit code " + exitCode);
        }
        System.exit(exitCode);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            err.println();
            err.println(Options.usage());
            return 2;
        }
        if (options.help()) {
            out.println(Options.usage());
            return 0;
        }

        try {
            Files.createDirectories(options.outDir());
        } catch (IOException e) {
            err.println("Failed to create output directory: " + options.outDir());
            err.println(e.getMessage());
            return 2;
        }

        ConfigurationJsonSchemaValidator validator = new ConfigurationJsonSchemaValidator();
        validator.setFailOnNotPresent(options.failOnNotPresent());
        validator.setSuppressionPatterns(options.suppressions());

        JsonSchemaConfigurationValidator facade = JsonSchemaConfigurationValidator.forClasspath(
            options.classpath(),
            options.environments(),
            options.deduceEnvironments(),
            validator
        );

        Set<ConfigurationError> errors;
        try {
            errors = facade.validate();
        } catch (Exception e) {
            err.println("Validation failed while loading configuration:");
            printDiscoveredConfigFiles(err, options.classpath());
            printThrowable(err, e);
            return 2;
        }

        try {
            ReportFiles reportFiles = writeReports(errors, options);
            new SystemErrConfigurationErrorReporter(
                err,
                reportFiles.htmlReport(),
                reportFiles.jsonReport(),
                options.projectBaseDir(),
                options.resourcesDirs()
            ).report(errors);
        } catch (IOException e) {
            err.println("Failed to write report: " + e.getMessage());
            return 2;
        }

        // Non-zero when any ERRORs are present.
        return errors.stream().anyMatch(e -> e.type() == ConfigurationError.Type.ERROR) ? 1 : 0;
    }

    private static void printThrowable(PrintStream err, Throwable e) {
        Throwable root = rootCause(e);
        err.println("  " + formatThrowable(root));
        if (root != e) {
            err.println("  (wrapped by: " + formatThrowable(e) + ")");
        }
    }

    private static void printDiscoveredConfigFiles(PrintStream err, String classpath) {
        List<Path> files = discoverApplicationConfigFilesOnClasspath(classpath);
        if (files.isEmpty()) {
            return;
        }
        err.println("  Config files on classpath:");
        for (Path path : files) {
            err.println("    - " + path.toAbsolutePath());
        }
    }

    private static List<Path> discoverApplicationConfigFilesOnClasspath(String classpath) {
        if (classpath == null || classpath.isBlank()) {
            return List.of();
        }

        List<Path> files = new ArrayList<>(2);
        String[] parts = classpath.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator));
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Path entry;
            try {
                entry = Path.of(trimmed);
            } catch (Exception ignored) {
                continue;
            }
            if (!java.nio.file.Files.isDirectory(entry)) {
                continue;
            }

            addIfExists(files, entry.resolve("application.yml"));
            addIfExists(files, entry.resolve("application.yaml"));
            addIfExists(files, entry.resolve("application.properties"));
            addIfExists(files, entry.resolve("application.toml"));
        }
        return files;
    }

    private static void addIfExists(List<Path> files, Path path) {
        if (java.nio.file.Files.exists(path)) {
            files.add(path);
        }
    }

    private static Throwable rootCause(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    private static String formatThrowable(Throwable e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getName();
        }
        return message;
    }

    private static ReportFiles writeReports(Set<ConfigurationError> errors, Options options) throws IOException {
        Path jsonFile = null;
        Path htmlFile = null;

        if (options.format() == Format.JSON || options.format() == Format.BOTH) {
            jsonFile = options.outDir().resolve("configuration-errors.json");
            try (OutputStream os = Files.newOutputStream(jsonFile)) {
                new JsonConfigurationErrorReporter(JsonMapper.createDefault(), os).report(errors);
            }
        }
        if (options.format() == Format.HTML || options.format() == Format.BOTH) {
            htmlFile = options.outDir().resolve("configuration-errors.html");
            try (OutputStream os = Files.newOutputStream(htmlFile)) {
                new HtmlConfigurationErrorReporter(os).report(errors);
            }
        }
        return new ReportFiles(htmlFile, jsonFile);
    }

    private record ReportFiles(Path htmlReport, Path jsonReport) {
    }

    enum Format {
        JSON,
        HTML,
        BOTH
    }

    record Options(
        boolean help,
        String classpath,
        List<String> environments,
        List<String> suppressions,
        boolean failOnNotPresent,
        boolean deduceEnvironments,
        Path outDir,
        Format format,
        @Nullable Path projectBaseDir,
        List<Path> resourcesDirs
    ) {
        @SuppressWarnings("java:S3776")
        static Options parse(String[] args) {
            boolean help = false;
            String classpath = null;
            List<String> environments = new ArrayList<>(1);
            List<String> suppressions = new ArrayList<>(0);
            boolean failOnNotPresent = true;
            boolean deduceEnvironments = false;
            Path outDir = null;
            Format format = Format.BOTH;
            Path projectBaseDir = null;
            List<Path> resourcesDirs = new ArrayList<>(0);

            List<String> list = Arrays.asList(args);
            for (int i = 0; i < list.size(); i++) {
                String arg = list.get(i);
                if ("--help".equals(arg) || "-h".equals(arg)) {
                    help = true;
                    continue;
                }

                String key;
                String value;
                int eq = arg.indexOf('=');
                if (eq > -1) {
                    key = arg.substring(0, eq);
                    value = arg.substring(eq + 1);
                } else {
                    key = arg;
                    value = null;
                }

                switch (key) {
                    case "--classpath" -> {
                        value = value != null ? value : nextValue(list, ++i, key);
                        classpath = value;
                    }
                    case "--env" -> {
                        value = value != null ? value : nextValue(list, ++i, key);
                        if (!value.isBlank()) {
                            environments.add(value);
                        }
                    }
                    case "--environments" -> {
                        value = value != null ? value : nextValue(list, ++i, key);
                        environments.addAll(splitCsv(value));
                    }
                    case "--suppress" -> {
                        value = value != null ? value : nextValue(list, ++i, key);
                        if (!value.isBlank()) {
                            suppressions.add(value);
                        }
                    }
                    case "--suppressions" -> {
                        value = value != null ? value : nextValue(list, ++i, key);
                        suppressions.addAll(splitCsv(value));
                    }
                    case "--fail-on-not-present" -> {
                        value = value != null ? value : nextValue(list, ++i, key);
                        failOnNotPresent = Boolean.parseBoolean(value);
                    }
                    case "--no-fail-on-not-present" -> failOnNotPresent = false;
                    case "--deduce-environments" -> {
                        value = value != null ? value : nextValue(list, ++i, key);
                        deduceEnvironments = Boolean.parseBoolean(value);
                    }
                    case "--no-deduce-environments" -> deduceEnvironments = false;
                    case "--out" -> {
                        value = value != null ? value : nextValue(list, ++i, key);
                        outDir = Path.of(value);
                    }
                    case "--format" -> {
                        value = value != null ? value : nextValue(list, ++i, key);
                        format = parseFormat(value);
                    }
                    case "--project-base-dir" -> {
                        value = value != null ? value : nextValue(list, ++i, key);
                        projectBaseDir = parsePath(value, key);
                    }
                    case "--resources-dirs" -> {
                        value = value != null ? value : nextValue(list, ++i, key);
                        for (String d : splitCsv(value)) {
                            resourcesDirs.add(parsePath(d, key));
                        }
                    }
                    default -> throw new IllegalArgumentException("Unknown argument: " + key);
                }
            }

            if (help) {
                return new Options(true, "", List.of(), List.of(), true, false, Path.of("."), Format.BOTH, null, List.of());
            }
            if (classpath == null || classpath.isBlank()) {
                throw new IllegalArgumentException("Missing required argument: --classpath");
            }
            if (outDir == null) {
                throw new IllegalArgumentException("Missing required argument: --out");
            }

            return new Options(
                false,
                classpath,
                List.copyOf(environments),
                List.copyOf(suppressions),
                failOnNotPresent,
                deduceEnvironments,
                outDir,
                format,
                projectBaseDir,
                List.copyOf(resourcesDirs)
            );
        }

        static String usage() {
            return "Usage: " + ConfigurationJsonSchemaValidatorCli.class.getName() + " [options]\n" +
                "\n" +
                "Options:\n" +
                "  --classpath <cp>                 Classpath (path separator separated) used to discover schemas\n" +
                "  --env <name>                     Add an environment (repeatable)\n" +
                "  --environments <csv>             Comma-separated environments\n" +
                "  --suppress <pattern>             Suppression pattern (repeatable)\n" +
                "  --suppressions <csv>             Comma-separated suppression patterns\n" +
                "  --fail-on-not-present <bool>     Whether unknown properties are errors (default: true)\n" +
                "  --no-fail-on-not-present         Convenience flag to disable unknown property errors\n" +
                "  --deduce-environments <bool>     Whether to deduce environments (default: false)\n" +
                "  --no-deduce-environments         Convenience flag to disable environment deduction\n" +
                "  --out <dir>                      Output directory to write reports\n" +
                "  --format <json|html|both>        Report format(s) (default: both)\n" +
                "  --project-base-dir <dir>         Project base directory used to render relative origin paths\n" +
                "  --resources-dirs <csv>           Comma-separated resource directories under the project base dir\n" +
                "  --help                           Print this help\n";
        }

        private static Path parsePath(String value, String key) {
            try {
                return Path.of(value);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid path for " + key + ": " + value);
            }
        }

        private static String nextValue(List<String> args, int index, String key) {
            if (index >= args.size()) {
                throw new IllegalArgumentException("Missing value for " + key);
            }
            return args.get(index);
        }

        private static List<String> splitCsv(String csv) {
            if (csv == null || csv.isBlank()) {
                return List.of();
            }
            String[] parts = csv.split(",");
            List<String> result = new ArrayList<>(parts.length);
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
            return result;
        }

        private static Format parseFormat(@Nullable String value) {
            if (value == null) {
                return Format.BOTH;
            }
            String normalized = value.trim().toLowerCase(Locale.ENGLISH);
            return switch (normalized) {
                case "json" -> Format.JSON;
                case "html" -> Format.HTML;
                case "both" -> Format.BOTH;
                default -> throw new IllegalArgumentException("Invalid format: " + value);
            };
        }
    }
}
