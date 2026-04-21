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
package io.micronaut.jsonschema.registry.model;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

/**
 * Represents a generated JSON Schema and metadata used for registry synchronization.
 *
 * @since 1.0.0
 */
@Internal
public final class GeneratedSchema {

    @NonNull
    private final String title;

    @NonNull
    private final String uri;

    /**
     * Raw JSON schema as produced by the generator, not canonicalized.
     */
    @NonNull
    private final String rawJson;

    /**
     * Canonical, normalized JSON used for comparison/fingerprints.
     */
    @NonNull
    private final String canonicalJson;

    /**
     * Stable fingerprint of the canonical schema JSON.
     */
    @NonNull
    private final String fingerprint;

    /**
     * The fully-qualified name of the source Java type, if known.
     */
    @NonNull
    private final String sourceType;

    public GeneratedSchema(
        @NonNull String title,
        @NonNull String uri,
        @NonNull String rawJson,
        @NonNull String canonicalJson,
        @NonNull String fingerprint,
        @NonNull String sourceType
    ) {
        this.title = title;
        this.uri = uri;
        this.rawJson = rawJson;
        this.canonicalJson = canonicalJson;
        this.fingerprint = fingerprint;
        this.sourceType = sourceType;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @NonNull
    public String getUri() {
        return uri;
    }

    @NonNull
    public String getRawJson() {
        return rawJson;
    }

    @NonNull
    public String getCanonicalJson() {
        return canonicalJson;
    }

    @NonNull
    public String getFingerprint() {
        return fingerprint;
    }

    @NonNull
    public String getSourceType() {
        return sourceType;
    }
}
