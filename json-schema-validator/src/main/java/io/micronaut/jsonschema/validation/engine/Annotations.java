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
package io.micronaut.jsonschema.validation.engine;

import org.jspecify.annotations.Nullable;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

/**
 * The evaluated properties and items of one instance location, collected for
 * {@code unevaluatedProperties} and {@code unevaluatedItems}.
 *
 * @author Graeme Rocher
 * @since 2.3.2
 */
final class Annotations {
    private @Nullable Set<String> properties;
    private boolean allItems;
    private int prefixItems;
    private @Nullable BitSet items;

    void addProperty(String name) {
        Set<String> props = properties;
        if (props == null) {
            props = new HashSet<>();
            properties = props;
        }
        props.add(name);
    }

    boolean isPropertyEvaluated(String name) {
        Set<String> props = properties;
        return props != null && props.contains(name);
    }

    void evaluateItemsUpTo(int count) {
        if (count > prefixItems) {
            prefixItems = count;
        }
    }

    void evaluateAllItems() {
        allItems = true;
    }

    void evaluateItem(int index) {
        BitSet bits = items;
        if (bits == null) {
            bits = new BitSet();
            items = bits;
        }
        bits.set(index);
    }

    boolean isItemEvaluated(int index) {
        if (allItems || index < prefixItems) {
            return true;
        }
        BitSet bits = items;
        return bits != null && bits.get(index);
    }

    void merge(Annotations other) {
        Set<String> otherProps = other.properties;
        if (otherProps != null) {
            if (properties == null) {
                properties = new HashSet<>(otherProps);
            } else {
                properties.addAll(otherProps);
            }
        }
        allItems |= other.allItems;
        evaluateItemsUpTo(other.prefixItems);
        BitSet otherItems = other.items;
        if (otherItems != null) {
            if (items == null) {
                items = (BitSet) otherItems.clone();
            } else {
                items.or(otherItems);
            }
        }
    }
}
