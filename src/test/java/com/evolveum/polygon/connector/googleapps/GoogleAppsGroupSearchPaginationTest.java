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
 * Tests for GoogleAppsConnector Group search pagination functionality
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsGroupSearchPaginationTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test page size configuration")
    public void testPageSizeConfiguration() {
        // Setup mock response with different page size
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("customer", equalTo("my_customer"))
                .withQueryParam("maxResults", equalTo("5"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [\n" +
                                "    {\n" +
                                "      \"id\": \"pagesize001\",\n" +
                                "      \"etag\": \"\\\"pagesize001-etag\\\"\",\n" +
                                "      \"email\": \"pagesize@example.com\",\n" +
                                "      \"name\": \"PageSize Group\",\n" +
                                "      \"description\": \"Page size test group\",\n" +
                                "      \"aliases\": [],\n" +
                                "      \"nonEditableAliases\": [],\n" +
                                "      \"adminCreated\": true,\n" +
                                "      \"directMembersCount\": \"3\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Set specific page size
        OperationOptions options = new OperationOptionsBuilder()
                .setPageSize(5)
                .build();

        List<ConnectorObject> results = new ArrayList<>();
        connectorFacade.search(ObjectClass.GROUP, null, results::add, options);

        // Verify results
        assertThat(results).hasSize(1);
        ConnectorObject group = results.get(0);
        assertThat(group.getUid().getUidValue()).isEqualTo("pagesize001");
        assertThat(group.getName().getNameValue()).isEqualTo("pagesize@example.com");

        // Verify the request was made with correct page size
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("5")));
    }

    @Test
    @DisplayName("Test full pagination without page size limit")
    public void testFullPaginationWithoutPageSizeLimit() {
        // Setup mock responses for pagination without explicit page size limit
        // First page
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("customer", equalTo("my_customer"))
                .withQueryParam("pageToken", absent())
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"nextPageToken\": \"unlimited_page2_token\",\n" +
                                "  \"groups\": [\n" +
                                "    {\n" +
                                "      \"id\": \"unlimited001\",\n" +
                                "      \"etag\": \"\\\"unlimited001-etag\\\"\",\n" +
                                "      \"email\": \"unlimited001@example.com\",\n" +
                                "      \"name\": \"Unlimited Page 1 Group 1\",\n" +
                                "      \"description\": \"First group on unlimited first page\",\n" +
                                "      \"aliases\": [],\n" +
                                "      \"nonEditableAliases\": [],\n" +
                                "      \"adminCreated\": true,\n" +
                                "      \"directMembersCount\": \"8\"\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"id\": \"unlimited002\",\n" +
                                "      \"etag\": \"\\\"unlimited002-etag\\\"\",\n" +
                                "      \"email\": \"unlimited002@example.com\",\n" +
                                "      \"name\": \"Unlimited Page 1 Group 2\",\n" +
                                "      \"description\": \"Second group on unlimited first page\",\n" +
                                "      \"aliases\": [],\n" +
                                "      \"nonEditableAliases\": [],\n" +
                                "      \"adminCreated\": true,\n" +
                                "      \"directMembersCount\": \"12\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Second page
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("customer", equalTo("my_customer"))
                .withQueryParam("pageToken", equalTo("unlimited_page2_token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [\n" +
                                "    {\n" +
                                "      \"id\": \"unlimited003\",\n" +
                                "      \"etag\": \"\\\"unlimited003-etag\\\"\",\n" +
                                "      \"email\": \"unlimited003@example.com\",\n" +
                                "      \"name\": \"Unlimited Page 2 Group 1\",\n" +
                                "      \"description\": \"First group on unlimited second page\",\n" +
                                "      \"aliases\": [],\n" +
                                "      \"nonEditableAliases\": [],\n" +
                                "      \"adminCreated\": true,\n" +
                                "      \"directMembersCount\": \"5\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // No page size limit - should get all results across pages
        List<ConnectorObject> results = new ArrayList<>();
        connectorFacade.search(ObjectClass.GROUP, null, results::add, null);

        // Verify all results from both pages
        assertThat(results).hasSize(3);

        // First page results
        ConnectorObject group1 = results.get(0);
        assertThat(group1.getUid().getUidValue()).isEqualTo("unlimited001");
        assertThat(group1.getAttributeByName("name").getValue().get(0)).isEqualTo("Unlimited Page 1 Group 1");

        ConnectorObject group2 = results.get(1);
        assertThat(group2.getUid().getUidValue()).isEqualTo("unlimited002");
        assertThat(group2.getAttributeByName("name").getValue().get(0)).isEqualTo("Unlimited Page 1 Group 2");

        // Second page result
        ConnectorObject group3 = results.get(2);
        assertThat(group3.getUid().getUidValue()).isEqualTo("unlimited003");
        assertThat(group3.getAttributeByName("name").getValue().get(0)).isEqualTo("Unlimited Page 2 Group 1");

        // Verify both page requests were made
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("pageToken", absent()));
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("pageToken", equalTo("unlimited_page2_token")));
    }

    @Test
    @DisplayName("Test paged search with page size limit")
    public void testPagedSearchWithPageSizeLimit() {
        // Setup mock responses for limited pagination
        // First page with explicit page size
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("customer", equalTo("my_customer"))
                .withQueryParam("maxResults", equalTo("2"))
                .withQueryParam("pageToken", absent())
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"nextPageToken\": \"limited_page2_token\",\n" +
                                "  \"groups\": [\n" +
                                "    {\n" +
                                "      \"id\": \"limited001\",\n" +
                                "      \"etag\": \"\\\"limited001-etag\\\"\",\n" +
                                "      \"email\": \"limited001@example.com\",\n" +
                                "      \"name\": \"Limited Page 1 Group 1\",\n" +
                                "      \"description\": \"First group on limited first page\",\n" +
                                "      \"aliases\": [],\n" +
                                "      \"nonEditableAliases\": [],\n" +
                                "      \"adminCreated\": true,\n" +
                                "      \"directMembersCount\": \"6\"\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"id\": \"limited002\",\n" +
                                "      \"etag\": \"\\\"limited002-etag\\\"\",\n" +
                                "      \"email\": \"limited002@example.com\",\n" +
                                "      \"name\": \"Limited Page 1 Group 2\",\n" +
                                "      \"description\": \"Second group on limited first page\",\n" +
                                "      \"aliases\": [],\n" +
                                "      \"nonEditableAliases\": [],\n" +
                                "      \"adminCreated\": true,\n" +
                                "      \"directMembersCount\": \"9\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // With page size limit - should only get first page
        OperationOptions options = new OperationOptionsBuilder()
                .setPageSize(2)
                .build();

        List<ConnectorObject> results = new ArrayList<>();
        connectorFacade.search(ObjectClass.GROUP, null, results::add, options);

        // Verify only first page results (ConnID framework manages pagination)
        assertThat(results).hasSize(2);

        ConnectorObject group1 = results.get(0);
        assertThat(group1.getUid().getUidValue()).isEqualTo("limited001");
        assertThat(group1.getAttributeByName("name").getValue().get(0)).isEqualTo("Limited Page 1 Group 1");

        ConnectorObject group2 = results.get(1);
        assertThat(group2.getUid().getUidValue()).isEqualTo("limited002");
        assertThat(group2.getAttributeByName("name").getValue().get(0)).isEqualTo("Limited Page 1 Group 2");

        // Verify only first page request was made with correct parameters
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("2"))
                .withQueryParam("pageToken", absent()));
    }
}