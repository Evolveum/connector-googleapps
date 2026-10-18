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
 *
 * Portions Copyrighted 2025 Nomura Research Institute, Ltd.
 */
package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.ObjectClass;

/**
 * Constants used throughout the Google Apps Connector.
 *
 * @author Hiroyuki Wada
 */
public final class GoogleAppsConstants {

    // ObjectClass definitions
    public static final ObjectClass ORG_UNIT = new ObjectClass("OrgUnit");
    public static final ObjectClass MEMBER = new ObjectClass("Member");
    public static final ObjectClass ALIAS = new ObjectClass("Alias");
    public static final ObjectClass LICENSE_ASSIGNMENT = new ObjectClass("LicenseAssignment");

    // Field definitions for API calls
    public static final String ID_ETAG = "id,etag";
    public static final String ORG_UNIT_PATH_ETAG = "orgUnitPath,etag";
    public static final String PRODUCT_ID_SKU_ID_USER_ID = "productId,skuId,userId";

    // Attribute names
    public static final String ID_ATTR = "id";
    public static final String ETAG_ATTR = "etag";
    public static final String NAME_ATTR = "name";
    public static final String ADMIN_CREATED_ATTR = "adminCreated";
    public static final String NON_EDITABLE_ALIASES_ATTR = "nonEditableAliases";
    public static final String DIRECT_MEMBERS_COUNT_ATTR = "directMembersCount";
    public static final String SUSPENDED_ATTR = "suspended";
    public static final String CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR = "changePasswordAtNextLogin";
    public static final String IP_WHITELISTED_ATTR = "ipWhitelisted";
    public static final String ORG_UNIT_PATH_ATTR = "orgUnitPath";
    public static final String INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR = "includeInGlobalAddressList";
    public static final String IMS_ATTR = "ims";
    public static final String EMAILS_ATTR = "emails";
    public static final String EXTERNAL_IDS_ATTR = "externalIds";
    public static final String RELATIONS_ATTR = "relations";
    public static final String ADDRESSES_ATTR = "addresses";
    public static final String ORGANIZATIONS_ATTR = "organizations";
    public static final String PHONES_ATTR = "phones";
    public static final String GIVEN_NAME_ATTR = "givenName";
    public static final String FAMILY_NAME_ATTR = "familyName";
    public static final String FULL_NAME_ATTR = "fullName";
    public static final String IS_ADMIN_ATTR = "isAdmin";
    public static final String IS_DELEGATED_ADMIN_ATTR = "isDelegatedAdmin";
    public static final String LAST_LOGIN_TIME_ATTR = "lastLoginTime";
    public static final String CREATION_TIME_ATTR = "creationTime";
    public static final String AGREED_TO_TERMS_ATTR = "agreedToTerms";
    public static final String SUSPENSION_REASON_ATTR = "suspensionReason";
    public static final String ALIASES_ATTR = "aliases";
    public static final String CUSTOMER_ID_ATTR = "customerId";
    public static final String IS_MAILBOX_SETUP_ATTR = "isMailboxSetup";
    public static final String THUMBNAIL_PHOTO_URL_ATTR = "thumbnailPhotoUrl";
    public static final String DELETION_TIME_ATTR = "deletionTime";
    public static final String PRIMARY_EMAIL_ATTR = "primaryEmail";
    public static final String EMAIL_ATTR = "email";
    public static final String ALIAS_ATTR = "alias";
    public static final String PARENT_ORG_UNIT_PATH_ATTR = "parentOrgUnitPath";
    public static final String BLOCK_INHERITANCE_ATTR = "blockInheritance";
    public static final String PRODUCT_ID_ATTR = "productId";
    public static final String SKU_ID_ATTR = "skuId";
    public static final String USER_ID_ATTR = "userId";
    public static final String SELF_LINK_ATTR = "selfLink";
    public static final String ROLE_ATTR = "role";
    public static final String MEMBERS_ATTR = "__MEMBERS__";
    public static final String GROUP_KEY_ATTR = "groupKey";
    public static final String TYPE_ATTR = "type";
    public static final String PHOTO_ATTR = "__PHOTO__";
    public static final String LOCATIONS_ATTR = "locations";

    // Constants
    public static final String MY_CUSTOMER_ID = "my_customer";
    public static final String EMPTY_STRING = "";
    public static final String SHOW_DELETED_PARAM = "showDeleted";
    public static final String ASCENDING_ORDER = "ASCENDING";
    public static final String DESCENDING_ORDER = "DESCENDING";

}