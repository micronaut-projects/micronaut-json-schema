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
package io.micronaut.jsonschema.registry.oracle;

/**
 * Records timed Oracle materialization operations.
 *
 * @since 2.2.0
 */
public interface OracleOperationRecorder {
    /**
     * No-op operation recorder.
     */
    OracleOperationRecorder NOOP = new OracleOperationRecorder() {
    };

    /**
     * Record the named operation.
     *
     * @param operationName Operation name
     * @param operation Operation callback
     * @param <T> Operation return type
     * @return Operation result
     * @throws Exception If the operation fails
     */
    default <T> T record(String operationName, OracleOperation<T> operation) throws Exception {
        return operation.execute();
    }

    /**
     * Checked operation callback.
     *
     * @param <T> Return type
     */
    @FunctionalInterface
    interface OracleOperation<T> {
        /**
         * Execute the operation.
         *
         * @return Operation result
         * @throws Exception If the operation fails
         */
        T execute() throws Exception;
    }
}
