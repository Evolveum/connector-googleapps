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
 * Tests for GoogleAppsConnector Group search with OP_PAGED_RESULTS_OFFSET functionality
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsGroupSearchPaginationOffsetTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test offset=1, pageSize=2 (first page)")
    public void testOffsetFirstPage() {
        // Setup data phase directly (no skip needed for offset=1)
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("2"))
                .withQueryParam("fields", matching("nextPageToken,groups\\(.*\\)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [\n" +
                                "    {\n" +
                                "      \"id\": \"group001\",\n" +
                                "      \"etag\": \"\\\"group001-etag\\\"\",\n" +
                                "      \"email\": \"group001@example.com\",\n" +
                                "      \"name\": \"Group 001\",\n" +
                                "      \"description\": \"Test Group 001\"\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"id\": \"group002\",\n" +
                                "      \"etag\": \"\\\"group002-etag\\\"\",\n" +
                                "      \"email\": \"group002@example.com\",\n" +
                                "      \"name\": \"Group 002\",\n" +
                                "      \"description\": \"Test Group 002\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Set offset and page size
        OperationOptions options = new OperationOptionsBuilder()
                .setPagedResultsOffset(1)  // Start from first record
                .setPageSize(2)
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with offset
        connectorFacade.search(ObjectClass.GROUP, null, results::add, options);

        // Verify results - should get first 2 groups
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getUid().getUidValue()).isEqualTo("group001");
        assertThat(results.get(1).getUid().getUidValue()).isEqualTo("group002");

        // Verify skip phase was NOT called (skipCount = 0)
        GoogleApiMockServer.verify(0, getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("fields", equalTo("nextPageToken,groups(id)")));

        // Verify data phase was called directly
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("2")));
    }

    @Test
    @DisplayName("Test offset=3, pageSize=2 (skip 2, get next 2)")
    public void testOffsetWithSkip() {
        // Setup skip phase - skip 2 records with minimal fields
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("2"))
                .withQueryParam("fields", equalTo("nextPageToken,groups(id)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [\n" +
                                "    {\"id\": \"skip001\"},\n" +
                                "    {\"id\": \"skip002\"}\n" +
                                "  ],\n" +
                                "  \"nextPageToken\": \"skip_token_2\"\n" +
                                "}")));

        // Setup data phase - get next 2 records with full fields
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("2"))
                .withQueryParam("pageToken", equalTo("skip_token_2"))
                .withQueryParam("fields", matching("nextPageToken,groups\\(.*\\)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [\n" +
                                "    {\n" +
                                "      \"id\": \"group003\",\n" +
                                "      \"etag\": \"\\\"group003-etag\\\"\",\n" +
                                "      \"email\": \"group003@example.com\",\n" +
                                "      \"name\": \"Group 003\",\n" +
                                "      \"description\": \"Test Group 003\"\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"id\": \"group004\",\n" +
                                "      \"etag\": \"\\\"group004-etag\\\"\",\n" +
                                "      \"email\": \"group004@example.com\",\n" +
                                "      \"name\": \"Group 004\",\n" +
                                "      \"description\": \"Test Group 004\"\n" +
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
        connectorFacade.search(ObjectClass.GROUP, null, results::add, options);

        // Verify results - should get group003 and group004
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getUid().getUidValue()).isEqualTo("group003");
        assertThat(results.get(1).getUid().getUidValue()).isEqualTo("group004");

        // Verify skip phase was called with minimal fields
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("2"))
                .withQueryParam("fields", equalTo("nextPageToken,groups(id)")));

        // Verify data phase was called with full fields
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("2"))
                .withQueryParam("pageToken", equalTo("skip_token_2"))
                .withQueryParam("fields", matching("nextPageToken,groups\\(.*\\)")));
    }

    @Test
    @DisplayName("Test offset=201, pageSize=50 (exceeds config max 200)")
    public void testOffsetExceedsConfigMax() {
        // Setup first skip page - use config max (200)
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("200"))
                .withQueryParam("fields", equalTo("nextPageToken,groups(id)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [" +
                                generateGroupsJson(1, 200) +
                                "  ],\n" +
                                "  \"nextPageToken\": \"skip_token_200\"\n" +
                                "}")));

        // Setup data phase - get 50 records from position 201
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("50"))
                .withQueryParam("pageToken", equalTo("skip_token_200"))
                .withQueryParam("fields", matching("nextPageToken,groups\\(.*\\)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [" +
                                generateFullGroupsJson(201, 250) +
                                "  ]\n" +
                                "}")));

        // Set offset=201 (skip first 200) and pageSize=50
        OperationOptions options = new OperationOptionsBuilder()
                .setPagedResultsOffset(201)
                .setPageSize(50)
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with large offset
        connectorFacade.search(ObjectClass.GROUP, null, results::add, options);

        // Verify results - should get group201 to group250
        assertThat(results).hasSize(50);
        assertThat(results.get(0).getUid().getUidValue()).isEqualTo("group201");
        assertThat(results.get(49).getUid().getUidValue()).isEqualTo("group250");

        // Verify skip phase used config max (200)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("200"))
                .withQueryParam("fields", equalTo("nextPageToken,groups(id)")));

        // Verify data phase used pageSize (50)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("50"))
                .withQueryParam("pageToken", equalTo("skip_token_200")));
    }

    @Test
    @DisplayName("Test offset=1, pageSize=250 (exceeds config max 200)")
    public void testPageSizeExceedsConfigMax() {
        // Setup first data phase - should use config max (200) not pageSize (250)
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("200"))
                .withQueryParam("fields", matching("nextPageToken,groups\\(.*\\)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [" +
                                generateFullGroupsJson(1, 200) +
                                "  ],\n" +
                                "  \"nextPageToken\": \"data_token_200\"\n" +
                                "}")));

        // Setup second data phase - get remaining 50 records
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("50"))
                .withQueryParam("pageToken", equalTo("data_token_200"))
                .withQueryParam("fields", matching("nextPageToken,groups\\(.*\\)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [" +
                                generateFullGroupsJson(201, 250) +
                                "  ]\n" +
                                "}")));

        // Set offset=1, pageSize=250 (exceeds config max of 200)
        OperationOptions options = new OperationOptionsBuilder()
                .setPagedResultsOffset(1)
                .setPageSize(250)
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search
        connectorFacade.search(ObjectClass.GROUP, null, results::add, options);

        // Verify all 250 results returned
        assertThat(results).hasSize(250);
        assertThat(results.get(0).getUid().getUidValue()).isEqualTo("group001");
        assertThat(results.get(249).getUid().getUidValue()).isEqualTo("group250");

        // Verify first data phase used config max (200)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("200")));

        // Verify second data phase used remaining (50)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("50"))
                .withQueryParam("pageToken", equalTo("data_token_200")));
    }

    @Test
    @DisplayName("Test offset exceeds total records")
    public void testOffsetExceedsTotalRecords() {
        // Setup skip phase - return fewer records than expected, no nextPageToken
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("49"))
                .withQueryParam("fields", equalTo("nextPageToken,groups(id)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [\n" +
                                "    {\"id\": \"last_group\"}\n" +
                                "  ]\n" +
                                "}")));

        // Set offset=50 but only 1 group exists
        OperationOptions options = new OperationOptionsBuilder()
                .setPagedResultsOffset(50)
                .setPageSize(10)
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with offset beyond available data
        connectorFacade.search(ObjectClass.GROUP, null, results::add, options);

        // Verify no results returned
        assertThat(results).isEmpty();

        // Verify only skip phase was called, no data phase
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("49"))
                .withQueryParam("fields", equalTo("nextPageToken,groups(id)")));

        // Verify data phase was NOT called (no pageToken provided)
        GoogleApiMockServer.verify(0, getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("pageToken", matching(".*")));
    }

    @Test
    @DisplayName("Test offset=601, pageSize=10 (multiple skip phases)")
    public void testMultipleSkipPhases() {
        // Setup first skip phase - skip 200 records (config max)
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("200"))
                .withQueryParam("fields", equalTo("nextPageToken,groups(id)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [" +
                                generateGroupsJson(1, 200) +
                                "  ],\n" +
                                "  \"nextPageToken\": \"skip_token_200\"\n" +
                                "}")));

        // Setup second skip phase - skip next 200 records
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("200"))
                .withQueryParam("pageToken", equalTo("skip_token_200"))
                .withQueryParam("fields", equalTo("nextPageToken,groups(id)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [" +
                                generateGroupsJson(201, 400) +
                                "  ],\n" +
                                "  \"nextPageToken\": \"skip_token_400\"\n" +
                                "}")));

        // Setup third skip phase - skip next 200 records
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("200"))
                .withQueryParam("pageToken", equalTo("skip_token_400"))
                .withQueryParam("fields", equalTo("nextPageToken,groups(id)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [" +
                                generateGroupsJson(401, 600) +
                                "  ],\n" +
                                "  \"nextPageToken\": \"skip_token_600\"\n" +
                                "}")));

        // Setup data phase - get 10 records from position 601
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("10"))
                .withQueryParam("pageToken", equalTo("skip_token_600"))
                .withQueryParam("fields", matching("nextPageToken,groups\\(.*\\)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [" +
                                generateFullGroupsJson(601, 610) +
                                "  ]\n" +
                                "}")));

        // Set offset=601 (skip first 600) and pageSize=10
        OperationOptions options = new OperationOptionsBuilder()
                .setPagedResultsOffset(601)  // Skip first 600 records
                .setPageSize(10)
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with large offset requiring multiple skip phases
        connectorFacade.search(ObjectClass.GROUP, null, results::add, options);

        // Verify results - should get group601 to group610
        assertThat(results).hasSize(10);
        assertThat(results.get(0).getUid().getUidValue()).isEqualTo("group601");
        assertThat(results.get(9).getUid().getUidValue()).isEqualTo("group610");

        // Verify first skip phase (200 records)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("200"))
                .withQueryParam("fields", equalTo("nextPageToken,groups(id)"))
                .withQueryParam("pageToken", absent()));

        // Verify second skip phase (200 more records)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("200"))
                .withQueryParam("pageToken", equalTo("skip_token_200"))
                .withQueryParam("fields", equalTo("nextPageToken,groups(id)")));

        // Verify third skip phase (200 more records)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("200"))
                .withQueryParam("pageToken", equalTo("skip_token_400"))
                .withQueryParam("fields", equalTo("nextPageToken,groups(id)")));

        // Verify data phase (10 target records)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("10"))
                .withQueryParam("pageToken", equalTo("skip_token_600"))
                .withQueryParam("fields", matching("nextPageToken,groups\\(.*\\)")));
    }

    @Test
    @DisplayName("Test offset=201, pageSize=5 (boundary: exactly one page + 1)")
    public void testBoundaryOffsetExactlyOnePlusOne() {
        // Setup first skip phase - skip exactly 200 records (config max)
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("200"))
                .withQueryParam("fields", equalTo("nextPageToken,groups(id)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [" +
                                generateGroupsJson(1, 200) +
                                "  ],\n" +
                                "  \"nextPageToken\": \"skip_token_200\"\n" +
                                "}")));

        // Setup data phase - get 5 records from position 201
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("5"))
                .withQueryParam("pageToken", equalTo("skip_token_200"))
                .withQueryParam("fields", matching("nextPageToken,groups\\(.*\\)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [" +
                                generateFullGroupsJson(201, 205) +
                                "  ]\n" +
                                "}")));

        // Set offset=201 (skip exactly 200, right at boundary) and pageSize=5
        OperationOptions options = new OperationOptionsBuilder()
                .setPagedResultsOffset(201)  // Exactly config max + 1
                .setPageSize(5)
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with boundary offset
        connectorFacade.search(ObjectClass.GROUP, null, results::add, options);

        // Verify results - should get group201 to group205
        assertThat(results).hasSize(5);
        assertThat(results.get(0).getUid().getUidValue()).isEqualTo("group201");
        assertThat(results.get(4).getUid().getUidValue()).isEqualTo("group205");

        // Verify exactly ONE skip phase (200 records)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("200"))
                .withQueryParam("fields", equalTo("nextPageToken,groups(id)"))
                .withQueryParam("pageToken", absent()));

        // Verify data phase
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("5"))
                .withQueryParam("pageToken", equalTo("skip_token_200"))
                .withQueryParam("fields", matching("nextPageToken,groups\\(.*\\)")));
    }

    @Test
    @DisplayName("Test offset=202, pageSize=5 (boundary: one page + 2)")
    public void testBoundaryOffsetOnePlusTwo() {
        // Setup first skip phase - skip 200 records (config max)
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("200"))
                .withQueryParam("fields", equalTo("nextPageToken,groups(id)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [" +
                                generateGroupsJson(1, 200) +
                                "  ],\n" +
                                "  \"nextPageToken\": \"skip_token_200\"\n" +
                                "}")));

        // Setup second skip phase - skip 1 more record
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("1"))
                .withQueryParam("pageToken", equalTo("skip_token_200"))
                .withQueryParam("fields", equalTo("nextPageToken,groups(id)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [\n" +
                                "    {\"id\": \"group201\"}\n" +
                                "  ],\n" +
                                "  \"nextPageToken\": \"skip_token_201\"\n" +
                                "}")));

        // Setup data phase - get 5 records from position 202
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("5"))
                .withQueryParam("pageToken", equalTo("skip_token_201"))
                .withQueryParam("fields", matching("nextPageToken,groups\\(.*\\)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [" +
                                generateFullGroupsJson(202, 206) +
                                "  ]\n" +
                                "}")));

        // Set offset=202 (skip 201 records, requires 2 skip phases) and pageSize=5
        OperationOptions options = new OperationOptionsBuilder()
                .setPagedResultsOffset(202)  // Config max + 2, requires 2 skip phases
                .setPageSize(5)
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with boundary offset requiring 2 skip phases
        connectorFacade.search(ObjectClass.GROUP, null, results::add, options);

        // Verify results - should get group202 to group206
        assertThat(results).hasSize(5);
        assertThat(results.get(0).getUid().getUidValue()).isEqualTo("group202");
        assertThat(results.get(4).getUid().getUidValue()).isEqualTo("group206");

        // Verify first skip phase (200 records)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("200"))
                .withQueryParam("fields", equalTo("nextPageToken,groups(id)"))
                .withQueryParam("pageToken", absent()));

        // Verify second skip phase (1 record)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("1"))
                .withQueryParam("pageToken", equalTo("skip_token_200"))
                .withQueryParam("fields", equalTo("nextPageToken,groups(id)")));

        // Verify data phase
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("5"))
                .withQueryParam("pageToken", equalTo("skip_token_201"))
                .withQueryParam("fields", matching("nextPageToken,groups\\(.*\\)")));
    }

    @Test
    @DisplayName("Test multiple page skipping with config max")
    public void testMultiplePageSkipWithConfigMax() {
        // Setup first skip page - use config max (200)
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("200"))
                .withQueryParam("fields", equalTo("nextPageToken,groups(id)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [" +
                                generateGroupsJson(1, 200) +
                                "  ],\n" +
                                "  \"nextPageToken\": \"skip_token_200\"\n" +
                                "}")));

        // Setup second skip page - skip remaining 50 records
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("50"))
                .withQueryParam("pageToken", equalTo("skip_token_200"))
                .withQueryParam("fields", equalTo("nextPageToken,groups(id)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [" +
                                generateGroupsJson(201, 250) +
                                "  ],\n" +
                                "  \"nextPageToken\": \"skip_token_250\"\n" +
                                "}")));

        // Setup data phase - get 25 records from position 251
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("25"))
                .withQueryParam("pageToken", equalTo("skip_token_250"))
                .withQueryParam("fields", matching("nextPageToken,groups\\(.*\\)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [" +
                                generateFullGroupsJson(251, 275) +
                                "  ]\n" +
                                "}")));

        // Set offset=251 (skip first 250) and pageSize=25
        OperationOptions options = new OperationOptionsBuilder()
                .setPagedResultsOffset(251)
                .setPageSize(25)
                .build();

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search with large offset requiring multiple skip pages
        connectorFacade.search(ObjectClass.GROUP, null, results::add, options);

        // Verify results - should get group251 to group275
        assertThat(results).hasSize(25);
        assertThat(results.get(0).getUid().getUidValue()).isEqualTo("group251");
        assertThat(results.get(24).getUid().getUidValue()).isEqualTo("group275");

        // Verify first skip phase used config max (200)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("200"))
                .withQueryParam("fields", equalTo("nextPageToken,groups(id)")));

        // Verify second skip phase used remaining (50)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("50"))
                .withQueryParam("pageToken", equalTo("skip_token_200"))
                .withQueryParam("fields", equalTo("nextPageToken,groups(id)")));

        // Verify data phase used pageSize (25)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("maxResults", equalTo("25"))
                .withQueryParam("pageToken", equalTo("skip_token_250")));
    }

    /**
     * Generate JSON for groups with only ID field (for skip phase)
     */
    private String generateGroupsJson(int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end; i++) {
            if (i > start) sb.append(",");
            sb.append(String.format("{\"id\": \"group%03d\"}", i));
        }
        return sb.toString();
    }

    /**
     * Generate JSON for groups with full fields (for data phase)
     */
    private String generateFullGroupsJson(int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end; i++) {
            if (i > start) sb.append(",");
            sb.append(String.format(
                    "{\n" +
                            "      \"id\": \"group%03d\",\n" +
                            "      \"etag\": \"\\\"group%03d-etag\\\"\",\n" +
                            "      \"email\": \"group%03d@example.com\",\n" +
                            "      \"name\": \"Group %03d\",\n" +
                            "      \"description\": \"Test Group %03d\"\n" +
                            "    }", i, i, i, i, i));
        }
        return sb.toString();
    }
}