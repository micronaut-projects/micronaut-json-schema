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
package io.micronaut.jsonschema.registry

import spock.lang.Specification

final class DefaultJsonSchemaNormalizerSpec extends Specification {

    private final JsonSchemaNormalizer normalizer = new DefaultJsonSchemaNormalizer()

    void "normalizes object keys recursively"() {
        when:
        String normalized = normalizer.normalize('''
            {
              "required": ["id", "name"],
              "properties": {
                "name": { "type": "string" },
                "id": { "type": "integer" }
              },
              "type": "object"
            }
            ''')

        then:
        normalized == '{"properties":{"id":{"type":"integer"},"name":{"type":"string"}},"required":["id","name"],"type":"object"}'
    }

    void "compares equivalent schemas after normalization"() {
        expect:
        normalizer.equivalent(
                '{"type":"object","properties":{"id":{"type":"integer"}}}',
                '{"properties":{"id":{"type":"integer"}},"type":"object"}'
        )
    }

    void "preserves null values during normalization"() {
        expect:
        normalizer.normalize('{"type":"object","default":null,"properties":{"value":{"const":null}}}') ==
                '{"default":null,"properties":{"value":{"const":null}},"type":"object"}'
    }

    void "sorts object keys by unicode code point order"() {
        given:
        String privateUseKey = "\uE000"
        String supplementaryKey = new String(Character.toChars(0x10000))

        when:
        String normalized = normalizer.normalize("{\"${supplementaryKey}\":1,\"${privateUseKey}\":2}")

        then:
        normalized.indexOf("\"${privateUseKey}\"") < normalized.indexOf("\"${supplementaryKey}\"")
    }
}
