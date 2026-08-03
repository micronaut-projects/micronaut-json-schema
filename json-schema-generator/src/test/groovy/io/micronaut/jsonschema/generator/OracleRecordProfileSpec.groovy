package io.micronaut.jsonschema.generator

import com.github.javaparser.ast.body.RecordDeclaration

class OracleRecordProfileSpec extends AbstractGeneratorSpec {

    void "oracle profile generates JsonSchema records with default type mappings"() {
        when:
        def type = (RecordDeclaration) generateRecordProfileType("Product", '''
        {
          "title":"Product",
          "type":"object",
          "properties":{
            "id":{"type":"integer"},
            "name":{"type":"string","minLength":1},
            "createdAt":{"type":"string","format":"date-time"},
            "sessionId":{"type":"string","format":"uuid"},
            "website":{"type":"string","format":"uri"},
            "gateway":{"type":"string","format":"ipv4"},
            "active":{"type":"boolean"},
            "metadata":{
              "type":"object",
              "additionalProperties": true
            }
          },
          "required":["id","name"]
        }
        ''')

        then:
        type.annotations*.nameAsString.containsAll(["Serdeable", "JsonSchema"])
        type.parameters.collect { it.nameAsString } == [
            "id",
            "name",
            "createdAt",
            "sessionId",
            "website",
            "gateway",
            "active",
            "metadata"
        ]
        type.parameters.find { it.nameAsString == "active" }.typeAsString == "Boolean"
        type.parameters.find { it.nameAsString == "createdAt" }.typeAsString == "ZonedDateTime"
        type.parameters.find { it.nameAsString == "gateway" }.typeAsString == "Inet4Address"
        type.parameters.find { it.nameAsString == "id" }.typeAsString == "int"
        type.parameters.find { it.nameAsString == "metadata" }.typeAsString == "Map<String,Object>"
        type.parameters.find { it.nameAsString == "sessionId" }.typeAsString == "UUID"
        type.parameters.find { it.nameAsString == "website" }.typeAsString == "URI"
        type.parameters.find { it.nameAsString == "id" }.annotations*.nameAsString.contains("NotNull")
        !type.parameters.find { it.nameAsString == "active" }.annotations*.nameAsString.contains("Nullable")
        !type.parameters.find { it.nameAsString == "metadata" }.annotations*.nameAsString.contains("Nullable")
        type.parameters.find { it.nameAsString == "name" }.annotations*.nameAsString.containsAll(["NotNull", "Size"])
    }

    void "oracle profile sanitizes duality view _id property names and preserves JSON binding"() {
        when:
        def type = (RecordDeclaration) generateRecordProfileType("ApartmentView", '''
        {
          "title":"ApartmentView",
          "type":"object",
          "properties":{
            "_id":{
              "type":"object",
              "properties":{
                "buildingId":{"type":"integer"},
                "flatId":{"type":"integer"}
              },
              "required":["buildingId","flatId"]
            },
            "name":{"type":"string"}
          },
          "required":["_id","name"]
        }
        ''')

        then:
        type.parameters*.nameAsString.containsAll(["_id", "name"])
        !type.parameters.find { it.nameAsString == "_id" }.annotations*.nameAsString.contains("JsonProperty")
        type.members.find { it instanceof RecordDeclaration && it.nameAsString == "Id" } != null
    }

    void "oracle profile maps nullable duality view oneOf scalar fields"() {
        when:
        def content = generateRecordProfileTypeAndGetContent("ApartmentView", '''
        {
          "title":"ApartmentView",
          "type":"object",
          "properties":{
            "status":{
              "oneOf":[
                {"type":"null","extendedType":"null"},
                {"type":"string","extendedType":"string","maxLength":20}
              ]
            },
            "floorNo":{
              "oneOf":[
                {"type":"null","extendedType":"null"},
                {"type":"number","extendedType":"number","sqlPrecision":10,"sqlScale":0}
              ]
            },
            "createdAt":{
              "oneOf":[
                {"type":"null","extendedType":"null"},
                {"type":"string","extendedType":"timestampTz","sqlPrecision":6}
              ]
            },
            "updatedAt":{"type":"string","extendedType":"timestamp","sqlPrecision":6},
            "businessDate":{"type":"string","extendedType":"date"},
            "elapsed":{"type":"string","extendedType":"dsInterval"},
            "billingPeriod":{"type":"string","extendedType":"ymInterval"},
            "ratio":{"type":"number","extendedType":"double"},
            "sample":{"type":"number","extendedType":"float"},
            "payload":{"type":"string","extendedType":"binary"},
            "arrayExtendedAt":{"type":["string","null"],"extendedType":["null","timestampTz"]},
            "formattedTimestamp":{"type":"string","format":"date-time","extendedType":"timestamp"}
          },
          "additionalProperties": false
        }
        ''')

        then:
        content.contains("@Nullable @Size(max = 20) String status")
        content.contains("@Nullable Float floorNo")
        content.contains("@Nullable ZonedDateTime createdAt")
        content.contains("LocalDateTime updatedAt")
        content.contains("LocalDateTime businessDate")
        content.contains("Duration elapsed")
        content.contains("Period billingPeriod")
        content.contains("Double ratio")
        content.contains("Float sample")
        content.contains("byte[] payload")
        content.contains("@Nullable ZonedDateTime arrayExtendedAt")
        content.contains("ZonedDateTime formattedTimestamp")
    }
}
