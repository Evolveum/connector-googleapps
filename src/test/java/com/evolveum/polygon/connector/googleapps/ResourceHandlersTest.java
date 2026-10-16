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

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.directory.Directory;
import com.google.api.services.directory.model.Member;
import com.google.api.services.directory.model.OrgUnit;
import com.google.api.services.directory.model.User;
import com.google.api.services.licensing.Licensing;
import com.google.api.services.licensing.model.LicenseAssignment;
import com.google.api.services.licensing.model.LicenseAssignmentInsert;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributesAccessor;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.PredefinedAttributes;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class ResourceHandlersTest {

    private static final String PRODUCT = "Google-Drive-storage";
    private static final String SKU = "Google-Drive-storage-1TB";
    private static final String USER = "ada@example.com";

    @Test
    public void createMemberAppliesDefaultRoleAndRequiredKeys() {
        Directory.Members.Insert request = GroupHandler.createMember(directory().members(),
                attributes(AttributeBuilder.build(GoogleAppsConnector.GROUP_KEY_ATTR, "engineering@example.com"),
                        AttributeBuilder.build(GoogleAppsConnector.EMAIL_ATTR, USER)));

        Member content = (Member) request.getJsonContent();
        assertEquals(request.getGroupKey(), "engineering@example.com");
        assertEquals(content.getEmail(), USER);
        assertEquals(content.getRole(), "MEMBER");
        assertEquals(request.getFields(), GoogleAppsConnector.EMAIL_ETAG);
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void createMemberRequiresGroupKey() {
        GroupHandler.createMember(directory().members(),
                attributes(AttributeBuilder.build(GoogleAppsConnector.EMAIL_ATTR, USER)));
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void createMemberRequiresMemberKey() {
        GroupHandler.createMember(directory().members(),
                attributes(AttributeBuilder.build(GoogleAppsConnector.GROUP_KEY_ATTR, "engineering@example.com")));
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
    public void groupRequestMappingsBuildAndSkipEmptyPatch() {
        Directory.Groups.Insert insert = GroupHandler.createGroup(directory().groups(),
                attributes(new Name("engineering@example.com"),
                        AttributeBuilder.build(GoogleAppsConnector.NAME_ATTR, "Engineering"),
                        AttributeBuilder.build(PredefinedAttributes.DESCRIPTION, "Team")));
        com.google.api.services.directory.model.Group insertBody =
                (com.google.api.services.directory.model.Group) insert.getJsonContent();
        assertEquals(insertBody.getEmail(), "engineering@example.com");
        assertEquals(insertBody.getName(), "Engineering");
        assertEquals(insertBody.getDescription(), "Team");
        assertEquals(insert.getFields(), GoogleAppsConnector.ID_ETAG);

        assertNull(GroupHandler.updateGroup(directory().groups(), "engineering@example.com", attributes()));
        Directory.Groups.Patch patch = GroupHandler.updateGroup(directory().groups(), "engineering@example.com",
                attributes(AttributeBuilder.build(PredefinedAttributes.DESCRIPTION, "New description")));
        assertNotNull(patch);
        assertEquals(((com.google.api.services.directory.model.Group) patch.getJsonContent()).getDescription(),
                "New description");
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void groupUpdateRejectsBlankPrimaryEmail() {
        GroupHandler.updateGroup(directory().groups(), "engineering@example.com", attributes(new Name(" ")));
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void createGroupRequiresPrimaryEmail() {
        GroupHandler.createGroup(directory().groups(), attributes(
                AttributeBuilder.build(GoogleAppsConnector.NAME_ATTR, "Engineering")));
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void groupUpdateRejectsNonStringDescription() {
        GroupHandler.updateGroup(directory().groups(), "engineering@example.com",
                attributes(AttributeBuilder.build(PredefinedAttributes.DESCRIPTION, 42)));
    }

    @Test
    public void memberMappingsPreserveEtagAndMapAttributes() {
        Member member = new Member().setId("123").setEmail(USER).setRole("OWNER").setType("USER")
                .setEtag("member-version");

        Uid uid = GroupHandler.generateMemberId("engineering@example.com", member);
        ConnectorObject object = GroupHandler.fromMember("engineering@example.com", member);

        assertEquals(uid.getUidValue(), "engineering@example.com/" + USER);
        assertEquals(uid.getRevision(), "member-version");
        assertEquals(object.getUid(), uid);
        assertEquals(object.getAttributeByName(GoogleAppsConnector.GROUP_KEY_ATTR).getValue(), List.of("123"));
        assertEquals(object.getAttributeByName(GoogleAppsConnector.ROLE_ATTR).getValue(), List.of("OWNER"));
        assertEquals(object.getAttributeByName(GoogleAppsConnector.TYPE_ATTR).getValue(), List.of("USER"));
        assertNull(GroupHandler.generateMemberId("engineering@example.com", new Member().setEmail(USER)).getRevision());
    }

    @Test
    public void memberUpdateDefaultsRoleAndDeleteBuildRequests() {
        Directory.Members.Patch patch = GroupHandler.updateMembers(directory().members(), "g", USER, " ");
        assertEquals(((Member) patch.getJsonContent()).getRole(), "MEMBER");
        assertEquals(patch.getFields(), GoogleAppsConnector.EMAIL_ETAG);
        assertEquals(GroupHandler.deleteMembers(directory().members(), "g", USER).getMemberKey(), USER);
    }

    @Test
    public void userCreateUpdatePhotoAndAliasesBuildRequests() {
        Directory.Users.Insert insert = UserHandler.createUser(directory().users(), attributes(
                new Name(USER), AttributeBuilder.buildPassword(new GuardedString("P@ssw0rd".toCharArray())),
                AttributeBuilder.build(UserHandler.GIVEN_NAME_ATTR, "Ada"),
                AttributeBuilder.build(UserHandler.FAMILY_NAME_ATTR, "Lovelace"),
                AttributeBuilder.build(UserHandler.SUSPENDED_ATTR, true),
                AttributeBuilder.build(UserHandler.ORG_UNIT_PATH_ATTR, "/Engineering")));
        com.google.api.services.directory.model.User user =
                (com.google.api.services.directory.model.User) insert.getJsonContent();
        assertEquals(user.getPrimaryEmail(), USER);
        assertEquals(user.getName().getGivenName(), "Ada");
        assertEquals(user.getName().getFamilyName(), "Lovelace");
        assertEquals(user.getSuspended(), Boolean.TRUE);
        assertEquals(user.getOrgUnitPath(), "/Engineering");
        assertEquals(insert.getFields(), UserHandler.ID_ETAG);

        assertNull(UserHandler.updateUser(directory().users(), new Uid("u"), attributes()));
        Directory.Users.Patch patch = UserHandler.updateUser(directory().users(), new Uid("u"), attributes(
                AttributeBuilder.build(UserHandler.FAMILY_NAME_ATTR, "Byron"),
                AttributeBuilder.build(UserHandler.INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR, "false")));
        assertNotNull(patch);
        com.google.api.services.directory.model.User patchBody =
                (com.google.api.services.directory.model.User) patch.getJsonContent();
        assertEquals(patchBody.getName().getFamilyName(), "Byron");
        assertEquals(patchBody.getIncludeInGlobalAddressList(), Boolean.FALSE);
        assertEquals(UserHandler.createUpdateUserPhoto(directory().users().photos(), USER,
                new byte[]{1, 2, 3}).getFields(), GoogleAppsConnector.ID_ATTR);
        assertEquals(UserHandler.createUserAlias(directory().users().aliases(), USER,
                "ada.alias@example.com").getFields(), UserHandler.ID_ETAG);
        assertEquals(UserHandler.deleteUserAlias(directory().users().aliases(), USER,
                "ada.alias@example.com").getUserKey(), USER);
    }

    @Test
    public void userCreateMapsStructuredAndOptionalScalarAttributes() {
        Directory.Users.Insert insert = UserHandler.createUser(directory().users(), attributes(
                new Name(USER), AttributeBuilder.buildPassword(new GuardedString("secret".toCharArray())),
                AttributeBuilder.build(UserHandler.GIVEN_NAME_ATTR, "Ada"),
                AttributeBuilder.build(UserHandler.FAMILY_NAME_ATTR, "Lovelace"),
                AttributeBuilder.build(UserHandler.IMS_ATTR, "{\"type\":\"work\",\"value\":\"ada-chat\"}"),
                AttributeBuilder.build(UserHandler.EMAILS_ATTR, "{\"type\":\"work\",\"value\":\"ada@example.com\"}"),
                AttributeBuilder.build(UserHandler.EXTERNAL_IDS_ATTR, "{\"type\":\"account\",\"value\":\"42\"}"),
                AttributeBuilder.build(UserHandler.RELATIONS_ATTR, "{\"type\":\"manager\",\"value\":\"grace@example.com\"}"),
                AttributeBuilder.build(UserHandler.ADDRESSES_ATTR, "{\"type\":\"work\",\"streetAddress\":\"London\"}"),
                AttributeBuilder.build(UserHandler.ORGANIZATIONS_ATTR, "{\"name\":\"Analytical Engine\"}"),
                AttributeBuilder.build(UserHandler.PHONES_ATTR, "{\"type\":\"work\",\"value\":\"+44123\"}"),
                AttributeBuilder.build(UserHandler.LOCATIONS_ATTR, "{\"type\":\"desk\",\"area\":\"A1\"}"),
                AttributeBuilder.build(UserHandler.SUSPENDED_ATTR, true),
                AttributeBuilder.build(UserHandler.CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR, true),
                AttributeBuilder.build(UserHandler.IP_WHITELISTED_ATTR, false),
                AttributeBuilder.build(UserHandler.ORG_UNIT_PATH_ATTR, "/Engineering"),
                AttributeBuilder.build(UserHandler.INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR, true)));

        User content = (User) insert.getJsonContent();

        assertEquals(((java.util.List<?>) content.getIms()).size(), 1);
        assertEquals(((java.util.List<?>) content.getEmails()).size(), 1);
        assertEquals(((java.util.List<?>) content.getExternalIds()).size(), 1);
        assertEquals(((java.util.List<?>) content.getRelations()).size(), 1);
        assertEquals(((java.util.List<?>) content.getAddresses()).size(), 1);
        assertEquals(((java.util.List<?>) content.getOrganizations()).size(), 1);
        assertEquals(((java.util.List<?>) content.getPhones()).size(), 1);
        assertEquals(((java.util.List<?>) content.getLocations()).size(), 1);
        assertEquals(content.getSuspended(), Boolean.TRUE);
        assertEquals(content.getChangePasswordAtNextLogin(), Boolean.TRUE);
        assertEquals(content.getIpWhitelisted(), Boolean.FALSE);
        assertEquals(content.getOrgUnitPath(), "/Engineering");
        assertEquals(content.getIncludeInGlobalAddressList(), Boolean.TRUE);
    }

    @Test
    public void userUpdateMapsEveryOptionalFieldAndAcceptsNullValues() {
        Directory.Users.Patch patch = UserHandler.updateUser(directory().users(), new Uid("u"), attributes(
                new Name("new@example.com"),
                AttributeBuilder.build(UserHandler.GIVEN_NAME_ATTR, "Augusta"),
                AttributeBuilder.build(UserHandler.FAMILY_NAME_ATTR, "King"),
                AttributeBuilder.buildPassword(new GuardedString("new-secret".toCharArray())),
                AttributeBuilder.build(UserHandler.SUSPENDED_ATTR, true),
                AttributeBuilder.build(UserHandler.CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR, false),
                AttributeBuilder.build(UserHandler.IP_WHITELISTED_ATTR, true),
                AttributeBuilder.build(UserHandler.IMS_ATTR, "{\"type\":\"work\",\"value\":\"ada-chat\"}"),
                AttributeBuilder.build(UserHandler.EMAILS_ATTR, "{\"type\":\"work\",\"value\":\"new@example.com\"}"),
                AttributeBuilder.build(UserHandler.EXTERNAL_IDS_ATTR, "{\"type\":\"account\",\"value\":\"42\"}"),
                AttributeBuilder.build(UserHandler.RELATIONS_ATTR, "{\"type\":\"manager\",\"value\":\"grace@example.com\"}"),
                AttributeBuilder.build(UserHandler.ADDRESSES_ATTR, "{\"type\":\"work\",\"streetAddress\":\"London\"}"),
                AttributeBuilder.build(UserHandler.ORGANIZATIONS_ATTR, "{\"name\":\"Analytical Engine\"}"),
                AttributeBuilder.build(UserHandler.PHONES_ATTR, "{\"type\":\"work\",\"value\":\"+44123\"}"),
                AttributeBuilder.build(UserHandler.LOCATIONS_ATTR, "{\"type\":\"desk\",\"area\":\"A1\"}"),
                AttributeBuilder.build(UserHandler.ORG_UNIT_PATH_ATTR, "/Engineering"),
                AttributeBuilder.build(UserHandler.INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR, true)));

        assertNotNull(patch);
        User content = (User) patch.getJsonContent();
        assertEquals(content.getPrimaryEmail(), "new@example.com");
        assertEquals(content.getName().getGivenName(), "Augusta");
        assertEquals(content.getName().getFamilyName(), "King");
        assertEquals(content.getPassword(), "new-secret");
        assertEquals(content.getSuspended(), Boolean.TRUE);
        assertEquals(content.getChangePasswordAtNextLogin(), Boolean.FALSE);
        assertEquals(content.getIpWhitelisted(), Boolean.TRUE);
        assertEquals(((java.util.List<?>) content.getIms()).size(), 1);
        assertEquals(((java.util.List<?>) content.getEmails()).size(), 1);
        assertEquals(((java.util.List<?>) content.getExternalIds()).size(), 1);
        assertEquals(((java.util.List<?>) content.getRelations()).size(), 1);
        assertEquals(((java.util.List<?>) content.getAddresses()).size(), 1);
        assertEquals(((java.util.List<?>) content.getOrganizations()).size(), 1);
        assertEquals(((java.util.List<?>) content.getPhones()).size(), 1);
        assertEquals(((java.util.List<?>) content.getLocations()).size(), 1);
        assertEquals(content.getOrgUnitPath(), "/Engineering");
        assertEquals(content.getIncludeInGlobalAddressList(), Boolean.TRUE);
    }

    @Test
    public void userUpdateCanInitializeThePatchFromEachOptionalFieldAlone() {
        AttributesAccessor[] individualAttributes = new AttributesAccessor[] {
            attributes(AttributeBuilder.build(UserHandler.GIVEN_NAME_ATTR, "Ada")),
            attributes(AttributeBuilder.build(UserHandler.FAMILY_NAME_ATTR, "Lovelace")),
            attributes(AttributeBuilder.buildPassword(new GuardedString("secret".toCharArray()))),
            attributes(AttributeBuilder.build(UserHandler.SUSPENDED_ATTR, true)),
            attributes(AttributeBuilder.build(UserHandler.CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR, true)),
            attributes(AttributeBuilder.build(UserHandler.IP_WHITELISTED_ATTR, true)),
            attributes(AttributeBuilder.build(UserHandler.IMS_ATTR, "{\"type\":\"work\"}")),
            attributes(AttributeBuilder.build(UserHandler.EMAILS_ATTR, "{\"type\":\"work\"}")),
            attributes(AttributeBuilder.build(UserHandler.EXTERNAL_IDS_ATTR, "{\"type\":\"account\"}")),
            attributes(AttributeBuilder.build(UserHandler.RELATIONS_ATTR, "{\"type\":\"manager\"}")),
            attributes(AttributeBuilder.build(UserHandler.ADDRESSES_ATTR, "{\"type\":\"work\"}")),
            attributes(AttributeBuilder.build(UserHandler.ORGANIZATIONS_ATTR, "{\"name\":\"Org\"}")),
            attributes(AttributeBuilder.build(UserHandler.PHONES_ATTR, "{\"type\":\"work\"}")),
            attributes(AttributeBuilder.build(UserHandler.LOCATIONS_ATTR, "{\"type\":\"desk\"}")),
            attributes(AttributeBuilder.build(UserHandler.ORG_UNIT_PATH_ATTR, "/Engineering")),
            attributes(AttributeBuilder.build(UserHandler.INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR, true))
        };

        for (AttributesAccessor singleAttribute : individualAttributes) {
            assertNotNull(UserHandler.updateUser(directory().users(), new Uid("u"),
                    singleAttribute));
        }
        assertNull(UserHandler.updateUser(directory().users(), new Uid("u"),
                new AttributesAccessor(Set.of())));
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void userCreateRequiresPassword() {
        UserHandler.createUser(directory().users(), attributes(new Name(USER),
                AttributeBuilder.build(UserHandler.GIVEN_NAME_ATTR, "Ada"),
                AttributeBuilder.build(UserHandler.FAMILY_NAME_ATTR, "Lovelace")));
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void userCreateRequiresGivenName() {
        UserHandler.createUser(directory().users(), attributes(new Name(USER),
                AttributeBuilder.buildPassword(new GuardedString("secret".toCharArray())),
                AttributeBuilder.build(UserHandler.FAMILY_NAME_ATTR, "Lovelace")));
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void userCreateRequiresFamilyName() {
        UserHandler.createUser(directory().users(), attributes(new Name(USER),
                AttributeBuilder.buildPassword(new GuardedString("secret".toCharArray())),
                AttributeBuilder.build(UserHandler.GIVEN_NAME_ATTR, "Ada")));
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void userUpdateRejectsNonStringGivenName() {
        UserHandler.updateUser(directory().users(), new Uid("u"),
                attributes(AttributeBuilder.build(UserHandler.GIVEN_NAME_ATTR, 42)));
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void userUpdateRejectsUnrecognizedBooleanValue() {
        UserHandler.updateUser(directory().users(), new Uid("u"),
                attributes(AttributeBuilder.build(UserHandler.SUSPENDED_ATTR, "sometimes")));
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
