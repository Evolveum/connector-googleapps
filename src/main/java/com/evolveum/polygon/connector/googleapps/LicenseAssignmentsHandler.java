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

import com.google.api.services.licensing.Licensing;
import com.google.api.services.licensing.LicensingRequest;
import com.google.api.services.licensing.model.LicenseAssignment;
import com.google.api.services.licensing.model.LicenseAssignmentInsert;
import com.google.api.services.licensing.model.LicenseAssignmentList;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.spi.SearchResultsHandler;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.*;

/**
 * A NAME does ...
 *
 * @author Laszlo Hordos
 */
public class LicenseAssignmentsHandler {

    /**
     * Setup logging for the {@link LicenseAssignmentsHandler}.
     */
    private static final Log logger = Log.getLog(LicenseAssignmentsHandler.class);

    // /////////////
    //
    // LicenseAssignment
    // https://developers.google.com/admin-sdk/licensing/v1/reference/licenseAssignments
    //
    // /////////////

    public static ObjectClassInfo getLicenseAssignmentClassInfo() {
        // @formatter:off
            /*
            {
			  "kind": "licensing#licenseAssignment",
			  "etags": etag,
			  "selfLink": string,
			  "userId": string,
			  "productId": string,
			  "skuId": string
			}
            */
        // @formatter:on
        ObjectClassInfoBuilder builder = new ObjectClassInfoBuilder();
        builder.setType(LICENSE_ASSIGNMENT.getObjectClassValue());
        // productId
        builder.addAttributeInfo(AttributeInfoBuilder.define(PRODUCT_ID_ATTR).setRequired(true)
                .build());
        // skuId
        builder.addAttributeInfo(AttributeInfoBuilder.define(SKU_ID_ATTR).setRequired(true).build());
        // userId
        builder.addAttributeInfo(AttributeInfoBuilder.define(USER_ID_ATTR).setRequired(true)
                .build());

        // optional
        builder.addAttributeInfo(AttributeInfoBuilder.define(SELF_LINK_ATTR).setCreateable(false)
                .setUpdateable(false).build());

        return builder.build();
    }

    public static Licensing.LicenseAssignments.Insert createLicenseAssignment(
            Licensing.LicenseAssignments service, AttributesAccessor attributes) {

        String productId = attributes.findString(PRODUCT_ID_ATTR);
        if (StringUtil.isBlank(productId)) {
            throw new InvalidAttributeValueException(
                    "Missing required attribute 'productId'. A product's unique identifier. Required when creating a LicenseAssignment.");
        }

        String skuId = attributes.findString(SKU_ID_ATTR);
        if (StringUtil.isBlank(skuId)) {
            throw new InvalidAttributeValueException(
                    "Missing required attribute 'skuId'. A product SKU's unique identifier. Required when creating a LicenseAssignment.");
        }

        String userId = attributes.findString(USER_ID_ATTR);
        if (StringUtil.isBlank(userId)) {
            throw new InvalidAttributeValueException(
                    "Missing required attribute 'userId'. The user's current primary email address. Required when creating a LicenseAssignment.");
        }

        return createLicenseAssignment(service, productId, skuId, userId);
    }

    public static Licensing.LicenseAssignments.Insert createLicenseAssignment(
            Licensing.LicenseAssignments service, String productId, String skuId, String userId) {
        try {
            LicenseAssignmentInsert resource = new LicenseAssignmentInsert();
            resource.setUserId(userId);
            return service.insert(productId, skuId, resource).setFields(PRODUCT_ID_SKU_ID_USER_ID);
            // } catch (HttpResponseException e){
        } catch (IOException e) {
            logger.warn(e, "Failed to initialize LicenseAssignments#Insert");
            throw ConnectorException.wrap(e);
        }
    }

    public static final Pattern LICENSE_NAME_PATTERN =
            Pattern.compile("(?i)(Google-Coordinate|Google-Drive-storage|Google-Vault)\\/sku\\/(Google-Coordinate|Google-Drive-storage-20GB|Google-Drive-storage-50GB|Google-Drive-storage-200GB|Google-Drive-storage-400GB|Google-Drive-storage-1TB|Google-Drive-storage-2TB|Google-Drive-storage-4TB|Google-Drive-storage-8TB|Google-Drive-storage-16TB|Google-Vault)\\/user\\/(.+)");


    public static Licensing.LicenseAssignments.Delete deleteLicenseAssignment(
            Licensing.LicenseAssignments service, String groupKey) {

        Matcher name = LICENSE_NAME_PATTERN.matcher(groupKey);
        if (!name.matches()) {
            throw new UnknownUidException("Unrecognised id");
        }

        String productId = name.group(1);
        String skuId = name.group(2);
        String userId = name.group(3);

        try {
            return service.delete(productId, skuId, userId);
            // } catch (HttpResponseException e){
        } catch (IOException e) {
            logger.warn(e, "Failed to initialize LicenseAssignments#Delete");
            throw ConnectorException.wrap(e);
        }
    }
 
    public static ConnectorObject fromLicenseAssignment(LicenseAssignment content) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(LICENSE_ASSIGNMENT);
        Uid uid = generateLicenseAssignmentId(content);
        builder.setUid(uid);
        builder.setName(uid.getUidValue());

        builder.addAttribute(AttributeBuilder.build(SELF_LINK_ATTR, content.getSelfLink()));
        builder.addAttribute(AttributeBuilder.build(USER_ID_ATTR, content.getUserId()));
        builder.addAttribute(AttributeBuilder.build(PRODUCT_ID_ATTR, content.getProductId()));
        builder.addAttribute(AttributeBuilder.build(SKU_ID_ATTR, content.getSkuId()));

        return builder.build();
    }

    public static Uid generateLicenseAssignmentId(LicenseAssignment content) {
        String id =
                content.getProductId() + "/sku/" + content.getSkuId() + "/user/"
                        + content.getUserId();
        if (null != content.getEtags()) {
            return new Uid(id, content.getEtags(), new Name(content.getUserId()));
        } else {
            return new Uid(id, new Name(content.getUserId()));
        }
    }

    /**
     * Execute LicenseAssignment updateDelta with attribute delta processing.
     * This handles LicenseAssignment attribute updates (only skuId can be updated).
     */
    public static Set<AttributeDelta> executeLicenseAssignmentUpdateDelta(GoogleApiExecutor executor, Uid uid,
                                                                         Set<AttributeDelta> modifications) {
        final Set<AttributeDelta> sideEffectDeltas = new HashSet<>();
        
        // Update LicenseAssignment attributes using LicenseAssignments.patch API  
        if (!modifications.isEmpty()) {
            final Licensing.LicenseAssignments.Patch patch = buildLicenseAssignmentUpdateRequest(executor.getLicensing().licenseAssignments(), uid, modifications);
            if (patch != null) {
                executor.execute(patch, new RequestResultHandler.NoOp<Licensing.LicenseAssignments.Patch, LicenseAssignment>());
            }
        }
        
        return sideEffectDeltas;
    }

    /**
     * Build LicenseAssignment update request from attribute deltas.
     * Only skuId can be updated for LicenseAssignment.
     */
    private static Licensing.LicenseAssignments.Patch buildLicenseAssignmentUpdateRequest(Licensing.LicenseAssignments service, Uid uid, Set<AttributeDelta> deltas) {
        LicenseAssignment content = null;
        
        // Parse the uid to get productId, oldSkuId, and userId
        Matcher name = LICENSE_NAME_PATTERN.matcher(uid.getUidValue());
        if (!name.matches()) {
            throw new UnknownUidException("Unrecognised id: " + uid.getUidValue());
        }
        
        String productId = name.group(1);
        String oldSkuId = name.group(2);  
        String userId = name.group(3);
        
        // Check for skuId updates
        for (AttributeDelta delta : deltas) {
            String attributeName = delta.getName();
            
            if (SKU_ID_ATTR.equals(attributeName)) {
                String newSkuId = RequestResultHandler.getSingleValue(delta, String.class);
                if (newSkuId != null && !oldSkuId.equalsIgnoreCase(newSkuId)) {
                    content = new LicenseAssignment();
                    content.setSkuId(newSkuId);
                }
            }
            // productId and userId are read-only and cannot be updated
        }
        
        if (content == null) {
            return null; // No changes or same skuId
        }
        
        try {
            return service.patch(productId, oldSkuId, userId, content);
        } catch (IOException e) {
            logger.warn(e, "Failed to create license assignment patch request");
            throw ConnectorException.wrap(e);
        }
    }

    /**
     * Execute LicenseAssignment read query by UID.
     */
    public static void executeLicenseAssignmentReadQuery(GoogleApiExecutor executor, Uid uid, final ResultsHandler handler) {
        try {
            Matcher name = LICENSE_NAME_PATTERN.matcher(uid.getUidValue());
            if (!name.matches()) {
                return;
            }

            String productId = name.group(1);
            String skuId = name.group(2);
            String userId = name.group(3);

            Licensing.LicenseAssignments.Get request
                    = executor.getLicensing().licenseAssignments().get(productId, skuId,
                    userId);

            executor.execute(request,
                    new RequestResultHandler.ReadQuery<>(
                        LicenseAssignmentsHandler::fromLicenseAssignment,
                        null, // No cache for LicenseAssignments
                        handler));

        } catch (IOException e) {
            logger.warn(e, "Failed to initialize Groups#Get");
            throw ConnectorException.wrap(e);
        }
    }

    /**
     * Execute LicenseAssignment search query.
     */
    public static void executeLicenseAssignmentSearchQuery(GoogleApiExecutor executor, final ResultsHandler handler, OperationOptions options) {
        try {

            String productId = "";
            String skuId = "";

            boolean paged = false;

            LicensingRequest<LicenseAssignmentList> request = null;

            if (StringUtil.isBlank(productId)) {
                // TODO iterate over the three productids
                throw new ConnectorException("productId is required");
            } else if (StringUtil.isBlank(skuId)) {
                Licensing.LicenseAssignments.ListForProduct r
                        = executor.getLicensing().licenseAssignments().listForProduct(
                        productId, MY_CUSTOMER_ID);

                if (options.getPageSize() != null && 0 < options.getPageSize()) {
                    r.setMaxResults(Long.valueOf(options.getPageSize()));
                    paged = true;
                }
                r.setPageToken(options.getPagedResultsCookie());
                request = r;
            } else {
                Licensing.LicenseAssignments.ListForProductAndSku r
                        = executor.getLicensing().licenseAssignments()
                        .listForProductAndSku(productId, skuId, MY_CUSTOMER_ID);

                if (options.getPageSize() != null && 0 < options.getPageSize()) {
                    r.setMaxResults(Long.valueOf(options.getPageSize()));
                    paged = true;
                }
                r.setPageToken(options.getPagedResultsCookie());
                request = r;
            }

            String nextPageToken = null;
            do {
                //TODO license request has no page token property. How to page?
                /*if(StringUtil.isNotBlank(nextPageToken)){
                    request.setPageToken(nextPageToken);
                }*/
                nextPageToken
                        = executor.execute(request,
                        new RequestResultHandler<LicensingRequest<LicenseAssignmentList>, LicenseAssignmentList, String>() {
                            public String handleResult(LicensingRequest request,
                                                       final LicenseAssignmentList value) {
                                if (null != value.getItems()) {
                                    for (LicenseAssignment resource : value
                                            .getItems()) {
                                        handler.handle(fromLicenseAssignment(resource));
                                    }
                                }
                                return value.getNextPageToken();
                            }
                        });
                if (request instanceof Licensing.LicenseAssignments.ListForProduct) {
                    ((Licensing.LicenseAssignments.ListForProduct) request).setPageToken(nextPageToken);
                } else {
                    ((Licensing.LicenseAssignments.ListForProductAndSku) request).setPageToken(nextPageToken);
                }
            } while (!paged && StringUtil.isNotBlank(nextPageToken));

            if (paged && StringUtil.isNotBlank(nextPageToken)) {
                logger.info("Paged Search was requested");
                ((SearchResultsHandler) handler).handleResult(new SearchResult(
                        nextPageToken, 0));
            }

        } catch (IOException e) {
            logger.warn(e, "Failed to initialize Groups#List");
            throw ConnectorException.wrap(e);
        }
    }

    /**
     * Execute LicenseAssignment create operation.
     */
    public static Uid executeLicenseAssignmentCreate(GoogleApiExecutor executor, Set<Attribute> createAttributes) {
        final AttributesAccessor accessor = new AttributesAccessor(createAttributes);
        
        return executor.execute(
                createLicenseAssignment(executor.getLicensing().licenseAssignments(), accessor),
                new RequestResultHandler.Create<>(LICENSE_ASSIGNMENT, 
                    LicenseAssignmentsHandler::generateLicenseAssignmentId));
    }

    /**
     * Execute LicenseAssignment delete operation.
     */
    public static void executeLicenseAssignmentDelete(GoogleApiExecutor executor, Uid uid) {
        Licensing.LicenseAssignments.Delete deleteRequest = deleteLicenseAssignment(executor.getLicensing().licenseAssignments(), uid.getUidValue());

        executor.execute(deleteRequest,
                new RequestResultHandler<Licensing.LicenseAssignments.Delete, Void, Void>() {
                    public Void handleResult(Licensing.LicenseAssignments.Delete request,
                                             Void value) {
                        return null;
                    }

                    public Void handleNotFound(final IOException e) {
                        throw new UnknownUidException(uid, LICENSE_ASSIGNMENT);
                    }
                });
    }

    /**
     * Unified query execution method that handles both read and search operations.
     */
    public static void executeQuery(GoogleApiExecutor executor, GoogleFilter googleFilter, 
                                   ResultsHandler handler, OperationOptions options, SchemaDefinition schemaDef) {
        if (googleFilter.isReadByUid()) {
            // Read request by UID
            executeLicenseAssignmentReadQuery(executor, googleFilter.getUid(), handler);
        } else {
            // Search query (including list all when filter is null)
            executeLicenseAssignmentSearchQuery(executor, handler, options);
        }
    }

}
