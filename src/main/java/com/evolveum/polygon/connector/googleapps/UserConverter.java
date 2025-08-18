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
import com.google.api.services.directory.model.User;
import com.google.api.services.directory.model.UserPhoto;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.objects.*;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;

import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.*;
import static org.identityconnectors.framework.common.objects.OperationalAttributes.ENABLE_NAME;

/**
 * UserConverter handles conversion between Google User objects and ConnID ConnectorObjects.
 *
 * @author Hiroyuki Wada
 */
public class UserConverter {

    private static final Log logger = Log.getLog(UserConverter.class);

    /**
     * Convert Google User to ConnectorObject.
     */
    public static ConnectorObject fromUser(User user, Set<String> attributesToGet,
                                           Directory.Groups groupService, GoogleAppsConfiguration configuration,
                                           OperationOptions options) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        if (null != user.getEtag()) {
            builder.setUid(new Uid(user.getId(), user.getEtag(), new Name(user.getPrimaryEmail())));
        } else {
            builder.setUid(user.getId());
        }
        builder.setName(user.getPrimaryEmail());

        return getUserFromResource(user, builder, attributesToGet, groupService, configuration, options);
    }

    private static ConnectorObject getUserFromResource(User user, ConnectorObjectBuilder builder,
                                                       Set<String> attributesToGet, Directory.Groups groupService,
                                                       GoogleAppsConfiguration configuration, OperationOptions options) {
        // Optional
        // If both givenName and familyName are empty then Google didn't return
        // with 'name'
        if (attributesToGet.contains(GIVEN_NAME_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(GIVEN_NAME_ATTR,
                    null != user.getName() ? user.getName().getGivenName() : null));
        }
        if (attributesToGet.contains(FAMILY_NAME_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(FAMILY_NAME_ATTR,
                    null != user.getName() ? user.getName().getFamilyName() : null));
        }
        if (attributesToGet.contains(FULL_NAME_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(FULL_NAME_ATTR,
                    null != user.getName() ? user.getName().getFullName() : null));
        }

        if (attributesToGet.contains(IS_ADMIN_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(IS_ADMIN_ATTR, user.getIsAdmin()));
        }
        if (attributesToGet.contains(IS_DELEGATED_ADMIN_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(IS_DELEGATED_ADMIN_ATTR, user
                    .getIsDelegatedAdmin()));
        }
        if (attributesToGet.contains(LAST_LOGIN_TIME_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(LAST_LOGIN_TIME_ATTR,
                    null != user.getLastLoginTime() ? user.getLastLoginTime().toString() : null));
        }
        if (attributesToGet.contains(CREATION_TIME_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(CREATION_TIME_ATTR,
                    null != user.getCreationTime() ? user.getCreationTime().toString() : null));
        }
        if (attributesToGet.contains(AGREED_TO_TERMS_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(AGREED_TO_TERMS_ATTR, user
                    .getAgreedToTerms()));
        }
        if (attributesToGet.contains(ENABLE_NAME)) {
            if (Boolean.TRUE.equals(user.getSuspended())) {
                builder.addAttribute(AttributeBuilder.build(ENABLE_NAME, false));
            } else {
                builder.addAttribute(AttributeBuilder.build(ENABLE_NAME, true));
            }
        }
        if (attributesToGet.contains(SUSPENDED_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(SUSPENDED_ATTR, user.getSuspended()));
        }
        if (attributesToGet.contains(SUSPENSION_REASON_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(SUSPENSION_REASON_ATTR, user
                    .getSuspensionReason()));
        }
        if (attributesToGet.contains(CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR, user
                    .getChangePasswordAtNextLogin()));
        }
        if (attributesToGet.contains(IP_WHITELISTED_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(IP_WHITELISTED_ATTR, user
                    .getIpWhitelisted()));
        }
        if (attributesToGet.contains(IMS_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(IMS_ATTR, (Collection) GoogleAppsUtil.structAttrToString((Collection) user.getIms())));
        }
        if (attributesToGet.contains(EMAILS_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(EMAILS_ATTR, (Collection) GoogleAppsUtil.structAttrToString((Collection) user.getEmails())));
        }
        if (attributesToGet.contains(EXTERNAL_IDS_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(EXTERNAL_IDS_ATTR, (Collection) GoogleAppsUtil.structAttrToString((Collection) user
                    .getExternalIds())));
        }
        if (attributesToGet.contains(RELATIONS_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(RELATIONS_ATTR, (Collection) GoogleAppsUtil.structAttrToString((Collection) user
                    .getRelations())));
        }
        if (attributesToGet.contains(ADDRESSES_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(ADDRESSES_ATTR, (Collection) GoogleAppsUtil.structAttrToString((Collection) user
                    .getAddresses())));
        }
        if (attributesToGet.contains(ORGANIZATIONS_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(ORGANIZATIONS_ATTR, (Collection) GoogleAppsUtil.structAttrToString((Collection) user
                    .getOrganizations())));
        }
        if (attributesToGet.contains(PHONES_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(PHONES_ATTR, (Collection) GoogleAppsUtil.structAttrToString((Collection) user.getPhones())));
        }
        if (attributesToGet.contains(ALIASES_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(ALIASES_ATTR, (Collection) GoogleAppsUtil.structAttrToString((Collection) user.getAliases())));
        }

        if (attributesToGet.contains(LOCATIONS_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(LOCATIONS_ATTR, (Collection) GoogleAppsUtil.structAttrToString((Collection) user
                    .getLocations())));
        }

        if (attributesToGet.contains(NON_EDITABLE_ALIASES_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(NON_EDITABLE_ALIASES_ATTR, user
                    .getNonEditableAliases()));
        }

        if (attributesToGet.contains(CUSTOMER_ID_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(CUSTOMER_ID_ATTR, user.getCustomerId()));
        }
        if (attributesToGet.contains(ORG_UNIT_PATH_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(ORG_UNIT_PATH_ATTR, user.getOrgUnitPath()));
        }
        if (attributesToGet.contains(IS_MAILBOX_SETUP_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(IS_MAILBOX_SETUP_ATTR, user
                    .getIsMailboxSetup()));
        }
        if (attributesToGet.contains(INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR, user
                    .getIncludeInGlobalAddressList()));
        }
        if (attributesToGet.contains(THUMBNAIL_PHOTO_URL_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(THUMBNAIL_PHOTO_URL_ATTR, user
                    .getThumbnailPhotoUrl()));
        }
        if (attributesToGet.contains(PHOTO_ATTR)) {
            if (RequestResultHandler.shouldReturnPartialAttribute(PHOTO_ATTR, attributesToGet, options)) {
                // Return incomplete flag instead of actual photo data for performance
                builder.addAttribute(RequestResultHandler.createIncompleteAttribute(PHOTO_ATTR));
            } else {
                byte[] decodedePhoto = null;
                UserPhoto photo = getUserPhoto(user.getId(), configuration);

                if (null != photo) {
                    decodedePhoto = photo.decodePhotoData();
                }
                builder.addAttribute(AttributeBuilder.build(PHOTO_ATTR, decodedePhoto));
            }
        }
        if (attributesToGet.contains(DELETION_TIME_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(DELETION_TIME_ATTR, null != user
                    .getDeletionTime() ? user.getDeletionTime().toString() : null));
        }

        // Expensive to get
        if (attributesToGet.contains(PredefinedAttributes.GROUPS_NAME)) {
            if (RequestResultHandler.shouldReturnPartialAttribute(PredefinedAttributes.GROUPS_NAME, attributesToGet, options)) {
                // Return incomplete flag instead of actual groups data for performance
                builder.addAttribute(RequestResultHandler.createIncompleteAttribute(PredefinedAttributes.GROUPS_NAME));
            } else {
                builder.addAttribute(AttributeBuilder.build(PredefinedAttributes.GROUPS_NAME,
                        GroupConverter.listGroups(groupService, user.getId())));
            }
        }

        return builder.build();
    }


    private static UserPhoto getUserPhoto(String userId, GoogleAppsConfiguration configuration) {
        UserPhoto photo = null;
        try {
            photo = configuration.getDirectory().users().photos().get(userId).execute();
        } catch (GoogleJsonResponseException e) {
            logger.info("No photo is found for user: " + userId);
        } catch (IOException e) {
            logger.info("No photo is found for user: " + userId);
        }
        return photo;
    }
}