package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Basic tests for GoogleAppsConnector Group search and listing operations.
 * <p>
 * This class tests basic group search functionality including:
 * - All groups search
 * - Paginated search results
 * - Empty search results handling
 * <p>
 * Group search operations use: GET /admin/directory/v1/groups?customer=my_customer
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsGroupSearchBasicTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test search all groups")
    public void testSearchGroups() {
        // Setup mock response for groups search
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("customer", equalTo("my_customer"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [\n" +
                                "    {\n" +
                                "      \"id\": \"search001\",\n" +
                                "      \"etag\": \"\\\"search001-etag\\\"\",\n" +
                                "      \"email\": \"search001@example.com\",\n" +
                                "      \"name\": \"Search Test Group 1\",\n" +
                                "      \"description\": \"First search test group\",\n" +
                                "      \"aliases\": [],\n" +
                                "      \"nonEditableAliases\": [],\n" +
                                "      \"adminCreated\": true,\n" +
                                "      \"directMembersCount\": \"10\"\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"id\": \"search002\",\n" +
                                "      \"etag\": \"\\\"search002-etag\\\"\",\n" +
                                "      \"email\": \"search002@example.com\",\n" +
                                "      \"name\": \"Search Test Group 2\",\n" +
                                "      \"description\": \"Second search test group\",\n" +
                                "      \"aliases\": [],\n" +
                                "      \"nonEditableAliases\": [],\n" +
                                "      \"adminCreated\": true,\n" +
                                "      \"directMembersCount\": \"5\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search for all groups (no filter)
        connectorFacade.search(ObjectClass.GROUP, null, results::add, null);

        // Verify results
        assertThat(results).hasSize(2);

        // Verify first group
        ConnectorObject group1 = results.get(0);
        assertThat(group1.getUid().getUidValue()).isEqualTo("search001");
        assertThat(group1.getName().getNameValue()).isEqualTo("search001@example.com");
        assertThat(group1.getAttributeByName("name").getValue().get(0)).isEqualTo("Search Test Group 1");
        assertThat(group1.getAttributeByName("__DESCRIPTION__").getValue().get(0)).isEqualTo("First search test group");

        // Verify second group
        ConnectorObject group2 = results.get(1);
        assertThat(group2.getUid().getUidValue()).isEqualTo("search002");
        assertThat(group2.getName().getNameValue()).isEqualTo("search002@example.com");
        assertThat(group2.getAttributeByName("name").getValue().get(0)).isEqualTo("Search Test Group 2");
        assertThat(group2.getAttributeByName("__DESCRIPTION__").getValue().get(0)).isEqualTo("Second search test group");

        // Verify the request was made to the correct endpoint
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("customer", equalTo("my_customer")));
    }

    @Test
    @DisplayName("Test search groups with pagination")
    public void testSearchGroupsWithPagination() {
        // Setup mock response for first page (without pageToken)
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("customer", equalTo("my_customer"))
                .withQueryParam("maxResults", equalTo("2"))
                .withQueryParam("pageToken", absent())
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"nextPageToken\": \"page2token\",\n" +
                                "  \"groups\": [\n" +
                                "    {\n" +
                                "      \"id\": \"page001\",\n" +
                                "      \"etag\": \"\\\"page001-etag\\\"\",\n" +
                                "      \"email\": \"page001@example.com\",\n" +
                                "      \"name\": \"Page 1 Group 1\",\n" +
                                "      \"description\": \"First page, first group\",\n" +
                                "      \"aliases\": [],\n" +
                                "      \"nonEditableAliases\": [],\n" +
                                "      \"adminCreated\": true,\n" +
                                "      \"directMembersCount\": \"15\"\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"id\": \"page002\",\n" +
                                "      \"etag\": \"\\\"page002-etag\\\"\",\n" +
                                "      \"email\": \"page002@example.com\",\n" +
                                "      \"name\": \"Page 1 Group 2\",\n" +
                                "      \"description\": \"First page, second group\",\n" +
                                "      \"aliases\": [],\n" +
                                "      \"nonEditableAliases\": [],\n" +
                                "      \"adminCreated\": true,\n" +
                                "      \"directMembersCount\": \"8\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Set page size
        OperationOptions options = new OperationOptionsBuilder()
                .setPageSize(2)
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with pagination
        connectorFacade.search(ObjectClass.GROUP, null, results::add, options);

        // Verify first page results (ConnID framework only returns first page automatically)
        assertThat(results).hasSize(2);

        // Verify first page results
        ConnectorObject group1 = results.get(0);
        assertThat(group1.getUid().getUidValue()).isEqualTo("page001");
        assertThat(group1.getAttributeByName("name").getValue().get(0)).isEqualTo("Page 1 Group 1");

        ConnectorObject group2 = results.get(1);
        assertThat(group2.getUid().getUidValue()).isEqualTo("page002");
        assertThat(group2.getAttributeByName("name").getValue().get(0)).isEqualTo("Page 1 Group 2");

        // Verify first page request was made
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("customer", equalTo("my_customer"))
                .withQueryParam("maxResults", equalTo("2"))
                .withQueryParam("pageToken", absent()));
    }

    @Test
    @DisplayName("Test search groups with empty results")
    public void testSearchGroupsEmpty() {
        // Setup mock response for empty groups search
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("customer", equalTo("my_customer"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": []\n" +
                                "}")));

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search for all groups (expecting no results)
        connectorFacade.search(ObjectClass.GROUP, null, results::add, null);

        // Verify no results returned
        assertThat(results).isEmpty();

        // Verify the request was made to the correct endpoint
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("customer", equalTo("my_customer")));
    }
}