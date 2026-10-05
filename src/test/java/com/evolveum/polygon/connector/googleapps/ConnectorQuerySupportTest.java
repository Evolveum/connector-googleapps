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

import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.PredefinedAttributes;
import org.testng.annotations.Test;

import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class ConnectorQuerySupportTest {

    private final GoogleAppsConnector connector = new GoogleAppsConnector();

    @Test
    public void requestedAccountFieldsContainIdEtagAndTopLevelAttributes() {
        OperationOptions options = new OperationOptionsBuilder()
                .setAttributesToGet("/name/givenName", "phones(type,value)", "emails")
                .build();

        Set<String> attributes = connector.getAttributesToGet(ObjectClass.ACCOUNT, options);

        assertTrue(attributes.contains(GoogleAppsConnector.ID_ATTR));
        assertTrue(attributes.contains(GoogleAppsConnector.ETAG_ATTR));
        assertTrue(attributes.contains("name"));
        assertTrue(attributes.contains("phones"));
        assertTrue(attributes.contains("emails"));
    }

    @Test
    public void requestedOrgUnitFieldsContainPathInsteadOfId() {
        OperationOptions options = new OperationOptionsBuilder().setAttributesToGet("description").build();

        Set<String> attributes = connector.getAttributesToGet(GoogleAppsConnector.ORG_UNIT, options);

        assertTrue(attributes.contains(GoogleAppsConnector.ORG_UNIT_PATH_ATTR));
        assertTrue(attributes.contains(GoogleAppsConnector.ETAG_ATTR));
        assertTrue(attributes.contains("description"));
    }

    @Test
    public void noRequestedFieldsMeansNoFieldsProjection() {
        assertNull(connector.getAttributesToGet(ObjectClass.ACCOUNT, new OperationOptionsBuilder().build()));
        assertNull(connector.getFields(new OperationOptionsBuilder().build(), GoogleAppsConnector.ID_ATTR));
    }

    @Test
    public void googleNamesAndFieldsMapConnIdAttributes() {
        OperationOptions options = new OperationOptionsBuilder()
                .setAttributesToGet(Name.NAME, PredefinedAttributes.DESCRIPTION, GoogleAppsConnector.FAMILY_NAME_ATTR)
                .build();

        assertEquals(connector.googleName(ObjectClass.ACCOUNT, Name.NAME), GoogleAppsConnector.PRIMARY_EMAIL_ATTR);
        assertEquals(connector.googleName(ObjectClass.GROUP, Name.NAME), GoogleAppsConnector.EMAIL_ATTR);
        assertEquals(connector.googleName(ObjectClass.ACCOUNT, PredefinedAttributes.DESCRIPTION), GoogleAppsConnector.DESCRIPTION_ATTR);

        String fields = connector.getFields(options, GoogleAppsConnector.ID_ATTR);
        assertTrue(fields.contains(GoogleAppsConnector.ID_ATTR));
        assertTrue(fields.contains(GoogleAppsConnector.DESCRIPTION_ATTR));
        assertTrue(fields.contains("name/familyName"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void malformedRequestedFieldIsRejected() {
        OperationOptions options = new OperationOptionsBuilder().setAttributesToGet("//nested").build();
        connector.getAttributesToGet(ObjectClass.ACCOUNT, options);
    }
}
