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
import com.google.api.services.directory.Directory;
import com.google.api.services.licensing.Licensing;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/** Exception paths that must reject invalid ConnId input before issuing Google API requests. */
public class ConnectorExceptionPathsTest {

    @Test
    public void createOperationsRejectMissingRequiredAttributes() {
        // Given: Resource inputs missing mandatory attributes
        GoogleAppsConnector connector = connector();
        OperationOptionsBuilder options = new OperationOptionsBuilder();

        // When: Create resources with incomplete input
        // Then: Reject inputs before issuing API requests
        expectThrows(InvalidAttributeValueException.class,
                () -> connector.create(ObjectClass.ACCOUNT, attributes(new Name("user@example.com")), options.build()));
        expectThrows(InvalidAttributeValueException.class,
                () -> connector.create(ObjectClass.GROUP, attributes(AttributeBuilder.build("description", "group")), options.build()));
        expectThrows(InvalidAttributeValueException.class,
                () -> connector.create(GoogleAppsConstants.MEMBER, attributes(), options.build()));
        expectThrows(InvalidAttributeValueException.class,
                () -> connector.create(GoogleAppsConstants.ORG_UNIT,
                        attributes(AttributeBuilder.build(GoogleAppsConstants.PARENT_ORG_UNIT_PATH_ATTR, "/")), options.build()));
        expectThrows(InvalidAttributeValueException.class,
                () -> connector.create(GoogleAppsConstants.LICENSE_ASSIGNMENT, attributes(), options.build()));

        connector.dispose();
    }

    @Test
    public void updateAndDeleteOperationsRejectMalformedInputBeforeApiExecution() {
        // Given: Single-valued additions and malformed resource identifiers
        GoogleAppsConnector connector = connector();
        OperationOptionsBuilder options = new OperationOptionsBuilder();

        try {
            // When: Update or delete invalid resources
            // Then: Reject input before issuing requests
            expectThrows(InvalidAttributeValueException.class,
                    () -> connector.updateDelta(ObjectClass.ACCOUNT, new Uid("user@example.com"),
                            Set.of(addition(GoogleAppsConstants.SUSPENDED_ATTR, true)), options.build()));
            expectThrows(InvalidAttributeValueException.class,
                    () -> connector.updateDelta(ObjectClass.GROUP, new Uid("group@example.com"),
                            Set.of(addition(GoogleAppsConstants.NAME_ATTR, "Engineering")), options.build()));
            expectThrows(InvalidAttributeValueException.class,
                    () -> connector.updateDelta(GoogleAppsConstants.ORG_UNIT, new Uid("Engineering"),
                            Set.of(addition(GoogleAppsConstants.PARENT_ORG_UNIT_PATH_ATTR, "/Engineering")), options.build()));
            expectThrows(UnknownUidException.class,
                    () -> connector.updateDelta(GoogleAppsConstants.MEMBER, new Uid("bad-member-id"),
                            Set.of(AttributeDeltaBuilder.build(GoogleAppsConstants.ROLE_ATTR, "MANAGER")), options.build()));
            expectThrows(UnknownUidException.class,
                    () -> connector.updateDelta(GoogleAppsConstants.LICENSE_ASSIGNMENT, new Uid("bad-license-id"),
                            Set.of(AttributeDeltaBuilder.build(GoogleAppsConstants.SKU_ID_ATTR, "replacement")), options.build()));
            expectThrows(UnknownUidException.class,
                    () -> connector.delete(GoogleAppsConstants.MEMBER, new Uid("bad-member-id"), options.build()));
        } finally {
            connector.dispose();
        }
    }

    @Test
    public void queryOperationsRejectMalformedMemberUidAndUnsupportedSearchButIgnoreUnknownLicenseUid() {
        // Given: Invalid identifiers and unsupported resource searches
        GoogleAppsConnector connector = connector();
        ResultsHandler handler = object -> {
            throw new AssertionError("Unexpected search result: " + object);
        };
        OperationOptionsBuilder options = new OperationOptionsBuilder();
        GoogleFilter member = new GoogleFilter(FilterBuilder.equalTo(new Uid("bad-member-id")));
        GoogleFilter license = new GoogleFilter(new Uid("bad-license-id"));

        try {
            // When: Execute invalid resource queries
            // Then: Reject invalid searches without returning objects
            expectThrows(InvalidAttributeValueException.class,
                    () -> connector.executeQuery(GoogleAppsConstants.MEMBER, member, handler, options.build()));
            expectThrows(InvalidAttributeValueException.class,
                    () -> connector.executeQuery(GoogleAppsConstants.MEMBER, null, handler, options.build()));
            ConnectorException missingProduct = expectThrows(ConnectorException.class,
                    () -> connector.executeQuery(GoogleAppsConstants.LICENSE_ASSIGNMENT, null, handler, options.build()));
            assertTrue(missingProduct.getMessage().contains("productId is required"));

            // Unrecognized assignment identifiers represent missing resources.
            connector.executeQuery(GoogleAppsConstants.LICENSE_ASSIGNMENT, license, handler, options.build());
            connector.executeQuery(GoogleAppsConstants.MEMBER, new GoogleFilter(new Uid("bad-member-id")), handler, options.build());
        } finally {
            connector.dispose();
        }
    }

    private static AttributeDelta addition(String name, Object value) {
        AttributeDeltaBuilder builder = new AttributeDeltaBuilder();
        builder.setName(name);
        builder.addValueToAdd(value);
        return builder.build();
    }

    private static GoogleAppsConnector connector() {
        GoogleAppsConnector connector = new GoogleAppsConnector();
        connector.init(new LocalConfiguration());
        return connector;
    }

    private static Set<Attribute> attributes(Attribute... attributes) {
        return new HashSet<>(Arrays.asList(attributes));
    }

    private static final class LocalConfiguration extends GoogleAppsConfiguration {
        private final MockHttpTransport transport = new MockHttpTransport() {
            @Override
            public LowLevelHttpRequest buildRequest(String method, String url) {
                throw new AssertionError("Unexpected API request: " + method + " " + url);
            }
        };
        private final Directory directory = new Directory.Builder(transport, new GsonFactory(), request -> { })
                .setApplicationName("connector-googleapps-test")
                .build();
        private final Licensing licensing = new Licensing.Builder(transport, new GsonFactory(), request -> { })
                .setApplicationName("connector-googleapps-test")
                .build();

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
