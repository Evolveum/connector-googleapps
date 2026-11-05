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
import com.google.api.services.licensing.Licensing;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Uid;
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
        GoogleAppsConnector connector = connector();
        OperationOptionsBuilder options = new OperationOptionsBuilder();

        expectThrows(InvalidAttributeValueException.class,
                () -> connector.create(ObjectClass.ACCOUNT, attributes(new Name("user@example.com")), options.build()));
        expectThrows(InvalidAttributeValueException.class,
                () -> connector.create(ObjectClass.GROUP, attributes(AttributeBuilder.build("description", "group")), options.build()));
        expectThrows(InvalidAttributeValueException.class,
                () -> connector.create(GoogleAppsConnector.MEMBER, attributes(), options.build()));
        expectThrows(InvalidAttributeValueException.class,
                () -> connector.create(GoogleAppsConnector.ORG_UNIT,
                        attributes(AttributeBuilder.build(GoogleAppsConnector.PARENT_ORG_UNIT_PATH_ATTR, "/")), options.build()));
        expectThrows(InvalidAttributeValueException.class,
                () -> connector.create(GoogleAppsConnector.LICENSE_ASSIGNMENT, attributes(), options.build()));

        connector.dispose();
    }

    @Test
    public void updateAndDeleteOperationsRejectMalformedInputBeforeApiExecution() {
        GoogleAppsConnector connector = connector();
        OperationOptionsBuilder options = new OperationOptionsBuilder();

        expectThrows(InvalidAttributeValueException.class,
                () -> connector.update(ObjectClass.ACCOUNT, new Uid("user@example.com"),
                        attributes(AttributeBuilder.build(UserHandler.SUSPENDED_ATTR, "sometimes")), options.build()));
        expectThrows(InvalidAttributeValueException.class,
                () -> connector.update(ObjectClass.GROUP, new Uid("group@example.com"),
                        attributes(new Name(" ")), options.build()));
        expectThrows(InvalidAttributeValueException.class,
                () -> connector.update(GoogleAppsConnector.ORG_UNIT, new Uid("Engineering"),
                        attributes(AttributeBuilder.build(GoogleAppsConnector.PARENT_ORG_UNIT_PATH_ATTR, " ")), options.build()));
        expectThrows(UnknownUidException.class,
                () -> connector.update(GoogleAppsConnector.MEMBER, new Uid("bad-member-id"),
                        attributes(AttributeBuilder.build(GoogleAppsConnector.ROLE_ATTR, "MANAGER")), options.build()));
        expectThrows(UnknownUidException.class,
                () -> connector.update(GoogleAppsConnector.LICENSE_ASSIGNMENT, new Uid("bad-license-id"),
                        attributes(AttributeBuilder.build(GoogleAppsConnector.SKU_ID_ATTR, "replacement")), options.build()));
        expectThrows(UnknownUidException.class,
                () -> connector.delete(GoogleAppsConnector.MEMBER, new Uid("bad-member-id"), options.build()));

        connector.dispose();
    }

    @Test
    public void queryOperationsRejectMalformedMemberUidAndUnsupportedSearchButIgnoreUnknownLicenseUid() {
        GoogleAppsConnector connector = connector();
        ResultsHandler handler = object -> true;
        OperationOptionsBuilder options = new OperationOptionsBuilder();

        expectThrows(InvalidAttributeValueException.class,
                () -> connector.executeQuery(GoogleAppsConnector.MEMBER,
                        org.identityconnectors.framework.common.objects.filter.FilterBuilder.equalTo(
                                AttributeBuilder.build(Uid.NAME, "bad-member-id")), handler, options.build()));
        expectThrows(UnsupportedOperationException.class,
                () -> connector.executeQuery(GoogleAppsConnector.MEMBER, null, handler, options.build()));
        ConnectorException missingProduct = expectThrows(ConnectorException.class,
                () -> connector.executeQuery(GoogleAppsConnector.LICENSE_ASSIGNMENT, null, handler, options.build()));
        assertTrue(missingProduct.getMessage().contains("productId is required"));

        // An unrecognized assignment UID is deliberately treated as a not-found read and must not call Google.
        connector.executeQuery(GoogleAppsConnector.LICENSE_ASSIGNMENT,
                org.identityconnectors.framework.common.objects.filter.FilterBuilder.equalTo(
                        AttributeBuilder.build(Uid.NAME, "bad-license-id")), handler, options.build());
        connector.dispose();
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
        private final Directory directory = new Directory.Builder(new NetHttpTransport(), new GsonFactory(), request -> { })
                .setApplicationName("connector-googleapps-test")
                .build();
        private final Licensing licensing = new Licensing.Builder(new NetHttpTransport(), new GsonFactory(), request -> { })
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
