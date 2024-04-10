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
package io.micronaut.jsonschema.visitor.aggregator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.JavadocBlockTag;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.jsonschema.visitor.JsonSchemaConfigurationVisitor.JsonSchemaContext;
import io.micronaut.jsonschema.visitor.model.Schema;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An aggregator for adding information from the jackson serialization annotations.
 */
@Internal
public class DocumentationInfoAggregator implements SchemaInfoAggregator {

    @Override
    public Schema addInfo(TypedElement element, Schema schema, VisitorContext visitorContext, JsonSchemaContext context) {
        addElementDoc(element, schema, visitorContext);
        addRecordDocs(element.getGenericType(), schema, visitorContext);
        return schema;
    }

    /**
     * Add description to element based on the javadoc.
     */
    private void addElementDoc(TypedElement element, Schema schema, VisitorContext visitorContext) {
        if ((element instanceof ClassElement) && element.getGenericType().isRecord()) {
            return;
        }
        Optional<String> documentation = element.getDocumentation();
        if (schema.getDescription() == null || !(element instanceof ClassElement)) {
            documentation.ifPresent(schema::setDescription);
        }
        if (schema.getDescription() == null && documentation.isEmpty() && !(element instanceof ClassElement)) {
            documentation = element.getGenericType().getDocumentation();
            documentation.ifPresent(schema::setDescription);
        }
    }

    /**
     * Add record documentation.
     * Description is added to properties based on javadoc {@code @param} blocks.
     */
    private void addRecordDocs(ClassElement element, Schema schema, VisitorContext visitorContext) {
        if (!element.isRecord()) {
            return;
        }
        String javadocString = element.getDocumentation().orElse(null);
        if (javadocString == null) {
            return;
        }
        Javadoc javadoc = StaticJavaParser.parseJavadoc(javadocString);
        if (schema.getDescription() == null && !javadoc.getDescription().isEmpty()) {
            schema.setDescription(javadoc.getDescription().toText());
        }
        Map<String, String> propertiesDescription = new HashMap<>();
        for (JavadocBlockTag block : javadoc.getBlockTags()) {
            if (block.getType() == JavadocBlockTag.Type.PARAM) {
                block.getName().ifPresent(name -> propertiesDescription.put(name, block.getContent().toText()));
            }
        }

        if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            for (PropertyElement property: element.getBeanProperties()) {
                Schema propertySchema = schema.getProperties().get(property.getName());
                if (propertySchema != null && propertiesDescription.containsKey(property.getName())) {
                    propertySchema.setDescription(propertiesDescription.get(property.getName()));
                }
            }
        }
    }

}
