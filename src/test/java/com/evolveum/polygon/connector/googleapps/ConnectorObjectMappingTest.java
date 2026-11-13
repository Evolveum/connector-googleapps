/*
 * Copyright (c) 2026 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.polygon.connector.googleapps;

import com.google.api.services.directory.model.Group;
import com.google.api.services.directory.model.User;
import com.google.api.services.directory.model.UserName;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

public class ConnectorObjectMappingTest {

    @Test
    public void userResourceMapsRequestedAttributesWithoutExpensiveAssociations() {
        // Given: User attributes without expensive associations
        Set<String> attributes = new HashSet<>(Arrays.asList(
                GoogleAppsConstants.GIVEN_NAME_ATTR,
                GoogleAppsConstants.FAMILY_NAME_ATTR,
                GoogleAppsConstants.FULL_NAME_ATTR,
                GoogleAppsConstants.IS_ADMIN_ATTR,
                GoogleAppsConstants.IS_DELEGATED_ADMIN_ATTR,
                GoogleAppsConstants.LAST_LOGIN_TIME_ATTR,
                GoogleAppsConstants.CREATION_TIME_ATTR,
                GoogleAppsConstants.AGREED_TO_TERMS_ATTR,
                GoogleAppsConstants.SUSPENDED_ATTR,
                GoogleAppsConstants.SUSPENSION_REASON_ATTR,
                GoogleAppsConstants.CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR,
                GoogleAppsConstants.IP_WHITELISTED_ATTR,
                GoogleAppsConstants.IMS_ATTR,
                GoogleAppsConstants.EMAILS_ATTR,
                GoogleAppsConstants.EXTERNAL_IDS_ATTR,
                GoogleAppsConstants.RELATIONS_ATTR,
                GoogleAppsConstants.ADDRESSES_ATTR,
                GoogleAppsConstants.ORGANIZATIONS_ATTR,
                GoogleAppsConstants.PHONES_ATTR,
                GoogleAppsConstants.ALIASES_ATTR,
                GoogleAppsConstants.LOCATIONS_ATTR,
                GoogleAppsConstants.NON_EDITABLE_ALIASES_ATTR,
                GoogleAppsConstants.CUSTOMER_ID_ATTR,
                GoogleAppsConstants.ORG_UNIT_PATH_ATTR,
                GoogleAppsConstants.IS_MAILBOX_SETUP_ATTR,
                GoogleAppsConstants.INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR,
                GoogleAppsConstants.THUMBNAIL_PHOTO_URL_ATTR,
                GoogleAppsConstants.DELETION_TIME_ATTR));
        User user = new User()
                .setId("user-id")
                .setEtag("etag-1")
                .setPrimaryEmail("ada@example.com")
                .setName(new UserName().setGivenName("Ada").setFamilyName("Lovelace").setFullName("Ada Lovelace"))
                .setIsAdmin(true)
                .setIsDelegatedAdmin(false)
                .setLastLoginTime(new com.google.api.client.util.DateTime("2026-01-01T00:00:00.000Z"))
                .setCreationTime(new com.google.api.client.util.DateTime("2020-01-01T00:00:00.000Z"))
                .setAgreedToTerms(true)
                .setSuspended(false)
                .setSuspensionReason("NONE")
                .setChangePasswordAtNextLogin(false)
                .setIpWhitelisted(false)
                .setNonEditableAliases(List.of("legacy@example.com"))
                .setCustomerId("customer-id")
                .setOrgUnitPath("/Engineering")
                .setIsMailboxSetup(true)
                .setIncludeInGlobalAddressList(true)
                .setThumbnailPhotoUrl("https://example.test/photo")
                .setDeletionTime(new com.google.api.client.util.DateTime("2026-02-01T00:00:00.000Z"));

        // When: Convert the requested user attributes
        ConnectorObject object = UserConverter.fromUser(user, attributes, null, new GoogleAppsConfiguration(), new OperationOptionsBuilder().build());

        // Then: Preserve identity and omit unrequested associations
        assertEquals(object.getObjectClass(), ObjectClass.ACCOUNT);
        assertEquals(object.getUid().getUidValue(), "user-id");
        assertEquals(object.getUid().getRevision(), "etag-1");
        assertEquals(object.getName().getNameValue(), "ada@example.com");
        assertEquals(object.getAttributeByName(GoogleAppsConstants.GIVEN_NAME_ATTR).getValue(), List.of("Ada"));
        assertEquals(object.getAttributeByName(GoogleAppsConstants.FAMILY_NAME_ATTR).getValue(), List.of("Lovelace"));
        assertEquals(object.getAttributeByName(GoogleAppsConstants.SUSPENDED_ATTR).getValue(), List.of(false));
        assertEquals(object.getAttributeByName(GoogleAppsConstants.ORG_UNIT_PATH_ATTR).getValue(), List.of("/Engineering"));
        assertNull(object.getAttributeByName(GoogleAppsConstants.PHOTO_ATTR));
        assertNull(object.getAttributeByName(org.identityconnectors.framework.common.objects.PredefinedAttributes.GROUPS_NAME));
    }

    @Test
    public void userResourceUsesIdWithoutRevisionWhenNoEtagAndNoName() {
        // Given: User without revision or structured name
        Set<String> attributes = new HashSet<>(List.of(GoogleAppsConstants.GIVEN_NAME_ATTR));
        User user = new User().setId("user-id").setPrimaryEmail("ada@example.com");

        // When: Convert the requested name attribute
        ConnectorObject object = UserConverter.fromUser(user, attributes, null, new GoogleAppsConfiguration(), new OperationOptionsBuilder().build());

        // Then: Preserve identity without inventing a revision
        assertEquals(object.getUid().getUidValue(), "user-id");
        assertNull(object.getUid().getRevision());
        assertNotNull(object.getAttributeByName(GoogleAppsConstants.GIVEN_NAME_ATTR));
    }

    @Test
    public void groupResourceMapsProjectedAndDefaultAttributes() {
        // Given: Group fields without requested memberships
        Set<String> attributes = new HashSet<>(Arrays.asList(
                GoogleAppsConstants.NAME_ATTR,
                org.identityconnectors.framework.common.objects.PredefinedAttributes.DESCRIPTION,
                GoogleAppsConstants.ADMIN_CREATED_ATTR,
                GoogleAppsConstants.ALIASES_ATTR,
                GoogleAppsConstants.NON_EDITABLE_ALIASES_ATTR,
                GoogleAppsConstants.DIRECT_MEMBERS_COUNT_ATTR));
        Group group = new Group().setId("group-id").setEtag("etag-2").setEmail("eng@example.com")
                .setName("Engineering").setDescription("Team").setAdminCreated(true)
                .setAliases(List.of("engineering@example.com"))
                .setNonEditableAliases(List.of("old@example.com"))
                .setDirectMembersCount(4L);

        // When: Convert projected group attributes
        ConnectorObject object = GroupConverter.fromGroup(group, attributes, null, new OperationOptionsBuilder().build());
        ConnectorObject groupWithoutEtag = GroupConverter.fromGroup(new Group().setId("g2").setEmail("g2@example.com"),
                new HashSet<>(), null, new OperationOptionsBuilder().build());

        // Then: Preserve identity and omit unrequested memberships
        assertEquals(object.getObjectClass(), ObjectClass.GROUP);
        assertEquals(object.getUid().getUidValue(), "group-id");
        assertEquals(object.getUid().getRevision(), "etag-2");
        assertEquals(object.getName().getNameValue(), "eng@example.com");
        assertEquals(object.getAttributeByName(GoogleAppsConstants.NAME_ATTR).getValue(), List.of("Engineering"));
        assertEquals(object.getAttributeByName(GoogleAppsConstants.DIRECT_MEMBERS_COUNT_ATTR).getValue(), List.of(4L));
        assertNull(object.getAttributeByName(GoogleAppsConstants.MEMBERS_ATTR));

        assertNull(groupWithoutEtag.getUid().getRevision());
    }
}
