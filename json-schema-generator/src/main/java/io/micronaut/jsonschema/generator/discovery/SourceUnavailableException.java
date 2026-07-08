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

/**
 * A configured discovery source is unavailable before per-object schema retrieval can start.
 *
 * @since 2.1.0
 */
public final class SourceUnavailableException extends Exception {

    private final String scope;
    private final String name;
    private final DiscoveryStep step;
    private final String code;

    /**
     * @param scope Discovery scope in which the source is unavailable
     * @param name Source object name, or {@code null} when the failure applies to the whole source
     * @param step Discovery step that detected the unavailable source
     * @param code Stable diagnostic code for the failure
     * @param message Human-readable failure description
     */
    public SourceUnavailableException(String scope, String name, DiscoveryStep step, String code, String message) {
        super(message);
        this.scope = scope;
        this.name = name;
        this.step = step;
        this.code = code;
    }

    /**
     * @return Discovery scope in which the source is unavailable
     */
    public String scope() {
        return scope;
    }

    /**
     * @return Source object name, or {@code null} when the failure applies to the whole source
     */
    public String name() {
        return name;
    }

    /**
     * @return Discovery step that detected the unavailable source
     */
    public DiscoveryStep step() {
        return step;
    }

    /**
     * @return Stable diagnostic code for the failure
     */
    public String code() {
        return code;
    }
}
