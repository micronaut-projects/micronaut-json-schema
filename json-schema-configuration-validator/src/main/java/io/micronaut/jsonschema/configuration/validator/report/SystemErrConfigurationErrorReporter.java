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
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.PrintStream;
import java.nio.file.Path;
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
        this.err = Objects.requireNonNull(err, "err");
        this.htmlReport = htmlReport;
        this.jsonReport = jsonReport;
    }

    @Override
    public void report(Set<ConfigurationError> errors) throws IOException {
        PrintWriter writer = new PrintWriter(err);
        boolean useAnsi = shouldUseAnsi(err);

        if (!errors.isEmpty()) {
            writer.println(style(useAnsi, Ansi.BOLD) + "Configuration Validation Errors Present" + style(useAnsi, Ansi.RESET));
            writer.println();
        }

        writeTable(writer, errors, useAnsi);
        writer.println();
        printReportLocationIfPresent(writer, useAnsi);
        writer.flush();
    }

    private static void writeTable(PrintWriter writer, Set<ConfigurationError> errors, boolean useAnsi) {
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

    private static Row toRow(ConfigurationError error) {
        String origin = "-";
        if (error.originLocation() != null) {
            origin = error.originLocation() + ":" + error.lineNumber();
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
}
