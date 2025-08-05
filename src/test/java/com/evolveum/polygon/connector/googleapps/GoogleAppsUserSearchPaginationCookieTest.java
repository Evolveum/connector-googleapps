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
 * Tests for GoogleAppsConnector User search with cookie-based pagination
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsUserSearchPaginationCookieTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test cookie-based pagination with pageToken")
    public void testCookieBasedPagination() {
        // Setup mock response for pageToken request
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("pageToken", equalTo("test_cookie_token"))
                .withQueryParam("maxResults", equalTo("2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [\n" +
                                "    {\n" +
                                "      \"id\": \"cookie001\",\n" +
                                "      \"etag\": \"\\\"cookie001-etag\\\"\",\n" +
                                "      \"primaryEmail\": \"cookie001@example.com\",\n" +
                                "      \"name\": {\n" +
                                "        \"givenName\": \"Cookie\",\n" +
                                "        \"familyName\": \"Test1\"\n" +
                                "      }\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"id\": \"cookie002\",\n" +
                                "      \"etag\": \"\\\"cookie002-etag\\\"\",\n" +
                                "      \"primaryEmail\": \"cookie002@example.com\",\n" +
                                "      \"name\": {\n" +
                                "        \"givenName\": \"Cookie\",\n" +
                                "        \"familyName\": \"Test2\"\n" +
                                "      }\n" +
                                "    }\n" +
                                "  ],\n" +
                                "  \"nextPageToken\": \"next_cookie_token\"\n" +
                                "}")));

        // Set cookie (pageToken) and page size
        OperationOptions options = new OperationOptionsBuilder()
                .setPagedResultsCookie("test_cookie_token")
                .setPageSize(2)
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with cookie
        connectorFacade.search(ObjectClass.ACCOUNT, null, results::add, options);

        // Verify results
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getUid().getUidValue()).isEqualTo("cookie001");
        assertThat(results.get(1).getUid().getUidValue()).isEqualTo("cookie002");

        // Verify request was sent with correct pageToken
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("pageToken", equalTo("test_cookie_token"))
                .withQueryParam("maxResults", equalTo("2")));
    }

    @Test
    @DisplayName("Test cookie-based pagination without pageSize")
    public void testCookieWithoutPageSize() {
        // Setup mock response
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("pageToken", equalTo("no_size_token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [\n" +
                                "    {\n" +
                                "      \"id\": \"nosize001\",\n" +
                                "      \"etag\": \"\\\"nosize001-etag\\\"\",\n" +
                                "      \"primaryEmail\": \"nosize001@example.com\",\n" +
                                "      \"name\": {\n" +
                                "        \"givenName\": \"NoSize\",\n" +
                                "        \"familyName\": \"Test\"\n" +
                                "      }\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Set only cookie (no page size)
        OperationOptions options = new OperationOptionsBuilder()
                .setPagedResultsCookie("no_size_token")
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with cookie only
        connectorFacade.search(ObjectClass.ACCOUNT, null, results::add, options);

        // Verify results
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getUid().getUidValue()).isEqualTo("nosize001");

        // Verify request was sent with pageToken and default config maxResults
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("pageToken", equalTo("no_size_token"))
                .withQueryParam("maxResults", equalTo("500")));
    }
}