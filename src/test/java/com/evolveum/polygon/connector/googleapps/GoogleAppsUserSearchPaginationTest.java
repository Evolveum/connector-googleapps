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
 * Tests for GoogleAppsConnector User search pagination functionality
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsUserSearchPaginationTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test page size configuration")
    public void testPageSizeConfiguration() {
        // Setup mock response with different page size
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("5"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [\n" +
                                "    {\n" +
                                "      \"id\": \"pagesize001\",\n" +
                                "      \"etag\": \"\\\"pagesize001-etag\\\"\",\n" +
                                "      \"primaryEmail\": \"pagesize@example.com\",\n" +
                                "      \"name\": {\n" +
                                "        \"givenName\": \"PageSize\",\n" +
                                "        \"familyName\": \"Test\"\n" +
                                "      }\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Set specific page size
        OperationOptions options = new OperationOptionsBuilder()
                .setPageSize(5)
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with specific page size
        connectorFacade.search(ObjectClass.ACCOUNT, null, results::add, options);

        // Verify results
        assertThat(results).hasSize(1);
        ConnectorObject user = results.get(0);
        assertThat(user.getUid().getUidValue()).isEqualTo("pagesize001");

        // Verify request was sent with correct maxResults parameter
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("5")));
    }

    @Test
    @DisplayName("Test full pagination without page size limit")
    public void testFullPaginationAllPages() {
        // Setup first page response with nextPageToken
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [\n" +
                                "    {\n" +
                                "      \"id\": \"token001\",\n" +
                                "      \"etag\": \"\\\"token001-etag\\\"\",\n" +
                                "      \"primaryEmail\": \"token@example.com\",\n" +
                                "      \"name\": {\n" +
                                "        \"givenName\": \"Token\",\n" +
                                "        \"familyName\": \"Test\"\n" +
                                "      }\n" +
                                "    }\n" +
                                "  ],\n" +
                                "  \"nextPageToken\": \"next_token_123\"\n" +
                                "}")));

        // Setup second page response (final page)
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("pageToken", equalTo("next_token_123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [\n" +
                                "    {\n" +
                                "      \"id\": \"token002\",\n" +
                                "      \"etag\": \"\\\"token002-etag\\\"\",\n" +
                                "      \"primaryEmail\": \"token2@example.com\",\n" +
                                "      \"name\": {\n" +
                                "        \"givenName\": \"Token2\",\n" +
                                "        \"familyName\": \"Test\"\n" +
                                "      }\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search without page size limit (should fetch all pages)
        connectorFacade.search(ObjectClass.ACCOUNT, null, results::add, null);

        // Verify all results from both pages are returned
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getUid().getUidValue()).isEqualTo("token001");
        assertThat(results.get(1).getUid().getUidValue()).isEqualTo("token002");

        // Verify both requests were made
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users")));

        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("pageToken", equalTo("next_token_123")));
    }

    @Test
    @DisplayName("Test paged search with page size limit")
    public void testPagedSearchWithLimit() {
        // Setup mock response with nextPageToken
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [\n" +
                                "    {\n" +
                                "      \"id\": \"token001\",\n" +
                                "      \"etag\": \"\\\"token001-etag\\\"\",\n" +
                                "      \"primaryEmail\": \"token@example.com\",\n" +
                                "      \"name\": {\n" +
                                "        \"givenName\": \"Token\",\n" +
                                "        \"familyName\": \"Test\"\n" +
                                "      }\n" +
                                "    }\n" +
                                "  ],\n" +
                                "  \"nextPageToken\": \"unused_token_456\"\n" +
                                "}")));

        // Set page size option
        OperationOptions options = new OperationOptionsBuilder()
                .setPageSize(1)
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with page size limit
        connectorFacade.search(ObjectClass.ACCOUNT, null, results::add, options);

        // Verify only first page results (respecting page size limit)
        assertThat(results).hasSize(1);
        ConnectorObject user = results.get(0);
        assertThat(user.getUid().getUidValue()).isEqualTo("token001");

        // Verify only first page request was made (no automatic pagination)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("1")));

        // Verify second page was NOT requested
        GoogleApiMockServer.verify(0, getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("pageToken", equalTo("unused_token_456")));
    }
}