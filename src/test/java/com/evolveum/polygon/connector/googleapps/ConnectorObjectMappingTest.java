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
    public void userResourceUsesIdWithoutRevisionWhenNoEtagAndNoName() {
        GoogleAppsConnector connector = new GoogleAppsConnector();
        Set<String> attributes = new HashSet<>(List.of(UserHandler.GIVEN_NAME_ATTR));
        User user = new User().setId("user-id").setPrimaryEmail("ada@example.com");

        ConnectorObject object = connector.fromUser(user, attributes, null);

        assertEquals(object.getUid().getUidValue(), "user-id");
        assertNull(object.getUid().getRevision());
        assertNotNull(object.getAttributeByName(UserHandler.GIVEN_NAME_ATTR));
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
