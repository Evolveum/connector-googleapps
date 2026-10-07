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

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.directory.Directory;
import com.google.api.services.licensing.Licensing;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
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
import static org.testng.Assert.expectThrows;

/** Connector operations are exercised against in-memory Google API responses. */
public class GoogleAppsConnectorOperationsTest {

    @Test
    public void createUserCanCreateAliasesPhotoAdminAndAutomaticLicense() {
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
                AttributeBuilder.build(UserHandler.GIVEN_NAME_ATTR, "Ada"),
                AttributeBuilder.build(UserHandler.FAMILY_NAME_ATTR, "Lovelace"),
                AttributeBuilder.build(GoogleAppsConnector.ALIASES_ATTR, List.of("ada.alias@example.com")),
                AttributeBuilder.build(GoogleAppsConnector.PHOTO_ATTR, (Object) new byte[]{1, 2, 3}),
                AttributeBuilder.build( "isAdmin", true));

        Uid uid = connector.create(ObjectClass.ACCOUNT, attributes, new OperationOptionsBuilder().build());

        assertEquals(uid.getUidValue(), "ada@example.com");
        assertEquals(uid.getRevision(), "user-v1");
        assertEquals(transport.requestCount, 5);
        connector.dispose();
    }

    @Test
    public void createsGroupWithMembersAndOtherSupportedObjectClasses() {
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
        groupMember.put(GoogleAppsConnector.EMAIL_ATTR, "ada@example.com");
        groupMember.put(GoogleAppsConnector.ROLE_ATTR, "MANAGER");

        Uid groupUid = connector.create(ObjectClass.GROUP, attributes(
                new Name("engineering@example.com"),
                AttributeBuilder.build(GoogleAppsConnector.MEMBERS_ATTR, List.of(groupMember))),
                new OperationOptionsBuilder().build());
        Uid memberUid = connector.create(GoogleAppsConnector.MEMBER, attributes(
                AttributeBuilder.build(GoogleAppsConnector.GROUP_KEY_ATTR, "engineering@example.com"),
                AttributeBuilder.build(GoogleAppsConnector.EMAIL_ATTR, "grace@example.com")),
                new OperationOptionsBuilder().build());
        Uid orgUnitUid = connector.create(GoogleAppsConnector.ORG_UNIT, attributes(
                new Name("Engineering"),
                AttributeBuilder.build(GoogleAppsConnector.PARENT_ORG_UNIT_PATH_ATTR, "/")),
                new OperationOptionsBuilder().build());
        Uid licenseUid = connector.create(GoogleAppsConnector.LICENSE_ASSIGNMENT, attributes(
                AttributeBuilder.build(GoogleAppsConnector.PRODUCT_ID_ATTR, "product"),
                AttributeBuilder.build(GoogleAppsConnector.SKU_ID_ATTR, "sku"),
                AttributeBuilder.build(GoogleAppsConnector.USER_ID_ATTR, "ada@example.com")),
                new OperationOptionsBuilder().build());

        assertEquals(groupUid.getUidValue(), "engineering@example.com");
        assertEquals(memberUid.getUidValue(), "engineering@example.com/grace@example.com");
        assertEquals(orgUnitUid.getUidValue(), "Engineering");
        assertEquals(orgUnitUid.getRevision(), "ou-v1");
        assertEquals(licenseUid.getUidValue(), "product/sku/sku/user/ada@example.com");
        assertEquals(transport.requestCount, 5);
        connector.dispose();
    }

    @Test
    public void updateOperationBranchesBuildRequestsAndReturnUpdatedUids() {
        ScriptedTransport transport = new ScriptedTransport(
                json("{\"id\":\"ada@example.com\",\"etag\":\"user-v2\"}"),
                json("{\"id\":\"engineering@example.com\",\"etag\":\"group-v2\"}"),
                json("{\"id\":\"member-id\",\"email\":\"ada@example.com\",\"role\":\"OWNER\",\"etag\":\"member-v2\"}"),
                json("{\"name\":\"New Engineering\",\"orgUnitPath\":\"/Engineering\",\"etag\":\"ou-v2\"}"),
                json("{\"productId\":\"Google-Drive-storage\",\"skuId\":\"Google-Drive-storage-2TB\",\"userId\":\"ada@example.com\",\"etags\":\"license-v2\"}"));
        LocalConfiguration configuration = new LocalConfiguration(transport);
        GoogleAppsConnector connector = connector(configuration);
        OperationOptionsBuilder options = new OperationOptionsBuilder();

        Uid user = connector.update(ObjectClass.ACCOUNT, new Uid("ada@example.com"),
                attributes(AttributeBuilder.build(UserHandler.GIVEN_NAME_ATTR, "Ada")), options.build());
        Uid group = connector.update(ObjectClass.GROUP, new Uid("engineering@example.com"),
                attributes(AttributeBuilder.build(GoogleAppsConnector.NAME_ATTR, "Engineering")), options.build());
        Uid member = connector.update(GoogleAppsConnector.MEMBER,
                new Uid("engineering@example.com/ada@example.com"),
                attributes(AttributeBuilder.build(GoogleAppsConnector.ROLE_ATTR, "OWNER")), options.build());
        Uid orgUnit = connector.update(GoogleAppsConnector.ORG_UNIT, new Uid("Engineering"),
                attributes(new Name("New Engineering")), options.build());
        Uid license = connector.update(GoogleAppsConnector.LICENSE_ASSIGNMENT,
                new Uid("Google-Drive-storage/sku/Google-Drive-storage-1TB/user/ada@example.com"),
                attributes(AttributeBuilder.build(GoogleAppsConnector.SKU_ID_ATTR, "Google-Drive-storage-2TB")), options.build());

        assertEquals(user.getRevision(), "user-v2");
        assertEquals(group.getRevision(), "group-v2");
        assertEquals(member.getUidValue(), "engineering@example.com/ada@example.com");
        assertEquals(member.getRevision(), "member-v2");
        assertEquals(orgUnit.getRevision(), "ou-v2");
        assertEquals(license.getUidValue(), "Google-Drive-storage/sku/Google-Drive-storage-2TB/user/ada@example.com");
        assertEquals(transport.requestCount, 5);
        connector.dispose();
    }

    @Test
    public void deleteOperationsTreatGoogleNotFoundAsUnknownUid() {
        ScriptedTransport transport = new ScriptedTransport(notFound(), notFound(), notFound(), notFound(), notFound());
        GoogleAppsConnector connector = connector(new LocalConfiguration(transport));
        OperationOptionsBuilder options = new OperationOptionsBuilder();

        expectThrows(UnknownUidException.class,
                () -> connector.delete(ObjectClass.ACCOUNT, new Uid("missing@example.com"), options.build()));
        expectThrows(UnknownUidException.class,
                () -> connector.delete(ObjectClass.GROUP, new Uid("missing-group"), options.build()));
        expectThrows(UnknownUidException.class,
                () -> connector.delete(GoogleAppsConnector.MEMBER,
                        new Uid("group@example.com/missing@example.com"), options.build()));
        expectThrows(UnknownUidException.class,
                () -> connector.delete(GoogleAppsConnector.ORG_UNIT, new Uid("Missing"), options.build()));
        expectThrows(UnknownUidException.class,
                () -> connector.delete(GoogleAppsConnector.LICENSE_ASSIGNMENT,
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
        ScriptedTransport transport = new ScriptedTransport(notFound());
        GoogleAppsConnector connector = connector(new LocalConfiguration(transport));
        ResultsHandler handler = object -> true;
        OperationOptionsBuilder options = new OperationOptionsBuilder();

        connector.executeQuery(ObjectClass.ACCOUNT,
                FilterBuilder.equalTo(AttributeBuilder.build(Uid.NAME, "missing@example.com")),
                handler, options.build());
        expectThrows(UnsupportedOperationException.class,
                () -> connector.executeQuery(GoogleAppsConnector.MEMBER, null, handler, options.build()));
        assertEquals(transport.requestCount, 1);
        connector.dispose();
    }

    @Test
    public void accountGroupMemberAndOrgUnitSearchesMapGoogleResponses() {
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

        connector.executeQuery(ObjectClass.ACCOUNT,
                FilterBuilder.startsWith(AttributeBuilder.build(Name.NAME, "ada")), users::add,
                new OperationOptionsBuilder().setAttributesToGet(UserHandler.GIVEN_NAME_ATTR).build());
        connector.executeQuery(ObjectClass.GROUP,
                FilterBuilder.contains(AttributeBuilder.build(GoogleAppsConnector.MEMBERS_ATTR, "ada@example.com")),
                groups::add, new OperationOptionsBuilder().setAttributesToGet(GoogleAppsConnector.NAME_ATTR).build());
        connector.executeQuery(GoogleAppsConnector.MEMBER,
                FilterBuilder.equalTo(AttributeBuilder.build(GoogleAppsConnector.GROUP_KEY_ATTR,
                        "engineering@example.com")), members::add, new OperationOptionsBuilder().build());
        connector.executeQuery(GoogleAppsConnector.ORG_UNIT, null, orgUnits::add,
                new OperationOptionsBuilder().setAttributesToGet(GoogleAppsConnector.ORG_UNIT_PATH_ATTR).build());

        assertEquals(users.size(), 1);
        assertEquals(users.get(0).getUid().getUidValue(), "ada@example.com");
        assertEquals(users.get(0).getAttributeByName(UserHandler.GIVEN_NAME_ATTR).getValue().get(0), "Ada");
        assertEquals(groups.size(), 1);
        assertEquals(groups.get(0).getUid().getUidValue(), "engineering@example.com");
        assertEquals(members.size(), 1);
        assertEquals(members.get(0).getObjectClass(), GoogleAppsConnector.MEMBER);
        assertEquals(orgUnits.size(), 1);
        assertEquals(orgUnits.get(0).getUid().getUidValue(), "Engineering");
        assertEquals(transport.requestCount, 4);
        connector.dispose();
    }

    @Test
    public void testOperationAndUidReadsCoverEachDirectoryResource() {
        ScriptedTransport transport = new ScriptedTransport(
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

        connector.test();
        connector.executeQuery(ObjectClass.ACCOUNT,
                FilterBuilder.equalTo(AttributeBuilder.build(Uid.NAME, "ada@example.com")),
                users::add, options.setAttributesToGet(UserHandler.GIVEN_NAME_ATTR).build());
        connector.executeQuery(ObjectClass.GROUP,
                FilterBuilder.equalTo(AttributeBuilder.build(Uid.NAME, "engineering@example.com")),
                groups::add, options.setAttributesToGet(GoogleAppsConnector.NAME_ATTR).build());
        connector.executeQuery(GoogleAppsConnector.MEMBER,
                FilterBuilder.equalTo(AttributeBuilder.build(Uid.NAME,
                        "engineering@example.com/ada@example.com")), members::add, options.build());
        connector.executeQuery(GoogleAppsConnector.ORG_UNIT,
                FilterBuilder.equalTo(AttributeBuilder.build(Uid.NAME, "Engineering")),
                orgUnits::add, options.setAttributesToGet(GoogleAppsConnector.ORG_UNIT_PATH_ATTR).build());

        assertEquals(users.size(), 1);
        assertEquals(users.get(0).getUid().getUidValue(), "ada@example.com");
        assertEquals(groups.size(), 1);
        assertEquals(members.size(), 1);
        assertEquals(orgUnits.size(), 1);
        assertEquals(transport.requestCount, 4);
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

    private static final class ScriptedTransport extends MockHttpTransport {
        private final Queue<MockLowLevelHttpResponse> responses = new ArrayDeque<>();
        private int requestCount;

        private ScriptedTransport(MockLowLevelHttpResponse... responses) {
            this.responses.addAll(Arrays.asList(responses));
        }

        @Override
        public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
            requestCount++;
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
