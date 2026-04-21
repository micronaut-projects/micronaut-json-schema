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
package io.micronaut.jsonschema.registry.api;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

/**
 * Describes the action required on a target in order to converge to the desired schema.
 *
 * @since 1.0.0
 */
@Internal
public final class RegistryTargetDiff {

    /**
     * The action to perform.
     */
    public enum Action {
        NONE,
        CREATE,
        UPDATE
    }

    @NonNull
    private final Action action;

    @NonNull
    private final String message;

    private RegistryTargetDiff(@NonNull Action action, @NonNull String message) {
        this.action = action;
        this.message = message;
    }

    @NonNull
    public static RegistryTargetDiff none() {
        return new RegistryTargetDiff(Action.NONE, "Up-to-date");
        }
    @NonNull
    public static RegistryTargetDiff create(@NonNull String reason) {
        return new RegistryTargetDiff(Action.CREATE, reason);
    }

    @NonNull
    public static RegistryTargetDiff update(@NonNull String reason) {
        return new RegistryTargetDiff(Action.UPDATE, reason);
    }

    @NonNull
    public Action getAction() {
        return action;
    }

    @NonNull
    public String getMessage() {
        return message;
    }
}
