/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 ForgeRock AS. All rights reserved.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://opensource.org/licenses/CDDL-1.0
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://opensource.org/licenses/CDDL-1.0
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */
package com.evolveum.polygon.connector.googleapps;

import com.evolveum.polygon.connector.googleapps.cache.ConnectorObjectsCache;
import com.google.api.services.directory.Directory;
import com.google.api.services.directory.model.Users;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.PoolableConnector;
import org.identityconnectors.framework.spi.operations.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.*;
import static com.evolveum.polygon.connector.googleapps.GroupHandler.getGroupClassInfo;
import static com.evolveum.polygon.connector.googleapps.LicenseAssignmentsHandler.getLicenseAssignmentClassInfo;
import static com.evolveum.polygon.connector.googleapps.MemberHandler.getMemberClassInfo;
import static com.evolveum.polygon.connector.googleapps.OrgunitsHandler.getOrgunitClassInfo;
import static com.evolveum.polygon.connector.googleapps.UserHandler.getUserClassInfo;

/**
 * Main implementation of the GoogleApps Connector.
 */
@ConnectorClass(displayNameKey = "GoogleApps.connector.display",
        configurationClass = GoogleAppsConfiguration.class)
public class GoogleAppsConnector implements Connector, CreateOp, DeleteOp, SchemaOp,
        SearchOp<GoogleFilter>, TestOp, UpdateDeltaOp, PoolableConnector {

    /**
     * Setup logging for the {@link GoogleAppsConnector}.
     */
    private static final Log logger = Log.getLog(GoogleAppsConnector.class);

    /**
     * Place holder for the {@link Configuration} passed into the init() method
     * {@link GoogleAppsConnector#init(org.identityconnectors.framework.spi.Configuration)}
     * .
     */
    private GoogleAppsConfiguration configuration;
    private ConnectorObjectsCache objectsCache;
    private Schema schema = null;
    private Map<ObjectClass, SchemaDefinition> schemaCache = new HashMap<>();
    private GoogleApiExecutor apiExecutor;

    @Override
    public Configuration getConfiguration() {
        return this.configuration;
    }

    @Override
    public void init(final Configuration configuration) {
        this.configuration = (GoogleAppsConfiguration) configuration;
        this.objectsCache = ConnectorObjectsCache.getInstance(this.configuration, logger);
        this.apiExecutor = new GoogleApiExecutor(this.configuration);
    }

    @Override
    public void dispose() {
        configuration = null;
        objectsCache = null;
        schema = null;
        schemaCache.clear();
        apiExecutor = null;
    }

    /**
     * ****************
     * SPI Operations
     * <p>
     * Implement the following operations using the contract and description
     * found in the Javadoc for these methods. ****************
     */
    @Override
    public Uid create(final ObjectClass objectClass, final Set<Attribute> createAttributes,
                      final OperationOptions options) {
        if (ObjectClass.ACCOUNT.equals(objectClass)) {
            // Delegate User creation to UserHandler (includes license assignment if configured)
            return UserHandler.executeUserCreate(apiExecutor, createAttributes);

        } else if (ObjectClass.GROUP.equals(objectClass)) {
            // Delegate Group creation to GroupHandler (member addition removed as not needed)
            return GroupHandler.executeGroupCreate(apiExecutor, createAttributes);

        } else if (MEMBER.equals(objectClass)) {
            // Delegate Member creation to MemberHandler
            return MemberHandler.executeMemberCreate(apiExecutor, createAttributes);

        } else if (ORG_UNIT.equals(objectClass)) {
            // Delegate OrgUnit creation to OrgunitsHandler
            return OrgunitsHandler.executeOrgUnitCreate(apiExecutor, createAttributes);

        } else if (LICENSE_ASSIGNMENT.equals(objectClass)) {
            // Delegate LicenseAssignment creation to LicenseAssignmentsHandler
            return LicenseAssignmentsHandler.executeLicenseAssignmentCreate(apiExecutor, createAttributes);

        } else {
            logger.warn("Create of type {0} is not supported", configuration.getConnectorMessages()
                    .format(objectClass.getDisplayNameKey(), objectClass.getObjectClassValue()));
            throw new UnsupportedOperationException("Create of type"
                    + objectClass.getObjectClassValue() + " is not supported");
        }
    }

    @Override
    public void delete(final ObjectClass objectClass, final Uid uid, final OperationOptions options) {
        if (ObjectClass.ACCOUNT.equals(objectClass)) {
            // Delegate Account deletion to UserHandler
            UserHandler.executeAccountDelete(apiExecutor, objectsCache, uid);

        } else if (ObjectClass.GROUP.equals(objectClass)) {
            // Delegate Group deletion to GroupHandler
            GroupHandler.executeGroupDelete(apiExecutor, objectsCache, uid);

        } else if (MEMBER.equals(objectClass)) {
            // Delegate Member deletion to MemberHandler
            MemberHandler.executeMemberDelete(apiExecutor, uid);

        } else if (ORG_UNIT.equals(objectClass)) {
            // Delegate OrgUnit deletion to OrgunitsHandler
            OrgunitsHandler.executeOrgUnitDelete(apiExecutor, uid);

        } else if (LICENSE_ASSIGNMENT.equals(objectClass)) {
            // Delegate LicenseAssignment deletion to LicenseAssignmentsHandler
            LicenseAssignmentsHandler.executeLicenseAssignmentDelete(apiExecutor, uid);

        } else {
            logger.warn("Delete of type {0} is not supported", configuration.getConnectorMessages()
                    .format(objectClass.getDisplayNameKey(), objectClass.getObjectClassValue()));
            throw new UnsupportedOperationException("Delete of type"
                    + objectClass.getObjectClassValue() + " is not supported");
        }
    }

    @Override
    public Schema schema() {
        if (null == schema) {
            final SchemaBuilder builder = new SchemaBuilder(GoogleAppsConnector.class);

            ObjectClassInfo user = getUserClassInfo();
            builder.defineObjectClass(user);

            ObjectClassInfo group = getGroupClassInfo();
            builder.defineObjectClass(group);

            ObjectClassInfo member = getMemberClassInfo();
            builder.defineObjectClass(member);

            ObjectClassInfo orgUnit = getOrgunitClassInfo();
            builder.defineObjectClass(orgUnit);

            ObjectClassInfo licenseAssignment = getLicenseAssignmentClassInfo();
            builder.defineObjectClass(licenseAssignment);

            builder.defineOperationOption(OperationOptionInfoBuilder.buildAttributesToGet(),
                    SearchOp.class);
            builder.defineOperationOption(OperationOptionInfoBuilder.buildAllowPartialAttributeValues(),
                    SearchOp.class);
            builder.defineOperationOption(OperationOptionInfoBuilder.buildPageSize(),
                    SearchOp.class);
            builder.defineOperationOption(OperationOptionInfoBuilder.buildPagedResultsOffset(),
                    SearchOp.class);
            builder.defineOperationOption(OperationOptionInfoBuilder.buildPagedResultsCookie(),
                    SearchOp.class);
            builder.defineOperationOption(OperationOptionInfoBuilder.buildSortKeys(),
                    SearchOp.class);
            builder.defineOperationOption(OperationOptionInfoBuilder.buildReturnDefaultAttributes(),
                    SearchOp.class);
            builder.defineOperationOption(
                    new OperationOptionInfo(SHOW_DELETED_PARAM, Boolean.class), SearchOp.class);

            schema = builder.build();

            // Initialize schema cache
            initializeSchemaCache();
        }
        return schema;
    }

    @Override
    public FilterTranslator<GoogleFilter> createFilterTranslator(ObjectClass objectClass,
                                                                 OperationOptions options) {
        return new GoogleFilterTranslator();
    }

    @Override
    public void executeQuery(ObjectClass objectClass, GoogleFilter filter, final ResultsHandler handler,
                             OperationOptions options) {
        final long startTime = System.currentTimeMillis();

        // Get schema definition for this object class
        SchemaDefinition schemaDef = getSchemaDefinition(objectClass);

        // Use EMPTY filter if null was passed
        if (filter == null) {
            filter = GoogleFilter.EMPTY;
        }

        logger.info("executeQuery() - objectClass: " + objectClass +
                ", uid: " + (filter.isReadByUid() ? filter.getUid().getUidValue() : "null") +
                ", name: " + (filter.isReadByName() ? filter.getName().getNameValue() : "null"));

        if (ObjectClass.ACCOUNT.equals(objectClass)) {
            UserHandler.executeQuery(apiExecutor, objectsCache, filter, handler, options, schemaDef);

        } else if (ObjectClass.GROUP.equals(objectClass)) {
            GroupHandler.executeQuery(apiExecutor, objectsCache, filter, handler, options, schemaDef);

        } else if (MEMBER.equals(objectClass)) {
            // Handle Member search with special groupKey extraction
            if (filter.hasSearchQuery()) {
                // Extract groupKey from search filter for Member operations
                String groupKey = extractGroupKeyFromFilter(filter.getFilter());
                if (groupKey != null) {
                    MemberHandler.executeQueryWithGroupKey(apiExecutor, groupKey, handler, options, schemaDef);
                } else {
                    MemberHandler.executeQuery(apiExecutor, filter, handler, options, schemaDef);
                }
            } else {
                MemberHandler.executeQuery(apiExecutor, filter, handler, options, schemaDef);
            }

        } else if (ORG_UNIT.equals(objectClass)) {
            OrgunitsHandler.executeQuery(apiExecutor, filter, handler, options, schemaDef);

        } else if (LICENSE_ASSIGNMENT.equals(objectClass)) {
            LicenseAssignmentsHandler.executeQuery(apiExecutor, filter, handler, options, schemaDef);

        } else {
            logger.warn("Search of type {0} is not supported", configuration.getConnectorMessages()
                    .format(objectClass.getDisplayNameKey(), objectClass.getObjectClassValue()));
            throw new UnsupportedOperationException("Search of type"
                    + objectClass.getObjectClassValue() + " is not supported");
        }

        logger.info("executeQuery() - finished in " + timeFrom(startTime));
    }

    @Override
    public void test() {
        logger.info("Testing connection... ");
        try {
            // Test connection with minimal data fetch (only 1 user, only id field)
            Directory.Users.List request = configuration.getDirectory().users().list()
                    .setCustomer(MY_CUSTOMER_ID)
                    .setMaxResults(1)
                    .setFields("users(id)");
            apiExecutor.execute(request, new RequestResultHandler.NoOp<Directory.Users.List, Users>());
        } catch (IOException e) {
            logger.error("failed: {0}", e);
            throw ConnectorException.wrap(e);
        }
        logger.info("OK.");
    }

    @Override
    public Set<AttributeDelta> updateDelta(ObjectClass objclass, Uid uid, Set<AttributeDelta> modifications, OperationOptions options) {
        if (ObjectClass.ACCOUNT.equals(objclass)) {
            // Delegate User updateDelta to UserHandler
            Set<AttributeDelta> sideEffectDeltas = UserHandler.executeUserUpdateDelta(apiExecutor, uid, modifications);

            // Update cache
            objectsCache.markUserAsUpdatedNow(uid.getUidValue());

            return sideEffectDeltas;

        } else if (ObjectClass.GROUP.equals(objclass)) {
            // Delegate Group updateDelta to GroupHandler
            Set<AttributeDelta> sideEffectDeltas = GroupHandler.executeGroupUpdateDelta(apiExecutor, uid, modifications);

            // Update cache
            objectsCache.markGroupAsUpdatedNow(uid.getUidValue());

            return sideEffectDeltas;
        } else if (MEMBER.equals(objclass)) {
            // Delegate MEMBER updateDelta to MemberHandler
            return MemberHandler.executeMemberUpdateDelta(apiExecutor, uid, modifications);

        } else if (ORG_UNIT.equals(objclass)) {
            // Delegate ORG_UNIT updateDelta to OrgunitsHandler
            return OrgunitsHandler.executeOrgunitUpdateDelta(apiExecutor, uid, modifications);

        } else if (LICENSE_ASSIGNMENT.equals(objclass)) {
            // Delegate LICENSE_ASSIGNMENT updateDelta to LicenseAssignmentsHandler
            return LicenseAssignmentsHandler.executeLicenseAssignmentUpdateDelta(apiExecutor, uid, modifications);

        } else {
            logger.warn("UpdateDelta of type {0} is not supported", configuration.getConnectorMessages()
                    .format(objclass.getDisplayNameKey(), objclass.getObjectClassValue()));
            throw new UnsupportedOperationException("UpdateDelta of type"
                    + objclass.getObjectClassValue() + " is not supported");
        }
    }

    /**
     * Initialize schema cache for fast attribute metadata access.
     */
    private void initializeSchemaCache() {
        for (ObjectClassInfo oci : schema.getObjectClassInfo()) {
            ObjectClass objectClass = new ObjectClass(oci.getType());
            SchemaDefinition schemaDef = new SchemaDefinition(oci);
            schemaCache.put(objectClass, schemaDef);
        }
        logger.info("Schema cache initialized for {0} object classes", schemaCache.size());
    }

    /**
     * Get schema definition for the specified object class.
     */
    public SchemaDefinition getSchemaDefinition(ObjectClass objectClass) {
        SchemaDefinition schemaDef = schemaCache.get(objectClass);
        if (schemaDef == null) {
            // If schema cache is empty, initialize it first
            if (schemaCache.isEmpty()) {
                schema(); // This will initialize the schema cache
                schemaDef = schemaCache.get(objectClass);
            }
        }
        return schemaDef;
    }

    /**
     * Get the cached Google API executor instance.
     */
    public GoogleApiExecutor getApiExecutor() {
        return apiExecutor;
    }

    /**
     * Extract groupKey from Member search filter.
     * Used for Member operations that require groupKey parameter.
     */
    private String extractGroupKeyFromFilter(org.identityconnectors.framework.common.objects.filter.Filter filter) {
        if (filter instanceof org.identityconnectors.framework.common.objects.filter.EqualsFilter) {
            org.identityconnectors.framework.common.objects.filter.EqualsFilter equalsFilter =
                    (org.identityconnectors.framework.common.objects.filter.EqualsFilter) filter;
            if (GROUP_KEY_ATTR.equals(equalsFilter.getAttribute().getName())) {
                Object value = equalsFilter.getAttribute().getValue().get(0);
                return value != null ? value.toString() : null;
            }
        }
        return null;
    }

    @Override
    public void checkAlive() {
        // Do nothing
    }

    private String timeFrom(long startTime) {
        return (System.currentTimeMillis() - startTime) + " ms";
    }
}