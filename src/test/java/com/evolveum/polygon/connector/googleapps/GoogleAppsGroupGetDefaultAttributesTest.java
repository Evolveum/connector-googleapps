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
 * Tests for GoogleAppsConnector Group Get operations with RETURN_DEFAULT_ATTRIBUTES settings.
 * <p>
 * This class tests single group retrieval by UID and Name with RETURN_DEFAULT_ATTRIBUTES
 * and attributesToGet combinations, which uses different field parameter formats
 * compared to search operations:
 * - UID: GET /admin/directory/v1/groups/{id} with fields: "field1,field2,..."
 * - Name: GET /admin/directory/v1/groups/{email} with fields: "field1,field2,..."
 * <p>
 * Compare with GoogleAppsGroupSearchDefaultAttributesTest which tests list operations:
 * - Search: GET /admin/directory/v1/groups with fields: "nextPageToken,groups(field1,field2,...)"
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsGroupGetDefaultAttributesTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test get by UID with RETURN_DEFAULT_ATTRIBUTES = true and explicit attributesToGet")
    public void testGetByUidWithReturnDefaultAttributesTrueAndAttributesToGet() {
        // This test verifies that when retrieving a single group by UID with both
        // RETURN_DEFAULT_ATTRIBUTES=true and explicit attributesToGet specified,
        // the fields parameter includes all default attributes plus the explicitly requested ones.

        // Setup mock response for single group get by UID
        // The fields parameter should contain many default attributes (no "groups(...)" wrapper)
        stubFor(get(urlPathEqualTo("/admin/directory/v1/groups/getuid001"))
                .withQueryParam("fields", matching(".*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"getuid001\",\n" +
                                "  \"etag\": \"\\\"getuid001-etag\\\"\",\n" +
                                "  \"email\": \"getuid@example.com\",\n" +
                                "  \"name\": \"GetUid Group\",\n" +
                                "  \"description\": \"GetUid Description\",\n" +
                                "  \"adminCreated\": true,\n" +
                                "  \"aliases\": []\n" +
                                "}")));

        // Setup mocks for members API call (non-default attribute)
        stubFor(get(urlPathEqualTo("/admin/directory/v1/groups/getuid001/members"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"members\": [\n" +
                                "    {\n" +
                                "      \"id\": \"member001\",\n" +
                                "      \"email\": \"member001@example.com\",\n" +
                                "      \"role\": \"MEMBER\",\n" +
                                "      \"type\": \"USER\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Test with both RETURN_DEFAULT_ATTRIBUTES = true and explicit attributesToGet
        OperationOptions options = new OperationOptionsBuilder()
                .setReturnDefaultAttributes(true)
                .setAttributesToGet("__MEMBERS__")  // non-default attribute
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute get by UID with combined attributes
        Filter filter = FilterBuilder.equalTo(new Uid("getuid001"));
        connectorFacade.search(ObjectClass.GROUP, filter, results::add, options);

        // Verify results contain both default and explicit attributes
        assertThat(results).hasSize(1);
        ConnectorObject group = results.get(0);
        assertThat(group.getUid().getUidValue()).isEqualTo("getuid001");

        // Non-default attribute that was explicitly requested
        Attribute members = group.getAttributeByName("__MEMBERS__");
        assertThat(members).isNotNull();
        assertThat(members.getValue()).isNotEmpty(); // Should contain actual members data

        // With RETURN_DEFAULT_ATTRIBUTES=true, default attributes should also be returned
        assertThat(group.getAttributeByName("name")).isNotNull();
        assertThat(group.getAttributeByName("__DESCRIPTION__")).isNotNull();
        assertThat(group.getAttributeByName("aliases")).isNotNull();

        // Verify request used GET /admin/directory/v1/groups/{id} endpoint
        // Get the actual request to verify fields parameter format
        List<LoggedRequest> groupRequests = GoogleApiMockServer.getAllRequests().stream()
                .filter(request -> request.getUrl().startsWith("/admin/directory/v1/groups/getuid001?"))
                .collect(Collectors.toList());
        assertThat(groupRequests).hasSize(1);

        String fieldsParam = groupRequests.get(0).queryParameter("fields").firstValue();
        assertThat(fieldsParam).isNotNull();

        // For single group get, fields parameter should NOT have "nextPageToken,groups(...)" wrapper
        // It should be direct field list: "field1,field2,..."
        assertThat(fieldsParam).doesNotContain("nextPageToken");
        assertThat(fieldsParam).doesNotContain("groups(");

        // Split by comma and convert to Set for order-independent comparison
        Set<String> actualFields = Set.of(fieldsParam.split(","));

        // Verify all default attributes are included (excludes members which uses separate API)
        Set<String> expectedDefaultFields = Set.of(
                // Mandatory connector fields
                "id",
                "etag",
                "email",
                // Default Group attributes
                "name",
                "description",
                "adminCreated",
                "aliases",
                "nonEditableAliases",
                "directMembersCount"
        );

        // Verify exact field count and contents
        assertThat(actualFields).containsExactlyInAnyOrderElementsOf(expectedDefaultFields);

        // Verify that members was requested via separate API
        List<LoggedRequest> memberRequests = GoogleApiMockServer.getAllRequests().stream()
                .filter(request -> request.getUrl().contains("/members"))
                .collect(Collectors.toList());
        assertThat(memberRequests).hasSize(1);
        assertThat(memberRequests.get(0).getUrl()).contains("/admin/directory/v1/groups/getuid001/members");
    }

    @Test
    @DisplayName("Test get by Name with RETURN_DEFAULT_ATTRIBUTES = true and explicit attributesToGet")
    public void testGetByNameWithReturnDefaultAttributesTrueAndAttributesToGet() {
        // This test verifies that when retrieving a single group by Name (email) with both
        // RETURN_DEFAULT_ATTRIBUTES=true and explicit attributesToGet specified,
        // the fields parameter includes all default attributes plus the explicitly requested ones.

        // Setup mock response for single group get by Name (email)
        // The fields parameter should contain many default attributes (no "groups(...)" wrapper)
        stubFor(get(urlPathEqualTo("/admin/directory/v1/groups/getname@example.com"))
                .withQueryParam("fields", matching(".*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"getname001\",\n" +
                                "  \"etag\": \"\\\"getname001-etag\\\"\",\n" +
                                "  \"email\": \"getname@example.com\",\n" +
                                "  \"name\": \"GetName Group\",\n" +
                                "  \"description\": \"GetName Description\",\n" +
                                "  \"adminCreated\": true,\n" +
                                "  \"aliases\": []\n" +
                                "}")));

        // Setup mocks for members API call (non-default attribute)
        stubFor(get(urlPathEqualTo("/admin/directory/v1/groups/getname001/members"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"members\": [\n" +
                                "    {\n" +
                                "      \"id\": \"member002\",\n" +
                                "      \"email\": \"member002@example.com\",\n" +
                                "      \"role\": \"MEMBER\",\n" +
                                "      \"type\": \"USER\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Test with both RETURN_DEFAULT_ATTRIBUTES = true and explicit attributesToGet
        OperationOptions options = new OperationOptionsBuilder()
                .setReturnDefaultAttributes(true)
                .setAttributesToGet("__MEMBERS__")  // non-default attribute
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute get by Name with combined attributes
        Filter filter = FilterBuilder.equalTo(new Name("getname@example.com"));
        connectorFacade.search(ObjectClass.GROUP, filter, results::add, options);

        // Verify results contain both default and explicit attributes
        assertThat(results).hasSize(1);
        ConnectorObject group = results.get(0);
        assertThat(group.getUid().getUidValue()).isEqualTo("getname001");
        assertThat(group.getName().getNameValue()).isEqualTo("getname@example.com");

        // Non-default attribute that was explicitly requested
        Attribute members = group.getAttributeByName("__MEMBERS__");
        assertThat(members).isNotNull();
        assertThat(members.getValue()).isNotEmpty(); // Should contain actual members data

        // With RETURN_DEFAULT_ATTRIBUTES=true, default attributes should also be returned
        assertThat(group.getAttributeByName("name")).isNotNull();
        assertThat(group.getAttributeByName("__DESCRIPTION__")).isNotNull();
        assertThat(group.getAttributeByName("aliases")).isNotNull();

        // Verify request used GET /admin/directory/v1/groups/{email} endpoint
        // Get the actual request to verify fields parameter format
        List<LoggedRequest> groupRequests = GoogleApiMockServer.getAllRequests().stream()
                .filter(request -> request.getUrl().startsWith("/admin/directory/v1/groups/getname@example.com?"))
                .collect(Collectors.toList());
        assertThat(groupRequests).hasSize(1);

        String fieldsParam = groupRequests.get(0).queryParameter("fields").firstValue();
        assertThat(fieldsParam).isNotNull();

        // For single group get, fields parameter should NOT have "nextPageToken,groups(...)" wrapper
        // It should be direct field list: "field1,field2,..."
        assertThat(fieldsParam).doesNotContain("nextPageToken");
        assertThat(fieldsParam).doesNotContain("groups(");

        // Split by comma and convert to Set for order-independent comparison
        Set<String> actualFields = Set.of(fieldsParam.split(","));

        // Verify all default attributes are included (excludes members which uses separate API)
        Set<String> expectedDefaultFields = Set.of(
                // Mandatory connector fields
                "id",
                "etag",
                "email",
                // Default Group attributes
                "name",
                "description",
                "adminCreated",
                "aliases",
                "nonEditableAliases",
                "directMembersCount"
        );

        // Verify exact field count and contents
        assertThat(actualFields).containsExactlyInAnyOrderElementsOf(expectedDefaultFields);

        // Verify that members was requested via separate API
        List<LoggedRequest> memberRequests = GoogleApiMockServer.getAllRequests().stream()
                .filter(request -> request.getUrl().contains("/members"))
                .collect(Collectors.toList());
        assertThat(memberRequests).hasSize(1);
        assertThat(memberRequests.get(0).getUrl()).contains("/admin/directory/v1/groups/getname001/members");
    }
}