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
package io.micronaut.jsonschema.configuration.validator.report;

import io.micronaut.jsonschema.configuration.validator.ConfigurationError;
import io.micronaut.jsonschema.configuration.validator.DependencyInjectionError;
import io.micronaut.core.naming.NameUtils;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports errors to {@code System.err}.
 */
public final class SystemErrConfigurationErrorReporter implements ConfigurationErrorReporter {
    @Nullable
    private final Path htmlReport;

    @Nullable
    private final Path jsonReport;

    private final PrintStream err;

    @Nullable
    private final Path projectBaseDir;

    private final List<Path> resourcesDirs;

    /**
     * Creates a reporter that writes to {@link System#err}.
     */
    public SystemErrConfigurationErrorReporter() {
        this(System.err, null, null);
    }

    /**
     * Creates a reporter that writes to the given stream.
     *
     * @param err The error stream
     */
    public SystemErrConfigurationErrorReporter(PrintStream err) {
        this(err, null, null);
    }

    /**
     * Creates a reporter that writes to the given stream and optionally prints the location of
     * written report files.
     *
     * @param err The error stream
     * @param htmlReport The HTML report file location (optional)
     * @param jsonReport The JSON report file location (optional)
     */
    public SystemErrConfigurationErrorReporter(PrintStream err, @Nullable Path htmlReport, @Nullable Path jsonReport) {
        this(err, htmlReport, jsonReport, null, List.of());
    }

    /**
     * Creates a reporter that writes to the given stream and optionally prints the location of
     * written report files.
     *
     * @param err The error stream
     * @param htmlReport The HTML report file location (optional)
     * @param jsonReport The JSON report file location (optional)
     * @param projectBaseDir The project base directory used to render relative origin paths (optional)
     * @param resourcesDirs Resource directories (relative to base dir) used to resolve classpath origins
     */
    public SystemErrConfigurationErrorReporter(
        PrintStream err,
        @Nullable Path htmlReport,
        @Nullable Path jsonReport,
        @Nullable Path projectBaseDir,
        List<Path> resourcesDirs
    ) {
        this.err = Objects.requireNonNull(err, "err");
        this.htmlReport = htmlReport;
        this.jsonReport = jsonReport;

        this.projectBaseDir = projectBaseDir != null ? projectBaseDir.toAbsolutePath().normalize() : null;
        this.resourcesDirs = normalizeResourceDirs(this.projectBaseDir, resourcesDirs);
    }

    @Override
    public void report(Set<ConfigurationError> errors, Set<DependencyInjectionError> dependencyInjectionErrors) throws IOException {
        PrintWriter writer = new PrintWriter(err);
        boolean useAnsi = shouldUseAnsi(err);

        if (!errors.isEmpty() || !dependencyInjectionErrors.isEmpty()) {
            writer.println(style(useAnsi, Ansi.BOLD) + "Validation Errors Present" + style(useAnsi, Ansi.RESET));
            writer.println();
        }

        writeTable(writer, errors, useAnsi);
        writer.println();
        writeDependencyInjectionTable(writer, dependencyInjectionErrors, useAnsi);
        writer.println();
        writeDependencyInjectionGraph(writer, dependencyInjectionErrors);
        writer.println();
        printReportLocationIfPresent(writer, useAnsi);
        writer.flush();
    }

    private void writeTable(PrintWriter writer, Set<ConfigurationError> errors, boolean useAnsi) {
        if (errors.isEmpty()) {
            writer.println("No configuration validation errors.");
            return;
        }

        List<ConfigurationError> ordered = errors.stream()
            .sorted(Comparator.comparing(ConfigurationError::property))
            .toList();

        List<Row> rows = new ArrayList<>(ordered.size());
        for (ConfigurationError error : ordered) {
            rows.add(toRow(error));
        }

        Table table = Table.of(
            List.of("Property", "Type", "Message", "Origin", "Value"),
            rows,
            List.of(60, 8, 80, 40, 40)
        );
        table.print(writer, useAnsi);
    }

    private static void writeDependencyInjectionTable(PrintWriter writer, Set<DependencyInjectionError> errors, boolean useAnsi) {
        if (errors.isEmpty()) {
            writer.println("No dependency injection validation errors.");
            return;
        }

        writer.println(style(useAnsi, Ansi.BOLD) + "Dependency Injection Errors" + style(useAnsi, Ansi.RESET));
        List<DependencyInjectionError> ordered = errors.stream()
            .sorted(Comparator.comparing(
                DependencyInjectionError::injectionPoint,
                Comparator.nullsLast(Comparator.naturalOrder())
            ).thenComparing(DependencyInjectionError::bean))
            .toList();

        List<DependencyRow> rows = new ArrayList<>(ordered.size());
        for (DependencyInjectionError error : ordered) {
            rows.add(new DependencyRow(
                sanitize(error.injectionPoint()),
                sanitize(shortName(error.bean())),
                sanitize(formatDetails(error.message(), error.disabledReason()))
            ));
        }

        DependencyTable table = DependencyTable.of(
            List.of("Injection Point", "Bean", "Details"),
            rows,
            List.of(50, 40, 120)
        );
        table.print(writer, useAnsi);
    }

    private static String shortName(@Nullable String typeName) {
        return typeName == null ? "" : NameUtils.getShortenedName(typeName);
    }

    private static String formatDetails(String message, @Nullable String disabledReason) {
        if (disabledReason == null || disabledReason.isBlank()) {
            return message;
        }
        return message + " | Disabled: " + disabledReason;
    }

    private static void writeDependencyInjectionGraph(PrintWriter writer, Set<DependencyInjectionError> errors) {
        if (errors.isEmpty()) {
            return;
        }
        writer.println("Dependency Injection Failure Paths:");
        List<DependencyInjectionError> ordered = errors.stream()
            .sorted(Comparator.comparing(DependencyInjectionError::rootBean).thenComparing(DependencyInjectionError::bean))
            .toList();
        for (DependencyInjectionError error : ordered) {
            writer.println("- root: " + error.rootBean());
            List<String> nodes = normalizedPathNodes(error);
            if (nodes.isEmpty()) {
                writer.println("  +--> " + error.bean() + " (failed)");
                continue;
            }
            writer.println("  +--> " + sanitize(nodes.get(0)));
            for (int i = 1; i < nodes.size(); i++) {
                writer.println("  |    |");
                writer.println("  |    +--> " + sanitize(nodes.get(i)));
            }
        }
    }

    private static List<String> normalizedPathNodes(DependencyInjectionError error) {
        if (error.failingPath().isEmpty()) {
            return List.of(error.rootBean(), error.bean() + " (failed)");
        }
        List<String> nodes = new ArrayList<>(error.failingPath().size());
        for (String pathEntry : error.failingPath()) {
            nodes.add(cleanPathNode(pathEntry));
        }
        return nodes;
    }

    private static String cleanPathNode(@Nullable String pathEntry) {
        if (pathEntry == null) {
            return "";
        }
        String cleaned = pathEntry.trim();
        if (cleaned.startsWith("*")) {
            cleaned = cleaned.substring(1).trim();
        }
        return cleaned;
    }

    private Row toRow(ConfigurationError error) {
        String origin = "-";
        if (error.originLocation() != null) {
            origin = rewriteOriginIfPossible(error.originLocation());
            if (error.lineNumber() > 0) {
                origin = origin + ":" + error.lineNumber();
            }
        }

        String value = "-";
        if (error.rawValue() != null) {
            value = sanitize(String.valueOf(error.rawValue()));
        }

        return new Row(
            sanitize(error.property()),
            error.type().name(),
            sanitize(error.message()),
            sanitize(origin),
            value
        );
    }

    private static List<Path> normalizeResourceDirs(@Nullable Path baseDir, @Nullable List<Path> resourcesDirs) {
        if (baseDir == null || resourcesDirs == null || resourcesDirs.isEmpty()) {
            return List.of();
        }
        List<Path> resolved = new ArrayList<>(resourcesDirs.size());
        for (Path p : resourcesDirs) {
            if (p == null) {
                continue;
            }
            Path dir = p.isAbsolute() ? p : baseDir.resolve(p);
            resolved.add(dir.normalize());
        }
        return List.copyOf(resolved);
    }

    private String rewriteOriginIfPossible(String origin) {
        if (projectBaseDir == null || resourcesDirs.isEmpty()) {
            return origin;
        }
        return rewriteOrigin(origin, projectBaseDir, resourcesDirs);
    }

    private static String rewriteOrigin(String origin, Path baseDir, List<Path> resourcesDirs) {
        Path originPath = tryParseAbsolutePath(origin);
        if (originPath != null) {
            Path normalized = originPath.toAbsolutePath().normalize();
            if (normalized.startsWith(baseDir)) {
                return baseDir.relativize(normalized).toString();
            }
            return origin;
        }

        String resourceName = stripClasspathPrefix(origin).trim();
        if (resourceName.startsWith("/")) {
            resourceName = resourceName.substring(1);
        }
        if (resourceName.isEmpty() || resourceName.contains("!") || resourceName.contains("://")) {
            return origin;
        }

        for (Path dir : resourcesDirs) {
            Path candidate = dir.resolve(resourceName).normalize();
            if (Files.exists(candidate)) {
                Path absolute = candidate.toAbsolutePath().normalize();
                if (absolute.startsWith(baseDir)) {
                    return baseDir.relativize(absolute).toString();
                }
                return candidate.toString();
            }
        }
        return origin;
    }

    @Nullable
    private static Path tryParseAbsolutePath(String origin) {
        String trimmed = origin.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("file:")) {
            try {
                return Paths.get(URI.create(trimmed));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        try {
            Path p = Path.of(trimmed);
            return p.isAbsolute() ? p : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String stripClasspathPrefix(String origin) {
        if (origin.startsWith("classpath:")) {
            return origin.substring("classpath:".length());
        }
        return origin;
    }

    private void printReportLocationIfPresent(PrintWriter writer, boolean useAnsi) {
        Path preferred = htmlReport != null ? htmlReport : jsonReport;
        if (preferred == null) {
            return;
        }
        Path absolute = preferred.toAbsolutePath().normalize();

        // Most terminals will detect a file URI as a clickable link.
        writer.println(style(useAnsi, Ansi.DIM) + "report: " + style(useAnsi, Ansi.RESET) + absolute.toUri());
    }

    private static boolean shouldUseAnsi(PrintStream err) {
        if (System.getenv("NO_COLOR") != null) {
            return false;
        }
        String term = System.getenv("TERM");
        if (term == null || term.isBlank() || "dumb".equalsIgnoreCase(term)) {
            return false;
        }
        // Use ANSI only when we appear to be running interactively.
        return System.console() != null;
    }

    private static String sanitize(@Nullable String s) {
        if (s == null) {
            return "-";
        }
        return s.replace("\r", " ").replace("\n", " ").replace("\t", " ");
    }

    private static String style(boolean useAnsi, String code) {
        return useAnsi ? code : "";
    }

    private static final class Ansi {
        private static final String RESET = "\u001B[0m";
        private static final String BOLD = "\u001B[1m";
        private static final String DIM = "\u001B[2m";
        private static final String RED = "\u001B[31m";
        private static final String YELLOW = "\u001B[33m";
    }

    private record Row(String property, String type, String message, String origin, String value) {
    }

    private record DependencyRow(String injectionPoint, String bean, String details) {
    }

    private static final class Table {
        private final List<String> headers;
        private final List<Row> rows;
        private final List<Integer> maxWidths;

        private Table(List<String> headers, List<Row> rows, List<Integer> maxWidths) {
            this.headers = headers;
            this.rows = rows;
            this.maxWidths = maxWidths;
        }

        static Table of(List<String> headers, List<Row> rows, List<Integer> maxWidths) {
            return new Table(headers, rows, maxWidths);
        }

        void print(PrintWriter writer, boolean useAnsi) {
            int wProperty = computeWidth(headers.get(0), rows.stream().map(Row::property).toList(), maxWidths.get(0));
            int wType = computeWidth(headers.get(1), rows.stream().map(Row::type).toList(), maxWidths.get(1));
            int wMessage = computeWidth(headers.get(2), rows.stream().map(Row::message).toList(), maxWidths.get(2));
            int wOrigin = computeWidth(headers.get(3), rows.stream().map(Row::origin).toList(), maxWidths.get(3));
            int wValue = computeWidth(headers.get(4), rows.stream().map(Row::value).toList(), maxWidths.get(4));

            String sep = border(wProperty, wType, wMessage, wOrigin, wValue);
            writer.println(sep);
            writer.println(rowLine(
                style(useAnsi, Ansi.BOLD) + pad(headers.get(0), wProperty) + style(useAnsi, Ansi.RESET),
                style(useAnsi, Ansi.BOLD) + pad(headers.get(1), wType) + style(useAnsi, Ansi.RESET),
                style(useAnsi, Ansi.BOLD) + pad(headers.get(2), wMessage) + style(useAnsi, Ansi.RESET),
                style(useAnsi, Ansi.BOLD) + pad(headers.get(3), wOrigin) + style(useAnsi, Ansi.RESET),
                style(useAnsi, Ansi.BOLD) + pad(headers.get(4), wValue) + style(useAnsi, Ansi.RESET)
            ));
            writer.println(sep);

            for (Row r : rows) {
                String type = pad(truncate(r.type(), wType), wType);
                String coloredType;
                if (useAnsi) {
                    if ("ERROR".equals(r.type())) {
                        coloredType = Ansi.RED + type + Ansi.RESET;
                    } else if ("WARNING".equals(r.type())) {
                        coloredType = Ansi.YELLOW + type + Ansi.RESET;
                    } else {
                        coloredType = type;
                    }
                } else {
                    coloredType = type;
                }

                writer.println(rowLine(
                    pad(truncate(r.property(), wProperty), wProperty),
                    coloredType,
                    pad(truncate(r.message(), wMessage), wMessage),
                    pad(truncate(r.origin(), wOrigin), wOrigin),
                    pad(truncate(r.value(), wValue), wValue)
                ));
            }
            writer.println(sep);
        }

        private static int computeWidth(String header, List<String> values, int max) {
            int width = header.length();
            for (String v : values) {
                if (v == null) {
                    continue;
                }
                width = Math.max(width, v.length());
            }
            return Math.min(width, max);
        }

        private static String border(int wProperty, int wType, int wMessage, int wOrigin, int wValue) {
            return "+" + "-".repeat(wProperty + 2) +
                "+" + "-".repeat(wType + 2) +
                "+" + "-".repeat(wMessage + 2) +
                "+" + "-".repeat(wOrigin + 2) +
                "+" + "-".repeat(wValue + 2) +
                "+";
        }

        private static String rowLine(String property, String type, String message, String origin, String value) {
            return "| " + property + " | " + type + " | " + message + " | " + origin + " | " + value + " |";
        }

        private static String pad(@Nullable String s, int width) {
            if (s == null) {
                s = "";
            }
            if (s.length() >= width) {
                return s;
            }
            return s + " ".repeat(width - s.length());
        }

        private static String truncate(@Nullable String s, int width) {
            if (s == null) {
                return "";
            }
            if (s.length() <= width) {
                return s;
            }
            if (width <= 3) {
                return s.substring(0, width);
            }
            return s.substring(0, width - 3) + "...";
        }
    }

    private static final class DependencyTable {
        private final List<String> headers;
        private final List<DependencyRow> rows;
        private final List<Integer> maxWidths;

        private DependencyTable(List<String> headers, List<DependencyRow> rows, List<Integer> maxWidths) {
            this.headers = headers;
            this.rows = rows;
            this.maxWidths = maxWidths;
        }

        static DependencyTable of(List<String> headers, List<DependencyRow> rows, List<Integer> maxWidths) {
            return new DependencyTable(headers, rows, maxWidths);
        }

        void print(PrintWriter writer, boolean useAnsi) {
            int wInjectionPoint = Table.computeWidth(headers.get(0), rows.stream().map(DependencyRow::injectionPoint).toList(), maxWidths.get(0));
            int wBean = Table.computeWidth(headers.get(1), rows.stream().map(DependencyRow::bean).toList(), maxWidths.get(1));
            int wDetails = Table.computeWidth(headers.get(2), rows.stream().map(DependencyRow::details).toList(), maxWidths.get(2));

            String sep = border(wInjectionPoint, wBean, wDetails);
            writer.println(sep);
            writer.println(rowLine(
                style(useAnsi, Ansi.BOLD) + Table.pad(headers.get(0), wInjectionPoint) + style(useAnsi, Ansi.RESET),
                style(useAnsi, Ansi.BOLD) + Table.pad(headers.get(1), wBean) + style(useAnsi, Ansi.RESET),
                style(useAnsi, Ansi.BOLD) + Table.pad(headers.get(2), wDetails) + style(useAnsi, Ansi.RESET)
            ));
            writer.println(sep);

            for (DependencyRow r : rows) {
                writer.println(rowLine(
                    Table.pad(Table.truncate(r.injectionPoint(), wInjectionPoint), wInjectionPoint),
                    Table.pad(Table.truncate(r.bean(), wBean), wBean),
                    Table.pad(Table.truncate(r.details(), wDetails), wDetails)
                ));
            }
            writer.println(sep);
        }

        private static String border(int wInjectionPoint, int wBean, int wDetails) {
            return "+" + "-".repeat(wInjectionPoint + 2)
                + "+" + "-".repeat(wBean + 2)
                + "+" + "-".repeat(wDetails + 2)
                + "+";
        }

        private static String rowLine(String injectionPoint, String bean, String details) {
            return "| " + injectionPoint + " | " + bean + " | " + details + " |";
        }
    }
}
