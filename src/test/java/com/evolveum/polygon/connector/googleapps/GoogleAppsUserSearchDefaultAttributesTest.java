package com.evolveum.polygon.connector.googleapps;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.identityconnectors.framework.common.objects.*;
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
 * Tests for GoogleAppsConnector User search with RETURN_DEFAULT_ATTRIBUTES settings.
 * <p>
 * This test class focuses on verifying the behavior of the connector when
 * RETURN_DEFAULT_ATTRIBUTES is set to true or false, and how it affects
 * the fields parameter sent to Google API.
 *
 * @author Hiroyuki Wada
 */
@DisplayName("GoogleApps User Search - Default Attributes Tests")
class GoogleAppsUserSearchDefaultAttributesTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test search with RETURN_DEFAULT_ATTRIBUTES = false")
    public void testSearchWithReturnDefaultAttributesFalse() {
        // This test verifies that with RETURN_DEFAULT_ATTRIBUTES=false,
        // only explicitly requested attributes plus mandatory fields are included.
        // Mandatory fields (id, etag, primaryEmail) are always included by this connector for ConnectorObject construction.

        // Setup mock response for minimal fields only
        // Even with RETURN_DEFAULT_ATTRIBUTES=false, connector always includes id, etag, primaryEmail for ConnectorObject construction
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [\n" +
                                "    {\n" +
                                "      \"id\": \"minimal001\",\n" +
                                "      \"etag\": \"\\\"minimal001-etag\\\"\",\n" +
                                "      \"primaryEmail\": \"minimal@example.com\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Prepare operation options with RETURN_DEFAULT_ATTRIBUTES = false
        OperationOptions options = new OperationOptionsBuilder()
                .setReturnDefaultAttributes(false)
                .setAttributesToGet(Uid.NAME, Name.NAME) // Only request UID and Name
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with minimal attributes
        connectorFacade.search(ObjectClass.ACCOUNT, null, results::add, options);

        // Verify results contain minimal data
        assertThat(results).hasSize(1);
        ConnectorObject user = results.get(0);
        assertThat(user.getUid().getUidValue()).isEqualTo("minimal001");
        assertThat(user.getName().getNameValue()).isEqualTo("minimal@example.com");

        // Verify default attributes are not included (e.g., givenName, familyName)
        Attribute givenName = user.getAttributeByName("givenName");
        assertThat(givenName).isNull();

        Attribute familyName = user.getAttributeByName("familyName");
        assertThat(familyName).isNull();

        // Verify request was sent with expected fields parameter
        List<LoggedRequest> requests = GoogleApiMockServer.getAllRequests().stream()
                .filter(request -> request.getUrl().startsWith("/admin/directory/v1/users?"))
                .collect(Collectors.toList());
        assertThat(requests).hasSize(1);
        String fieldsParam = requests.get(0).queryParameter("fields").firstValue();
        assertThat(fieldsParam).isNotNull();

        // Verify fields parameter structure: should be "nextPageToken,users(...)"
        assertThat(fieldsParam).matches("^nextPageToken,users\\(.+\\)$");

        // Extract just the users field list by removing the outer structure
        String fieldsContent = fieldsParam.replaceFirst("nextPageToken,users\\(", "").replaceFirst("\\)$", "");

        // Split by comma and convert to Set for order-independent comparison
        Set<String> actualFields = Set.of(fieldsContent.split(","));

        // With RETURN_DEFAULT_ATTRIBUTES=false and explicit attributesToGet(Uid.NAME, Name.NAME),
        // only mandatory fields should be included (no default attributes)
        Set<String> expectedFields = Set.of(
                // Mandatory connector fields only
                "id",
                "etag",
                "primaryEmail"
        );

        assertThat(actualFields).containsExactlyInAnyOrderElementsOf(expectedFields);
    }

    @Test
    @DisplayName("Test search with RETURN_DEFAULT_ATTRIBUTES = true")
    public void testSearchWithReturnDefaultAttributesTrue() {
        // This test verifies that when RETURN_DEFAULT_ATTRIBUTES=true,
        // all default attributes are included in the fields parameter

        // Setup mock response for default attributes test
        // Should include many default attributes in fields parameter
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [\n" +
                                "    {\n" +
                                "      \"id\": \"default001\",\n" +
                                "      \"etag\": \"\\\"default001-etag\\\"\",\n" +
                                "      \"primaryEmail\": \"default@example.com\",\n" +
                                "      \"name\": {\n" +
                                "        \"givenName\": \"Default\",\n" +
                                "        \"familyName\": \"User\"\n" +
                                "      },\n" +
                                "      \"suspended\": false,\n" +
                                "      \"orgUnitPath\": \"/\",\n" +
                                "      \"emails\": [],\n" +
                                "      \"phones\": [],\n" +
                                "      \"addresses\": [],\n" +
                                "      \"organizations\": [],\n" +
                                "      \"creationTime\": \"2024-01-01T00:00:00.000Z\",\n" +
                                "      \"lastLoginTime\": \"2024-01-02T00:00:00.000Z\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Test with RETURN_DEFAULT_ATTRIBUTES = true (no explicit attributesToGet)
        OperationOptions options = new OperationOptionsBuilder()
                .setReturnDefaultAttributes(true)
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with default attributes
        connectorFacade.search(ObjectClass.ACCOUNT, null, results::add, options);

        // Verify results contain default attributes
        assertThat(results).hasSize(1);
        ConnectorObject user = results.get(0);
        assertThat(user.getUid().getUidValue()).isEqualTo("default001");

        // Verify some key default attributes are present
        assertThat(user.getAttributeByName("givenName")).isNotNull();
        assertThat(user.getAttributeByName("familyName")).isNotNull();
        assertThat(user.getAttributeByName("__ENABLE__")).isNotNull();
        assertThat(user.getAttributeByName("orgUnitPath")).isNotNull();

        // Verify request included default attributes in fields parameter
        // Get the actual request to verify fields parameter in detail
        List<LoggedRequest> requests = GoogleApiMockServer.getAllRequests().stream()
                .filter(request -> request.getUrl().startsWith("/admin/directory/v1/users?"))
                .collect(Collectors.toList());
        assertThat(requests).hasSize(1);
        String fieldsParam = requests.get(0).queryParameter("fields").firstValue();
        assertThat(fieldsParam).isNotNull();

        // Verify fields parameter structure: should be "nextPageToken,users(...)"
        assertThat(fieldsParam).matches("^nextPageToken,users\\(.+\\)$");

        // Extract just the users field list by removing the outer structure
        String fieldsContent = fieldsParam.replaceFirst("nextPageToken,users\\(", "").replaceFirst("\\)$", "");

        // Split by comma and convert to Set for order-independent comparison
        Set<String> actualFields = Set.of(fieldsContent.split(","));

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
    }

    @Test
    @DisplayName("Test search with RETURN_DEFAULT_ATTRIBUTES = true and explicit attributesToGet")
    public void testSearchWithReturnDefaultAttributesTrueAndAttributesToGet() {
        // This test verifies that when both RETURN_DEFAULT_ATTRIBUTES=true and
        // explicit attributesToGet are specified, both default and explicit attributes
        // are included in the fields parameter

        // Setup mock response for combined default + explicit attributes test
        // The fields parameter should contain many default attributes
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [\n" +
                                "    {\n" +
                                "      \"id\": \"combined001\",\n" +
                                "      \"etag\": \"\\\"combined001-etag\\\"\",\n" +
                                "      \"primaryEmail\": \"combined@example.com\",\n" +
                                "      \"name\": {\n" +
                                "        \"givenName\": \"Combined\",\n" +
                                "        \"familyName\": \"User\"\n" +
                                "      },\n" +
                                "      \"suspended\": false,\n" +
                                "      \"orgUnitPath\": \"/\",\n" +
                                "      \"emails\": []\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Setup mocks for __PHOTO__ API call
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users/combined001/photos/thumbnail"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"combined001\",\n" +
                                "  \"primaryEmail\": \"combined@example.com\",\n" +
                                "  \"photoData\": \"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==\"\n" +
                                "}")));

        // Setup mocks for __GROUPS__ API call
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("userKey", equalTo("combined001"))
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

        // Execute search with combined attributes
        connectorFacade.search(ObjectClass.ACCOUNT, null, results::add, options);

        // Verify results contain both default and explicit attributes
        assertThat(results).hasSize(1);
        ConnectorObject user = results.get(0);
        assertThat(user.getUid().getUidValue()).isEqualTo("combined001");

        // Verify that both default and non-default attributes are returned
        // (RETURN_DEFAULT_ATTRIBUTES=true should include default attributes + explicit attributesToGet)

        // Non-default attributes that were explicitly requested
        Attribute photo = user.getAttributeByName("__PHOTO__");
        assertThat(photo).isNotNull();
        assertThat(photo.getValue()).isNotEmpty(); // Should contain actual photo data

        Attribute groups = user.getAttributeByName("__GROUPS__");
        assertThat(groups).isNotNull();
        assertThat(groups.getValue()).isNotEmpty(); // Should contain actual groups data

        // With RETURN_DEFAULT_ATTRIBUTES=true, both default and explicit attributes should be returned
        // This tests the actual ConnID framework behavior
        assertThat(user.getAttributeByName("givenName")).isNotNull();
        assertThat(user.getAttributeByName("familyName")).isNotNull();
        assertThat(user.getAttributeByName("emails")).isNotNull();

        // Verify request included both default and explicit attributes in fields parameter
        // When RETURN_DEFAULT_ATTRIBUTES=true, fields should contain all default attributes
        // Get the actual request to verify fields parameter in detail
        List<LoggedRequest> requests = GoogleApiMockServer.getAllRequests().stream()
                .filter(request -> request.getUrl().startsWith("/admin/directory/v1/users?"))  // Only list requests, not photo requests
                .collect(Collectors.toList());
        assertThat(requests).hasSize(1);
        String fieldsParam = requests.get(0).queryParameter("fields").firstValue();
        assertThat(fieldsParam).isNotNull();

        // Verify fields parameter structure: should be "nextPageToken,users(...)"
        assertThat(fieldsParam).matches("^nextPageToken,users\\(.+\\)$");

        // Extract just the users field list by removing the outer structure
        String fieldsContent = fieldsParam.replaceFirst("nextPageToken,users\\(", "").replaceFirst("\\)$", "");

        // Split by comma and convert to Set for order-independent comparison
        Set<String> actualFields = Set.of(fieldsContent.split(","));

        // Verify all default attributes are included (excludes password, but __PHOTO__ and __GROUPS__ use separate APIs)
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

        // Verify exact field count and contents (should include all default fields)
        assertThat(actualFields).containsExactlyInAnyOrderElementsOf(expectedDefaultFields);

        // Verify that separate API calls were made for __PHOTO__ and __GROUPS__
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users/combined001/photos/thumbnail")));
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathMatching("/admin/directory/v1/groups")).withQueryParam("userKey", equalTo("combined001")));
    }
}