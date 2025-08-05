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
 * Tests for GoogleAppsConnector User search with OP_PAGED_RESULTS_OFFSET functionality
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsUserSearchPaginationOffsetTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test offset=1, pageSize=2 (first page)")
    public void testOffsetFirstPage() {
        // Setup data phase directly (no skip needed for offset=1, skipCount=0)
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("2"))
                .withQueryParam("fields", matching("nextPageToken,users\\(.*\\)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [\n" +
                                "    {\n" +
                                "      \"id\": \"user001\",\n" +
                                "      \"etag\": \"\\\"user001-etag\\\"\",\n" +
                                "      \"primaryEmail\": \"user001@example.com\",\n" +
                                "      \"name\": {\n" +
                                "        \"givenName\": \"User\",\n" +
                                "        \"familyName\": \"001\"\n" +
                                "      }\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"id\": \"user002\",\n" +
                                "      \"etag\": \"\\\"user002-etag\\\"\",\n" +
                                "      \"primaryEmail\": \"user002@example.com\",\n" +
                                "      \"name\": {\n" +
                                "        \"givenName\": \"User\",\n" +
                                "        \"familyName\": \"002\"\n" +
                                "      }\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Set offset and page size
        OperationOptions options = new OperationOptionsBuilder()
                .setPagedResultsOffset(1)  // Start from first record (skipCount=0)
                .setPageSize(2)
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with offset
        connectorFacade.search(ObjectClass.ACCOUNT, null, results::add, options);

        // Verify results - should get first 2 users
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getUid().getUidValue()).isEqualTo("user001");
        assertThat(results.get(1).getUid().getUidValue()).isEqualTo("user002");

        // Verify skip phase was NOT called (skipCount = 0)
        GoogleApiMockServer.verify(0, getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("fields", equalTo("nextPageToken,users(id)")));

        // Verify data phase was called directly
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("2")));
    }

    @Test
    @DisplayName("Test offset=3, pageSize=2 (skip 2, get next 2)")
    public void testOffsetWithSkip() {
        // Setup skip phase - skip 2 records with minimal fields
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("2"))
                .withQueryParam("fields", equalTo("nextPageToken,users(id)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [\n" +
                                "    {\"id\": \"skip001\"},\n" +
                                "    {\"id\": \"skip002\"}\n" +
                                "  ],\n" +
                                "  \"nextPageToken\": \"skip_token_2\"\n" +
                                "}")));

        // Setup data phase - get next 2 records with full fields
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("2"))
                .withQueryParam("pageToken", equalTo("skip_token_2"))
                .withQueryParam("fields", matching("nextPageToken,users\\(.*\\)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [\n" +
                                "    {\n" +
                                "      \"id\": \"user003\",\n" +
                                "      \"etag\": \"\\\"user003-etag\\\"\",\n" +
                                "      \"primaryEmail\": \"user003@example.com\",\n" +
                                "      \"name\": {\n" +
                                "        \"givenName\": \"User\",\n" +
                                "        \"familyName\": \"003\"\n" +
                                "      }\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"id\": \"user004\",\n" +
                                "      \"etag\": \"\\\"user004-etag\\\"\",\n" +
                                "      \"primaryEmail\": \"user004@example.com\",\n" +
                                "      \"name\": {\n" +
                                "        \"givenName\": \"User\",\n" +
                                "        \"familyName\": \"004\"\n" +
                                "      }\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Set offset=3 (skip first 2) and pageSize=2
        OperationOptions options = new OperationOptionsBuilder()
                .setPagedResultsOffset(3)  // Skip first 2 records
                .setPageSize(2)
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with offset
        connectorFacade.search(ObjectClass.ACCOUNT, null, results::add, options);

        // Verify results - should get user003 and user004
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getUid().getUidValue()).isEqualTo("user003");
        assertThat(results.get(1).getUid().getUidValue()).isEqualTo("user004");

        // Verify skip phase was called with minimal fields
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("2"))
                .withQueryParam("fields", equalTo("nextPageToken,users(id)")));

        // Verify data phase was called with full fields
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("2"))
                .withQueryParam("pageToken", equalTo("skip_token_2"))
                .withQueryParam("fields", matching("nextPageToken,users\\(.*\\)")));
    }

    @Test
    @DisplayName("Test offset=6, pageSize=2 (skip multiple pages)")
    public void testOffsetMultiplePageSkip() {
        // Setup first skip page - skip 5 records (maxResults limited to 5)
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("5"))
                .withQueryParam("fields", equalTo("nextPageToken,users(id)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [\n" +
                                "    {\"id\": \"skip001\"}, {\"id\": \"skip002\"}, {\"id\": \"skip003\"},\n" +
                                "    {\"id\": \"skip004\"}, {\"id\": \"skip005\"}\n" +
                                "  ],\n" +
                                "  \"nextPageToken\": \"skip_token_5\"\n" +
                                "}")));

        // Setup data phase - get 2 records from position 6
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("2"))
                .withQueryParam("pageToken", equalTo("skip_token_5"))
                .withQueryParam("fields", matching("nextPageToken,users\\(.*\\)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [\n" +
                                "    {\n" +
                                "      \"id\": \"user006\",\n" +
                                "      \"etag\": \"\\\"user006-etag\\\"\",\n" +
                                "      \"primaryEmail\": \"user006@example.com\",\n" +
                                "      \"name\": {\n" +
                                "        \"givenName\": \"User\",\n" +
                                "        \"familyName\": \"006\"\n" +
                                "      }\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"id\": \"user007\",\n" +
                                "      \"etag\": \"\\\"user007-etag\\\"\",\n" +
                                "      \"primaryEmail\": \"user007@example.com\",\n" +
                                "      \"name\": {\n" +
                                "        \"givenName\": \"User\",\n" +
                                "        \"familyName\": \"007\"\n" +
                                "      }\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Set offset=6 (skip first 5) and pageSize=2
        OperationOptions options = new OperationOptionsBuilder()
                .setPagedResultsOffset(6)  // Skip first 5 records
                .setPageSize(2)
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with offset
        connectorFacade.search(ObjectClass.ACCOUNT, null, results::add, options);

        // Verify results - should get user006 and user007
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getUid().getUidValue()).isEqualTo("user006");
        assertThat(results.get(1).getUid().getUidValue()).isEqualTo("user007");

        // Verify skip phase was called with maxResults=5 (limited by config max or remaining)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("5"))
                .withQueryParam("fields", equalTo("nextPageToken,users(id)")));

        // Verify data phase was called
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("2"))
                .withQueryParam("pageToken", equalTo("skip_token_5")));
    }

    @Test
    @DisplayName("Test offset=501, pageSize=10 (large skip using config max)")
    public void testLargeOffsetUsingConfigMax() {
        // Setup first skip page - use config max (500)
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("500"))
                .withQueryParam("fields", equalTo("nextPageToken,users(id)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [" +
                                // Generate 500 users
                                generateUsersJson(1, 500) +
                                "  ],\n" +
                                "  \"nextPageToken\": \"skip_token_500\"\n" +
                                "}")));

        // Setup data phase - get 10 records from position 501
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("10"))
                .withQueryParam("pageToken", equalTo("skip_token_500"))
                .withQueryParam("fields", matching("nextPageToken,users\\(.*\\)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [\n" +
                                generateFullUsersJson(501, 510) +
                                "  ]\n" +
                                "}")));

        // Set offset=501 (skip first 500) and pageSize=10
        OperationOptions options = new OperationOptionsBuilder()
                .setPagedResultsOffset(501)
                .setPageSize(10)
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with large offset
        connectorFacade.search(ObjectClass.ACCOUNT, null, results::add, options);

        // Verify results - should get user501 to user510
        assertThat(results).hasSize(10);
        assertThat(results.get(0).getUid().getUidValue()).isEqualTo("user501");
        assertThat(results.get(9).getUid().getUidValue()).isEqualTo("user510");

        // Verify skip phase used config max (500)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("500"))
                .withQueryParam("fields", equalTo("nextPageToken,users(id)")));

        // Verify data phase used pageSize (10)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("10"))
                .withQueryParam("pageToken", equalTo("skip_token_500")));
    }

    @Test
    @DisplayName("Test offset exceeds total records")
    public void testOffsetExceedsTotalRecords() {
        // Setup skip phase - return fewer records than expected, no nextPageToken
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("99"))
                .withQueryParam("fields", equalTo("nextPageToken,users(id)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [\n" +
                                "    {\"id\": \"last001\"}\n" +
                                "  ]\n" +
                                "}")));

        // Set offset=100 but only 1 user exists
        OperationOptions options = new OperationOptionsBuilder()
                .setPagedResultsOffset(100)
                .setPageSize(10)
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with offset beyond available data
        connectorFacade.search(ObjectClass.ACCOUNT, null, results::add, options);

        // Verify no results returned
        assertThat(results).isEmpty();

        // Verify only skip phase was called, no data phase
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("99"))
                .withQueryParam("fields", equalTo("nextPageToken,users(id)")));

        // Verify data phase was NOT called (no pageToken provided)
        GoogleApiMockServer.verify(0, getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("pageToken", matching(".*")));
    }

    @Test
    @DisplayName("Test offset=1201, pageSize=10 (multiple skip phases)")
    public void testMultipleSkipPhases() {
        // Setup first skip phase - skip 500 records (config max)
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("500"))
                .withQueryParam("fields", equalTo("nextPageToken,users(id)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [" +
                                generateUsersJson(1, 500) +
                                "  ],\n" +
                                "  \"nextPageToken\": \"skip_token_500\"\n" +
                                "}")));

        // Setup second skip phase - skip next 500 records
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("500"))
                .withQueryParam("pageToken", equalTo("skip_token_500"))
                .withQueryParam("fields", equalTo("nextPageToken,users(id)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [" +
                                generateUsersJson(501, 1000) +
                                "  ],\n" +
                                "  \"nextPageToken\": \"skip_token_1000\"\n" +
                                "}")));

        // Setup third skip phase - skip final 200 records
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("200"))
                .withQueryParam("pageToken", equalTo("skip_token_1000"))
                .withQueryParam("fields", equalTo("nextPageToken,users(id)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [" +
                                generateUsersJson(1001, 1200) +
                                "  ],\n" +
                                "  \"nextPageToken\": \"skip_token_1200\"\n" +
                                "}")));

        // Setup data phase - get 10 records from position 1201
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("10"))
                .withQueryParam("pageToken", equalTo("skip_token_1200"))
                .withQueryParam("fields", matching("nextPageToken,users\\(.*\\)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [" +
                                generateFullUsersJson(1201, 1210) +
                                "  ]\n" +
                                "}")));

        // Set offset=1201 (skip first 1200) and pageSize=10
        OperationOptions options = new OperationOptionsBuilder()
                .setPagedResultsOffset(1201)  // Skip first 1200 records
                .setPageSize(10)
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with large offset requiring multiple skip phases
        connectorFacade.search(ObjectClass.ACCOUNT, null, results::add, options);

        // Verify results - should get user1201 to user1210
        assertThat(results).hasSize(10);
        assertThat(results.get(0).getUid().getUidValue()).isEqualTo("user1201");
        assertThat(results.get(9).getUid().getUidValue()).isEqualTo("user1210");

        // Verify first skip phase (500 records)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("500"))
                .withQueryParam("fields", equalTo("nextPageToken,users(id)"))
                .withQueryParam("pageToken", absent()));

        // Verify second skip phase (500 more records)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("500"))
                .withQueryParam("pageToken", equalTo("skip_token_500"))
                .withQueryParam("fields", equalTo("nextPageToken,users(id)")));

        // Verify third skip phase (200 remaining records)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("200"))
                .withQueryParam("pageToken", equalTo("skip_token_1000"))
                .withQueryParam("fields", equalTo("nextPageToken,users(id)")));

        // Verify data phase (10 target records)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("10"))
                .withQueryParam("pageToken", equalTo("skip_token_1200"))
                .withQueryParam("fields", matching("nextPageToken,users\\(.*\\)")));
    }

    @Test
    @DisplayName("Test offset=501, pageSize=5 (boundary: exactly one page + 1)")
    public void testBoundaryOffsetExactlyOnePlusOne() {
        // Setup first skip phase - skip exactly 500 records (config max)
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("500"))
                .withQueryParam("fields", equalTo("nextPageToken,users(id)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [" +
                                generateUsersJson(1, 500) +
                                "  ],\n" +
                                "  \"nextPageToken\": \"skip_token_500\"\n" +
                                "}")));

        // Setup data phase - get 5 records from position 501
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("5"))
                .withQueryParam("pageToken", equalTo("skip_token_500"))
                .withQueryParam("fields", matching("nextPageToken,users\\(.*\\)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [" +
                                generateFullUsersJson(501, 505) +
                                "  ]\n" +
                                "}")));

        // Set offset=501 (skip exactly 500, right at boundary) and pageSize=5
        OperationOptions options = new OperationOptionsBuilder()
                .setPagedResultsOffset(501)  // Exactly config max + 1
                .setPageSize(5)
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with boundary offset
        connectorFacade.search(ObjectClass.ACCOUNT, null, results::add, options);

        // Verify results - should get user501 to user505
        assertThat(results).hasSize(5);
        assertThat(results.get(0).getUid().getUidValue()).isEqualTo("user501");
        assertThat(results.get(4).getUid().getUidValue()).isEqualTo("user505");

        // Verify exactly ONE skip phase (500 records)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("500"))
                .withQueryParam("fields", equalTo("nextPageToken,users(id)"))
                .withQueryParam("pageToken", absent()));

        // Verify data phase
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("5"))
                .withQueryParam("pageToken", equalTo("skip_token_500"))
                .withQueryParam("fields", matching("nextPageToken,users\\(.*\\)")));
    }

    @Test
    @DisplayName("Test offset=502, pageSize=5 (boundary: one page + 2)")
    public void testBoundaryOffsetOnePlusTwo() {
        // Setup first skip phase - skip 500 records (config max)
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("500"))
                .withQueryParam("fields", equalTo("nextPageToken,users(id)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [" +
                                generateUsersJson(1, 500) +
                                "  ],\n" +
                                "  \"nextPageToken\": \"skip_token_500\"\n" +
                                "}")));

        // Setup second skip phase - skip 1 more record
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("1"))
                .withQueryParam("pageToken", equalTo("skip_token_500"))
                .withQueryParam("fields", equalTo("nextPageToken,users(id)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [\n" +
                                "    {\"id\": \"user501\"}\n" +
                                "  ],\n" +
                                "  \"nextPageToken\": \"skip_token_501\"\n" +
                                "}")));

        // Setup data phase - get 5 records from position 502
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("5"))
                .withQueryParam("pageToken", equalTo("skip_token_501"))
                .withQueryParam("fields", matching("nextPageToken,users\\(.*\\)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [" +
                                generateFullUsersJson(502, 506) +
                                "  ]\n" +
                                "}")));

        // Set offset=502 (skip 501 records, requires 2 skip phases) and pageSize=5
        OperationOptions options = new OperationOptionsBuilder()
                .setPagedResultsOffset(502)  // Config max + 2, requires 2 skip phases
                .setPageSize(5)
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with boundary offset requiring 2 skip phases
        connectorFacade.search(ObjectClass.ACCOUNT, null, results::add, options);

        // Verify results - should get user502 to user506
        assertThat(results).hasSize(5);
        assertThat(results.get(0).getUid().getUidValue()).isEqualTo("user502");
        assertThat(results.get(4).getUid().getUidValue()).isEqualTo("user506");

        // Verify first skip phase (500 records)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("500"))
                .withQueryParam("fields", equalTo("nextPageToken,users(id)"))
                .withQueryParam("pageToken", absent()));

        // Verify second skip phase (1 record)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("1"))
                .withQueryParam("pageToken", equalTo("skip_token_500"))
                .withQueryParam("fields", equalTo("nextPageToken,users(id)")));

        // Verify data phase
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("5"))
                .withQueryParam("pageToken", equalTo("skip_token_501"))
                .withQueryParam("fields", matching("nextPageToken,users\\(.*\\)")));
    }

    @Test
    @DisplayName("Test offset=1, pageSize=600 (exceeds config max)")
    public void testPageSizeExceedsConfigMax() {
        // Setup data phase - should use config max (500) not pageSize (600)
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("500"))
                .withQueryParam("fields", matching("nextPageToken,users\\(.*\\)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [" +
                                generateFullUsersJson(1, 500) +
                                "  ],\n" +
                                "  \"nextPageToken\": \"data_token_500\"\n" +
                                "}")));

        // Setup second data phase - get remaining 100 records
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("100"))
                .withQueryParam("pageToken", equalTo("data_token_500"))
                .withQueryParam("fields", matching("nextPageToken,users\\(.*\\)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [" +
                                generateFullUsersJson(501, 600) +
                                "  ]\n" +
                                "}")));

        // Set offset=1, pageSize=600 (exceeds config max of 500)
        OperationOptions options = new OperationOptionsBuilder()
                .setPagedResultsOffset(1)
                .setPageSize(600)
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search
        connectorFacade.search(ObjectClass.ACCOUNT, null, results::add, options);

        // Verify all 600 results returned
        assertThat(results).hasSize(600);
        assertThat(results.get(0).getUid().getUidValue()).isEqualTo("user001");
        assertThat(results.get(599).getUid().getUidValue()).isEqualTo("user600");

        // Verify first data phase used config max (500)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("500")));

        // Verify second data phase used remaining (100)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("100"))
                .withQueryParam("pageToken", equalTo("data_token_500")));
    }

    /**
     * Generate JSON for users with only ID field (for skip phase)
     */
    private String generateUsersJson(int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end; i++) {
            if (i > start) sb.append(",");
            sb.append(String.format("{\"id\": \"user%03d\"}", i));
        }
        return sb.toString();
    }

    /**
     * Generate JSON for users with full fields (for data phase)
     */
    private String generateFullUsersJson(int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end; i++) {
            if (i > start) sb.append(",");
            sb.append(String.format(
                    "{\n" +
                            "      \"id\": \"user%03d\",\n" +
                            "      \"etag\": \"\\\"user%03d-etag\\\"\",\n" +
                            "      \"primaryEmail\": \"user%03d@example.com\",\n" +
                            "      \"name\": {\n" +
                            "        \"givenName\": \"User\",\n" +
                            "        \"familyName\": \"%03d\"\n" +
                            "      }\n" +
                            "    }", i, i, i, i));
        }
        return sb.toString();
    }
}