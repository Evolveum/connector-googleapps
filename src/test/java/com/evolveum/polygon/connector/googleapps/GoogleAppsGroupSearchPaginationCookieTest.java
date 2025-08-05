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
 * Tests for GoogleAppsConnector Group search with cookie-based pagination
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsGroupSearchPaginationCookieTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test cookie-based pagination with pageToken")
    public void testCookieBasedPagination() {
        // Setup mock response for pageToken request
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("customer", equalTo("my_customer"))
                .withQueryParam("pageToken", equalTo("test_group_cookie"))
                .withQueryParam("maxResults", equalTo("2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [\n" +
                                "    {\n" +
                                "      \"id\": \"group_cookie001\",\n" +
                                "      \"etag\": \"\\\"group_cookie001-etag\\\"\",\n" +
                                "      \"email\": \"cookie001@example.com\",\n" +
                                "      \"name\": \"Cookie Group 1\",\n" +
                                "      \"description\": \"First cookie group\",\n" +
                                "      \"aliases\": [],\n" +
                                "      \"nonEditableAliases\": [],\n" +
                                "      \"adminCreated\": true,\n" +
                                "      \"directMembersCount\": \"5\"\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"id\": \"group_cookie002\",\n" +
                                "      \"etag\": \"\\\"group_cookie002-etag\\\"\",\n" +
                                "      \"email\": \"cookie002@example.com\",\n" +
                                "      \"name\": \"Cookie Group 2\",\n" +
                                "      \"description\": \"Second cookie group\",\n" +
                                "      \"aliases\": [],\n" +
                                "      \"nonEditableAliases\": [],\n" +
                                "      \"adminCreated\": true,\n" +
                                "      \"directMembersCount\": \"8\"\n" +
                                "    }\n" +
                                "  ],\n" +
                                "  \"nextPageToken\": \"next_group_cookie\"\n" +
                                "}")));

        // Set cookie (pageToken) and page size
        OperationOptions options = new OperationOptionsBuilder()
                .setPagedResultsCookie("test_group_cookie")
                .setPageSize(2)
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with cookie
        connectorFacade.search(ObjectClass.GROUP, null, results::add, options);

        // Verify results
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getUid().getUidValue()).isEqualTo("group_cookie001");
        assertThat(results.get(1).getUid().getUidValue()).isEqualTo("group_cookie002");

        // Verify request was sent with correct pageToken
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("customer", equalTo("my_customer"))
                .withQueryParam("pageToken", equalTo("test_group_cookie"))
                .withQueryParam("maxResults", equalTo("2")));
    }

    @Test
    @DisplayName("Test cookie-based pagination without pageSize")
    public void testCookieWithoutPageSize() {
        // Setup mock response
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("customer", equalTo("my_customer"))
                .withQueryParam("pageToken", equalTo("no_size_group_token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [\n" +
                                "    {\n" +
                                "      \"id\": \"group_nosize001\",\n" +
                                "      \"etag\": \"\\\"group_nosize001-etag\\\"\",\n" +
                                "      \"email\": \"nosize001@example.com\",\n" +
                                "      \"name\": \"NoSize Group\",\n" +
                                "      \"description\": \"No page size group\",\n" +
                                "      \"aliases\": [],\n" +
                                "      \"nonEditableAliases\": [],\n" +
                                "      \"adminCreated\": true,\n" +
                                "      \"directMembersCount\": \"3\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Set only cookie (no page size)
        OperationOptions options = new OperationOptionsBuilder()
                .setPagedResultsCookie("no_size_group_token")
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with cookie only
        connectorFacade.search(ObjectClass.GROUP, null, results::add, options);

        // Verify results
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getUid().getUidValue()).isEqualTo("group_nosize001");

        // Verify request was sent with pageToken and default config maxResults
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("customer", equalTo("my_customer"))
                .withQueryParam("pageToken", equalTo("no_size_group_token"))
                .withQueryParam("maxResults", equalTo("200")));
    }
}