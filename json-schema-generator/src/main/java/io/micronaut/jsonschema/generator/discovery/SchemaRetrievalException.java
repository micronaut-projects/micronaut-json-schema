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
package io.micronaut.jsonschema.generator.discovery;

import java.io.IOException;

/**
 * Input-level schema retrieval failure with a stable diagnostic code.
 *
 * @since 2.2.0
 */
public final class SchemaRetrievalException extends IOException {

    private final String code;
    private final String retrievalMode;

    /**
     * @param code Stable diagnostic code for the retrieval failure
     * @param message Human-readable failure description
     * @param retrievalMode Retrieval mechanism that failed, or {@code null} when none was selected
     */
    public SchemaRetrievalException(String code, String message, String retrievalMode) {
        super(message);
        this.code = code;
        this.retrievalMode = retrievalMode;
    }

    /**
     * @return Stable diagnostic code for the retrieval failure
     */
    public String code() {
        return code;
    }

    /**
     * @return Retrieval mechanism that failed, or {@code null} when none was selected
     */
    public String retrievalMode() {
        return retrievalMode;
    }
}
