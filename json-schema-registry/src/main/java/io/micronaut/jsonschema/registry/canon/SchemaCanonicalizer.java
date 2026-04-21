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
package io.micronaut.jsonschema.registry.canon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Produces a canonical JSON representation of a schema and a stable fingerprint.
 * This implementation uses Jackson to order map entries by key and to avoid any pretty printing.
 *
 * @since 1.0.0
 */
@Internal
@Singleton
public final class SchemaCanonicalizer {

    private final ObjectMapper mapper;

    public SchemaCanonicalizer() {
        this.mapper = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
    }

    /**
     * Canonicalize a raw schema JSON (string) and compute a fingerprint.
     * @param rawJson Raw JSON schema
     * @return canonicalization result
     * @throws IllegalArgumentException if the schema cannot be parsed
     */
    @NonNull
    public Result canonicalize(@NonNull String rawJson) {
        try {
            JsonNode node = mapper.readTree(rawJson);
            String canonical = mapper.writeValueAsString(node);
            String digest = sha256(canonical);
            return new Result(canonical, digest);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid schema JSON", e);
        }
    }

    @NonNull
    private static String sha256(@NonNull String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit((b & 0xF), 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Canonicalization result.
     * @param canonicalJson Canonical JSON content
     * @param fingerprint Stable fingerprint of the canonical JSON
     */
    public record Result(@NonNull String canonicalJson, @NonNull String fingerprint) { }
}
