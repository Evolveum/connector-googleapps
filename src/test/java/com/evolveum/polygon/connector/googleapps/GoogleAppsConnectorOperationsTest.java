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

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.directory.Directory;
import com.google.api.services.licensing.Licensing;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
Connector operations are exercised against in-memory Google API responses.
*/
public class GoogleAppsConnectorOperationsTest {

    @Test
    public void createUserCanCreateAliasesPhotoAdminAndAutomaticLicense() {
        // Given: User attributes and related resource responses
        ScriptedTransport transport = new ScriptedTransport(
                json("{\"id\":\"ada@example.com\",\"etag\":\"user-v1\"}"),
                json("{\"id\":\"alias-id\",\"etag\":\"alias-v1\",\"alias\":\"ada.alias@example.com\"}"),
                json("{\"id\":\"ada@example.com\",\"etag\":\"photo-v1\"}"),
                empty(),
                json("{\"productId\":\"product\",\"skuId\":\"sku\",\"userId\":\"ada@example.com\",\"etags\":\"license-v1\"}"));
        LocalConfiguration configuration = new LocalConfiguration(transport);
        configuration.setAutoAddLicense(true);
        configuration.setProductId("product");
        configuration.setSkuId("sku");
        GoogleAppsConnector connector = connector(configuration);
        Set<Attribute> attributes = attributes(
                new Name("ada@example.com"),
                AttributeBuilder.buildPassword(new GuardedString("secret".toCharArray())),
                AttributeBuilder.build(GoogleAppsConstants.GIVEN_NAME_ATTR, "Ada"),
                AttributeBuilder.build(GoogleAppsConstants.FAMILY_NAME_ATTR, "Lovelace"),
                AttributeBuilder.build(GoogleAppsConstants.ALIASES_ATTR, List.of("ada.alias@example.com")),
                AttributeBuilder.build(GoogleAppsConstants.PHOTO_ATTR, (Object) new byte[]{1, 2, 3}),
                AttributeBuilder.build(GoogleAppsConstants.IS_ADMIN_ATTR, true));

        // When: Create the user and related resources
        Uid uid = connector.create(ObjectClass.ACCOUNT, attributes, new OperationOptionsBuilder().build());

        // Then: Verify identity and every expected request
        assertEquals(uid.getUidValue(), "ada@example.com");
        assertEquals(uid.getRevision(), "user-v1");
        assertEquals(transport.requestCount, 5);
        assertTrue(transport.responses.isEmpty());
        connector.dispose();
    }

    @Test
    public void createsGroupWithMembersAndOtherSupportedObjectClasses() {
        // Given: Group and standalone membership creation responses
        ScriptedTransport transport = new ScriptedTransport(
                json("{\"id\":\"engineering@example.com\",\"etag\":\"group-v1\",\"email\":\"engineering@example.com\"}"),
                json("{\"id\":\"member-id\",\"email\":\"ada@example.com\",\"role\":\"MANAGER\",\"etag\":\"member-v1\"}"),
                json("{\"id\":\"member-id\",\"email\":\"grace@example.com\",\"role\":\"MEMBER\",\"etag\":\"member-v2\"}"),
                json("{\"name\":\"Engineering\",\"orgUnitPath\":\"/Engineering\",\"etag\":\"ou-v1\"}"),
                json("{\"productId\":\"product\",\"skuId\":\"sku\",\"userId\":\"ada@example.com\",\"etags\":\"license-v2\"}"));
        LocalConfiguration configuration = new LocalConfiguration(transport);
        configuration.setProductId("product");
        configuration.setSkuId("sku");
        GoogleAppsConnector connector = connector(configuration);
        Map<String, String> groupMember = new HashMap<>();
        groupMember.put(GoogleAppsConstants.EMAIL_ATTR, "ada@example.com");
        groupMember.put(GoogleAppsConstants.ROLE_ATTR, "MANAGER");

        // When: Create the group and related resources
        Uid groupUid = connector.create(ObjectClass.GROUP, attributes(new Name("engineering@example.com")),
                new OperationOptionsBuilder().build());
        Uid managerUid = connector.create(GoogleAppsConstants.MEMBER, attributes(
                AttributeBuilder.build(GoogleAppsConstants.GROUP_KEY_ATTR, "engineering@example.com"),
                AttributeBuilder.build(GoogleAppsConstants.EMAIL_ATTR, groupMember.get(GoogleAppsConstants.EMAIL_ATTR)),
                AttributeBuilder.build(GoogleAppsConstants.ROLE_ATTR, groupMember.get(GoogleAppsConstants.ROLE_ATTR))), new OperationOptionsBuilder().build());
        Uid memberUid = connector.create(GoogleAppsConstants.MEMBER, attributes(
                AttributeBuilder.build(GoogleAppsConstants.GROUP_KEY_ATTR, "engineering@example.com"),
                AttributeBuilder.build(GoogleAppsConstants.EMAIL_ATTR, "grace@example.com")),
                new OperationOptionsBuilder().build());
        Uid orgUnitUid = connector.create(GoogleAppsConstants.ORG_UNIT, attributes(
                new Name("Engineering"),
                AttributeBuilder.build(GoogleAppsConstants.PARENT_ORG_UNIT_PATH_ATTR, "/")),
                new OperationOptionsBuilder().build());
        Uid licenseUid = connector.create(GoogleAppsConstants.LICENSE_ASSIGNMENT, attributes(
                AttributeBuilder.build(GoogleAppsConstants.PRODUCT_ID_ATTR, "product"),
                AttributeBuilder.build(GoogleAppsConstants.SKU_ID_ATTR, "sku"),
                AttributeBuilder.build(GoogleAppsConstants.USER_ID_ATTR, "ada@example.com")),
                new OperationOptionsBuilder().build());

        // Then: Preserve resource identities and creation order
        assertEquals(groupUid.getUidValue(), "engineering@example.com");
        assertEquals(managerUid.getUidValue(), "engineering@example.com/ada@example.com");
        assertEquals(memberUid.getUidValue(), "engineering@example.com/grace@example.com");
        assertEquals(orgUnitUid.getUidValue(), "Engineering");
        assertEquals(orgUnitUid.getRevision(), "ou-v1");
        assertEquals(licenseUid.getUidValue(), "product/sku/sku/user/ada@example.com");
        assertEquals(transport.requestCount, 5);
        connector.dispose();
    }

    @Test
    public void groupMembers() {
        // Given: Group with mixed membership inputs
        ScriptedTransport transport = new ScriptedTransport(
                groupExchange(json("{\"id\":\"group-id\",\"etag\":\"group-v1\"}")),
                memberExchange("ada@example.com", "MANAGER", json("{\"email\":\"ada@example.com\",\"etag\":\"ada-v1\"}")),
                memberExchange("grace@example.com", "MEMBER", json("{\"email\":\"grace@example.com\",\"etag\":\"grace-v1\"}")),
                memberExchange("alan@example.com", "OWNER", json("{\"email\":\"alan@example.com\",\"etag\":\"alan-v1\"}")));
        GoogleAppsConnector connector = connector(new LocalConfiguration(transport));
        Set<Attribute> attributes = attributes(new Name("engineering@example.com"), AttributeBuilder.build(GoogleAppsConstants.MEMBERS_ATTR,
                List.of(Map.of("email", "ada@example.com", "role", "MANAGER"), "grace@example.com", Map.of("email", "alan@example.com", "role", "OWNER"))));
        try {
            // When: Create group and memberships
            Uid uid = connector.create(ObjectClass.GROUP, attributes, new OperationOptionsBuilder().build());

            // Then: Verify identity and every request
            assertEquals(uid.getUidValue(), "group-id");
            assertEquals(uid.getRevision(), "group-v1");
            assertEquals(uid.getNameHintValue(), "engineering@example.com");
            assertEquals(transport.requestCount, 4);
            assertTrue(transport.exchanges.isEmpty());
        } finally {
            connector.dispose();
        }
    }

    @Test(dataProvider = "defaultRoles")
    public void defaultRole(Map<String, String> member) {
        // Given: Member without an explicit role
        ScriptedTransport transport = new ScriptedTransport(groupExchange(json("{\"id\":\"group-id\",\"etag\":\"group-v1\"}")),
                memberExchange("ada@example.com", "MEMBER", json("{\"email\":\"ada@example.com\",\"etag\":\"ada-v1\"}")));
        GoogleAppsConnector connector = connector(new LocalConfiguration(transport));
        try {
            // When: Create group with default membership
            Uid uid = connector.create(ObjectClass.GROUP, attributes(new Name("engineering@example.com"),
                    AttributeBuilder.build(GoogleAppsConstants.MEMBERS_ATTR, List.of(member))), new OperationOptionsBuilder().build());

            // Then: Verify default role and identity
            assertEquals(uid.getUidValue(), "group-id");
            assertEquals(uid.getRevision(), "group-v1");
            assertEquals(uid.getNameHintValue(), "engineering@example.com");
            assertEquals(transport.requestCount, 2);
            assertTrue(transport.exchanges.isEmpty());
        } finally {
            connector.dispose();
        }
    }

    @DataProvider
    public Object[][] defaultRoles() {
        return new Object[][]{{Map.of("email", "ada@example.com")}, {Map.of("email", "ada@example.com", "role", " ")}};
    }

    @Test(dataProvider = "emptyMembers")
    public void emptyMembers(List<Object> members) {
        // Given: Group without requested memberships
        ScriptedTransport transport = new ScriptedTransport(groupExchange(json("{\"id\":\"group-id\",\"etag\":\"group-v1\"}")));
        GoogleAppsConnector connector = connector(new LocalConfiguration(transport));
        Set<Attribute> attributes = attributes(new Name("engineering@example.com"));
        if (members != null) {
            attributes.add(AttributeBuilder.build(GoogleAppsConstants.MEMBERS_ATTR, members));
        }
        try {
            // When: Create the group
            Uid uid = connector.create(ObjectClass.GROUP, attributes, new OperationOptionsBuilder().build());

            // Then: Verify group without membership requests
            assertEquals(uid.getUidValue(), "group-id");
            assertEquals(uid.getRevision(), "group-v1");
            assertEquals(uid.getNameHintValue(), "engineering@example.com");
            assertEquals(transport.requestCount, 1);
            assertTrue(transport.exchanges.isEmpty());
        } finally {
            connector.dispose();
        }
    }

    @DataProvider
    public Object[][] emptyMembers() {
        return new Object[][]{{null}, {List.of()}};
    }

    @Test(dataProvider = "invalidMembers")
    public void invalidMembers(Object member) {
        // Given: Valid member followed by invalid input
        ScriptedTransport transport = new ScriptedTransport();
        GoogleAppsConnector connector = connector(new LocalConfiguration(transport));
        Set<Attribute> attributes = attributes(new Name("engineering@example.com"),
                AttributeBuilder.build(GoogleAppsConstants.MEMBERS_ATTR, List.of(Map.of("email", "ada@example.com", "role", "MANAGER"), member)));
        try {
            // When: Attempt group creation
            // Then: Reject input before any write
            expectThrows(InvalidAttributeValueException.class, () -> connector.create(ObjectClass.GROUP, attributes, new OperationOptionsBuilder().build()));
            assertEquals(transport.requestCount, 0);
        } finally {
            connector.dispose();
        }
    }

    @DataProvider
    public Object[][] invalidMembers() {
        return new Object[][]{{17}, {" "}, {Map.of("role", "MEMBER")}, {Map.of("email", " ")}, {Map.of("email", 17)},
                {Map.of("email", "grace@example.com", "role", 17)}};
    }

    @Test(dataProvider = "memberFailures")
    public void memberFailure(int status, String reason, Class<? extends ConnectorException> failureType) {
        // Given: Membership failure after one successful addition
        ScriptedTransport transport = new ScriptedTransport(groupExchange(json("{\"id\":\"group-id\",\"etag\":\"group-v1\"}")),
                memberExchange("ada@example.com", "MEMBER", json("{\"email\":\"ada@example.com\",\"etag\":\"ada-v1\"}")),
                memberExchange("grace@example.com", "MEMBER", error(status, reason, "Membership rejected")));
        GoogleAppsConnector connector = connector(new LocalConfiguration(transport));
        Set<Attribute> attributes = attributes(new Name("engineering@example.com"),
                AttributeBuilder.build(GoogleAppsConstants.MEMBERS_ATTR, List.of("ada@example.com", "grace@example.com", "alan@example.com")));
        try {
            // When: Attempt all requested membership writes
            // Then: Propagate failure and stop further writes
            ConnectorException failure = expectThrows(failureType, () -> connector.create(ObjectClass.GROUP, attributes, new OperationOptionsBuilder().build()));
            assertEquals(failure.getClass(), failureType);
            assertTrue(failure.getCause() instanceof GoogleJsonResponseException);
            assertEquals(((GoogleJsonResponseException) failure.getCause()).getStatusCode(), status);
            assertEquals(transport.requestCount, 3);
            assertTrue(transport.exchanges.isEmpty());
        } finally {
            connector.dispose();
        }
    }

    @DataProvider
    public Object[][] memberFailures() {
        return new Object[][]{{403, "forbidden", ConnectorException.class}, {404, "notFound", UnknownUidException.class},
                {409, "duplicate", AlreadyExistsException.class}};
    }

    @Test
    public void groupFailure() {
        // Given: Existing group and requested membership
        ScriptedTransport transport = new ScriptedTransport(groupExchange(duplicate()));
        GoogleAppsConnector connector = connector(new LocalConfiguration(transport));
        Set<Attribute> attributes = attributes(new Name("engineering@example.com"),
                AttributeBuilder.build(GoogleAppsConstants.MEMBERS_ATTR, List.of("ada@example.com")));
        try {
            // When: Attempt duplicate group creation
            // Then: Preserve error without adding memberships
            AlreadyExistsException failure = expectThrows(AlreadyExistsException.class,
                    () -> connector.create(ObjectClass.GROUP, attributes, new OperationOptionsBuilder().build()));
            assertTrue(failure.getCause() instanceof GoogleJsonResponseException);
            assertEquals(((GoogleJsonResponseException) failure.getCause()).getStatusCode(), 409);
            assertEquals(transport.requestCount, 1);
            assertTrue(transport.exchanges.isEmpty());
        } finally {
            connector.dispose();
        }
    }

    @Test
    public void createMember() {
        // Given: Standalone member creation response
        ScriptedTransport transport = new ScriptedTransport(new Exchange("https://admin.googleapis.com/admin/directory/v1/groups/engineering@example.com/members",
                Map.of("email", "grace@example.com"), json("{\"id\":\"grace-id\",\"email\":\"grace@example.com\",\"role\":\"MEMBER\",\"etag\":\"grace-v1\"}")));
        GoogleAppsConnector connector = connector(new LocalConfiguration(transport));
        try {
            // When: Create the standalone member
            Uid uid = connector.create(GoogleAppsConstants.MEMBER, attributes(AttributeBuilder.build(GoogleAppsConstants.GROUP_KEY_ATTR, "engineering@example.com"),
                    AttributeBuilder.build(GoogleAppsConstants.EMAIL_ATTR, "grace@example.com")), new OperationOptionsBuilder().build());

            // Then: Verify member identity and request
            assertEquals(uid.getUidValue(), "engineering@example.com/grace@example.com");
            assertEquals(uid.getNameHintValue(), "grace@example.com");
            assertEquals(uid.getRevision(), "grace-v1");
            assertEquals(transport.requestCount, 1);
            assertTrue(transport.exchanges.isEmpty());
        } finally {
            connector.dispose();
        }
    }

    @Test
    public void createOrgUnit() {
        // Given: Organizational unit creation response
        ScriptedTransport transport = new ScriptedTransport(new Exchange("https://admin.googleapis.com/admin/directory/v1/customer/my_customer/orgunits?fields=orgUnitPath,etag",
                Map.of("name", "Engineering", "parentOrgUnitPath", "/"), json("{\"name\":\"Engineering\",\"orgUnitPath\":\"/Engineering\",\"etag\":\"ou-v1\"}")));
        GoogleAppsConnector connector = connector(new LocalConfiguration(transport));
        try {
            // When: Create the organizational unit
            Uid uid = connector.create(GoogleAppsConstants.ORG_UNIT, attributes(new Name("Engineering"),
                    AttributeBuilder.build(GoogleAppsConstants.PARENT_ORG_UNIT_PATH_ATTR, "/")), new OperationOptionsBuilder().build());

            // Then: Verify organizational unit and request
            assertEquals(uid.getUidValue(), "Engineering");
            assertEquals(uid.getNameHintValue(), "Engineering");
            assertEquals(uid.getRevision(), "ou-v1");
            assertEquals(transport.requestCount, 1);
            assertTrue(transport.exchanges.isEmpty());
        } finally {
            connector.dispose();
        }
    }

    @Test
    public void createLicense() {
        // Given: License assignment creation response
        ScriptedTransport transport = new ScriptedTransport(new Exchange(
                "https://licensing.googleapis.com/apps/licensing/v1/product/product/sku/sku/user?fields=productId,skuId,userId",
                Map.of("userId", "ada@example.com"), json("{\"productId\":\"product\",\"skuId\":\"sku\",\"userId\":\"ada@example.com\"}")));
        GoogleAppsConnector connector = connector(new LocalConfiguration(transport));
        try {
            // When: Create the license assignment
            Uid uid = connector.create(GoogleAppsConstants.LICENSE_ASSIGNMENT, attributes(AttributeBuilder.build(GoogleAppsConstants.PRODUCT_ID_ATTR, "product"),
                    AttributeBuilder.build(GoogleAppsConstants.SKU_ID_ATTR, "sku"), AttributeBuilder.build(GoogleAppsConstants.USER_ID_ATTR, "ada@example.com")),
                    new OperationOptionsBuilder().build());

            // Then: Verify license identity and request
            assertEquals(uid.getUidValue(), "product/sku/sku/user/ada@example.com");
            assertEquals(uid.getRevision(), null);
            assertEquals(transport.requestCount, 1);
            assertTrue(transport.exchanges.isEmpty());
        } finally {
            connector.dispose();
        }
    }

    @Test
    public void updateOperationBranchesBuildRequestsAndReturnUpdatedUids() {
        // Given: Expected updates for every resource type
        ScriptedTransport transport = new ScriptedTransport(
                new Exchange("PUT", "https://admin.googleapis.com/admin/directory/v1/users/ada@example.com?fields=id,etag",
                        Map.of("name", Map.of("givenName", "Ada")), json("{\"id\":\"ada@example.com\",\"etag\":\"user-v2\"}")),
                new Exchange("PATCH", "https://admin.googleapis.com/admin/directory/v1/groups/engineering@example.com",
                        Map.of("name", "Engineering"), json("{\"id\":\"engineering@example.com\",\"etag\":\"group-v2\"}")),
                new Exchange("PATCH", "https://admin.googleapis.com/admin/directory/v1/groups/engineering@example.com/members/ada@example.com",
                        Map.of("role", "OWNER"), json("{\"id\":\"member-id\",\"email\":\"ada@example.com\",\"role\":\"OWNER\"}")),
                new Exchange("PATCH", "https://admin.googleapis.com/admin/directory/v1/customer/my_customer/orgunits/Engineering?fields=orgUnitPath,etag",
                        Map.of("name", "New Engineering"), json("{\"name\":\"New Engineering\",\"orgUnitPath\":\"/New Engineering\",\"etag\":\"ou-v2\"}")),
                new Exchange("PATCH", "https://licensing.googleapis.com/apps/licensing/v1/product/Google-Drive-storage/sku/Google-Drive-storage-1TB/user/ada@example.com",
                        Map.of("skuId", "Google-Drive-storage-2TB"),
                        json("{\"productId\":\"Google-Drive-storage\",\"skuId\":\"Google-Drive-storage-2TB\",\"userId\":\"ada@example.com\"}")));
        GoogleAppsConnector connector = connector(new LocalConfiguration(transport));
        OperationOptionsBuilder options = new OperationOptionsBuilder();

        try {
            // When: Apply replacement deltas to each resource
            Set<AttributeDelta> user = connector.updateDelta(ObjectClass.ACCOUNT, new Uid("ada@example.com"),
                    Set.of(AttributeDeltaBuilder.build(GoogleAppsConstants.GIVEN_NAME_ATTR, "Ada")), options.build());
            Set<AttributeDelta> group = connector.updateDelta(ObjectClass.GROUP, new Uid("engineering@example.com"),
                    Set.of(AttributeDeltaBuilder.build(GoogleAppsConstants.NAME_ATTR, "Engineering")), options.build());
            Set<AttributeDelta> member = connector.updateDelta(GoogleAppsConstants.MEMBER, new Uid("engineering@example.com/ada@example.com"),
                    Set.of(AttributeDeltaBuilder.build(GoogleAppsConstants.ROLE_ATTR, "OWNER")), options.build());
            Set<AttributeDelta> orgUnit = connector.updateDelta(GoogleAppsConstants.ORG_UNIT, new Uid("Engineering"),
                    Set.of(AttributeDeltaBuilder.build(Name.NAME, "New Engineering")), options.build());
            Set<AttributeDelta> license = connector.updateDelta(GoogleAppsConstants.LICENSE_ASSIGNMENT,
                    new Uid("Google-Drive-storage/sku/Google-Drive-storage-1TB/user/ada@example.com"),
                    Set.of(AttributeDeltaBuilder.build(GoogleAppsConstants.SKU_ID_ATTR, "Google-Drive-storage-2TB")), options.build());

            // Then: Verify requests and returned side effects
            assertEquals(user, Set.of());
            assertEquals(group, Set.of());
            assertEquals(member, Set.of());
            assertEquals(orgUnit, Set.of(AttributeDeltaBuilder.build(Uid.NAME, "New Engineering")));
            assertEquals(license, Set.of(AttributeDeltaBuilder.build(Uid.NAME, "Google-Drive-storage/sku/Google-Drive-storage-2TB/user/ada@example.com")));
            assertEquals(transport.requestCount, 5);
            assertTrue(transport.exchanges.isEmpty());
        } finally {
            connector.dispose();
        }
    }

    @Test
    public void deleteOperationsTreatGoogleNotFoundAsUnknownUid() {
        // Given: Missing resources for every deletion type
        ScriptedTransport transport = new ScriptedTransport(notFound(), notFound(), notFound(), notFound(), notFound());
        GoogleAppsConnector connector = connector(new LocalConfiguration(transport));
        OperationOptionsBuilder options = new OperationOptionsBuilder();

        // When: Delete resources absent from Google
        // Then: Report unknown identities for each request
        expectThrows(UnknownUidException.class,
                () -> connector.delete(ObjectClass.ACCOUNT, new Uid("missing@example.com"), options.build()));
        expectThrows(UnknownUidException.class,
                () -> connector.delete(ObjectClass.GROUP, new Uid("missing-group"), options.build()));
        expectThrows(UnknownUidException.class,
                () -> connector.delete(GoogleAppsConstants.MEMBER,
                        new Uid("group@example.com/missing@example.com"), options.build()));
        expectThrows(UnknownUidException.class,
                () -> connector.delete(GoogleAppsConstants.ORG_UNIT, new Uid("Missing"), options.build()));
        expectThrows(UnknownUidException.class,
                () -> connector.delete(GoogleAppsConstants.LICENSE_ASSIGNMENT,
                        new Uid("Google-Drive-storage/sku/Google-Drive-storage-1TB/user/missing@example.com"), options.build()));

        assertEquals(transport.requestCount, 5);
        connector.dispose();
    }

    @Test
    public void duplicateCreateResponseBecomesAlreadyExists() {
        ScriptedTransport transport = new ScriptedTransport(duplicate());
        GoogleAppsConnector connector = connector(new LocalConfiguration(transport));

        expectThrows(AlreadyExistsException.class,
                () -> connector.create(ObjectClass.GROUP,
                        attributes(new Name("engineering@example.com")), new OperationOptionsBuilder().build()));
        connector.dispose();
    }

    @Test
    public void accountReadNotFoundIsHandledAndUnsupportedMemberSearchThrows() {
        // Given: Missing account and unsupported member search
        ScriptedTransport transport = new ScriptedTransport(notFound());
        GoogleAppsConnector connector = connector(new LocalConfiguration(transport));
        List<ConnectorObject> results = new ArrayList<>();
        OperationOptionsBuilder options = new OperationOptionsBuilder();

        try {
            // When: Read the missing account
            connector.executeQuery(ObjectClass.ACCOUNT, new GoogleFilter(new Uid("missing@example.com")), results::add, options.build());

            // Then: Return nothing and reject member enumeration
            expectThrows(InvalidAttributeValueException.class,
                    () -> connector.executeQuery(GoogleAppsConstants.MEMBER, null, results::add, options.build()));
            assertTrue(results.isEmpty());
            assertEquals(transport.requestCount, 1);
            assertTrue(transport.responses.isEmpty());
        } finally {
            connector.dispose();
        }
    }

    @Test
    public void accountGroupMemberAndOrgUnitSearchesMapGoogleResponses() {
        // Given: Search responses for directory resources
        ScriptedTransport transport = new ScriptedTransport(
                json("{\"users\":[{\"id\":\"ada@example.com\",\"etag\":\"user-v1\",\"primaryEmail\":\"ada@example.com\",\"name\":{\"givenName\":\"Ada\"}}]}"),
                json("{\"groups\":[{\"id\":\"engineering@example.com\",\"etag\":\"group-v1\",\"email\":\"engineering@example.com\",\"name\":\"Engineering\"}]}"),
                json("{\"members\":[{\"id\":\"member-id\",\"email\":\"ada@example.com\",\"role\":\"MEMBER\",\"type\":\"USER\",\"etag\":\"member-v1\"}]}"),
                json("{\"organizationUnits\":[{\"name\":\"Engineering\",\"orgUnitPath\":\"/Engineering\",\"etag\":\"ou-v1\"}]}"));
        GoogleAppsConnector connector = connector(new LocalConfiguration(transport));
        List<ConnectorObject> users = new ArrayList<>();
        List<ConnectorObject> groups = new ArrayList<>();
        List<ConnectorObject> members = new ArrayList<>();
        List<ConnectorObject> orgUnits = new ArrayList<>();

        // When: Query each resource type
        connector.executeQuery(ObjectClass.ACCOUNT,
                new GoogleFilter(FilterBuilder.startsWith(AttributeBuilder.build(Name.NAME, "ada"))), users::add,
                new OperationOptionsBuilder().setAttributesToGet(GoogleAppsConstants.GIVEN_NAME_ATTR).build());
        connector.executeQuery(ObjectClass.GROUP,
                new GoogleFilter(FilterBuilder.contains(AttributeBuilder.build(GoogleAppsConstants.MEMBERS_ATTR, "ada@example.com"))),
                groups::add, new OperationOptionsBuilder().setAttributesToGet(GoogleAppsConstants.NAME_ATTR).build());
        connector.executeQuery(GoogleAppsConstants.MEMBER,
                new GoogleFilter(FilterBuilder.equalTo(AttributeBuilder.build(GoogleAppsConstants.GROUP_KEY_ATTR, "engineering@example.com"))),
                members::add, new OperationOptionsBuilder().build());
        connector.executeQuery(GoogleAppsConstants.ORG_UNIT, null, orgUnits::add,
                new OperationOptionsBuilder().setAttributesToGet(GoogleAppsConstants.ORG_UNIT_PATH_ATTR).build());

        // Then: Verify returned identities and requested attributes
        assertEquals(users.size(), 1);
        assertEquals(users.get(0).getUid().getUidValue(), "ada@example.com");
        assertEquals(users.get(0).getAttributeByName(GoogleAppsConstants.GIVEN_NAME_ATTR).getValue().get(0), "Ada");
        assertEquals(groups.size(), 1);
        assertEquals(groups.get(0).getUid().getUidValue(), "engineering@example.com");
        assertEquals(members.size(), 1);
        assertEquals(members.get(0).getObjectClass(), GoogleAppsConstants.MEMBER);
        assertEquals(orgUnits.size(), 1);
        assertEquals(orgUnits.get(0).getUid().getUidValue(), "Engineering");
        assertEquals(transport.requestCount, 4);
        assertTrue(transport.responses.isEmpty());
        connector.dispose();
    }

    @Test
    public void testOperationAndUidReadsCoverEachDirectoryResource() {
        // Given: Connection and directory resource responses
        ScriptedTransport transport = new ScriptedTransport(
                json("{\"users\":[]}"),
                json("{\"id\":\"ada@example.com\",\"etag\":\"user-read\",\"primaryEmail\":\"ada@example.com\",\"name\":{\"givenName\":\"Ada\"}}"),
                json("{\"id\":\"engineering@example.com\",\"etag\":\"group-read\",\"email\":\"engineering@example.com\",\"name\":\"Engineering\"}"),
                json("{\"id\":\"member-read\",\"etag\":\"member-read\",\"email\":\"ada@example.com\",\"role\":\"MEMBER\",\"type\":\"USER\"}"),
                json("{\"name\":\"Engineering\",\"orgUnitPath\":\"/Engineering\",\"etag\":\"ou-read\"}"));
        GoogleAppsConnector connector = connector(new LocalConfiguration(transport));
        OperationOptionsBuilder options = new OperationOptionsBuilder();
        List<ConnectorObject> users = new ArrayList<>();
        List<ConnectorObject> groups = new ArrayList<>();
        List<ConnectorObject> members = new ArrayList<>();
        List<ConnectorObject> orgUnits = new ArrayList<>();

        // When: Check connection and read directory resources
        connector.test();
        connector.executeQuery(ObjectClass.ACCOUNT,
                new GoogleFilter(new Uid("ada@example.com")),
                users::add, options.setAttributesToGet(GoogleAppsConstants.GIVEN_NAME_ATTR).build());
        connector.executeQuery(ObjectClass.GROUP,
                new GoogleFilter(new Uid("engineering@example.com")),
                groups::add, options.setAttributesToGet(GoogleAppsConstants.NAME_ATTR).build());
        connector.executeQuery(GoogleAppsConstants.MEMBER,
                new GoogleFilter(new Uid("engineering@example.com/ada@example.com")), members::add, options.build());
        connector.executeQuery(GoogleAppsConstants.ORG_UNIT,
                new GoogleFilter(new Uid("Engineering")),
                orgUnits::add, options.setAttributesToGet(GoogleAppsConstants.ORG_UNIT_PATH_ATTR).build());

        // Then: Verify returned identities and requested attributes
        assertEquals(users.size(), 1);
        assertEquals(users.get(0).getUid().getUidValue(), "ada@example.com");
        assertEquals(groups.size(), 1);
        assertEquals(members.size(), 1);
        assertEquals(orgUnits.size(), 1);
        assertEquals(transport.requestCount, 5);
        assertTrue(transport.responses.isEmpty());
        connector.dispose();
    }

    private static GoogleAppsConnector connector(GoogleAppsConfiguration configuration) {
        GoogleAppsConnector connector = new GoogleAppsConnector();
        connector.init(configuration);
        return connector;
    }

    private static Set<Attribute> attributes(Attribute... attributes) {
        return new HashSet<>(Arrays.asList(attributes));
    }

    private static MockLowLevelHttpResponse json(String content) {
        return new MockLowLevelHttpResponse().setStatusCode(200)
                .setContentType("application/json; charset=UTF-8").setContent(content);
    }

    private static MockLowLevelHttpResponse empty() {
        return new MockLowLevelHttpResponse().setStatusCode(204).setZeroContent();
    }

    private static MockLowLevelHttpResponse notFound() {
        return error(404, "notFound", "Resource not found");
    }

    private static MockLowLevelHttpResponse duplicate() {
        return error(409, "duplicate", "Entity already exists");
    }

    private static MockLowLevelHttpResponse error(int status, String reason, String message) {
        return new MockLowLevelHttpResponse().setStatusCode(status)
                .setContentType("application/json; charset=UTF-8")
                .setContent("{\"error\":{\"code\":" + status + ",\"message\":\"" + message
                        + "\",\"errors\":[{\"domain\":\"global\",\"reason\":\"" + reason
                        + "\",\"message\":\"" + message + "\"}]}}");
    }

    private static Exchange groupExchange(MockLowLevelHttpResponse response) {
        return new Exchange("https://admin.googleapis.com/admin/directory/v1/groups?fields=id,etag", Map.of("email", "engineering@example.com"), response);
    }

    private static Exchange memberExchange(String email, String role, MockLowLevelHttpResponse response) {
        return new Exchange("https://admin.googleapis.com/admin/directory/v1/groups/group-id/members?fields=email,etag", Map.of("email", email, "role", role), response);
    }

    private static final class Exchange {
        private final String method;
        private final String url;
        private final Map<String, ?> content;
        private final MockLowLevelHttpResponse response;

        private Exchange(String url, Map<String, String> content, MockLowLevelHttpResponse response) {
            this("POST", url, content, response);
        }

        private Exchange(String method, String url, Map<String, ?> content, MockLowLevelHttpResponse response) {
            this.method = method;
            this.url = url;
            this.content = content;
            this.response = response;
        }
    }

    private static final class ScriptedTransport extends MockHttpTransport {
        private final Queue<MockLowLevelHttpResponse> responses = new ArrayDeque<>();
        private final Queue<Exchange> exchanges = new ArrayDeque<>();
        private final boolean strict;
        private int requestCount;

        private ScriptedTransport(MockLowLevelHttpResponse... responses) {
            this.responses.addAll(Arrays.asList(responses));
            strict = false;
        }

        private ScriptedTransport(Exchange exchange, Exchange... exchanges) {
            this.exchanges.add(exchange);
            this.exchanges.addAll(Arrays.asList(exchanges));
            strict = true;
        }

        @Override
        public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
            requestCount++;
            if (strict) {
                assertFalse(exchanges.isEmpty(), "Unexpected request: " + method + " " + url);
                Exchange exchange = exchanges.remove();
                assertEquals(method, exchange.method);
                assertEquals(url, exchange.url);
                return new MockLowLevelHttpRequest(url) {
                    @Override
                    public LowLevelHttpResponse execute() throws IOException {
                        assertEquals(new GsonFactory().fromString(getContentAsString(), Map.class), exchange.content);
                        return exchange.response;
                    }
                };
            }
            if (responses.isEmpty()) {
                throw new IOException("No scripted response for " + method + " " + url);
            }
            return new MockLowLevelHttpRequest(url).setResponse(responses.remove());
        }
    }

    private static final class LocalConfiguration extends GoogleAppsConfiguration {
        private final Directory directory;
        private final Licensing licensing;

        private LocalConfiguration(ScriptedTransport transport) {
            directory = new Directory.Builder(transport, new GsonFactory(), request -> { })
                    .setApplicationName("connector-googleapps-test")
                    .build();
            licensing = new Licensing.Builder(transport, new GsonFactory(), request -> { })
                    .setApplicationName("connector-googleapps-test")
                    .build();
        }

        @Override
        public Directory getDirectory() {
            return directory;
        }

        @Override
        public Licensing getLicensing() {
            return licensing;
        }
    }
}
