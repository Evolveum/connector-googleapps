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
import com.google.api.services.licensing.Licensing;
import com.google.api.services.licensing.model.LicenseAssignment;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestResourceHandlers {

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
                new HashSet<String>(Arrays.asList(GoogleAppsConnector.ORG_UNIT_PATH_ATTR)));

        assertEquals(object.getUid().getUidValue(), "Engineering");
        assertNotNull(object.getAttributeByName(GoogleAppsConnector.ORG_UNIT_PATH_ATTR));
        assertNull(object.getAttributeByName(PredefinedAttributes.DESCRIPTION));
        assertNull(object.getAttributeByName(GoogleAppsConnector.BLOCK_INHERITANCE_ATTR));
    }

    @Test
    public void licenseDeleteParsesAllPartsOfCompoundUid() {
        Licensing.LicenseAssignments.Delete request = LicenseAssignmentsHandler.deleteLicenseAssignment(
                licensing().licenseAssignments(), licenseUid());

        assertEquals(request.getProductId(), PRODUCT);
        assertEquals(request.getSkuId(), SKU);
        assertEquals(request.getUserId(), USER);
    }

    @Test
    public void licenseUpdateUsesExistingLicenseCoordinatesAndNewSku() {
        String replacementSku = "Google-Drive-storage-2TB";
        Licensing.LicenseAssignments.Patch request = LicenseAssignmentsHandler.updateLicenseAssignment(
                licensing().licenseAssignments(), licenseUid(),
                attributes(AttributeBuilder.build(GoogleAppsConnector.SKU_ID_ATTR, replacementSku)));

        LicenseAssignment content = (LicenseAssignment) request.getJsonContent();
        assertEquals(request.getProductId(), PRODUCT);
        assertEquals(request.getSkuId(), SKU);
        assertEquals(request.getUserId(), USER);
        assertEquals(content.getSkuId(), replacementSku);
    }

    @Test
    public void unchangedLicenseSkuDoesNotCreatePatch() {
        assertNull(LicenseAssignmentsHandler.updateLicenseAssignment(licensing().licenseAssignments(), licenseUid(),
                attributes(AttributeBuilder.build(GoogleAppsConnector.SKU_ID_ATTR, SKU))));
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
        return new AttributesAccessor(new HashSet<Attribute>(Arrays.asList(attributes)));
    }

    private static String licenseUid() {
        return PRODUCT + "/sku/" + SKU + "/user/" + USER;
    }
}
