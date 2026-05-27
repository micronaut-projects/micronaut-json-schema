package io.micronaut.jsonschema.generator

import com.github.javaparser.ast.body.RecordDeclaration

class OracleRecordProfileSpec extends AbstractGeneratorSpec {

    void "oracle profile generates JsonSchema records with default type mappings"() {
        when:
        def type = (RecordDeclaration) generateType("Product", '''
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
        ''', b -> b
            .withAddGeneratedJsonSchemaAnnotation(true)
            .withBoxOptionalBooleans(true)
            .withTreatAdditionalPropertiesAsField(true)
            .withSortPropertiesByName(true))

        then:
        type.annotations*.nameAsString.containsAll(["Serdeable", "JsonSchema"])
        type.parameters.collect { it.nameAsString } == [
            "active",
            "createdAt",
            "gateway",
            "id",
            "metadata",
            "name",
            "sessionId",
            "website",
            "additionalProperties"
        ]
        type.parameters.find { it.nameAsString == "active" }.typeAsString == "Boolean"
        type.parameters.find { it.nameAsString == "createdAt" }.typeAsString == "ZonedDateTime"
        type.parameters.find { it.nameAsString == "gateway" }.typeAsString == "Inet4Address"
        type.parameters.find { it.nameAsString == "id" }.typeAsString == "int"
        type.parameters.find { it.nameAsString == "metadata" }.typeAsString == "Map<String,Object>"
        type.parameters.find { it.nameAsString == "sessionId" }.typeAsString == "UUID"
        type.parameters.find { it.nameAsString == "website" }.typeAsString == "URI"
        type.parameters.find { it.nameAsString == "additionalProperties" }.typeAsString == "Map<String,Object>"
        type.parameters.find { it.nameAsString == "id" }.annotations*.nameAsString.contains("NotNull")
        !type.parameters.find { it.nameAsString == "active" }.annotations*.nameAsString.contains("Nullable")
        !type.parameters.find { it.nameAsString == "metadata" }.annotations*.nameAsString.contains("Nullable")
        type.parameters.find { it.nameAsString == "name" }.annotations*.nameAsString.containsAll(["NotNull", "Size"])
    }

    void "oracle profile sanitizes duality view _id property names and preserves JSON binding"() {
        when:
        def type = (RecordDeclaration) generateType("ApartmentView", '''
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
        ''', b -> b
            .withAddGeneratedJsonSchemaAnnotation(true)
            .withBoxOptionalBooleans(true)
            .withTreatAdditionalPropertiesAsField(true)
            .withSortPropertiesByName(true))

        then:
        type.parameters*.nameAsString.containsAll(["id", "name"])
        type.parameters.find { it.nameAsString == "id" }.annotations*.nameAsString.contains("JsonProperty")
        type.members.find { it instanceof RecordDeclaration && it.nameAsString == "Id" } != null
    }
}
