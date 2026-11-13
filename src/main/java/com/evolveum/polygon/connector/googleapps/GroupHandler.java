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

import com.evolveum.polygon.connector.googleapps.cache.ConnectorObjectsCache;
import com.google.api.client.googleapis.services.json.AbstractGoogleJsonClientRequest;
import com.google.api.client.util.Data;
import com.google.api.services.directory.Directory;
import com.google.api.services.directory.model.Group;
import com.google.api.services.directory.model.Groups;
import com.google.api.services.directory.model.Member;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.AttributesAccessor;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.PredefinedAttributeInfos;
import org.identityconnectors.framework.common.objects.PredefinedAttributes;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.common.objects.SortKey;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.SearchResultsHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.ADMIN_CREATED_ATTR;
import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.ALIASES_ATTR;
import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.DIRECT_MEMBERS_COUNT_ATTR;
import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.EMAIL_ATTR;
import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.ETAG_ATTR;
import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.ID_ATTR;
import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.ID_ETAG;
import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.MEMBERS_ATTR;
import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.MY_CUSTOMER_ID;
import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.NAME_ATTR;
import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.ROLE_ATTR;
import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.NON_EDITABLE_ALIASES_ATTR;

/**
 * A GroupHandler is a util class to cover all Group related operations.
 * 
 * @author Laszlo Hordos
 */
public class GroupHandler {

    /**
     * Setup logging for the {@link GroupHandler}.
     */
    private static final Log logger = Log.getLog(GroupHandler.class);

    // /////////////
    //
    // GROUP
    //
    // /////////////

    public static ObjectClassInfo getGroupClassInfo() {
        // @formatter:off
            /* GROUP from https://devsite.googleplex.com/admin-sdk/directory/v1/reference/groups#resource
            {
              "kind": "admin#directory#group",
              "id": string,
              "etag": etag,
              "email": string,
              "name": string,
              "directMembersCount": long,
              "description": string,
              "adminCreated": boolean,
              "aliases": [
                string
              ],
              "nonEditableAliases": [
                string
              ]
            }
            */
        // @formatter:on
        ObjectClassInfoBuilder builder = new ObjectClassInfoBuilder();
        builder.setType(ObjectClass.GROUP_NAME);
        builder.setDescription("A Google Group used as an email distribution list and membership container. "
                + "Group memberships are represented separately by the Member object class.");
        // email (mapped to Name.NAME)
        builder.addAttributeInfo(AttributeInfoBuilder.define(Name.NAME).setRequired(true)
                .setSubtype(AttributeInfo.Subtypes.STRING_CASE_IGNORE)
                .build());
        builder.addAttributeInfo(AttributeInfoBuilder.build(NAME_ATTR));
        builder.addAttributeInfo(PredefinedAttributeInfos.DESCRIPTION);

        // Read-only
        builder.addAttributeInfo(AttributeInfoBuilder.define(ADMIN_CREATED_ATTR, Boolean.TYPE)
                .setUpdateable(false).setCreateable(false).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(ALIASES_ATTR).setUpdateable(false)
                .setCreateable(false).setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(NON_EDITABLE_ALIASES_ATTR)
                .setUpdateable(false).setCreateable(false).setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(DIRECT_MEMBERS_COUNT_ATTR, Long.TYPE)
                .setUpdateable(false).setCreateable(false).build());

        // Virtual Attribute
        builder.addAttributeInfo(AttributeInfoBuilder.define(MEMBERS_ATTR).setMultiValued(true)
                .setReturnedByDefault(false).setUpdateable(false).setCreateable(false).build());

        return builder.build();
    }

    // https://support.google.com/a/answer/33386
    public static Directory.Groups.Insert createGroup(Directory.Groups groups,
            AttributesAccessor attributes) {
        Group group = new Group();
        group.setEmail(GoogleAppsUtil.getName(attributes.getName()));
        // Optional
        group.setDescription(attributes.findString(PredefinedAttributes.DESCRIPTION));
        group.setName(attributes.findString(NAME_ATTR));

        try {
            return groups.insert(group).setFields(ID_ETAG);
            // } catch (HttpResponseException e){
        } catch (IOException e) {
            logger.warn(e, "Failed to initialize Groups#Insert");
            throw ConnectorException.wrap(e);
        }
    }

    /**
     * Execute group updateDelta with attribute delta processing.
     * This handles only core group attribute updates (name, email, description).
     * Member updates are handled via User side in midPoint.
     */
    public static Set<AttributeDelta> executeGroupUpdateDelta(GoogleApiExecutor executor, Uid uid,
                                                             Set<AttributeDelta> modifications) {
        final Set<AttributeDelta> sideEffectDeltas = new HashSet<>();

        // Update core group attributes using Groups.patch API
        if (!modifications.isEmpty()) {
            final Directory.Groups.Patch patch = buildGroupUpdateRequest(executor.getDirectory().groups(), uid, modifications);
            if (patch != null) {
                executor.execute(patch, new RequestResultHandler.NoOp<Directory.Groups.Patch, Group>());
            }
        }

        return sideEffectDeltas;
    }

    /**
     * Build group update request from attribute deltas.
     */
    private static Directory.Groups.Patch buildGroupUpdateRequest(Directory.Groups service, Uid uid, Set<AttributeDelta> deltas) {
        Group group = new Group();
        boolean hasChanges = false;

        for (AttributeDelta delta : deltas) {
            String attributeName = delta.getName();

            // Handle single-valued attributes
            if (Name.NAME.equals(attributeName)) {
                String value = RequestResultHandler.getSingleValue(delta, String.class);
                if (StringUtil.isBlank(value)) {
                    throw new InvalidAttributeValueException("Group primary email cannot be null or blank.");
                }
                group.setEmail(value);
                hasChanges = true;
            } else if (NAME_ATTR.equals(attributeName)) {
                String value = RequestResultHandler.getSingleValue(delta, String.class);
                group.setName(value);
                hasChanges = true;
            } else if (PredefinedAttributes.DESCRIPTION.equals(attributeName)) {
                String value = RequestResultHandler.getSingleValue(delta, String.class);
                group.setDescription(value != null ? value : Data.NULL_STRING);
                hasChanges = true;
            }
        }

        if (!hasChanges) {
            return null;
        }

        try {
            return service.patch(uid.getUidValue(), group);
        } catch (IOException e) {
            logger.warn(e, "Failed to create group patch request");
            throw ConnectorException.wrap(e);
        }
    }

    /**
     * Execute group read query by UID.
     */
    public static void executeGroupReadQuery(GoogleApiExecutor apiExecutor, ConnectorObjectsCache objectsCache,
                                           Uid uid, final ResultsHandler handler, OperationOptions options,
                                           final Set<String> attributesToGet, SchemaDefinition schemaDef) {
        try {
            // Try the cache first
            ConnectorObject cachedGroup = objectsCache.getGroup(uid.getUidValue());
            if (cachedGroup != null) {
                handler.handle(cachedGroup);
                return;
            }

            Directory.Groups.Get request = apiExecutor.getDirectory().groups().get(uid.getUidValue());
            String fields = schemaDef.createGoogleApiFieldsString(attributesToGet, ID_ATTR, ETAG_ATTR, EMAIL_ATTR);
            request.setFields(fields);

            apiExecutor.execute(request,
                    new RequestResultHandler.ReadQuery<>(
                        group -> GroupConverter.fromGroup(group, attributesToGet, apiExecutor.getDirectory().members(), options),
                        objectsCache::addGroup,
                        handler));

        } catch (IOException e) {
            logger.warn(e, "Failed to initialize Groups#Get");
            throw ConnectorException.wrap(e);
        }
    }

    /**
     * Execute group read query by Name.
     */
    public static void executeGroupReadQuery(GoogleApiExecutor apiExecutor, ConnectorObjectsCache objectsCache,
                                           Name name, final ResultsHandler handler, OperationOptions options,
                                           final Set<String> attributesToGet, SchemaDefinition schemaDef) {
        try {
            Directory.Groups.Get request = apiExecutor.getDirectory().groups().get(name.getNameValue());
            String fields = schemaDef.createGoogleApiFieldsString(attributesToGet, ID_ATTR, ETAG_ATTR, EMAIL_ATTR);
            request.setFields(fields);

            apiExecutor.execute(request,
                    new RequestResultHandler.ReadQuery<>(
                        group -> GroupConverter.fromGroup(group, attributesToGet, apiExecutor.getDirectory().members(), options),
                        objectsCache::addGroup,
                        handler));

        } catch (IOException e) {
            logger.warn(e, "Failed to initialize Groups#Get");
            throw ConnectorException.wrap(e);
        }
    }

    /**
     * Execute group search query.
     */
    public static void executeGroupSearchQuery(GoogleApiExecutor apiExecutor, GoogleFilter googleFilter,
                                             final ResultsHandler handler, OperationOptions options,
                                             final Set<String> attributesToGet, SchemaDefinition schemaDef) {
        try {
            // Create and configure base request with all common settings
            Directory.Groups.List baseRequest = apiExecutor.getDirectory().groups().list();
            if (googleFilter.hasSearchQuery()) {
                googleFilter.configureGroupRequest(baseRequest);
            } else {
                baseRequest.setCustomer(MY_CUSTOMER_ID);
            }

            // Apply sort configuration to base request
            if (null != options.getSortKeys()) {
                for (SortKey sortKey : options.getSortKeys()) {
                    if (sortKey.getField().equalsIgnoreCase(Name.NAME)
                            || sortKey.getField().equalsIgnoreCase(EMAIL_ATTR)) {
                        baseRequest.setOrderBy("email");
                        if (sortKey.isAscendingOrder()) {
                            baseRequest.setSortOrder("ASCENDING");
                        } else {
                            baseRequest.setSortOrder("DESCENDING");
                        }
                        break; // Only first valid sort key is used
                    }
                }
            }

            // Get paging configuration and parameters
            int configMaxResults = apiExecutor.getConfiguration().getGroupPagingMaxResults();
            Integer pagedResultsOffset = options.getPagedResultsOffset();
            Integer pageSize = options.getPageSize();
            String pagedResultsCookie = options.getPagedResultsCookie();

            // Route to appropriate search method based on paging parameters
            if (pagedResultsOffset != null && pagedResultsOffset > 0) {
                // Offset-based paging
                executeGroupOffsetBasedSearch(apiExecutor, baseRequest, handler, attributesToGet,
                        configMaxResults, schemaDef, pagedResultsOffset, pageSize, options);
            } else if (pagedResultsCookie != null || (pageSize != null && pageSize > 0)) {
                // Cookie-based paging (with or without pageSize)
                executeGroupCookieBasedSearch(apiExecutor, baseRequest, handler, attributesToGet,
                        configMaxResults, schemaDef, pageSize, pagedResultsCookie, options);
            } else {
                // No paging - fetch all with automatic pagination
                executeGroupUnpagedSearch(apiExecutor, baseRequest, handler, attributesToGet,
                        configMaxResults, schemaDef, options);
            }

        } catch (IOException e) {
            logger.warn(e, "Failed to initialize Groups#List");
            throw ConnectorException.wrap(e);
        }
    }

    /**
     * Execute offset-based search with two-phase approach.
     */
    private static void executeGroupOffsetBasedSearch(GoogleApiExecutor apiExecutor, Directory.Groups.List baseRequest,
                                                    ResultsHandler handler, Set<String> attributesToGet,
                                                    int configMaxResults, SchemaDefinition schemaDef,
                                                    int pagedResultsOffset, Integer pageSize, OperationOptions options) throws IOException {
        int skipCount = pagedResultsOffset - 1;  // Convert 1-based to 0-based
        int targetSize = pageSize != null ? pageSize : Integer.MAX_VALUE;

        String nextToken = null;

        // Phase 1: Skip to offset position (if needed)
        if (skipCount > 0) {
            Directory.Groups.List skipRequest = apiExecutor.getDirectory().groups().list();
            copyGroupBaseRequestSettings(skipRequest, baseRequest);

            // Minimal fields for efficient skipping
            skipRequest.setFields("nextPageToken,groups(id)");

            int skipped = 0;
            while (skipped < skipCount) {
                skipRequest.setPageToken(nextToken);

                // Optimize maxResults for skipping
                int remainingToSkip = skipCount - skipped;
                skipRequest.setMaxResults(Math.min(remainingToSkip, configMaxResults));

                Groups result = apiExecutor.execute(skipRequest,
                        new RequestResultHandler<Directory.Groups.List, Groups, Groups>() {
                            public Groups handleResult(Directory.Groups.List request, Groups value) {
                                return value;
                            }
                        });

                if (result.getGroups() != null) {
                    skipped += result.getGroups().size();
                }

                nextToken = result.getNextPageToken();
                if (nextToken == null) {
                    // Reached end of data before offset
                    return;
                }
            }
        }

        // Phase 2: Fetch actual data with all required fields
        Directory.Groups.List dataRequest = apiExecutor.getDirectory().groups().list();
        copyGroupBaseRequestSettings(dataRequest, baseRequest);
        dataRequest.setPageToken(nextToken);

        // Full fields for actual data
        String fields = schemaDef.createGoogleApiFieldsString(attributesToGet, ID_ATTR, ETAG_ATTR, EMAIL_ATTR);
        dataRequest.setFields("nextPageToken,groups(" + fields + ")");

        int fetched = 0;
        while (fetched < targetSize) {
            // Optimize maxResults for data fetching
            int remaining = targetSize - fetched;
            dataRequest.setMaxResults(Math.min(remaining, configMaxResults));

            Groups result = apiExecutor.execute(dataRequest,
                    new RequestResultHandler<Directory.Groups.List, Groups, Groups>() {
                        public Groups handleResult(Directory.Groups.List request, Groups value) {
                            return value;
                        }
                    });

            if (result.getGroups() != null) {
                for (Group group : result.getGroups()) {
                    if (fetched >= targetSize) break;
                    handler.handle(GroupConverter.fromGroup(group,
                            attributesToGet, apiExecutor.getDirectory().members(), options));
                    fetched++;
                }
            }

            String nextPageToken = result.getNextPageToken();
            if (nextPageToken == null || fetched >= targetSize) {
                break;
            }
            dataRequest.setPageToken(nextPageToken);
        }
    }

    /**
     * Execute cookie-based search with single page retrieval.
     */
    private static void executeGroupCookieBasedSearch(GoogleApiExecutor apiExecutor, Directory.Groups.List baseRequest,
                                                    ResultsHandler handler, Set<String> attributesToGet,
                                                    int configMaxResults, SchemaDefinition schemaDef,
                                                    Integer pageSize, String pagedResultsCookie, OperationOptions options) throws IOException {
        Directory.Groups.List request = apiExecutor.getDirectory().groups().list();
        copyGroupBaseRequestSettings(request, baseRequest);

        // Set maxResults: use pageSize if provided, otherwise use config max
        if (pageSize != null && pageSize > 0) {
            int effectiveMaxResults = Math.min(pageSize, configMaxResults);
            request.setMaxResults(effectiveMaxResults);
        } else {
            request.setMaxResults(configMaxResults);
        }
        request.setPageToken(pagedResultsCookie);

        // Set fields
        String fields = schemaDef.createGoogleApiFieldsString(attributesToGet, ID_ATTR, ETAG_ATTR, EMAIL_ATTR);
        request.setFields("nextPageToken,groups(" + fields + ")");

        // Execute and return one page with continuation token
        String nextPageToken = apiExecutor.execute(request,
                new RequestResultHandler<Directory.Groups.List, Groups, String>() {
                    public String handleResult(Directory.Groups.List request, Groups value) {
                        if (null != value.getGroups()) {
                            for (Group group : value.getGroups()) {
                                handler.handle(GroupConverter.fromGroup(group,
                                        attributesToGet, apiExecutor.getDirectory().members(), options));
                            }
                        }
                        return value.getNextPageToken();
                    }
                });

        if (StringUtil.isNotBlank(nextPageToken)) {
            logger.info("Paged Search was requested and next token is:{0}", nextPageToken);
            ((SearchResultsHandler) handler).handleResult(new SearchResult(nextPageToken, 0));
        }
    }

    /**
     * Execute unpaged search with automatic pagination.
     */
    private static void executeGroupUnpagedSearch(GoogleApiExecutor apiExecutor, Directory.Groups.List baseRequest,
                                                 ResultsHandler handler, Set<String> attributesToGet,
                                                 int configMaxResults, SchemaDefinition schemaDef, OperationOptions options) throws IOException {
        Directory.Groups.List request = apiExecutor.getDirectory().groups().list();
        copyGroupBaseRequestSettings(request, baseRequest);
        request.setMaxResults(configMaxResults);

        // Set fields
        String fields = schemaDef.createGoogleApiFieldsString(attributesToGet, ID_ATTR, ETAG_ATTR, EMAIL_ATTR);
        request.setFields("nextPageToken,groups(" + fields + ")");

        // Fetch all pages
        String nextPageToken = null;
        do {
            request.setPageToken(nextPageToken);
            nextPageToken = apiExecutor.execute(request,
                    new RequestResultHandler<Directory.Groups.List, Groups, String>() {
                        public String handleResult(Directory.Groups.List request, Groups value) {
                            if (null != value.getGroups()) {
                                for (Group group : value.getGroups()) {
                                    handler.handle(GroupConverter.fromGroup(group,
                                            attributesToGet, apiExecutor.getDirectory().members(), options));
                                }
                            }
                            return value.getNextPageToken();
                        }
                    });
        } while (StringUtil.isNotBlank(nextPageToken));
    }

    /**
     * Copy all settings from baseRequest to targetRequest.
     */
    private static void copyGroupBaseRequestSettings(Directory.Groups.List targetRequest, Directory.Groups.List baseRequest) {
        if (baseRequest.getCustomer() != null) {
            targetRequest.setCustomer(baseRequest.getCustomer());
        }
        if (baseRequest.getDomain() != null) {
            targetRequest.setDomain(baseRequest.getDomain());
        }
        if (baseRequest.getQuery() != null) {
            targetRequest.setQuery(baseRequest.getQuery());
        }
        if (baseRequest.getOrderBy() != null) {
            targetRequest.setOrderBy(baseRequest.getOrderBy());
            targetRequest.setSortOrder(baseRequest.getSortOrder());
        }
    }

    /**
    Creates a group and its requested memberships after validating all member values.
    Members accept email strings or maps with email and optional role. Membership failures propagate;
    the group and any earlier memberships remain created because these requests are not transactional.
    */
    public static Uid executeGroupCreate(GoogleApiExecutor executor, Set<Attribute> createAttributes) {
        AttributesAccessor accessor = new AttributesAccessor(createAttributes);
        List<Member> members = parseMembers(accessor);
        String email = GoogleAppsUtil.getName(accessor.getName());

        // The insert response contains only id and etag, so retain the validated input name.
        Uid uid = executor.execute(createGroup(executor.getDirectory().groups(), accessor),
                new RequestResultHandler.Create<>(ObjectClass.GROUP, (Group group) -> new Uid(group.getId(), group.getEtag(), new Name(email))));
        for (Member member : members) {
            Directory.Members.Insert request = MemberHandler.createMemberByEmail(executor.getDirectory().members(), uid.getUidValue(), member.getEmail(),
                    StringUtil.isBlank(member.getRole()) ? "MEMBER" : member.getRole()).setFields(EMAIL_ATTR + "," + ETAG_ATTR);
            executor.execute(request,
                    new RequestResultHandler<Directory.Members.Insert, Member, Void>() {
                        @Override
                        public Void handleResult(Directory.Members.Insert request, Member result) {
                            return null;
                        }
                    });
        }
        return uid;
    }

    private static List<Member> parseMembers(AttributesAccessor accessor) {
        List<Member> members = new ArrayList<>();
        List<Object> values = accessor.findList(MEMBERS_ATTR);
        if (values == null) {
            return members;
        }
        for (Object value : values) {
            Object email = value;
            Object role = null;
            if (value instanceof Map) {
                Map<?, ?> member = (Map<?, ?>) value;
                email = member.get(EMAIL_ATTR);
                role = member.get(ROLE_ATTR);
            }
            if (!(email instanceof String) || StringUtil.isBlank((String) email)) {
                throw new InvalidAttributeValueException("Each '__MEMBERS__' value must be a nonblank email string or a map with a nonblank 'email' string.");
            }
            if (role != null && !(role instanceof String)) {
                throw new InvalidAttributeValueException("The '__MEMBERS__' member 'role' must be a string when provided.");
            }
            members.add(new Member().setEmail((String) email).setRole((String) role));
        }
        return members;
    }

    /**
     * Execute Group delete operation.
     */
    public static void executeGroupDelete(GoogleApiExecutor executor, ConnectorObjectsCache objectsCache, Uid uid) {
        try {
            AbstractGoogleJsonClientRequest<Void> request = executor.getDirectory().groups().delete(uid.getUidValue());

            executor.execute(request, new RequestResultHandler.Delete(uid, ObjectClass.GROUP));

            // Remove from cache
            objectsCache.removeGroup(uid.getUidValue());

        } catch (IOException e) {
            throw ConnectorException.wrap(e);
        }
    }

    /**
     * Unified query execution method for Groups that handles both read and search operations.
     */
    public static void executeQuery(GoogleApiExecutor executor, ConnectorObjectsCache objectsCache,
                                   GoogleFilter googleFilter, ResultsHandler handler, OperationOptions options,
                                   SchemaDefinition schemaDef) {
        // Get attributes to retrieve - call only once
        final Set<String> attributesToGet = schemaDef.createFullAttributesToGet(options);
        if (googleFilter.isReadByUid()) {
            // Read request by UID
            executeGroupReadQuery(executor, objectsCache, googleFilter.getUid(), handler, options, attributesToGet, schemaDef);
        } else if (googleFilter.isReadByName()) {
            // Read request by Name
            executeGroupReadQuery(executor, objectsCache, googleFilter.getName(), handler, options, attributesToGet, schemaDef);
        } else {
            // Search query (including list all when filter has no search query)
            executeGroupSearchQuery(executor, googleFilter, handler, options, attributesToGet, schemaDef);
        }
    }

}
