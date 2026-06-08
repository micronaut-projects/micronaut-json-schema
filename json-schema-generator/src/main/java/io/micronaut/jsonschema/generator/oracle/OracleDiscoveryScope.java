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
package io.micronaut.jsonschema.generator.oracle;

/**
 * Discovery object scope used in Oracle diagnostics.
 *
 * @since 2.1.0
 */
public enum OracleDiscoveryScope {
    /**
     * Oracle SQL domain discovery.
     */
    DOMAIN,

    /**
     * Oracle JSON relational duality view discovery.
     */
    DUALITY_VIEW,

    /**
     * Custom provider-specific discovery scope.
     */
    CUSTOM
}
