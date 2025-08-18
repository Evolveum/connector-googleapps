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
 */

package com.evolveum.polygon.connector.googleapps;

import com.google.api.client.googleapis.services.json.AbstractGoogleJsonClientRequest;
import com.google.api.services.directory.Directory;
import com.google.api.services.directory.model.OrgUnit;
import com.google.api.services.directory.model.OrgUnits;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.StartsWithFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.*;

/**
 * OrgunitsHandler is a util class to cover all Organizations Unit related
 * operations.
 *
 * @author Laszlo Hordos
 */
public class OrgunitsHandler {

    /**
     * Setup logging for the {@link OrgunitsHandler}.
     */
    private static final Log logger = Log.getLog(OrgunitsHandler.class);

    // /////////////
    //
    // ORGUNIT
    // https://developers.google.com/admin-sdk/directory/v1/reference/orgunits
    //
    // /////////////

    public static ObjectClassInfo getOrgunitClassInfo() {
        // @formatter:off
            /*
            {
			  "kind": "admin#directory#orgUnit",
			  "etag": etag,
			  "name": string,
			  "description": string,
			  "orgUnitPath": string,
			  "parentOrgUnitPath": string,
			  "blockInheritance": boolean
			}
            */
        // @formatter:on
        ObjectClassInfoBuilder builder = new ObjectClassInfoBuilder();
        builder.setType(ORG_UNIT.getObjectClassValue());
        builder.setContainer(true);
        // primaryEmail
        builder.addAttributeInfo(Name.INFO);
        // parentOrgUnitPath
        builder.addAttributeInfo(AttributeInfoBuilder.define(PARENT_ORG_UNIT_PATH_ATTR)
                .setRequired(true).build());

        // optional
        builder.addAttributeInfo(PredefinedAttributeInfos.DESCRIPTION);
        builder.addAttributeInfo(AttributeInfoBuilder.build(ORG_UNIT_PATH_ATTR));
        builder.addAttributeInfo(AttributeInfoBuilder.build(BLOCK_INHERITANCE_ATTR, Boolean.class));

        return builder.build();
    }

    public static Directory.Orgunits.Insert createOrgunit(Directory.Orgunits service,
                                                          AttributesAccessor attributes) {

        OrgUnit resource = new OrgUnit();

        resource.setName(GoogleAppsUtil.getName(attributes.getName()));

        // parentOrgUnitPath The organization unit's parent path. For example,
        // /corp/sales is the parent path for /corp/sales/sales_support
        // organization unit.
        String parentOrgUnitPath = attributes.findString(PARENT_ORG_UNIT_PATH_ATTR);
        if (StringUtil.isNotBlank(parentOrgUnitPath)) {
            if (parentOrgUnitPath.charAt(0) != '/') {
                parentOrgUnitPath = "/" + parentOrgUnitPath;
            }
            resource.setParentOrgUnitPath(parentOrgUnitPath);
        } else {
            throw new InvalidAttributeValueException(
                    "Missing required attribute 'parentOrgUnitPath'. The organization unit's parent path. Required when creating an orgunit.");
        }

        // Optional
        resource.setBlockInheritance(attributes.findBoolean(BLOCK_INHERITANCE_ATTR));
        resource.setDescription(attributes.findString(PredefinedAttributes.DESCRIPTION));

        try {
            return service.insert(MY_CUSTOMER_ID, resource).setFields(ORG_UNIT_PATH_ETAG);
            // } catch (HttpResponseException e){
        } catch (IOException e) {
            logger.warn(e, "Failed to initialize Groups#Insert");
            throw ConnectorException.wrap(e);
        }
    }

    public static ConnectorObject fromOrgunit(OrgUnit content, Set<String> attributesToGet) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(ORG_UNIT);

        builder.setUid(generateOrgUnitId(content));
        builder.setName(content.getName());

        // Optional

        if (null == attributesToGet || attributesToGet.contains(PredefinedAttributes.DESCRIPTION)) {
            builder.addAttribute(AttributeBuilder.build(PredefinedAttributes.DESCRIPTION, content
                    .getDescription()));
        }
        if (null == attributesToGet || attributesToGet.contains(ORG_UNIT_PATH_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(ORG_UNIT_PATH_ATTR, content
                    .getOrgUnitPath()));
        }
        if (null == attributesToGet || attributesToGet.contains(PARENT_ORG_UNIT_PATH_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(PARENT_ORG_UNIT_PATH_ATTR, content
                    .getParentOrgUnitPath()));
        }
        if (null == attributesToGet || attributesToGet.contains(BLOCK_INHERITANCE_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(BLOCK_INHERITANCE_ATTR, content
                    .getBlockInheritance()));
        }

        return builder.build();
    }

    public static Uid generateOrgUnitId(OrgUnit content) {
        Uid uid = null;
        String orgUnitPath = content.getOrgUnitPath();
        if (orgUnitPath.startsWith("/")) {
            orgUnitPath = orgUnitPath.substring(1);
        }

        if (null != content.getEtag()) {
            uid = new Uid(orgUnitPath, content.getEtag(), new Name(content.getName()));
        } else {
            uid = new Uid(orgUnitPath, new Name(content.getName()));
        }
        return uid;
    }

    /**
     * Execute OrgUnit updateDelta with attribute delta processing.
     * This handles OrgUnit attribute updates (name, description, parentOrgUnitPath, blockInheritance).
     */
    public static Set<AttributeDelta> executeOrgunitUpdateDelta(GoogleApiExecutor executor, Uid uid,
                                                                Set<AttributeDelta> modifications) {
        final Set<AttributeDelta> sideEffectDeltas = new HashSet<>();

        // Update OrgUnit attributes using Orgunits.patch API
        if (!modifications.isEmpty()) {
            final Directory.Orgunits.Patch patch = buildOrgunitUpdateRequest(executor.getDirectory().orgunits(), uid, modifications);
            if (patch != null) {
                executor.execute(patch, new RequestResultHandler.NoOp<Directory.Orgunits.Patch, OrgUnit>());
            }
        }

        return sideEffectDeltas;
    }

    /**
     * Build OrgUnit update request from attribute deltas.
     */
    private static Directory.Orgunits.Patch buildOrgunitUpdateRequest(Directory.Orgunits service, Uid uid, Set<AttributeDelta> deltas) {
        OrgUnit resource = new OrgUnit();
        boolean hasChanges = false;

        for (AttributeDelta delta : deltas) {
            String attributeName = delta.getName();

            // Handle single-valued attributes
            if (Name.NAME.equals(attributeName)) {
                String value = RequestResultHandler.getSingleValue(delta, String.class);
                resource.setName(value);
                hasChanges = true;
            } else if (PredefinedAttributes.DESCRIPTION.equals(attributeName)) {
                String value = RequestResultHandler.getSingleValue(delta, String.class);
                resource.setDescription(value != null ? value : EMPTY_STRING);
                hasChanges = true;
            } else if (PARENT_ORG_UNIT_PATH_ATTR.equals(attributeName)) {
                String value = RequestResultHandler.getSingleValue(delta, String.class);
                if (value != null) {
                    // parentOrgUnitPath cannot be null - only update if not null
                    if (StringUtil.isNotBlank(value)) {
                        if (value.charAt(0) != '/') {
                            value = "/" + value;
                        }
                        resource.setParentOrgUnitPath(value);
                        hasChanges = true;
                    }
                }
                // Note: parentOrgUnitPath cannot be cleared (set to null)
            } else if (BLOCK_INHERITANCE_ATTR.equals(attributeName)) {
                Boolean value = RequestResultHandler.getSingleValue(delta, Boolean.class);
                resource.setBlockInheritance(value != null ? value : Boolean.FALSE);
                hasChanges = true;
            }
        }

        if (!hasChanges) {
            return null;
        }

        try {
            return service.patch(MY_CUSTOMER_ID, uid.getUidValue(), resource).setFields(ORG_UNIT_PATH_ETAG);
        } catch (IOException e) {
            logger.warn(e, "Failed to create orgunit patch request");
            throw ConnectorException.wrap(e);
        }
    }

    /**
     * Execute OrgUnit read query by UID.
     */
    public static void executeOrgUnitReadQuery(GoogleApiExecutor executor, Uid uid,
                                               final ResultsHandler handler, OperationOptions options,
                                               final Set<String> attributesToGet, SchemaDefinition schemaDef) {
        try {
            Directory.Orgunits.Get request = executor.getDirectory().orgunits().get(MY_CUSTOMER_ID, uid.getUidValue());
            String fields = schemaDef.createGoogleApiFieldsString(attributesToGet, ORG_UNIT_PATH_ATTR, ETAG_ATTR, NAME_ATTR);
            request.setFields(fields);

            executor.execute(request,
                    new RequestResultHandler.ReadQuery<>(
                            orgunit -> fromOrgunit(orgunit, attributesToGet),
                            null, // No cache for OrgUnits
                            handler));

        } catch (IOException e) {
            logger.warn(e, "Failed to initialize OrgUnits#Get");
            throw ConnectorException.wrap(e);
        }
    }

    /**
     * Execute OrgUnit search query.
     */
    public static void executeOrgUnitSearchQuery(GoogleApiExecutor executor, Filter query,
                                                 final ResultsHandler handler, OperationOptions options,
                                                 final Set<String> attributesToGet, SchemaDefinition schemaDef) {
        try {
            Directory.Orgunits.List request = executor.getDirectory().orgunits().list(MY_CUSTOMER_ID);
            if (null != query) {
                if (query instanceof StartsWithFilter
                        && AttributeUtil.namesEqual(ORG_UNIT_PATH_ATTR,
                        ((StartsWithFilter) query).getName())) {
                    request.setOrgUnitPath(((StartsWithFilter) query).getValue());
                } else {
                    throw new UnsupportedOperationException(
                            "Only StartsWithFilter('orgUnitPath') is supported");
                }
            } else {
                request.setOrgUnitPath("/");
            }

            String scope = options.getScope();
            if (OperationOptions.SCOPE_OBJECT.equalsIgnoreCase(scope)
                    || OperationOptions.SCOPE_ONE_LEVEL.equalsIgnoreCase(scope)) {
                request.setType("children");
            } else {
                request.setType("all");
            }

            // Implementation to support the 'OP_ATTRIBUTES_TO_GET'
            String fields = schemaDef.createGoogleApiFieldsString(attributesToGet, ORG_UNIT_PATH_ATTR, ETAG_ATTR, NAME_ATTR);
            request.setFields("organizationUnits(" + fields + ")");

            executor.execute(request,
                    new RequestResultHandler<Directory.Orgunits.List, OrgUnits, Void>() {
                        public Void handleResult(final Directory.Orgunits.List request,
                                                 final OrgUnits value) {
                            if (null != value.getOrganizationUnits()) {
                                for (OrgUnit orgunit : value.getOrganizationUnits()) {
                                    handler.handle(fromOrgunit(orgunit, attributesToGet));
                                }
                            }
                            return null;
                        }
                    });

        } catch (IOException e) {
            logger.warn(e, "Failed to initialize OrgUnits#List");
            throw ConnectorException.wrap(e);
        }
    }

    /**
     * Execute OrgUnit create operation.
     */
    public static Uid executeOrgUnitCreate(GoogleApiExecutor executor, Set<Attribute> createAttributes) {
        final AttributesAccessor accessor = new AttributesAccessor(createAttributes);

        return executor.execute(createOrgunit(executor.getDirectory().orgunits(), accessor),
                new RequestResultHandler.Create<>(ORG_UNIT,
                        OrgunitsHandler::generateOrgUnitId));
    }

    /**
     * Execute OrgUnit delete operation.
     */
    public static void executeOrgUnitDelete(GoogleApiExecutor executor, Uid uid) {
        try {
            AbstractGoogleJsonClientRequest<Void> request = executor.getDirectory().orgunits().delete(MY_CUSTOMER_ID, uid.getUidValue());

            executor.execute(request, new RequestResultHandler.Delete(uid, ORG_UNIT));

        } catch (IOException e) {
            throw ConnectorException.wrap(e);
        }
    }

    /**
     * Unified query execution method that handles both read and search operations.
     */
    public static void executeQuery(GoogleApiExecutor executor, GoogleFilter googleFilter,
                                    ResultsHandler handler, OperationOptions options, SchemaDefinition schemaDef) {
        // Get attributes to retrieve - call only once
        final Set<String> attributesToGet = schemaDef.createFullAttributesToGet(options);
        if (googleFilter.isReadByUid()) {
            // Read request by UID
            executeOrgUnitReadQuery(executor, googleFilter.getUid(), handler, options, attributesToGet, schemaDef);
        } else {
            // Search query (including list all when filter has no search query)
            executeOrgUnitSearchQuery(executor, googleFilter.hasSearchQuery() ? googleFilter.getFilter() : null, handler, options, attributesToGet, schemaDef);
        }
    }

}
