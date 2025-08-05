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
 * Tests for GoogleAppsConnector User search with specific attributesToGet parameters.
 * <p>
 * This test class focuses on verifying that when specific attributes are requested
 * via the attributesToGet option, the connector correctly includes them plus mandatory
 * fields (id, etag, primaryEmail) in the Google API fields parameter.
 *
 * @author Hiroyuki Wada
 */
@DisplayName("GoogleApps User Search - AttributesToGet Tests")
class GoogleAppsUserSearchAttributesToGetTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test search with specific attributesToGet")
    public void testSearchWithAttributesToGet() {
        // This test verifies that when specific attributes are requested,
        // the connector includes them plus mandatory fields (id, etag, primaryEmail)
        // in the Google API fields parameter for ConnectorObject construction.

        // Setup mock response with specific givenName and familyName fields
        // Note: id, etag, primaryEmail are always included by connector for ConnectorObject construction
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [\n" +
                                "    {\n" +
                                "      \"id\": \"fields001\",\n" +
                                "      \"etag\": \"\\\"fields001-etag\\\"\",\n" +
                                "      \"primaryEmail\": \"fields@example.com\",\n" +
                                "      \"name\": {\n" +
                                "        \"givenName\": \"Fields\",\n" +
                                "        \"familyName\": \"Test\"\n" +
                                "      }\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Prepare operation options with specific attributes to get
        OperationOptions options = new OperationOptionsBuilder()
                .setAttributesToGet("givenName", "familyName")
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with specific attributesToGet
        connectorFacade.search(ObjectClass.ACCOUNT, null, results::add, options);

        // Verify results contain expected data
        assertThat(results).hasSize(1);
        ConnectorObject user = results.get(0);
        assertThat(user.getUid().getUidValue()).isEqualTo("fields001");
        assertThat(user.getName().getNameValue()).isEqualTo("fields@example.com");

        // Verify specified attributes are present
        Attribute givenName = user.getAttributeByName("givenName");
        assertThat(givenName).isNotNull();
        assertThat(AttributeUtil.getStringValue(givenName)).isEqualTo("Fields");

        Attribute familyName = user.getAttributeByName("familyName");
        assertThat(familyName).isNotNull();
        assertThat(AttributeUtil.getStringValue(familyName)).isEqualTo("Test");

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

        // When specific attributesToGet are requested, the connector should include:
        // - The requested attributes (transformed to Google API field names)
        // - Mandatory fields (id, etag, primaryEmail)
        // - NOT default attributes (since setReturnDefaultAttributes is not explicitly true)
        Set<String> expectedFields = Set.of(
                // Mandatory connector fields
                "id",
                "etag",
                "primaryEmail",
                // Requested attributes (transformed to Google API field names)
                "name/givenName",
                "name/familyName"
        );

        assertThat(actualFields).containsExactlyInAnyOrderElementsOf(expectedFields);
    }

    @Test
    @DisplayName("Test search with comprehensive attributesToGet - all available attributes")
    public void testSearchWithComprehensiveAttributes() {
        // This test verifies that ALL available attributes can be properly requested via attributesToGet,
        // correctly mapped to Google API fields, and properly converted to ConnectorObject attributes.
        // This is the most comprehensive test covering the complete attribute mapping functionality.

        // Setup comprehensive mock response with ALL available attributes
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [\n" +
                                "    {\n" +
                                "      \"id\": \"comprehensive001\",\n" +
                                "      \"etag\": \"\\\"comprehensive001-etag\\\"\",\n" +
                                "      \"primaryEmail\": \"comprehensive@example.com\",\n" +
                                "      \"name\": {\n" +
                                "        \"givenName\": \"Comprehensive\",\n" +
                                "        \"familyName\": \"User\",\n" +
                                "        \"fullName\": \"Comprehensive User\"\n" +
                                "      },\n" +
                                "      \"suspended\": false,\n" +
                                "      \"changePasswordAtNextLogin\": false,\n" +
                                "      \"ipWhitelisted\": true,\n" +
                                "      \"orgUnitPath\": \"/Engineering\",\n" +
                                "      \"includeInGlobalAddressList\": true,\n" +
                                "      \"isAdmin\": false,\n" +
                                "      \"isDelegatedAdmin\": false,\n" +
                                "      \"customerId\": \"C01example\",\n" +
                                "      \"isMailboxSetup\": true,\n" +
                                "      \"lastLoginTime\": \"2024-01-15T10:30:00.000Z\",\n" +
                                "      \"creationTime\": \"2024-01-01T00:00:00.000Z\",\n" +
                                "      \"deletionTime\": null,\n" +
                                "      \"agreedToTerms\": true,\n" +
                                "      \"emails\": [\n" +
                                "        {\n" +
                                "          \"address\": \"alt@example.com\",\n" +
                                "          \"type\": \"work\",\n" +
                                "          \"primary\": false\n" +
                                "        }\n" +
                                "      ],\n" +
                                "      \"phones\": [\n" +
                                "        {\n" +
                                "          \"value\": \"+1-555-123-4567\",\n" +
                                "          \"type\": \"work\"\n" +
                                "        }\n" +
                                "      ],\n" +
                                "      \"addresses\": [\n" +
                                "        {\n" +
                                "          \"locality\": \"Mountain View\",\n" +
                                "          \"region\": \"CA\",\n" +
                                "          \"country\": \"US\"\n" +
                                "        }\n" +
                                "      ],\n" +
                                "      \"organizations\": [\n" +
                                "        {\n" +
                                "          \"name\": \"Example Corp\",\n" +
                                "          \"department\": \"Engineering\",\n" +
                                "          \"title\": \"Software Engineer\"\n" +
                                "        }\n" +
                                "      ],\n" +
                                "      \"ims\": [\n" +
                                "        {\n" +
                                "          \"im\": \"user@example.org\",\n" +
                                "          \"protocol\": \"gtalk\"\n" +
                                "        }\n" +
                                "      ],\n" +
                                "      \"externalIds\": [\n" +
                                "        {\n" +
                                "          \"value\": \"EMP001\",\n" +
                                "          \"type\": \"employee\"\n" +
                                "        }\n" +
                                "      ],\n" +
                                "      \"relations\": [\n" +
                                "        {\n" +
                                "          \"value\": \"manager@example.com\",\n" +
                                "          \"type\": \"manager\"\n" +
                                "        }\n" +
                                "      ],\n" +
                                "      \"locations\": [\n" +
                                "        {\n" +
                                "          \"buildingId\": \"Building-A\",\n" +
                                "          \"type\": \"desk\"\n" +
                                "        }\n" +
                                "      ],\n" +
                                "      \"aliases\": [\"alias1@example.com\", \"alias2@example.com\"],\n" +
                                "      \"nonEditableAliases\": [\"auto@example.com\"],\n" +
                                "      \"thumbnailPhotoUrl\": \"https://example.com/photo.jpg\",\n" +
                                "      \"suspensionReason\": null\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Request ALL available attributes (based on actual schema definition)
        OperationOptions options = new OperationOptionsBuilder()
                .setAttributesToGet(
                        // Name fields
                        "givenName",
                        "familyName",
                        "fullName",
                        // Core fields
                        "__ENABLE__",
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
                )
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with ALL attributes
        connectorFacade.search(ObjectClass.ACCOUNT, null, results::add, options);

        // Verify results contain ALL requested attributes
        assertThat(results).hasSize(1);
        ConnectorObject user = results.get(0);
        assertThat(user.getUid().getUidValue()).isEqualTo("comprehensive001");
        assertThat(user.getName().getNameValue()).isEqualTo("comprehensive@example.com");

        // Verify ALL basic attributes with proper mapping
        assertThat(AttributeUtil.getStringValue(user.getAttributeByName("givenName"))).isEqualTo("Comprehensive");
        assertThat(AttributeUtil.getStringValue(user.getAttributeByName("familyName"))).isEqualTo("User");
        assertThat(AttributeUtil.getStringValue(user.getAttributeByName("fullName"))).isEqualTo("Comprehensive User");
        assertThat(AttributeUtil.getBooleanValue(user.getAttributeByName("__ENABLE__"))).isTrue(); // !suspended
        assertThat(AttributeUtil.getBooleanValue(user.getAttributeByName("suspended"))).isFalse();

        // Verify ALL administrative attributes
        assertThat(AttributeUtil.getBooleanValue(user.getAttributeByName("changePasswordAtNextLogin"))).isFalse();
        assertThat(AttributeUtil.getBooleanValue(user.getAttributeByName("ipWhitelisted"))).isTrue();
        assertThat(AttributeUtil.getStringValue(user.getAttributeByName("orgUnitPath"))).isEqualTo("/Engineering");
        assertThat(AttributeUtil.getBooleanValue(user.getAttributeByName("includeInGlobalAddressList"))).isTrue();
        assertThat(AttributeUtil.getBooleanValue(user.getAttributeByName("isAdmin"))).isFalse();
        assertThat(AttributeUtil.getBooleanValue(user.getAttributeByName("isDelegatedAdmin"))).isFalse();

        // Verify ALL system attributes
        assertThat(AttributeUtil.getStringValue(user.getAttributeByName("customerId"))).isEqualTo("C01example");
        assertThat(AttributeUtil.getBooleanValue(user.getAttributeByName("isMailboxSetup"))).isTrue();
        assertThat(user.getAttributeByName("lastLoginTime")).isNotNull();
        assertThat(user.getAttributeByName("creationTime")).isNotNull();
        assertThat(AttributeUtil.getBooleanValue(user.getAttributeByName("agreedToTerms"))).isTrue();

        // Verify ALL multi-valued attributes are present and non-empty
        assertThat(user.getAttributeByName("emails").getValue()).isNotEmpty();
        assertThat(user.getAttributeByName("phones").getValue()).isNotEmpty();
        assertThat(user.getAttributeByName("addresses").getValue()).isNotEmpty();
        assertThat(user.getAttributeByName("organizations").getValue()).isNotEmpty();
        assertThat(user.getAttributeByName("ims").getValue()).isNotEmpty();
        assertThat(user.getAttributeByName("externalIds").getValue()).isNotEmpty();
        assertThat(user.getAttributeByName("relations").getValue()).isNotEmpty();
        assertThat(user.getAttributeByName("locations").getValue()).isNotEmpty();

        // Verify alias attributes
        assertThat(user.getAttributeByName("aliases").getValue()).isNotEmpty();
        assertThat(user.getAttributeByName("nonEditableAliases").getValue()).isNotEmpty();

        // Verify media attributes
        assertThat(AttributeUtil.getStringValue(user.getAttributeByName("thumbnailPhotoUrl"))).isEqualTo("https://example.com/photo.jpg");

        // Verify comprehensive field mapping in API request
        List<LoggedRequest> requests = GoogleApiMockServer.getAllRequests().stream()
                .filter(request -> request.getUrl().startsWith("/admin/directory/v1/users?"))
                .collect(Collectors.toList());
        assertThat(requests).hasSize(1);
        String fieldsParam = requests.get(0).queryParameter("fields").firstValue();
        assertThat(fieldsParam).isNotNull();

        // Extract and verify ALL expected fields
        String fieldsContent = fieldsParam.replaceFirst("nextPageToken,users\\(", "").replaceFirst("\\)$", "");
        Set<String> actualFields = Set.of(fieldsContent.split(","));

        // Verify ALL requested attributes are mapped to correct Google API field names
        Set<String> expectedFields = Set.of(
                // Mandatory connector fields
                "id",
                "etag",
                "primaryEmail",
                // Name fields (with transformation)
                "name/givenName",
                "name/familyName",
                "name/fullName",
                // Core fields (direct mapping)
                "suspended",
                "orgUnitPath",
                "emails",
                "phones",
                "addresses",
                "organizations",
                // Additional fields (direct mapping)
                "ims",
                "externalIds",
                "relations",
                "aliases",
                "locations",
                "nonEditableAliases",
                // Settings fields (direct mapping)
                "changePasswordAtNextLogin",
                "ipWhitelisted",
                "includeInGlobalAddressList",
                // Admin/time fields (direct mapping)
                "isAdmin",
                "isDelegatedAdmin",
                "lastLoginTime",
                "creationTime",
                "deletionTime",
                // Additional default fields (direct mapping)
                "customerId",
                "isMailboxSetup",
                "thumbnailPhotoUrl",
                "suspensionReason",
                "agreedToTerms"
        );

        assertThat(actualFields).containsExactlyInAnyOrderElementsOf(expectedFields);
    }
}