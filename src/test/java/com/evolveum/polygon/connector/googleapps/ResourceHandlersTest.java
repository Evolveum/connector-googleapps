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
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.directory.Directory;
import com.google.api.services.directory.model.Member;
import com.google.api.services.directory.model.OrgUnit;
import com.google.api.services.directory.model.User;
import com.google.api.services.licensing.Licensing;
import com.google.api.services.licensing.model.LicenseAssignment;
import com.google.api.services.licensing.model.LicenseAssignmentInsert;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.AttributesAccessor;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.PredefinedAttributes;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class ResourceHandlersTest {

    private static final String PRODUCT = "Google-Drive-storage";
    private static final String SKU = "Google-Drive-storage-1TB";
    private static final String USER = "ada@example.com";

    @Test
    public void createMemberAppliesDefaultRoleAndRequiredKeys() {
        // Given: Membership attributes without an explicit role
        AttributesAccessor attributes = attributes(AttributeBuilder.build(GROUP_KEY_ATTR, "engineering@example.com"), AttributeBuilder.build(EMAIL_ATTR, USER));

        // When: Build the membership creation request
        Directory.Members.Insert request = MemberHandler.createMember(directory().members(), attributes);

        // Then: Preserve required keys and omit defaults
        Member content = (Member) request.getJsonContent();
        assertEquals(request.getGroupKey(), "engineering@example.com");
        assertEquals(content.getEmail(), USER);
        assertNull(content.getRole());
        assertNull(request.getFields());
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void createMemberRequiresGroupKey() {
        // Given: Membership attributes without a group key
        AttributesAccessor attributes = attributes(AttributeBuilder.build(EMAIL_ATTR, USER));

        // When: Build the membership creation request
        // Then: Reject the missing group key
        MemberHandler.createMember(directory().members(), attributes);
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void createMemberRequiresMemberKey() {
        // Given: Membership attributes without a member email
        AttributesAccessor attributes = attributes(AttributeBuilder.build(GROUP_KEY_ATTR, "engineering@example.com"));

        // When: Build the membership creation request
        // Then: Reject the missing member email
        MemberHandler.createMember(directory().members(), attributes);
    }

    @Test
    public void createAndUpdateOrgUnitNormalizeParentPath() {
        Directory.Orgunits.Insert insert = OrgunitsHandler.createOrgunit(directory().orgunits(),
                attributes(new Name("Engineering"),
                        AttributeBuilder.build(GoogleAppsConnector.PARENT_ORG_UNIT_PATH_ATTR, "Company"),
                        AttributeBuilder.build(GoogleAppsConnector.BLOCK_INHERITANCE_ATTR, true),
                        AttributeBuilder.build(PredefinedAttributes.DESCRIPTION, "Product engineering")));

        OrgUnit insertContent = (OrgUnit) insert.getJsonContent();
        assertEquals(insert.getCustomerId(), GoogleAppsConnector.MY_CUSTOMER_ID);
        assertEquals(insertContent.getParentOrgUnitPath(), "/Company");
        assertEquals(insertContent.getName(), "Engineering");

        Directory.Orgunits.Patch patch = OrgunitsHandler.updateOrgunit(directory().orgunits(), "Engineering",
                attributes(AttributeBuilder.build(GoogleAppsConnector.PARENT_ORG_UNIT_PATH_ATTR, "Company/Platform")));
        assertNotNull(patch);
        OrgUnit patchContent = (OrgUnit) patch.getJsonContent();
        assertEquals(patch.getOrgUnitPath(), "Engineering");
        assertEquals(patchContent.getParentOrgUnitPath(), "/Company/Platform");
    }

    @Test
    public void orgUnitIdStripsOnlyLeadingSlashAndRetainsVersion() {
        OrgUnit orgUnit = new OrgUnit().setOrgUnitPath("/Company/Engineering").setEtag("version-1");

        Uid uid = OrgunitsHandler.generateOrgUnitId(orgUnit);

        assertEquals(uid.getUidValue(), "Company/Engineering");
        assertEquals(uid.getRevision(), "version-1");
    }

    @Test
    public void orgUnitProjectionReturnsOnlyRequestedAttributes() {
        OrgUnit orgUnit = new OrgUnit()
                .setName("Engineering")
                .setOrgUnitPath("/Engineering")
                .setParentOrgUnitPath("/")
                .setDescription("Product engineering")
                .setBlockInheritance(true);

        ConnectorObject object = OrgunitsHandler.fromOrgunit(orgUnit,
                new HashSet<>(List.of(GoogleAppsConnector.ORG_UNIT_PATH_ATTR)));

        assertEquals(object.getUid().getUidValue(), "Engineering");
        assertNotNull(object.getAttributeByName(GoogleAppsConnector.ORG_UNIT_PATH_ATTR));
        assertNull(object.getAttributeByName(PredefinedAttributes.DESCRIPTION));
        assertNull(object.getAttributeByName(GoogleAppsConnector.BLOCK_INHERITANCE_ATTR));
    }

    @Test
    public void orgUnitProjectionMapsAllOptionalFieldsWhenNoProjectionIsProvided() {
        OrgUnit orgUnit = new OrgUnit().setName("Engineering").setOrgUnitPath("/Engineering")
                .setParentOrgUnitPath("/").setDescription("Team").setBlockInheritance(true);

        ConnectorObject object = OrgunitsHandler.fromOrgunit(orgUnit, null);

        assertEquals(object.getAttributeByName(PredefinedAttributes.DESCRIPTION).getValue(), List.of("Team"));
        assertEquals(object.getAttributeByName(GoogleAppsConnector.ORG_UNIT_PATH_ATTR).getValue(), List.of("/Engineering"));
        assertEquals(object.getAttributeByName(GoogleAppsConnector.PARENT_ORG_UNIT_PATH_ATTR).getValue(), List.of("/"));
        assertEquals(object.getAttributeByName(GoogleAppsConnector.BLOCK_INHERITANCE_ATTR).getValue(), List.of(true));
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void createOrgUnitRequiresName() {
        OrgunitsHandler.createOrgunit(directory().orgunits(),
                attributes(AttributeBuilder.build(GoogleAppsConnector.PARENT_ORG_UNIT_PATH_ATTR, "/")));
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void createOrgUnitRequiresParentPath() {
        OrgunitsHandler.createOrgunit(directory().orgunits(), attributes(new Name("Engineering")));
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void createOrgUnitRejectsBlankParentPath() {
        OrgunitsHandler.createOrgunit(directory().orgunits(), attributes(new Name("Engineering"),
                AttributeBuilder.build(GoogleAppsConnector.PARENT_ORG_UNIT_PATH_ATTR, " ")));
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void updateOrgUnitRejectsBlankParentPath() {
        OrgunitsHandler.updateOrgunit(directory().orgunits(), "Engineering",
                attributes(AttributeBuilder.build(GoogleAppsConnector.PARENT_ORG_UNIT_PATH_ATTR, " ")));
    }

    @Test(expectedExceptions = ClassCastException.class)
    public void updateOrgUnitRejectsNonBooleanInheritanceValue() {
        OrgunitsHandler.updateOrgunit(directory().orgunits(), "Engineering",
                attributes(AttributeBuilder.build(GoogleAppsConnector.BLOCK_INHERITANCE_ATTR, "sometimes")));
    }

    @Test
    public void updateOrgUnitCanReturnNullOrClearDescription() {
        assertNull(OrgunitsHandler.updateOrgunit(directory().orgunits(), "Engineering", attributes()));

        Directory.Orgunits.Patch patch = OrgunitsHandler.updateOrgunit(directory().orgunits(), "Engineering",
                attributes(AttributeBuilder.build(PredefinedAttributes.DESCRIPTION)));

        assertNotNull(patch);
        assertEquals(((OrgUnit) patch.getJsonContent()).getDescription(), "");
    }

    @Test
    public void licenseDeleteParsesAllPartsOfCompoundUid() {
        Licensing.LicenseAssignments.Delete request = LicenseAssignmentsHandler.deleteLicenseAssignment(
                licensing().licenseAssignments(), licenseUid());

        assertEquals(request.getProductId(), PRODUCT);
        assertEquals(request.getSkuId(), SKU);
        assertEquals(request.getUserId(), USER);
    }

    @Test(expectedExceptions = UnknownUidException.class)
    public void licenseDeleteRejectsMalformedUid() {
        LicenseAssignmentsHandler.deleteLicenseAssignment(licensing().licenseAssignments(), "not-a-license-id");
    }

    @Test(expectedExceptions = UnknownUidException.class)
    public void licenseUpdateRejectsMalformedUid() {
        LicenseAssignmentsHandler.updateLicenseAssignment(licensing().licenseAssignments(), "not-a-license-id",
                attributes(AttributeBuilder.build(GoogleAppsConnector.SKU_ID_ATTR, "replacement")));
    }

    @Test
    public void licenseUpdateUsesExistingLicenseCoordinatesAndNewSku() {
        String replacementSku = "Google-Drive-storage-2TB";
        Licensing.LicenseAssignments.Patch request = LicenseAssignmentsHandler.updateLicenseAssignment(
                licensing().licenseAssignments(), licenseUid(),
                attributes(AttributeBuilder.build(GoogleAppsConnector.SKU_ID_ATTR, replacementSku)));

        assertNotNull(request);
        LicenseAssignment content = (LicenseAssignment) request.getJsonContent();
        assertEquals(request.getProductId(), PRODUCT);
        assertEquals(request.getSkuId(), SKU);
        assertEquals(request.getUserId(), USER);
        assertEquals(content.getSkuId(), replacementSku);
    }

    @Test
    public void createLicenseAssignmentMapsRequiredFieldsToRequest() {
        Licensing.LicenseAssignments.Insert request = LicenseAssignmentsHandler.createLicenseAssignment(
                licensing().licenseAssignments(), attributes(
                        AttributeBuilder.build(GoogleAppsConnector.PRODUCT_ID_ATTR, PRODUCT),
                        AttributeBuilder.build(GoogleAppsConnector.SKU_ID_ATTR, SKU),
                        AttributeBuilder.build(GoogleAppsConnector.USER_ID_ATTR, USER)));

        LicenseAssignmentInsert content = (LicenseAssignmentInsert) request.getJsonContent();
        assertEquals(request.getProductId(), PRODUCT);
        assertEquals(request.getSkuId(), SKU);
        assertEquals(request.getFields(), GoogleAppsConnector.PRODUCT_ID_SKU_ID_USER_ID);
        assertEquals(content.getUserId(), USER);
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void createLicenseAssignmentRequiresProduct() {
        LicenseAssignmentsHandler.createLicenseAssignment(licensing().licenseAssignments(), attributes(
                AttributeBuilder.build(GoogleAppsConnector.SKU_ID_ATTR, SKU),
                AttributeBuilder.build(GoogleAppsConnector.USER_ID_ATTR, USER)));
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void createLicenseAssignmentRequiresSku() {
        LicenseAssignmentsHandler.createLicenseAssignment(licensing().licenseAssignments(), attributes(
                AttributeBuilder.build(GoogleAppsConnector.PRODUCT_ID_ATTR, PRODUCT),
                AttributeBuilder.build(GoogleAppsConnector.USER_ID_ATTR, USER)));
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void createLicenseAssignmentRequiresUser() {
        LicenseAssignmentsHandler.createLicenseAssignment(licensing().licenseAssignments(), attributes(
                AttributeBuilder.build(GoogleAppsConnector.PRODUCT_ID_ATTR, PRODUCT),
                AttributeBuilder.build(GoogleAppsConnector.SKU_ID_ATTR, SKU)));
    }

    @Test
    public void updateLicenseAssignmentPassesRequestedSkuToGoogle() {
        Licensing.LicenseAssignments.Patch patch = LicenseAssignmentsHandler.updateLicenseAssignment(
                licensing().licenseAssignments(), licenseUid(),
                attributes(AttributeBuilder.build(GoogleAppsConnector.SKU_ID_ATTR, "new-sku")));

        assertNotNull(patch);
        assertEquals(((LicenseAssignment) patch.getJsonContent()).getSkuId(), "new-sku");
    }

    @Test
    public void unchangedLicenseSkuDoesNotCreatePatch() {
        assertNull(LicenseAssignmentsHandler.updateLicenseAssignment(licensing().licenseAssignments(), licenseUid(),
                attributes(AttributeBuilder.build(GoogleAppsConnector.SKU_ID_ATTR, SKU))));
    }

    @Test
    public void licenseUpdateWithoutSkuDoesNotCreatePatch() {
        assertNull(LicenseAssignmentsHandler.updateLicenseAssignment(licensing().licenseAssignments(), licenseUid(),
                attributes()));
    }

    @Test
    public void licenseIdIncludesAllBusinessKeysAndEtag() {
        LicenseAssignment assignment = new LicenseAssignment()
                .setProductId(PRODUCT).setSkuId(SKU).setUserId(USER).setEtags("version-1");

        Uid uid = LicenseAssignmentsHandler.generateLicenseAssignmentId(assignment);

        assertEquals(uid.getUidValue(), licenseUid());
        assertEquals(uid.getRevision(), "version-1");
        assertTrue(LicenseAssignmentsHandler.LICENSE_NAME_PATTERN.matcher(uid.getUidValue()).matches());
        assertFalse(LicenseAssignmentsHandler.LICENSE_NAME_PATTERN.matcher("invalid-license-id").matches());
    }

    @Test
    public void licenseWithoutEtagUsesUidWithoutRevision() {
        LicenseAssignment assignment = new LicenseAssignment()
                .setProductId(PRODUCT).setSkuId(SKU).setUserId(USER);

        Uid uid = LicenseAssignmentsHandler.generateLicenseAssignmentId(assignment);

        assertEquals(uid.getUidValue(), licenseUid());
        assertNull(uid.getRevision());
    }

    @Test
    public void licenseAssignmentMapsToConnectorObject() {
        LicenseAssignment assignment = new LicenseAssignment()
                .setProductId(PRODUCT)
                .setSkuId(SKU)
                .setUserId(USER)
                .setSelfLink("https://example.test/license")
                .setEtags("version-2");

        ConnectorObject object = LicenseAssignmentsHandler.fromLicenseAssignment(assignment);

        assertEquals(object.getObjectClass(), GoogleAppsConnector.LICENSE_ASSIGNMENT);
        assertEquals(object.getUid().getUidValue(), licenseUid());
        assertEquals(object.getUid().getRevision(), "version-2");
        assertEquals(object.getName().getNameValue(), licenseUid());
        assertEquals(object.getAttributeByName(GoogleAppsConnector.PRODUCT_ID_ATTR).getValue(), List.of(PRODUCT));
        assertEquals(object.getAttributeByName(GoogleAppsConnector.SKU_ID_ATTR).getValue(), List.of(SKU));
        assertEquals(object.getAttributeByName(GoogleAppsConnector.USER_ID_ATTR).getValue(), List.of(USER));
        assertEquals(object.getAttributeByName(GoogleAppsConnector.SELF_LINK_ATTR).getValue(),
                List.of("https://example.test/license"));
    }

    @Test
    public void groupRequestMappingsBuildAndSkipEmptyPatch() throws Exception {
        // Given: Group attributes and a description replacement
        AttributesAccessor attributes = attributes(new Name("engineering@example.com"), AttributeBuilder.build(NAME_ATTR, "Engineering"),
                AttributeBuilder.build(PredefinedAttributes.DESCRIPTION, "Team"));
        RecordingTransport transport = new RecordingTransport();
        GoogleApiExecutor executor = executor(transport);
        Set<AttributeDelta> modifications = Set.of(AttributeDeltaBuilder.build(PredefinedAttributes.DESCRIPTION, "New description"));

        // When: Create and update the group
        Directory.Groups.Insert insert = GroupHandler.createGroup(directory().groups(), attributes);
        Set<AttributeDelta> unchanged = GroupHandler.executeGroupUpdateDelta(executor, new Uid("engineering@example.com"), Set.of());
        Set<AttributeDelta> updated = GroupHandler.executeGroupUpdateDelta(executor, new Uid("engineering@example.com"), modifications);

        // Then: Verify contents and skip empty updates
        com.google.api.services.directory.model.Group content = (com.google.api.services.directory.model.Group) insert.getJsonContent();
        assertEquals(content.getEmail(), "engineering@example.com");
        assertEquals(content.getName(), "Engineering");
        assertEquals(content.getDescription(), "Team");
        assertEquals(insert.getFields(), ID_ETAG);
        assertEquals(unchanged, Set.of());
        assertEquals(updated, Set.of());
        assertEquals(transport.methods, List.of("PATCH"));
        assertEquals(transport.requests.size(), 1);
        MockLowLevelHttpRequest request = transport.requests.get(0);
        assertEquals(request.getUrl(), "https://admin.googleapis.com/admin/directory/v1/groups/engineering@example.com");
        assertEquals(new GsonFactory().fromString(request.getContentAsString(), Map.class), Map.of("description", "New description"));
    }

    @Test
    public void groupUpdateRejectsBlankPrimaryEmail() throws Exception {
        // Given: Blank email rejected by Directory API
        RecordingTransport transport = new RecordingTransport(400,
                "{\"error\":{\"code\":400,\"message\":\"Invalid email\",\"errors\":[{\"reason\":\"invalid\",\"message\":\"Invalid email\"}]}}");
        GoogleApiExecutor executor = executor(transport);
        Set<AttributeDelta> modifications = Set.of(AttributeDeltaBuilder.build(Name.NAME, " "));

        // When: Update the group's primary email
        ConnectorException failure = expectThrows(ConnectorException.class,
                () -> GroupHandler.executeGroupUpdateDelta(executor, new Uid("engineering@example.com"), modifications));

        // Then: Propagate rejection without retrying the update
        assertEquals(failure.getCause().getClass(), GoogleJsonResponseException.class);
        assertEquals(((GoogleJsonResponseException) failure.getCause()).getStatusCode(), 400);
        assertEquals(transport.methods, List.of("PATCH"));
        assertEquals(transport.requests.size(), 1);
        MockLowLevelHttpRequest request = transport.requests.get(0);
        assertEquals(request.getUrl(), "https://admin.googleapis.com/admin/directory/v1/groups/engineering@example.com");
        assertEquals(new GsonFactory().fromString(request.getContentAsString(), Map.class), Map.of("email", " "));
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void createGroupRequiresPrimaryEmail() {
        GroupHandler.createGroup(directory().groups(), attributes(
                AttributeBuilder.build(GoogleAppsConnector.NAME_ATTR, "Engineering")));
    }

    @Test
    public void groupUpdateRejectsNonStringDescription() {
        // Given: A non-string group description replacement
        RecordingTransport transport = new RecordingTransport();
        GoogleApiExecutor executor = executor(transport);
        Set<AttributeDelta> modifications = Set.of(AttributeDeltaBuilder.build(PredefinedAttributes.DESCRIPTION, 42));

        // When: Update the group description
        // Then: Reject the type before any request
        expectThrows(ClassCastException.class,
                () -> GroupHandler.executeGroupUpdateDelta(executor, new Uid("engineering@example.com"), modifications));
        assertTrue(transport.requests.isEmpty());
    }

    @Test
    public void memberMappingsPreserveEtagAndMapAttributes() {
        // Given: A membership with identity and revision
        Member member = new Member().setId("123").setEmail(USER).setRole("OWNER").setType("USER").setEtag("member-version");

        // When: Convert the membership and its identifier
        Uid uid = MemberHandler.generateMemberId("engineering@example.com", member);
        ConnectorObject object = MemberHandler.fromMember("engineering@example.com", member);

        // Then: Preserve identity and membership attributes
        assertEquals(uid.getUidValue(), "engineering@example.com/123");
        assertEquals(uid.getRevision(), "member-version");
        assertEquals(object.getUid(), uid);
        assertEquals(object.getAttributeByName(GROUP_KEY_ATTR).getValue(), List.of("ada@example.com"));
        assertEquals(object.getAttributeByName(ROLE_ATTR).getValue(), List.of("OWNER"));
        assertEquals(object.getAttributeByName(TYPE_ATTR).getValue(), List.of("USER"));
        assertNull(MemberHandler.generateMemberId("engineering@example.com", new Member().setEmail(USER)).getRevision());
    }

    @Test
    public void memberUpdateDefaultsRoleAndDeleteBuildRequests() throws Exception {
        // Given: Explicit default role and member identity
        RecordingTransport transport = new RecordingTransport();
        GoogleApiExecutor executor = executor(transport);
        Set<AttributeDelta> modifications = Set.of(AttributeDeltaBuilder.build(ROLE_ATTR, "MEMBER"));

        // When: Update membership and build deletion
        Set<AttributeDelta> result = MemberHandler.executeMemberUpdateDelta(executor, new Uid("engineering@example.com/" + USER), modifications);
        Directory.Members.Delete deletion = MemberHandler.deleteMembers(directory().members(), "engineering@example.com", USER);

        // Then: Verify role and target membership
        assertEquals(result, Set.of());
        assertEquals(transport.methods, List.of("PATCH"));
        assertEquals(transport.requests.size(), 1);
        MockLowLevelHttpRequest request = transport.requests.get(0);
        assertEquals(request.getUrl(), "https://admin.googleapis.com/admin/directory/v1/groups/engineering@example.com/members/ada@example.com");
        assertEquals(new GsonFactory().fromString(request.getContentAsString(), Map.class), Map.of("email", USER, "role", "MEMBER"));
        assertEquals(deletion.getGroupKey(), "engineering@example.com");
        assertEquals(deletion.getMemberKey(), USER);
        assertEquals(deletion.getRequestMethod(), "DELETE");
    }

    @Test
    public void userCreateUpdatePhotoAndAliasesBuildRequests() {
        // Given: User attributes and replacement values
        Directory.Users users = directory().users();
        AttributesAccessor attributes = attributes(new Name(USER), AttributeBuilder.buildPassword(new GuardedString("P@ssw0rd".toCharArray())),
                AttributeBuilder.build(GIVEN_NAME_ATTR, "Ada"), AttributeBuilder.build(FAMILY_NAME_ATTR, "Lovelace"),
                AttributeBuilder.build(SUSPENDED_ATTR, true), AttributeBuilder.build(ORG_UNIT_PATH_ATTR, "/Engineering"));
        Set<AttributeDelta> deltas = Set.of(AttributeDeltaBuilder.build(FAMILY_NAME_ATTR, "Byron"),
                AttributeDeltaBuilder.build(INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR, false));

        // When: Build user and related resource requests
        Directory.Users.Insert insert = UserHandler.createUser(users, attributes);
        Directory.Users.Update update = UserHandler.buildUserUpdateRequest(users, new Uid(USER), deltas, Optional.empty());
        Directory.Users.Photos.Update photo = UserHandler.createUpdateUserPhoto(users.photos(), USER, new byte[]{1, 2, 3});
        Directory.Users.Aliases.Insert alias = UserHandler.createUserAlias(users.aliases(), USER, "ada.alias@example.com");
        Directory.Users.Aliases.Delete deletion = UserHandler.deleteUserAlias(users.aliases(), USER, "ada.alias@example.com");

        // Then: Verify identities and request contents
        User user = (User) insert.getJsonContent();
        assertEquals(user.getPrimaryEmail(), USER);
        assertEquals(user.getPassword(), "P@ssw0rd");
        assertEquals(user.getName().getGivenName(), "Ada");
        assertEquals(user.getName().getFamilyName(), "Lovelace");
        assertEquals(user.getSuspended(), Boolean.TRUE);
        assertEquals(user.getOrgUnitPath(), "/Engineering");
        assertEquals(insert.getFields(), ID_ETAG);
        assertEquals(update.getRequestMethod(), "PUT");
        assertEquals(update.getUserKey(), USER);
        assertEquals(update.getFields(), ID_ETAG);
        User content = (User) update.getJsonContent();
        assertEquals(content.getName().getFamilyName(), "Byron");
        assertEquals(content.getIncludeInGlobalAddressList(), Boolean.FALSE);
        assertEquals(photo.getUserKey(), USER);
        assertEquals(photo.getFields(), ID_ATTR);
        assertEquals(alias.getUserKey(), USER);
        assertEquals(((com.google.api.services.directory.model.Alias) alias.getJsonContent()).getAlias(), "ada.alias@example.com");
        assertEquals(alias.getFields(), ID_ETAG);
        assertEquals(deletion.getUserKey(), USER);
        assertEquals(deletion.getAlias(), "ada.alias@example.com");
    }

    @Test
    public void userCreateMapsStructuredAndOptionalScalarAttributes() {
        // Given: Structured and scalar user attributes
        AttributesAccessor attributes = attributes(
                new Name(USER), AttributeBuilder.buildPassword(new GuardedString("secret".toCharArray())),
                AttributeBuilder.build(GIVEN_NAME_ATTR, "Ada"), AttributeBuilder.build(FAMILY_NAME_ATTR, "Lovelace"),
                AttributeBuilder.build(IMS_ATTR, "{\"type\":\"work\",\"value\":\"ada-chat\"}"),
                AttributeBuilder.build(EMAILS_ATTR, "{\"type\":\"work\",\"value\":\"ada@example.com\"}"),
                AttributeBuilder.build(EXTERNAL_IDS_ATTR, "{\"type\":\"account\",\"value\":\"42\"}"),
                AttributeBuilder.build(RELATIONS_ATTR, "{\"type\":\"manager\",\"value\":\"grace@example.com\"}"),
                AttributeBuilder.build(ADDRESSES_ATTR, "{\"type\":\"work\",\"streetAddress\":\"London\"}"),
                AttributeBuilder.build(ORGANIZATIONS_ATTR, "{\"name\":\"Analytical Engine\"}"),
                AttributeBuilder.build(PHONES_ATTR, "{\"type\":\"work\",\"value\":\"+44123\"}"),
                AttributeBuilder.build(LOCATIONS_ATTR, "{\"type\":\"desk\",\"area\":\"A1\"}"),
                AttributeBuilder.build(SUSPENDED_ATTR, true), AttributeBuilder.build(CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR, true), AttributeBuilder.build(IP_WHITELISTED_ATTR, false),
                AttributeBuilder.build(ORG_UNIT_PATH_ATTR, "/Engineering"), AttributeBuilder.build(INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR, true));

        // When: Build the user creation request
        Directory.Users.Insert insert = UserHandler.createUser(directory().users(), attributes);

        // Then: Preserve every supplied attribute value
        User content = (User) insert.getJsonContent();

        assertEquals(content.getIms(), List.of(Map.of("type", "work", "value", "ada-chat")));
        assertEquals(content.getEmails(), List.of(Map.of("type", "work", "value", "ada@example.com")));
        assertEquals(content.getExternalIds(), List.of(Map.of("type", "account", "value", "42")));
        assertEquals(content.getRelations(), List.of(Map.of("type", "manager", "value", "grace@example.com")));
        assertEquals(content.getAddresses(), List.of(Map.of("type", "work", "streetAddress", "London")));
        assertEquals(content.getOrganizations(), List.of(Map.of("name", "Analytical Engine")));
        assertEquals(content.getPhones(), List.of(Map.of("type", "work", "value", "+44123")));
        assertEquals(content.getLocations(), List.of(Map.of("type", "desk", "area", "A1")));
        assertEquals(content.getSuspended(), Boolean.TRUE);
        assertEquals(content.getChangePasswordAtNextLogin(), Boolean.TRUE);
        assertEquals(content.getIpWhitelisted(), Boolean.FALSE);
        assertEquals(content.getOrgUnitPath(), "/Engineering");
        assertEquals(content.getIncludeInGlobalAddressList(), Boolean.TRUE);
    }

    @Test
    public void userUpdateMapsEveryOptionalFieldAndAcceptsNullValues() {
        // Given: Replacements for every optional user field
        Set<AttributeDelta> deltas = Set.of(
                AttributeDeltaBuilder.build(Name.NAME, "new@example.com"),
                AttributeDeltaBuilder.build(GIVEN_NAME_ATTR, "Augusta"), AttributeDeltaBuilder.build(FAMILY_NAME_ATTR, "King"),
                AttributeDeltaBuilder.build(OperationalAttributes.PASSWORD_NAME, new GuardedString("new-secret".toCharArray())),
                AttributeDeltaBuilder.build(SUSPENDED_ATTR, true), AttributeDeltaBuilder.build(CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR, false),
                AttributeDeltaBuilder.build(IP_WHITELISTED_ATTR, true),
                AttributeDeltaBuilder.build(IMS_ATTR, "{\"type\":\"work\",\"value\":\"ada-chat\"}"),
                AttributeDeltaBuilder.build(EMAILS_ATTR, "{\"type\":\"work\",\"value\":\"new@example.com\"}"),
                AttributeDeltaBuilder.build(EXTERNAL_IDS_ATTR, "{\"type\":\"account\",\"value\":\"42\"}"),
                AttributeDeltaBuilder.build(RELATIONS_ATTR, "{\"type\":\"manager\",\"value\":\"grace@example.com\"}"),
                AttributeDeltaBuilder.build(ADDRESSES_ATTR, "{\"type\":\"work\",\"streetAddress\":\"London\"}"),
                AttributeDeltaBuilder.build(ORGANIZATIONS_ATTR, "{\"name\":\"Analytical Engine\"}"),
                AttributeDeltaBuilder.build(PHONES_ATTR, "{\"type\":\"work\",\"value\":\"+44123\"}"),
                AttributeDeltaBuilder.build(LOCATIONS_ATTR, "{\"type\":\"desk\",\"area\":\"A1\"}"),
                AttributeDeltaBuilder.build(ORG_UNIT_PATH_ATTR, "/Engineering"), AttributeDeltaBuilder.build(INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR, true));

        // When: Build the user update request
        Directory.Users.Update update = UserHandler.buildUserUpdateRequest(directory().users(), new Uid(USER), deltas, Optional.empty());

        // Then: Preserve every replacement value
        assertEquals(update.getUserKey(), USER);
        assertEquals(update.getRequestMethod(), "PUT");
        User content = (User) update.getJsonContent();
        assertEquals(content.getPrimaryEmail(), "new@example.com");
        assertEquals(content.getName().getGivenName(), "Augusta");
        assertEquals(content.getName().getFamilyName(), "King");
        assertEquals(content.getPassword(), "new-secret");
        assertEquals(content.getSuspended(), Boolean.TRUE);
        assertEquals(content.getChangePasswordAtNextLogin(), Boolean.FALSE);
        assertEquals(content.getIpWhitelisted(), Boolean.TRUE);
        assertEquals(content.getIms(), List.of(Map.of("type", "work", "value", "ada-chat")));
        assertEquals(content.getEmails(), List.of(Map.of("type", "work", "value", "new@example.com")));
        assertEquals(content.getExternalIds(), List.of(Map.of("type", "account", "value", "42")));
        assertEquals(content.getRelations(), List.of(Map.of("type", "manager", "value", "grace@example.com")));
        assertEquals(content.getAddresses(), List.of(Map.of("type", "work", "streetAddress", "London")));
        assertEquals(content.getOrganizations(), List.of(Map.of("name", "Analytical Engine")));
        assertEquals(content.getPhones(), List.of(Map.of("type", "work", "value", "+44123")));
        assertEquals(content.getLocations(), List.of(Map.of("type", "desk", "area", "A1")));
        assertEquals(content.getOrgUnitPath(), "/Engineering");
        assertEquals(content.getIncludeInGlobalAddressList(), Boolean.TRUE);
    }

    @Test(dataProvider = "userFields")
    public void userUpdateCanInitializeThePatchFromEachOptionalFieldAlone(String attribute, Object value, Map<String, Object> expected) throws Exception {
        // Given: One replacement attribute and expected payload
        AttributeDelta delta = AttributeDeltaBuilder.build(attribute, value);
        GsonFactory factory = new GsonFactory();

        // When: Build a single-field update
        Directory.Users.Update update = UserHandler.buildUserUpdateRequest(directory().users(), new Uid(USER), Set.of(delta), Optional.empty());

        // Then: Verify exact payload and target user
        assertEquals(update.getUserKey(), USER);
        assertEquals(update.getFields(), ID_ETAG);
        assertEquals(factory.fromString(factory.toString(update.getJsonContent()), Map.class), expected);
    }

    @DataProvider
    public Object[][] userFields() {
        return new Object[][] {
            {GIVEN_NAME_ATTR, "Ada", Map.of("name", Map.of("givenName", "Ada"))},
            {FAMILY_NAME_ATTR, "Lovelace", Map.of("name", Map.of("familyName", "Lovelace"))},
            {OperationalAttributes.PASSWORD_NAME, new GuardedString("secret".toCharArray()), Map.of("password", "secret")},
            {SUSPENDED_ATTR, true, Map.of("suspended", true)},
            {CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR, true, Map.of("changePasswordAtNextLogin", true)},
            {IP_WHITELISTED_ATTR, true, Map.of("ipWhitelisted", true)},
            {IMS_ATTR, "{\"type\":\"work\"}", Map.of("ims", List.of(Map.of("type", "work")))},
            {EMAILS_ATTR, "{\"type\":\"work\"}", Map.of("emails", List.of(Map.of("type", "work")))},
            {EXTERNAL_IDS_ATTR, "{\"type\":\"account\"}", Map.of("externalIds", List.of(Map.of("type", "account")))},
            {RELATIONS_ATTR, "{\"type\":\"manager\"}", Map.of("relations", List.of(Map.of("type", "manager")))},
            {ADDRESSES_ATTR, "{\"type\":\"work\"}", Map.of("addresses", List.of(Map.of("type", "work")))},
            {ORGANIZATIONS_ATTR, "{\"name\":\"Org\"}", Map.of("organizations", List.of(Map.of("name", "Org")))},
            {PHONES_ATTR, "{\"type\":\"work\"}", Map.of("phones", List.of(Map.of("type", "work")))},
            {LOCATIONS_ATTR, "{\"type\":\"desk\"}", Map.of("locations", List.of(Map.of("type", "desk")))},
            {ORG_UNIT_PATH_ATTR, "/Engineering", Map.of("orgUnitPath", "/Engineering")},
            {INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR, true, Map.of("includeInGlobalAddressList", true)}
        };
    }

    @Test
    public void userUpdateWithoutModificationsDoesNotBuildRequest() {
        // Given: No user modifications
        Set<AttributeDelta> deltas = Set.of();

        // When: Build the user update request
        Directory.Users.Update update = UserHandler.buildUserUpdateRequest(directory().users(), new Uid(USER), deltas, Optional.empty());

        // Then: Avoid an unnecessary update request
        assertNull(update);
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void userCreateRequiresPassword() {
        // Given: User missing a required creation attribute
        AttributesAccessor attributes = attributes(new Name(USER),
                AttributeBuilder.build(GIVEN_NAME_ATTR, "Ada"),
                AttributeBuilder.build(FAMILY_NAME_ATTR, "Lovelace"));

        // When: Build the user creation request
        // Then: Reject the missing required attribute
        UserHandler.createUser(directory().users(), attributes);
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void userCreateRequiresGivenName() {
        // Given: User missing a required creation attribute
        AttributesAccessor attributes = attributes(new Name(USER),
                AttributeBuilder.buildPassword(new GuardedString("secret".toCharArray())),
                AttributeBuilder.build(FAMILY_NAME_ATTR, "Lovelace"));

        // When: Build the user creation request
        // Then: Reject the missing required attribute
        UserHandler.createUser(directory().users(), attributes);
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void userCreateRequiresFamilyName() {
        // Given: User missing a required creation attribute
        AttributesAccessor attributes = attributes(new Name(USER),
                AttributeBuilder.buildPassword(new GuardedString("secret".toCharArray())),
                AttributeBuilder.build(GIVEN_NAME_ATTR, "Ada"));

        // When: Build the user creation request
        // Then: Reject the missing required attribute
        UserHandler.createUser(directory().users(), attributes);
    }

    @Test(expectedExceptions = ClassCastException.class)
    public void userUpdateRejectsNonStringGivenName() {
        // Given: A numeric value for givenName
        Set<AttributeDelta> deltas = Set.of(AttributeDeltaBuilder.build(GIVEN_NAME_ATTR, 42));

        // When: Build the user update request
        // Then: Reject the invalid attribute type
        UserHandler.buildUserUpdateRequest(directory().users(), new Uid(USER), deltas, Optional.empty());
    }

    @Test(expectedExceptions = ClassCastException.class)
    public void userUpdateRejectsUnrecognizedBooleanValue() {
        // Given: A string value for suspended
        Set<AttributeDelta> deltas = Set.of(AttributeDeltaBuilder.build(SUSPENDED_ATTR, "sometimes"));

        // When: Build the user update request
        // Then: Reject the invalid attribute type
        UserHandler.buildUserUpdateRequest(directory().users(), new Uid(USER), deltas, Optional.empty());
    }

    private static GoogleApiExecutor executor(MockHttpTransport transport) {
        Directory directory = new Directory.Builder(transport, new GsonFactory(), request -> { }).setApplicationName("connector-googleapps-test").build();
        return new GoogleApiExecutor(new GoogleAppsConfiguration() {
            @Override
            public Directory getDirectory() {
                return directory;
            }
        });
    }

    private static final class RecordingTransport extends MockHttpTransport {
        private final List<String> methods = new ArrayList<>();
        private final List<MockLowLevelHttpRequest> requests = new ArrayList<>();
        private final int status;
        private final String content;

        private RecordingTransport() {
            this(200, "{}");
        }

        private RecordingTransport(int status, String content) {
            this.status = status;
            this.content = content;
        }

        @Override
        public LowLevelHttpRequest buildRequest(String method, String url) {
            MockLowLevelHttpRequest request = new MockLowLevelHttpRequest(url).setResponse(
                    new MockLowLevelHttpResponse().setStatusCode(status).setContentType("application/json").setContent(content));
            methods.add(method);
            requests.add(request);
            return request;
        }
    }

    private static Directory directory() {
        return new Directory.Builder(new NetHttpTransport(), new GsonFactory(), request -> { })
                .setApplicationName("connector-googleapps-test")
                .build();
    }

    private static Licensing licensing() {
        return new Licensing.Builder(new NetHttpTransport(), new GsonFactory(), request -> { })
                .setApplicationName("connector-googleapps-test")
                .build();
    }

    private static AttributesAccessor attributes(Attribute... attributes) {
        return new AttributesAccessor(new HashSet<>(Arrays.asList(attributes)));
    }

    private static String licenseUid() {
        return PRODUCT + "/sku/" + SKU + "/user/" + USER;
    }
}
