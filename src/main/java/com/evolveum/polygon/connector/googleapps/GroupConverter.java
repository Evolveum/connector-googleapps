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

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.directory.Directory;
import com.google.api.services.directory.model.Group;
import com.google.api.services.directory.model.Groups;
import com.google.api.services.directory.model.Member;
import com.google.api.services.directory.model.Members;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.*;

import java.io.IOException;
import java.util.*;

import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.*;

/**
 * GroupConverter handles conversion between Google Group objects and ConnID ConnectorObjects.
 *
 * @author Hiroyuki Wada
 */
public class GroupConverter {

    private static final Log logger = Log.getLog(GroupConverter.class);

    /**
     * Convert Google Group to ConnectorObject.
     */
    public static ConnectorObject fromGroup(Group group, Set<String> attributesToGet,
                                            Directory.Members memberService, OperationOptions options) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(ObjectClass.GROUP);

        if (null != group.getEtag()) {
            builder.setUid(new Uid(group.getId(), group.getEtag(), new Name(group.getEmail())));
        } else {
            builder.setUid(group.getId());
        }
        builder.setName(group.getEmail());

        // Optional
        if (attributesToGet.contains(EMAIL_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(EMAIL_ATTR, group.getEmail()));
        }
        if (attributesToGet.contains(NAME_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(NAME_ATTR, group.getName()));
        }
        if (attributesToGet.contains(PredefinedAttributes.DESCRIPTION)) {
            builder.addAttribute(AttributeBuilder.build(PredefinedAttributes.DESCRIPTION, group
                    .getDescription()));
        }

        if (attributesToGet.contains(ADMIN_CREATED_ATTR)) {
            builder.addAttribute(AttributeBuilder
                    .build(ADMIN_CREATED_ATTR, group.getAdminCreated()));
        }
        if (attributesToGet.contains(ALIASES_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(ALIASES_ATTR, group.getAliases()));
        }
        if (attributesToGet.contains(NON_EDITABLE_ALIASES_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(NON_EDITABLE_ALIASES_ATTR, group
                    .getNonEditableAliases()));
        }
        if (attributesToGet.contains(DIRECT_MEMBERS_COUNT_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(DIRECT_MEMBERS_COUNT_ATTR, group
                    .getDirectMembersCount()));
        }

        // Expensive to get
        if (attributesToGet.contains(MEMBERS_ATTR)) {
            if (RequestResultHandler.shouldReturnPartialAttribute(MEMBERS_ATTR, attributesToGet, options)) {
                // Return incomplete flag instead of actual members data for performance
                builder.addAttribute(RequestResultHandler.createIncompleteAttribute(MEMBERS_ATTR));
            } else {
                builder.addAttribute(AttributeBuilder.build(MEMBERS_ATTR, listMembers(memberService, group
                        .getId(), null)));
            }
        }

        return builder.build();
    }

    /**
     * List group members as strings.
     */
    public static List<String> listMembers(Directory.Members service, String groupKey, String roles) {
        List<Map<String, String>> allMembers = listAllMembers(service, groupKey, roles);
        final List<String> resultMembers = new ArrayList<String>();

        for (Map<String, String> member : allMembers) {
            resultMembers.add(member.get("email"));
        }

        return resultMembers;
    }

    /**
     * List all group members with details.
     */
    public static List<Map<String, String>> listAllMembers(Directory.Members service, String groupKey, String roles) {
        final List<Map<String, String>> result = new ArrayList<Map<String, String>>();
        long startTime = System.currentTimeMillis();
        try {
            Directory.Members.List request = service.list(groupKey);
            request.setRoles(roles);
            do {
                Members currentPage = request.execute();
                List<Member> members = currentPage.getMembers();
                if (null != members) {
                    for (Member member : members) {
                        Map<String, String> data = new HashMap<String, String>();
                        data.put("id", member.getId());
                        data.put("email", member.getEmail());
                        data.put("role", member.getRole());
                        data.put("type", member.getType());
                        result.add(data);
                    }
                }
                request.setPageToken(currentPage.getNextPageToken());
            } while (null != request.getPageToken());

        } catch (GoogleJsonResponseException e) {
            if (404 == e.getDetails().getCode()) {
                logger.warn("Group {0} is not found", groupKey);
            } else {
                throw ConnectorException.wrap(e);
            }
        } catch (IOException e) {
            throw ConnectorException.wrap(e);
        }
        logger.info("listAllMembers() - finished in " + (System.currentTimeMillis() - startTime) + "ms");
        return result;
    }

    /**
     * List groups for a user.
     */
    public static Set<String> listGroups(Directory.Groups service, String userKey) {
        final Set<String> result = new HashSet<String>();
        long startTime = System.currentTimeMillis();
        try {
            Directory.Groups.List request = service.list();
            request.setUserKey(userKey);
            do {
                Groups currentPage = request.execute();
                List<Group> groups = currentPage.getGroups();
                if (null != groups) {
                    for (Group group : groups) {
                        result.add(group.getId());
                    }
                }
                request.setPageToken(currentPage.getNextPageToken());
            } while (null != request.getPageToken());

        } catch (GoogleJsonResponseException e) {
            if (404 == e.getDetails().getCode()) {
                logger.warn("User {0} is not found", userKey);
            } else {
                throw ConnectorException.wrap(e);
            }
        } catch (IOException e) {
            throw ConnectorException.wrap(e);
        }
        logger.info("listGroups() - finished in " + (System.currentTimeMillis() - startTime) + "ms");
        return result;
    }
}