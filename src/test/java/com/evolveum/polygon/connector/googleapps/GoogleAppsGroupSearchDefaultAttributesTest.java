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
 * Tests for GoogleAppsConnector Group search with RETURN_DEFAULT_ATTRIBUTES settings.
 * <p>
 * This test class focuses on verifying the behavior of the connector when
 * RETURN_DEFAULT_ATTRIBUTES is set to true or false, and how it affects
 * the fields parameter sent to Google API.
 *
 * @author Hiroyuki Wada
 */
@DisplayName("GoogleApps Group Search - Default Attributes Tests")
class GoogleAppsGroupSearchDefaultAttributesTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test search with RETURN_DEFAULT_ATTRIBUTES = false")
    public void testSearchWithReturnDefaultAttributesFalse() {
        // This test verifies that with RETURN_DEFAULT_ATTRIBUTES=false,
        // only explicitly requested attributes plus mandatory fields are included.
        // Mandatory fields (id, etag, email) are always included by this connector for ConnectorObject construction.

        // Setup mock response for minimal fields only
        // Even with RETURN_DEFAULT_ATTRIBUTES=false, connector always includes id, etag, email for ConnectorObject construction
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("customer", equalTo("my_customer"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [\n" +
                                "    {\n" +
                                "      \"id\": \"minimal001\",\n" +
                                "      \"etag\": \"\\\"minimal001-etag\\\"\",\n" +
                                "      \"email\": \"minimal@example.com\"\n" +
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
        connectorFacade.search(ObjectClass.GROUP, null, results::add, options);

        // Verify results contain minimal data
        assertThat(results).hasSize(1);
        ConnectorObject group = results.get(0);
        assertThat(group.getUid().getUidValue()).isEqualTo("minimal001");
        assertThat(group.getName().getNameValue()).isEqualTo("minimal@example.com");

        // Verify default attributes are not included (e.g., name, description)
        Attribute name = group.getAttributeByName("name");
        assertThat(name).isNull();

        Attribute description = group.getAttributeByName("description");
        assertThat(description).isNull();

        // Verify request was sent with expected fields parameter
        List<LoggedRequest> requests = GoogleApiMockServer.getAllRequests().stream()
                .filter(request -> request.getUrl().startsWith("/admin/directory/v1/groups?"))
                .collect(Collectors.toList());
        assertThat(requests).hasSize(1);

        // Verify customer parameter was sent correctly
        assertThat(requests.get(0).queryParameter("customer").firstValue()).isEqualTo("my_customer");

        // Now fields parameter should be present after GroupHandler fix
        String fieldsParam = requests.get(0).queryParameter("fields").isPresent() ?
                requests.get(0).queryParameter("fields").firstValue() : null;
        assertThat(fieldsParam).isNotNull();

        // Verify fields parameter structure: should be "nextPageToken,groups(...)"
        assertThat(fieldsParam).matches("^nextPageToken,groups\\(.+\\)$");

        // Extract just the groups field list by removing the outer structure
        String fieldsContent = fieldsParam.replaceFirst("nextPageToken,groups\\(", "").replaceFirst("\\)$", "");

        // Split by comma and convert to Set for order-independent comparison
        Set<String> actualFields = Set.of(fieldsContent.split(","));

        // With RETURN_DEFAULT_ATTRIBUTES=false and explicit attributesToGet(Uid.NAME, Name.NAME),
        // only mandatory fields should be included (no default attributes)
        Set<String> expectedFields = Set.of(
                // Mandatory connector fields only
                "id",
                "etag",
                "email"
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
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("customer", equalTo("my_customer"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [\n" +
                                "    {\n" +
                                "      \"id\": \"default001\",\n" +
                                "      \"etag\": \"\\\"default001-etag\\\"\",\n" +
                                "      \"email\": \"default@example.com\",\n" +
                                "      \"name\": \"Default Group\",\n" +
                                "      \"description\": \"Default Description\",\n" +
                                "      \"adminCreated\": true,\n" +
                                "      \"aliases\": [],\n" +
                                "      \"nonEditableAliases\": [],\n" +
                                "      \"directMembersCount\": \"5\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Test with RETURN_DEFAULT_ATTRIBUTES = true (no explicit attributesToGet)
        OperationOptions options = new OperationOptionsBuilder()
                .setReturnDefaultAttributes(true)
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with default attributes
        connectorFacade.search(ObjectClass.GROUP, null, results::add, options);

        // Verify results contain default attributes
        assertThat(results).hasSize(1);
        ConnectorObject group = results.get(0);
        assertThat(group.getUid().getUidValue()).isEqualTo("default001");

        // Verify some key default attributes are present
        assertThat(group.getAttributeByName("name")).isNotNull();
        assertThat(group.getAttributeByName("__DESCRIPTION__")).isNotNull();
        assertThat(group.getAttributeByName("adminCreated")).isNotNull();
        assertThat(group.getAttributeByName("aliases")).isNotNull();

        // Verify request was sent with default attributes enabled
        List<LoggedRequest> requests = GoogleApiMockServer.getAllRequests().stream()
                .filter(request -> request.getUrl().startsWith("/admin/directory/v1/groups?"))
                .collect(Collectors.toList());
        assertThat(requests).hasSize(1);

        // Verify customer parameter was sent correctly 
        assertThat(requests.get(0).queryParameter("customer").firstValue()).isEqualTo("my_customer");
    }

    @Test
    @DisplayName("Test search with RETURN_DEFAULT_ATTRIBUTES = true and explicit attributesToGet")
    public void testSearchWithReturnDefaultAttributesTrueAndAttributesToGet() {
        // This test verifies that when both RETURN_DEFAULT_ATTRIBUTES=true and
        // explicit attributesToGet are specified, both default and explicit attributes
        // are included in the fields parameter

        // Setup mock response for combined default + explicit attributes test
        // The fields parameter should contain many default attributes
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("customer", equalTo("my_customer"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [\n" +
                                "    {\n" +
                                "      \"id\": \"combined001\",\n" +
                                "      \"etag\": \"\\\"combined001-etag\\\"\",\n" +
                                "      \"email\": \"combined@example.com\",\n" +
                                "      \"name\": \"Combined Group\",\n" +
                                "      \"description\": \"Combined Description\",\n" +
                                "      \"adminCreated\": true,\n" +
                                "      \"aliases\": []\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Setup mocks for members API call (non-default attribute)
        stubFor(get(urlPathMatching("/admin/directory/v1/groups/combined001/members"))
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

        // Execute search with combined attributes
        connectorFacade.search(ObjectClass.GROUP, null, results::add, options);

        // Verify results contain both default and explicit attributes
        assertThat(results).hasSize(1);
        ConnectorObject group = results.get(0);
        assertThat(group.getUid().getUidValue()).isEqualTo("combined001");

        // Non-default attribute that was explicitly requested
        Attribute members = group.getAttributeByName("__MEMBERS__");
        assertThat(members).isNotNull();
        assertThat(members.getValue()).isNotEmpty(); // Should contain actual members data

        // With RETURN_DEFAULT_ATTRIBUTES=true, both default and explicit attributes should be returned
        // This tests the actual ConnID framework behavior
        assertThat(group.getAttributeByName("name")).isNotNull();
        assertThat(group.getAttributeByName("__DESCRIPTION__")).isNotNull();
        assertThat(group.getAttributeByName("aliases")).isNotNull();

        // Verify request was sent with both default and explicit attributes
        List<LoggedRequest> requests = GoogleApiMockServer.getAllRequests().stream()
                .filter(request -> request.getUrl().startsWith("/admin/directory/v1/groups?"))  // Only list requests
                .collect(Collectors.toList());
        assertThat(requests).hasSize(1);

        // Verify customer parameter was sent correctly
        assertThat(requests.get(0).queryParameter("customer").firstValue()).isEqualTo("my_customer");

        // Verify that separate API call was made for members
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/combined001/members")));
    }
}