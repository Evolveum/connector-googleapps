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

        assertDescription(schema, ObjectClass.ACCOUNT_NAME, "Google Workspace user account");
        assertDescription(schema, ObjectClass.GROUP_NAME, "Google Workspace group");
        assertDescription(schema, GoogleAppsConnector.MEMBER.getObjectClassValue(),
                "Google Workspace group membership");
        assertDescription(schema, GoogleAppsConnector.ORG_UNIT.getObjectClassValue(),
                "Google Workspace organizational unit");
        assertDescription(schema, GoogleAppsConnector.LICENSE_ASSIGNMENT.getObjectClassValue(),
                "Google Workspace product license assignment");
    }

    private static void assertDescription(Schema schema, String objectClassType, String expectedDescription) {
        ObjectClassInfo objectClassInfo = schema.findObjectClassInfo(objectClassType);
        assertNotNull("Object class is missing: " + objectClassType, objectClassInfo);
        assertEquals("Unexpected description for " + objectClassType,
                expectedDescription, objectClassInfo.getDescription());
    }
}
