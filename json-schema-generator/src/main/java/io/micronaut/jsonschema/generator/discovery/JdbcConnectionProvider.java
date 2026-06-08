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

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Lazily supplies a JDBC connection to discovery providers that need one.
 *
 * @since 2.1.0
 */
public interface JdbcConnectionProvider {

    /**
     * @return The JDBC connection
     * @throws SQLException If the connection cannot be opened
     */
    Connection getConnection() throws SQLException;
}
