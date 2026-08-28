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

import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Schema;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

public class TestSchemaDescriptions {

    @Test
    public void testObjectClassDescriptions() {
        Schema schema = new GoogleAppsConnector().schema();

        assertDescription(schema, ObjectClass.ACCOUNT_NAME,
                "A user identity in the configured Google Workspace domain. "
                        + "Represents the user's sign-in account, profile, status, organizational unit, aliases, "
                        + "and contact information.");
        assertDescription(schema, ObjectClass.GROUP_NAME,
                "A Google Group used as an email distribution list and membership container. "
                        + "Group memberships are represented separately by the Member object class.");
        assertDescription(schema, GoogleAppsConnector.MEMBER.getObjectClassValue(),
                "A membership relationship linking a user, another group, or a customer domain "
                        + "to a Google Group. It includes the member type and role, such as MEMBER, MANAGER, or OWNER.");
        assertDescription(schema, GoogleAppsConnector.ORG_UNIT.getObjectClassValue(),
                "A node in the Google Workspace organizational hierarchy. Users assigned to it "
                        + "receive the services and settings configured for that organizational unit.");
        assertDescription(schema, GoogleAppsConnector.LICENSE_ASSIGNMENT.getObjectClassValue(),
                "A relationship assigning a Google Workspace product SKU to a user. It connects "
                        + "the user, product, and SKU and can be used to assign, revoke, or change a license.");
    }

    private static void assertDescription(Schema schema, String objectClassType, String expectedDescription) {
        ObjectClassInfo objectClassInfo = schema.findObjectClassInfo(objectClassType);
        assertNotNull("Object class is missing: " + objectClassType, objectClassInfo);
        assertEquals("Unexpected description for " + objectClassType,
                expectedDescription, objectClassInfo.getDescription());
    }
}
