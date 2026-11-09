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
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.directory.Directory;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.PredefinedAttributes;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class ConnectorQuerySupportTest {

    private final GoogleAppsConnector connector = new GoogleAppsConnector();

    @Test
    public void requestedAccountFieldsContainIdEtagAndTopLevelAttributes() {
        // Given: Requested account fields and account schema
        OperationOptions options = new OperationOptionsBuilder().setAttributesToGet("givenName", "phones", "emails").build();
        SchemaDefinition schema = connector.getSchemaDefinition(ObjectClass.ACCOUNT);

        // When: Map requested attributes and mandatory identities
        Set<String> attributes = schema.createFullAttributesToGet(options);
        String fields = schema.createGoogleApiFieldsString(attributes, "id", "etag", "primaryEmail");

        // Then: Preserve projection and include required identities
        assertEquals(attributes, Set.of("givenName", "phones", "emails"));
        assertEquals(Set.of(fields.split(",")), Set.of("id", "etag", "primaryEmail", "name/givenName", "phones", "emails"));
    }

    @Test
    public void requestedOrgUnitFieldsContainPathInsteadOfId() {
        // Given: Organization unit description projection
        OperationOptions options = new OperationOptionsBuilder().setAttributesToGet(PredefinedAttributes.DESCRIPTION).build();
        SchemaDefinition schema = connector.getSchemaDefinition(GoogleAppsConstants.ORG_UNIT);

        // When: Include organization unit identity fields
        String fields = schema.createGoogleApiFieldsString(schema.createFullAttributesToGet(options), "orgUnitPath", "etag", "name");

        // Then: Use the path without account identifiers
        assertEquals(Set.of(fields.split(",")), Set.of("orgUnitPath", "etag", "name", "description"));
    }

    @Test
    public void noRequestedFieldsMeansNoFieldsProjection() {
        // Given: No requested fields and disabled defaults
        OperationOptions options = new OperationOptionsBuilder().setReturnDefaultAttributes(false).build();
        SchemaDefinition schema = connector.getSchemaDefinition(ObjectClass.ACCOUNT);

        // When: Resolve an empty field projection
        Set<String> attributes = schema.createFullAttributesToGet(options);
        String fields = schema.createGoogleApiFieldsString(attributes, "id");

        // Then: Leave both projection representations absent
        assertNull(attributes);
        assertNull(fields);
    }

    @Test
    public void googleNamesAndFieldsMapConnIdAttributes() {
        // Given: ConnId name and descriptive attributes
        OperationOptions options = new OperationOptionsBuilder().setAttributesToGet(Name.NAME, PredefinedAttributes.DESCRIPTION, "familyName").build();
        SchemaDefinition account = connector.getSchemaDefinition(ObjectClass.ACCOUNT);
        SchemaDefinition group = connector.getSchemaDefinition(ObjectClass.GROUP);

        // When: Map attributes with resource identity fields
        String userFields = account.createGoogleApiFieldsString(account.createFullAttributesToGet(options), "id", "primaryEmail");
        String groupFields = group.createGoogleApiFieldsString(Set.of(Name.NAME, PredefinedAttributes.DESCRIPTION), "id", "email");

        // Then: Translate description and omit special names
        assertEquals(Set.of(userFields.split(",")), Set.of("id", "primaryEmail", "description", "name/familyName"));
        assertEquals(Set.of(groupFields.split(",")), Set.of("id", "email", "description"));
    }

    @Test
    public void malformedRequestedFieldIsRejected() {
        // Given: Directory rejects a malformed field selector
        OperationOptions options = new OperationOptionsBuilder().setAttributesToGet("//nested").build();
        List<GenericUrl> requests = new ArrayList<>();
        MockHttpTransport transport = new MockHttpTransport() {
            @Override
            public LowLevelHttpRequest buildRequest(String method, String url) {
                assertEquals(method, "GET");
                requests.add(new GenericUrl(url));
                return new MockLowLevelHttpRequest(url).setResponse(new MockLowLevelHttpResponse().setStatusCode(400).setContentType("application/json")
                        .setContent("{\"error\":{\"code\":400,\"message\":\"Invalid field selection\",\"errors\":[{\"reason\":\"invalid\"}]}}"));
            }
        };
        Directory directory = new Directory.Builder(transport, new GsonFactory(), request -> { }).setApplicationName("connector-googleapps-test").build();
        GoogleAppsConnector connector = new GoogleAppsConnector();
        connector.init(new GoogleAppsConfiguration() {
            @Override
            public Directory getDirectory() {
                return directory;
            }
        });
        try {
            // When: Read using the rejected projection
            ConnectorException failure = expectThrows(ConnectorException.class,
                    () -> connector.executeQuery(ObjectClass.ACCOUNT, new GoogleFilter(new Uid("ada@example.com")), object -> {
                        throw new AssertionError("Unexpected result: " + object);
                    }, options));

            // Then: Preserve the selector and propagate rejection
            assertEquals(failure.getCause().getClass(), GoogleJsonResponseException.class);
            assertEquals(((GoogleJsonResponseException) failure.getCause()).getStatusCode(), 400);
            assertEquals(requests.size(), 1);
            assertEquals(requests.get(0).getRawPath(), "/admin/directory/v1/users/ada@example.com");
            List<?> fields = (List<?>) requests.get(0).get("fields");
            assertEquals(fields.size(), 1);
            assertEquals(Set.of(((String) fields.get(0)).split(",")), Set.of("id", "etag", "primaryEmail", "//nested"));
        } finally {
            connector.dispose();
        }
    }

    @Test
    public void legacyProjectionStripsNestedSelectorsAndRejectsMalformedSelectors() {
        // Given: Nested selectors handled by schema projection
        OperationOptions options = new OperationOptionsBuilder().setAttributesToGet("name/givenName", "phones(value)", "emails").build();
        SchemaDefinition schema = connector.getSchemaDefinition(ObjectClass.ACCOUNT);

        // When: Resolve attributes through the extracted schema
        Set<String> projected = schema.createFullAttributesToGet(options);
        String fields = schema.createGoogleApiFieldsString(projected, "id");

        // Then: Preserve selectors for Directory API validation
        assertEquals(projected, Set.of("name/givenName", "phones(value)", "emails"));
        assertEquals(Set.of(fields.split(",")), Set.of("id", "name/givenName", "phones(value)", "emails"));
    }

    @Test
    public void namesMapForOtherObjectClassesAndEveryNestedNameComponent() {
        // Given: Organization identity and account name components
        SchemaDefinition orgUnit = connector.getSchemaDefinition(GoogleAppsConstants.ORG_UNIT);
        SchemaDefinition account = connector.getSchemaDefinition(ObjectClass.ACCOUNT);
        Set<String> attributes = Set.of("givenName", "fullName", "familyName", "customAttribute");

        // When: Transform fields for each resource
        String orgFields = orgUnit.createGoogleApiFieldsString(Set.of(Name.NAME), "name");
        String userFields = account.createGoogleApiFieldsString(attributes);

        // Then: Keep custom fields and map names
        assertEquals(orgFields, "name");
        assertEquals(Set.of(userFields.split(",")), Set.of("name/givenName", "name/fullName", "name/familyName", "customAttribute"));
    }

    @Test
    public void fieldsProjectionIncludesNestedNamesButSkipsSpecialIdentityFields() {
        // Given: Special identities and nested account attributes
        OperationOptions options = new OperationOptionsBuilder().setAttributesToGet(Uid.NAME, Name.NAME, "givenName", "fullName", "customAttribute").build();
        SchemaDefinition schema = connector.getSchemaDefinition(ObjectClass.ACCOUNT);

        // When: Build the Google fields projection
        String fields = schema.createGoogleApiFieldsString(schema.createFullAttributesToGet(options), "id");

        // Then: Exclude ConnId identities from requested fields
        assertEquals(Set.of(fields.split(",")), Set.of("id", "name/givenName", "name/fullName", "customAttribute"));
    }

    @Test
    public void lifecycleSchemaAndFilterTranslationAreUsableWithoutApiCalls() {
        // Given: Configuration and an account name filter
        GoogleAppsConfiguration configuration = GoogleAppsConfigurationTest.validConfiguration();
        Filter filter = FilterBuilder.equalTo(AttributeBuilder.build(Name.NAME, "ada@example.com"));

        // When: Initialize and translate the query
        connector.init(configuration);
        try {
            Schema schema = connector.schema();
            List<GoogleFilter> queries = connector.createFilterTranslator(ObjectClass.ACCOUNT, new OperationOptionsBuilder().build()).translate(filter);

            // Then: Verify schema and direct name lookup
            assertSame(connector.getConfiguration(), configuration);
            assertNotNull(schema.findObjectClassInfo(ObjectClass.ACCOUNT.getObjectClassValue()));
            assertSame(connector.schema(), schema);
            assertEquals(queries.size(), 1);
            GoogleFilter query = queries.get(0);
            assertTrue(query.isReadByName());
            assertEquals(query.getName(), new Name("ada@example.com"));
            assertNull(query.getUid());
            assertNull(query.getFilter());
            assertFalse(query.hasSearchQuery());
        } finally {
            connector.dispose();
        }
        assertNull(connector.getConfiguration());
    }
}
