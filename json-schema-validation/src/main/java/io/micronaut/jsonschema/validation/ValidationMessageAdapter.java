/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.jsonschema.validation;

import com.networknt.schema.Error;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;

/**
 * Adapter from {@link Error} into {@link ValidationMessage}.
 *
 * @author Sergio del Amo
 * @since 1.0.0
 */
@Internal
public class ValidationMessageAdapter implements ValidationMessage {
    private final Error error;

    /**
     * @param error Original validation error.
     */
    public ValidationMessageAdapter(Error error) {
        this.error = error;
    }

    @Override
    @NonNull
    public String getMessage() {
        return error.getMessage();
    }

    /**
     * @return Original validation error.
     */
    @NonNull
    public Error getError() {
        return error;
    }

    @Override
    public String toString() {
        return "ValidationMessageAdapter{message=" + getMessage() + "}";
    }
}
