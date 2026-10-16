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
        GoogleAppsConnector connector = new GoogleAppsConnector();
        Set<String> attributes = new HashSet<>(Arrays.asList(
                GoogleAppsConnector.GIVEN_NAME_ATTR,
                GoogleAppsConnector.FAMILY_NAME_ATTR,
                GoogleAppsConnector.FULL_NAME_ATTR,
                GoogleAppsConnector.IS_ADMIN_ATTR,
                GoogleAppsConnector.IS_DELEGATED_ADMIN_ATTR,
                GoogleAppsConnector.LAST_LOGIN_TIME_ATTR,
                GoogleAppsConnector.CREATION_TIME_ATTR,
                GoogleAppsConnector.AGREED_TO_TERMS_ATTR,
                GoogleAppsConnector.SUSPENDED_ATTR,
                GoogleAppsConnector.SUSPENSION_REASON_ATTR,
                GoogleAppsConnector.CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR,
                GoogleAppsConnector.IP_WHITELISTED_ATTR,
                GoogleAppsConnector.IMS_ATTR,
                GoogleAppsConnector.EMAILS_ATTR,
                GoogleAppsConnector.EXTERNAL_IDS_ATTR,
                GoogleAppsConnector.RELATIONS_ATTR,
                GoogleAppsConnector.ADDRESSES_ATTR,
                GoogleAppsConnector.ORGANIZATIONS_ATTR,
                GoogleAppsConnector.PHONES_ATTR,
                GoogleAppsConnector.ALIASES_ATTR,
                GoogleAppsConnector.LOCATIONS_ATTR,
                GoogleAppsConnector.NON_EDITABLE_ALIASES_ATTR,
                GoogleAppsConnector.CUSTOMER_ID_ATTR,
                GoogleAppsConnector.ORG_UNIT_PATH_ATTR,
                GoogleAppsConnector.IS_MAILBOX_SETUP_ATTR,
                GoogleAppsConnector.INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR,
                GoogleAppsConnector.THUMBNAIL_PHOTO_URL_ATTR,
                GoogleAppsConnector.DELETION_TIME_ATTR));
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

        ConnectorObject object = connector.fromUser(user, attributes, null);

        assertEquals(object.getObjectClass(), ObjectClass.ACCOUNT);
        assertEquals(object.getUid().getUidValue(), "user-id");
        assertEquals(object.getUid().getRevision(), "etag-1");
        assertEquals(object.getName().getNameValue(), "ada@example.com");
        assertEquals(object.getAttributeByName(GoogleAppsConnector.GIVEN_NAME_ATTR).getValue(), List.of("Ada"));
        assertEquals(object.getAttributeByName(GoogleAppsConnector.FAMILY_NAME_ATTR).getValue(), List.of("Lovelace"));
        assertEquals(object.getAttributeByName(GoogleAppsConnector.SUSPENDED_ATTR).getValue(), List.of(false));
        assertEquals(object.getAttributeByName(GoogleAppsConnector.ORG_UNIT_PATH_ATTR).getValue(), List.of("/Engineering"));
        assertNull(object.getAttributeByName(GoogleAppsConnector.PHOTO_ATTR));
        assertNull(object.getAttributeByName(org.identityconnectors.framework.common.objects.PredefinedAttributes.GROUPS_NAME));
    }

    @Test
    public void userResourceUsesIdWithoutRevisionWhenNoEtagAndNoName() {
        GoogleAppsConnector connector = new GoogleAppsConnector();
        Set<String> attributes = new HashSet<>(List.of(GoogleAppsConnector.GIVEN_NAME_ATTR));
        User user = new User().setId("user-id").setPrimaryEmail("ada@example.com");

        ConnectorObject object = connector.fromUser(user, attributes, null);

        assertEquals(object.getUid().getUidValue(), "user-id");
        assertNull(object.getUid().getRevision());
        assertNotNull(object.getAttributeByName(GoogleAppsConnector.GIVEN_NAME_ATTR));
    }

    @Test
    public void groupResourceMapsProjectedAndDefaultAttributes() {
        GoogleAppsConnector connector = new GoogleAppsConnector();
        Set<String> attributes = new HashSet<>(Arrays.asList(
                GoogleAppsConnector.NAME_ATTR,
                org.identityconnectors.framework.common.objects.PredefinedAttributes.DESCRIPTION,
                GoogleAppsConnector.ADMIN_CREATED_ATTR,
                GoogleAppsConnector.ALIASES_ATTR,
                GoogleAppsConnector.NON_EDITABLE_ALIASES_ATTR,
                GoogleAppsConnector.DIRECT_MEMBERS_COUNT_ATTR));
        Group group = new Group().setId("group-id").setEtag("etag-2").setEmail("eng@example.com")
                .setName("Engineering").setDescription("Team").setAdminCreated(true)
                .setAliases(List.of("engineering@example.com"))
                .setNonEditableAliases(List.of("old@example.com"))
                .setDirectMembersCount(4L);

        ConnectorObject object = connector.fromGroup(group, attributes, null);

        assertEquals(object.getObjectClass(), ObjectClass.GROUP);
        assertEquals(object.getUid().getUidValue(), "group-id");
        assertEquals(object.getUid().getRevision(), "etag-2");
        assertEquals(object.getName().getNameValue(), "eng@example.com");
        assertEquals(object.getAttributeByName(GoogleAppsConnector.NAME_ATTR).getValue(), List.of("Engineering"));
        assertEquals(object.getAttributeByName(GoogleAppsConnector.DIRECT_MEMBERS_COUNT_ATTR).getValue(), List.of(4L));
        assertNull(object.getAttributeByName(GoogleAppsConnector.MEMBERS_ATTR));

        ConnectorObject groupWithoutEtag = connector.fromGroup(new Group().setId("g2").setEmail("g2@example.com"),
                new HashSet<>(), null);
        assertNull(groupWithoutEtag.getUid().getRevision());
    }
}
