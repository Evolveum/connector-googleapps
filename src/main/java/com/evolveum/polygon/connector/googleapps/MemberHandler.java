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

import com.google.api.services.directory.Directory;
import com.google.api.services.directory.model.Member;
import com.google.api.services.directory.model.Members;
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

import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.*;

/**
 * MemberHandler is a util class to cover all Member related operations.
 *
 * @author Hiroyuki Wada
 */
public class MemberHandler {

    /**
     * Setup logging for the {@link MemberHandler}.
     */
    private static final Log logger = Log.getLog(MemberHandler.class);

    // /////////////
    //
    // MEMBER
    // https://developers.google.com/admin-sdk/directory/v1/reference/members
    //
    // /////////////

    public static ObjectClassInfo getMemberClassInfo() {
        ObjectClassInfoBuilder builder = new ObjectClassInfoBuilder();
        builder.setType(MEMBER.getObjectClassValue());
        builder.setDescription("A membership relationship linking a user, another group, or a customer domain "
                + "to a Google Group. It includes the member type and role, such as MEMBER, MANAGER, or OWNER.");
        // groupKey
        builder.addAttributeInfo(AttributeInfoBuilder.define(GROUP_KEY_ATTR).setRequired(true)
                .build());
        // email
        builder.addAttributeInfo(AttributeInfoBuilder.define(EMAIL_ATTR).setRequired(true).build());
        // role
        builder.addAttributeInfo(AttributeInfoBuilder.define(ROLE_ATTR).build());
        // type
        builder.addAttributeInfo(AttributeInfoBuilder.build(TYPE_ATTR));
        return builder.build();
    }

    public static Directory.Members.Insert createMember(Directory.Members service,
                                                        AttributesAccessor attributes) {

        String groupKey = attributes.findString(GROUP_KEY_ATTR);
        if (StringUtil.isBlank(groupKey)) {
            throw new InvalidAttributeValueException(
                    "Missing required attribute 'groupKey'. Identifies the group in the API request. Required when creating a Member.");
        }

        String memberEmail = attributes.findString(EMAIL_ATTR);
        if (StringUtil.isBlank(memberEmail)) {
            throw new InvalidAttributeValueException(
                    "Missing required attribute 'email'. Identifies the group member in the API request. Required when creating a Member.");
        }

        String role = attributes.findString(ROLE_ATTR);
        return createMemberByEmail(service, groupKey, memberEmail, role);
    }

    public static Directory.Members.Insert createMemberByEmail(Directory.Members service, String groupKey,
                                                               String memberEmail, String role) {
        try {
            Member content = new Member();
            content.setEmail(memberEmail);

            if (StringUtil.isNotBlank(role)) {
                content.setRole(role);
            }

            return service.insert(groupKey, content);
            // } catch (HttpResponseException e){
        } catch (IOException e) {
            logger.warn(e, "Failed to initialize Members#Insert");
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Members.Delete deleteMembers(Directory.Members service,
                                                         String groupKey, String memberKey) {
        try {
            return service.delete(groupKey, memberKey);
        } catch (IOException e) {
            logger.warn(e, "Failed to initialize Members#Delete");
            throw ConnectorException.wrap(e);
        }
    }

    public static ConnectorObject fromMember(String groupKey, Member content) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(MEMBER);

        Uid uid = generateMemberId(groupKey, content);
        builder.setUid(uid);
        builder.setName(uid.getUidValue());

        builder.addAttribute(AttributeBuilder.build(GROUP_KEY_ATTR, groupKey));
        builder.addAttribute(AttributeBuilder.build(EMAIL_ATTR, content.getEmail()));
        builder.addAttribute(AttributeBuilder.build(ROLE_ATTR, content.getRole()));
        builder.addAttribute(AttributeBuilder.build(TYPE_ATTR, content.getType()));

        return builder.build();
    }

    public static Uid generateMemberId(String groupKey, Member content) {
        // Preserve identifiers already stored in midPoint shadows. A change to
        // immutable member IDs requires an explicit migration, not a code refactor.
        String memberKey = StringUtil.isNotBlank(content.getEmail()) ? content.getEmail() : content.getId();
        String id = groupKey + "/" + memberKey;
        if (null != content.getEtag()) {
            return new Uid(id, content.getEtag(), new Name(content.getEmail()));
        } else {
            return new Uid(id, new Name(content.getEmail()));
        }
    }

    /**
     * Execute Member updateDelta with attribute delta processing.
     * This handles Member attribute updates (only role can be updated).
     */
    public static Set<AttributeDelta> executeMemberUpdateDelta(GoogleApiExecutor executor, Uid uid,
                                                               Set<AttributeDelta> modifications) {
        final Set<AttributeDelta> sideEffectDeltas = new HashSet<>();

        // Update Member attributes using Members.patch API
        if (!modifications.isEmpty()) {
            final Directory.Members.Patch patch = buildMemberUpdateRequest(executor.getDirectory().members(),
                    uid, modifications);
            if (patch != null) {
                executor.execute(patch, new RequestResultHandler.NoOp<Directory.Members.Patch, Member>());
            }
        }

        return sideEffectDeltas;
    }

    /**
     * Build Member update request from attribute deltas.
     * Only role can be updated for Member.
     */
    private static Directory.Members.Patch buildMemberUpdateRequest(Directory.Members service,
                                                                    Uid uid, Set<AttributeDelta> deltas) {
        Member content = null;

        // Parse the uid to get groupKey and memberKey
        String[] ids = uid.getUidValue().split("/", 2);
        if (ids.length != 2) {
            throw new UnknownUidException("Unrecognised id: " + uid.getUidValue());
        }
        String groupKey = ids[0];
        String memberKey = ids[1];

        // Check for role updates (only allowed field for Member)
        for (AttributeDelta delta : deltas) {
            String attributeName = delta.getName();

            if (ROLE_ATTR.equals(attributeName)) {
                String newRole = RequestResultHandler.getSingleValue(delta, String.class);
                if (newRole != null) {
                    content = new Member();
                    content.setRole(newRole);
                }
            }
            // Other attributes (groupKey, email, type) are read-only
        }

        if (content == null) {
            return null; // No changes
        }

        try {
            return service.patch(groupKey, memberKey, content);
        } catch (IOException e) {
            logger.warn(e, "Failed to create member patch request");
            throw ConnectorException.wrap(e);
        }
    }

    /**
     * Execute Member read query by UID.
     */


    /**
     * Execute Member search query.
     */


    /**
     * Execute Member search query with specific groupKey.
     */


    /**
     * Execute Member create operation.
     */
    public static Uid executeMemberCreate(GoogleApiExecutor executor, Set<Attribute> createAttributes) {
        final AttributesAccessor accessor = new AttributesAccessor(createAttributes);

        return executor.execute(createMember(executor.getDirectory().members(), accessor),
                new RequestResultHandler<Directory.Members.Insert, Member, Uid>() {
                    public Uid handleResult(final Directory.Members.Insert request,
                                            final Member value) {
                        logger.ok("New Member is created:{0}/{1}", request.getGroupKey(), value
                                .getId());
                        return generateMemberId(request.getGroupKey(), value);
                    }

                    public Uid handleDuplicate(IOException e) {
                        // Member already exists, try to get the current member details
                        try {
                            String groupKey = accessor.findString(GROUP_KEY_ATTR);
                            String email = accessor.findString(EMAIL_ATTR);
                            Directory.Members.Get getRequest = executor.getDirectory().members().get(groupKey, email);
                            Member existingMember = executor.execute(getRequest, new RequestResultHandler<Directory.Members.Get, Member, Member>() {
                                public Member handleResult(Directory.Members.Get request, Member value) {
                                    return value;
                                }
                            });
                            if (existingMember != null) {
                                logger.ok("Member already exists, returning existing member: {0}/{1}",
                                        groupKey, existingMember.getId());
                                return generateMemberId(groupKey, existingMember);
                            }
                        } catch (Exception ex) {
                            logger.warn(ex, "Failed to get existing member details");
                        }
                        throw ConnectorException.wrap(e);
                    }
                });
    }

    /**
     * Execute Member delete operation.
     */
    public static void executeMemberDelete(GoogleApiExecutor executor, Uid uid) {
        String[] ids = uid.getUidValue().split("/", 2);
        if (ids.length != 2) {
            throw new UnknownUidException("Unrecognised id: " + uid.getUidValue());
        }

        Directory.Members.Delete deleteRequest = deleteMembers(executor.getDirectory().members(), ids[0], ids[1]);

        executor.execute(deleteRequest, new RequestResultHandler.Delete(uid, MEMBER));
    }

    /**
     * Unified query execution method for Members that handles both read and search operations.
     */


    /**
     * Execute Member query with extracted groupKey from search filter.
     */


}
