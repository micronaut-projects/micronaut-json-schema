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
package io.micronaut.jsonschema.registry.naming;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.jsonschema.registry.model.GeneratedSchema;
import jakarta.inject.Singleton;

import java.util.Objects;

/**
 * Default subject strategy using the simple name of the source type if available,
 * otherwise falls back to the schema title.
 *
 * @since 1.0.0
 */
@Internal
@Singleton
final class TypeSimpleSubjectStrategy implements SubjectStrategy {

    @Override
    @NonNull
    public String subjectFor(@NonNull GeneratedSchema schema) {
        Objects.requireNonNull(schema, "schema");
        String sourceType = schema.getSourceType();
        String candidate = null;
        if (sourceType != null && !sourceType.isEmpty()) {
            int idx = sourceType.lastIndexOf('.');
            candidate = idx >= 0 ? sourceType.substring(idx + 1) : sourceType;
        }
        if (candidate == null || candidate.isEmpty()) {
            candidate = schema.getTitle();
        }
        return sanitize(candidate);
    }

    private static String sanitize(String s) {
        // Keep it simple for now: restrict to letters, digits, underscore, dash, dot.
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.') {
                out.append(c);
            } else if (Character.isWhitespace(c)) {
                out.append('_');
            }
        }
        return out.length() == 0 ? "schema" : out.toString();
    }
}
