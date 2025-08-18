/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 ForgeRock AS. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Portions Copyrighted 2025 Nomura Research Institute, Ltd.
 */

package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.objects.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * SchemaDefinition provides cached access to schema metadata for fast attribute processing.
 *
 * @author Hiroyuki Wada
 */
public class SchemaDefinition {

    private static final Log logger = Log.getLog(SchemaDefinition.class);

    private final ObjectClass objectClass;
    private final Set<String> returnedByDefaultAttributesSet;

    public SchemaDefinition(ObjectClassInfo objectClassInfo) {
        this.objectClass = new ObjectClass(objectClassInfo.getType());
        Set<String> tempReturnedByDefault = new HashSet<>();

        // Build attribute metadata cache
        for (AttributeInfo attrInfo : objectClassInfo.getAttributeInfo()) {
            String attrName = attrInfo.getName();
            boolean returnedByDefault = attrInfo.isReturnedByDefault();

            // Cache returned by default attributes
            if (returnedByDefault) {
                tempReturnedByDefault.add(attrName);
            }
        }

        // Make all collections unmodifiable
        this.returnedByDefaultAttributesSet = Collections.unmodifiableSet(tempReturnedByDefault);

        logger.info("SchemaDefinition initialized for {0} with {1} default attributes",
                objectClass.getObjectClassValue(), returnedByDefaultAttributesSet.size());
    }

    /**
     * Create full set of ATTRIBUTES_TO_GET which is composed by RETURN_DEFAULT_ATTRIBUTES + ATTRIBUTES_TO_GET.
     *
     * @param options OperationOptions containing ATTRS_TO_GET and RETURN_DEFAULT_ATTRIBUTES
     * @return Set of ConnID attribute names to retrieve
     */
    public Set<String> createFullAttributesToGet(OperationOptions options) {
        Set<String> attributesToGet = new HashSet<>();

        if (shouldReturnDefaultAttributes(options)) {
            attributesToGet.addAll(returnedByDefaultAttributesSet);
        }

        if (options.getAttributesToGet() != null) {
            for (String a : options.getAttributesToGet()) {
                attributesToGet.add(a);
            }
        }

        // If ATTRS_TO_GET option is not present (also, RETURN_DEFAULT_ATTRIBUTES option is not present too),
        // then the connector should return only those attributes that the resource returns by default.
        if (options.getAttributesToGet() == null && options.getReturnDefaultAttributes() == null) {
            attributesToGet.addAll(returnedByDefaultAttributesSet);
        }

        return attributesToGet.isEmpty() ? null : attributesToGet;
    }


    /**
     * Create Google API fields string from attribute names set.
     * Delegates to SearchUtil for proper field transformations.
     *
     * @param attributesToGet Set of ConnID attribute names to retrieve (never null)
     * @param mandatoryFields Additional mandatory fields to always include
     * @return Comma-separated field names for Google API
     */
    public String createGoogleApiFieldsString(Set<String> attributesToGet, String... mandatoryFields) {
        return getFields(attributesToGet, mandatoryFields);
    }

    /**
     * Get fields string for Google API requests with attribute name transformations.
     */
    private String getFields(Set<String> attributesToGet, String... mandatoryFields) {
        if (null != attributesToGet) {
            Set<String> attributes = new HashSet<>();

            // Add mandatory fields first
            if (mandatoryFields != null) {
                for (String field : mandatoryFields) {
                    if (field != null) {
                        attributes.add(field);
                    }
                }
            }

            // Process requested attributes with transformations
            for (String attribute : attributesToGet) {
                String transformedField = transformAttributeToGoogleField(attribute);
                if (transformedField != null) {
                    attributes.add(transformedField);
                }
            }

            return String.join(",", attributes);
        }
        return null;
    }

    /**
     * Transform ConnID attribute name to Google API field name.
     */
    private String transformAttributeToGoogleField(String attributeName) {
        if (AttributeUtil.namesEqual(PredefinedAttributes.DESCRIPTION, attributeName)) {
            return "description";
        } else if (AttributeUtil.namesEqual("__ENABLE__", attributeName)) {
            if (ObjectClass.ACCOUNT.equals(objectClass)) {
                return "suspended";
            }
        } else if (AttributeUtil.isSpecialName(attributeName)) {
            return null; // Skip __UID__, __NAME__
        } else if (AttributeUtil.namesEqual("familyName", attributeName)) {
            return "name/familyName";
        } else if (AttributeUtil.namesEqual("givenName", attributeName)) {
            return "name/givenName";
        } else if (AttributeUtil.namesEqual("fullName", attributeName)) {
            return "name/fullName";
        } else {
            return attributeName;
        }
        return null;
    }

    /**
     * Check if default attributes should be returned based on operation options.
     */
    private boolean shouldReturnDefaultAttributes(OperationOptions options) {
        Boolean returnDefaultAttributes = options.getReturnDefaultAttributes();
        return returnDefaultAttributes != null && returnDefaultAttributes;
    }
}