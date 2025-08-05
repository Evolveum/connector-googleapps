package com.evolveum.polygon.connector.googleapps;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GoogleAppsConnector User Get operations with RETURN_DEFAULT_ATTRIBUTES settings.
 * <p>
 * This class tests single user retrieval by UID and Name with RETURN_DEFAULT_ATTRIBUTES
 * and attributesToGet combinations, which uses different field parameter formats
 * compared to search operations:
 * - UID: GET /admin/directory/v1/users/{id} with fields: "field1,field2,..."
 * - Name: GET /admin/directory/v1/users/{email} with fields: "field1,field2,..."
 * <p>
 * Compare with GoogleAppsUserSearchDefaultAttributesTest which tests list operations:
 * - Search: GET /admin/directory/v1/users with fields: "nextPageToken,users(field1,field2,...)"
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsUserGetDefaultAttributesTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test get by UID with RETURN_DEFAULT_ATTRIBUTES = true and explicit attributesToGet")
    public void testGetByUidWithReturnDefaultAttributesTrueAndAttributesToGet() {
        // This test verifies that when retrieving a single user by UID with both
        // RETURN_DEFAULT_ATTRIBUTES=true and explicit attributesToGet specified,
        // the fields parameter includes all default attributes plus the explicitly requested ones.

        // Setup mock response for single user get by UID
        // The fields parameter should contain many default attributes (no "users(...)" wrapper)
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users/getuid001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"getuid001\",\n" +
                                "  \"etag\": \"\\\"getuid001-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"getuid@example.com\",\n" +
                                "  \"name\": {\n" +
                                "    \"givenName\": \"GetUid\",\n" +
                                "    \"familyName\": \"User\"\n" +
                                "  },\n" +
                                "  \"suspended\": false,\n" +
                                "  \"orgUnitPath\": \"/\",\n" +
                                "  \"emails\": []\n" +
                                "}")));

        // Setup mocks for __PHOTO__ API call
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users/getuid001/photos/thumbnail"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"getuid001\",\n" +
                                "  \"primaryEmail\": \"getuid@example.com\",\n" +
                                "  \"photoData\": \"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==\"\n" +
                                "}")));

        // Setup mocks for __GROUPS__ API call
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("userKey", equalTo("getuid001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [\n" +
                                "    {\n" +
                                "      \"id\": \"group001\",\n" +
                                "      \"email\": \"testgroup@example.com\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Test with both RETURN_DEFAULT_ATTRIBUTES = true and explicit attributesToGet
        OperationOptions options = new OperationOptionsBuilder()
                .setReturnDefaultAttributes(true)
                .setAttributesToGet("__PHOTO__", "__GROUPS__")  // non-default attributes
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute get by UID with combined attributes
        Filter filter = FilterBuilder.equalTo(new Uid("getuid001"));
        connectorFacade.search(ObjectClass.ACCOUNT, filter, results::add, options);

        // Verify results contain both default and explicit attributes
        assertThat(results).hasSize(1);
        ConnectorObject user = results.get(0);
        assertThat(user.getUid().getUidValue()).isEqualTo("getuid001");

        // Non-default attributes that were explicitly requested
        Attribute photo = user.getAttributeByName("__PHOTO__");
        assertThat(photo).isNotNull();
        assertThat(photo.getValue()).isNotEmpty(); // Should contain actual photo data

        Attribute groups = user.getAttributeByName("__GROUPS__");
        assertThat(groups).isNotNull();
        assertThat(groups.getValue()).isNotEmpty(); // Should contain actual groups data

        // With RETURN_DEFAULT_ATTRIBUTES=true, default attributes should also be returned
        assertThat(user.getAttributeByName("givenName")).isNotNull();
        assertThat(user.getAttributeByName("familyName")).isNotNull();
        assertThat(user.getAttributeByName("emails")).isNotNull();

        // Verify request used GET /admin/directory/v1/users/{id} endpoint
        // Get the actual request to verify fields parameter format
        List<LoggedRequest> userRequests = GoogleApiMockServer.getAllRequests().stream()
                .filter(request -> request.getUrl().startsWith("/admin/directory/v1/users/getuid001?"))
                .collect(Collectors.toList());
        assertThat(userRequests).hasSize(1);

        String fieldsParam = userRequests.get(0).queryParameter("fields").firstValue();
        assertThat(fieldsParam).isNotNull();

        // For single user get, fields parameter should NOT have "nextPageToken,users(...)" wrapper
        // It should be direct field list: "field1,field2,..."
        assertThat(fieldsParam).doesNotContain("nextPageToken");
        assertThat(fieldsParam).doesNotContain("users(");

        // Split by comma and convert to Set for order-independent comparison
        Set<String> actualFields = Set.of(fieldsParam.split(","));

        // Verify all default attributes are included (excludes password, __PHOTO__, __GROUPS__)
        Set<String> expectedDefaultFields = Set.of(
                // Mandatory connector fields
                "id",
                "etag",
                "primaryEmail",

                // Name fields
                "name/givenName",
                "name/familyName",
                "name/fullName",

                // Core fields
                "suspended",
                "orgUnitPath",
                "emails",
                "phones",
                "addresses",
                "organizations",

                // Additional fields
                "ims",
                "externalIds",
                "relations",
                "aliases",
                "locations",
                "nonEditableAliases",

                // Settings fields
                "changePasswordAtNextLogin",
                "ipWhitelisted",
                "includeInGlobalAddressList",

                // Admin/time fields
                "isAdmin",
                "isDelegatedAdmin",
                "lastLoginTime",
                "creationTime",
                "deletionTime",

                // Additional default fields
                "customerId",
                "isMailboxSetup",
                "thumbnailPhotoUrl",
                "suspensionReason",
                "agreedToTerms"
        );

        // Verify exact field count and contents
        assertThat(actualFields).containsExactlyInAnyOrderElementsOf(expectedDefaultFields);

        // Verify that __PHOTO__ and __GROUPS__ were requested via separate APIs
        List<LoggedRequest> photoRequests = GoogleApiMockServer.getAllRequests().stream()
                .filter(request -> request.getUrl().contains("/photos/thumbnail"))
                .collect(Collectors.toList());
        assertThat(photoRequests).hasSize(1);
        assertThat(photoRequests.get(0).getUrl()).contains("/admin/directory/v1/users/getuid001/photos/thumbnail");

        List<LoggedRequest> groupRequests = GoogleApiMockServer.getAllRequests().stream()
                .filter(request -> request.getUrl().contains("/groups") && request.queryParameter("userKey").isPresent())
                .collect(Collectors.toList());
        assertThat(groupRequests).hasSize(1);
        assertThat(groupRequests.get(0).queryParameter("userKey").firstValue()).isEqualTo("getuid001");
    }

    @Test
    @DisplayName("Test get by Name with RETURN_DEFAULT_ATTRIBUTES = true and explicit attributesToGet")
    public void testGetByNameWithReturnDefaultAttributesTrueAndAttributesToGet() {
        // This test verifies that when retrieving a single user by Name (email) with both
        // RETURN_DEFAULT_ATTRIBUTES=true and explicit attributesToGet specified,
        // the fields parameter includes all default attributes plus the explicitly requested ones.

        // Setup mock response for single user get by Name (email)
        // The fields parameter should contain many default attributes (no "users(...)" wrapper)
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users/getname@example.com"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"getname001\",\n" +
                                "  \"etag\": \"\\\"getname001-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"getname@example.com\",\n" +
                                "  \"name\": {\n" +
                                "    \"givenName\": \"GetName\",\n" +
                                "    \"familyName\": \"User\"\n" +
                                "  },\n" +
                                "  \"suspended\": false,\n" +
                                "  \"orgUnitPath\": \"/\",\n" +
                                "  \"emails\": []\n" +
                                "}")));

        // Setup mocks for __PHOTO__ API call
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users/getname001/photos/thumbnail"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"getname001\",\n" +
                                "  \"primaryEmail\": \"getname@example.com\",\n" +
                                "  \"photoData\": \"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==\"\n" +
                                "}")));

        // Setup mocks for __GROUPS__ API call  
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("userKey", equalTo("getname001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [\n" +
                                "    {\n" +
                                "      \"id\": \"group002\",\n" +
                                "      \"email\": \"testgroup2@example.com\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Test with both RETURN_DEFAULT_ATTRIBUTES = true and explicit attributesToGet
        OperationOptions options = new OperationOptionsBuilder()
                .setReturnDefaultAttributes(true)
                .setAttributesToGet("__PHOTO__", "__GROUPS__")  // non-default attributes
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute get by Name with combined attributes
        Filter filter = FilterBuilder.equalTo(new Name("getname@example.com"));
        connectorFacade.search(ObjectClass.ACCOUNT, filter, results::add, options);

        // Verify results contain both default and explicit attributes
        assertThat(results).hasSize(1);
        ConnectorObject user = results.get(0);
        assertThat(user.getUid().getUidValue()).isEqualTo("getname001");
        assertThat(user.getName().getNameValue()).isEqualTo("getname@example.com");

        // Non-default attributes that were explicitly requested
        Attribute photo = user.getAttributeByName("__PHOTO__");
        assertThat(photo).isNotNull();
        assertThat(photo.getValue()).isNotEmpty(); // Should contain actual photo data

        Attribute groups = user.getAttributeByName("__GROUPS__");
        assertThat(groups).isNotNull();
        assertThat(groups.getValue()).isNotEmpty(); // Should contain actual groups data

        // With RETURN_DEFAULT_ATTRIBUTES=true, default attributes should also be returned
        assertThat(user.getAttributeByName("givenName")).isNotNull();
        assertThat(user.getAttributeByName("familyName")).isNotNull();
        assertThat(user.getAttributeByName("emails")).isNotNull();

        // Verify request used GET /admin/directory/v1/users/{email} endpoint
        // Get the actual request to verify fields parameter format
        List<LoggedRequest> userRequests = GoogleApiMockServer.getAllRequests().stream()
                .filter(request -> request.getUrl().startsWith("/admin/directory/v1/users/getname@example.com?"))
                .collect(Collectors.toList());
        assertThat(userRequests).hasSize(1);

        String fieldsParam = userRequests.get(0).queryParameter("fields").firstValue();
        assertThat(fieldsParam).isNotNull();

        // For single user get, fields parameter should NOT have "nextPageToken,users(...)" wrapper
        // It should be direct field list: "field1,field2,..."
        assertThat(fieldsParam).doesNotContain("nextPageToken");
        assertThat(fieldsParam).doesNotContain("users(");

        // Split by comma and convert to Set for order-independent comparison
        Set<String> actualFields = Set.of(fieldsParam.split(","));

        // Verify all default attributes are included (excludes password, __PHOTO__, __GROUPS__)
        Set<String> expectedDefaultFields = Set.of(
                // Mandatory connector fields
                "id",
                "etag",
                "primaryEmail",

                // Name fields
                "name/givenName",
                "name/familyName",
                "name/fullName",

                // Core fields
                "suspended",
                "orgUnitPath",
                "emails",
                "phones",
                "addresses",
                "organizations",

                // Additional fields
                "ims",
                "externalIds",
                "relations",
                "aliases",
                "locations",
                "nonEditableAliases",

                // Settings fields
                "changePasswordAtNextLogin",
                "ipWhitelisted",
                "includeInGlobalAddressList",

                // Admin/time fields
                "isAdmin",
                "isDelegatedAdmin",
                "lastLoginTime",
                "creationTime",
                "deletionTime",

                // Additional default fields
                "customerId",
                "isMailboxSetup",
                "thumbnailPhotoUrl",
                "suspensionReason",
                "agreedToTerms"
        );

        // Verify exact field count and contents
        assertThat(actualFields).containsExactlyInAnyOrderElementsOf(expectedDefaultFields);

        // Verify that __PHOTO__ and __GROUPS__ were requested via separate APIs
        List<LoggedRequest> photoRequests = GoogleApiMockServer.getAllRequests().stream()
                .filter(request -> request.getUrl().contains("/photos/thumbnail"))
                .collect(Collectors.toList());
        assertThat(photoRequests).hasSize(1);
        assertThat(photoRequests.get(0).getUrl()).contains("/admin/directory/v1/users/getname001/photos/thumbnail");

        List<LoggedRequest> groupRequests = GoogleApiMockServer.getAllRequests().stream()
                .filter(request -> request.getUrl().contains("/groups") && request.queryParameter("userKey").isPresent())
                .collect(Collectors.toList());
        assertThat(groupRequests).hasSize(1);
        assertThat(groupRequests.get(0).queryParameter("userKey").firstValue()).isEqualTo("getname001");
    }
}