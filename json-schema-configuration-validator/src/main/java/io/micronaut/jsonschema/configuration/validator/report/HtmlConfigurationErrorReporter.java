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
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Reports errors as a self-contained HTML document.
 */
public final class HtmlConfigurationErrorReporter implements ConfigurationErrorReporter {
    private final OutputStream output;

    /**
     * @param output The output stream
     */
    public HtmlConfigurationErrorReporter(@NonNull OutputStream output) {
        this.output = output;
    }

    @Override
    public void report(Set<ConfigurationError> errors) throws IOException {
        StringBuilder html = new StringBuilder(4096);
        html.append("<!doctype html><html><head><meta charset='utf-8'>")
            .append("<title>Configuration validation errors</title>")
            .append("<style>")
            .append("body{font-family:system-ui,-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;margin:24px;}")
            .append("table{border-collapse:collapse;width:100%;}")
            .append("th,td{border:1px solid #ddd;padding:8px;vertical-align:top;}")
            .append("th{background:#f6f6f6;text-align:left;}")
            .append("code{font-family:ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,monospace;}")
            .append("</style>")
            .append("</head><body>")
            .append("<h1>Configuration validation errors</h1>")
            .append("<p>Total: ").append(errors.size()).append("</p>")
            .append("<table><thead><tr>")
            .append("<th>Property</th><th>Type</th><th>Message</th><th>Origin</th><th>Raw</th><th>Value</th>")
            .append("</tr></thead><tbody>");

        for (ConfigurationError error : errors) {
            html.append("<tr>")
                .append("<td><code>").append(escape(error.property())).append("</code></td>")
                .append("<td>").append(escape(error.type() != null ? error.type().name() : null)).append("</td>")
                .append("<td>").append(escape(error.message())).append("</td>")
                .append("<td>").append(escape(error.originLocation())).append("</td>")
                .append("<td><code>").append(escape(error.rawPropertyName())).append("</code></td>")
                .append("<td><code>").append(escape(error.rawValue() != null ? String.valueOf(error.rawValue()) : null)).append("</code></td>")
                .append("</tr>");
        }
        html.append("</tbody></table></body></html>");

        output.write(html.toString().getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
