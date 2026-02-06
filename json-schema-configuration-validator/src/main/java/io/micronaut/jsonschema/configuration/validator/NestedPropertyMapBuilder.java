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
package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Internal
final class NestedPropertyMapBuilder {
    private NestedPropertyMapBuilder() {
    }

    @NonNull
    static Map<String, Object> nest(@NonNull Map<String, Object> flat) {
        Map<String, Object> root = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : flat.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isEmpty()) {
                continue;
            }
            insert(root, key, entry.getValue());
        }
        return root;
    }

    @SuppressWarnings("java:S3776")
    private static void insert(Map<String, Object> root, String key, Object value) {
        String[] segments = key.split("\\.");
        Object current = root;
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            boolean lastSegment = i == segments.length - 1;
            SegmentInfo info = SegmentInfo.parse(segment);

            if (!(current instanceof Map)) {
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> currentMap = (Map<String, Object>) current;

            Object next = currentMap.get(info.name);
            if (info.indexes.isEmpty()) {
                if (lastSegment) {
                    currentMap.put(info.name, value);
                    return;
                }
                if (!(next instanceof Map)) {
                    next = new LinkedHashMap<String, Object>();
                    currentMap.put(info.name, next);
                }
                current = next;
            } else {
                if (!(next instanceof List)) {
                    next = new ArrayList<>();
                    currentMap.put(info.name, next);
                }
                current = next;

                for (int idxPos = 0; idxPos < info.indexes.size(); idxPos++) {
                    int index = info.indexes.get(idxPos);
                    boolean lastIndex = idxPos == info.indexes.size() - 1;
                    boolean isLast = lastSegment && lastIndex;

                    @SuppressWarnings("unchecked")
                    List<Object> list = (List<Object>) current;
                    ensureSize(list, index + 1);
                    Object element = list.get(index);
                    if (isLast) {
                        list.set(index, value);
                        return;
                    }
                    if (lastIndex) {
                        if (!(element instanceof Map)) {
                            element = new LinkedHashMap<String, Object>();
                            list.set(index, element);
                        }
                        current = element;
                    } else {
                        if (!(element instanceof List)) {
                            element = new ArrayList<Object>();
                            list.set(index, element);
                        }
                        current = element;
                    }
                }
            }
        }
    }

    private static void ensureSize(List<Object> list, int size) {
        while (list.size() < size) {
            list.add(null);
        }
    }

    private record SegmentInfo(String name, List<Integer> indexes) {
        static SegmentInfo parse(String segment) {
            int bracket = segment.indexOf('[');
            if (bracket < 0) {
                return new SegmentInfo(segment, List.of());
            }

            String name = segment.substring(0, bracket);
            List<Integer> indexes = new ArrayList<>(1);
            int pos = bracket;
            while (pos < segment.length()) {
                int open = segment.indexOf('[', pos);
                if (open < 0) {
                    break;
                }
                int close = segment.indexOf(']', open + 1);
                if (close < 0) {
                    break;
                }
                String idxStr = segment.substring(open + 1, close);
                try {
                    indexes.add(Integer.parseInt(idxStr));
                } catch (NumberFormatException ignored) {
                    // ignore non-numeric indexes
                }
                pos = close + 1;
            }
            return new SegmentInfo(name, indexes);
        }
    }
}
