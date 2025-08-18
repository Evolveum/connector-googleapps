package com.evolveum.polygon.connector.googleapps;

import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import org.identityconnectors.framework.common.objects.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Tests for Google Apps Group search with orderBy functionality.
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsGroupSearchOrderByTest extends GoogleAppsConnectorTestBase {

    @BeforeEach
    public void setUp() {
        super.setUp();

        // Setup basic group list endpoint stub
        setupGroupListStub();
    }

    private void setupGroupListStub() {
        GoogleApiMockServer.stubFor(get(urlPathEqualTo("/admin/directory/v1/groups"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [\n" +
                                "    {\n" +
                                "      \"id\": \"group001\",\n" +
                                "      \"email\": \"group001@example.com\",\n" +
                                "      \"name\": \"Test Group 1\",\n" +
                                "      \"etag\": \"\\\"etag001\\\"\"\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"id\": \"group002\",\n" +
                                "      \"email\": \"group002@example.com\",\n" +
                                "      \"name\": \"Test Group 2\",\n" +
                                "      \"etag\": \"\\\"etag002\\\"\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));
    }

    @Test
    public void testOrderByEmailAscending() {
        OperationOptions options = new OperationOptionsBuilder()
                .setSortKeys(new SortKey(Name.NAME, true)) // true = ascending
                .build();

        connectorFacade.search(ObjectClass.GROUP, null, obj -> true, options);

        RequestPatternBuilder expectedRequest = getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("orderBy", equalTo("email"))
                .withQueryParam("sortOrder", equalTo("ASCENDING"));

        GoogleApiMockServer.verifyRequest(expectedRequest);
    }

    @Test
    public void testOrderByEmailDescending() {
        OperationOptions options = new OperationOptionsBuilder()
                .setSortKeys(new SortKey(Name.NAME, false)) // false = descending
                .build();

        connectorFacade.search(ObjectClass.GROUP, null, obj -> true, options);

        RequestPatternBuilder expectedRequest = getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("orderBy", equalTo("email"))
                .withQueryParam("sortOrder", equalTo("DESCENDING"));

        GoogleApiMockServer.verifyRequest(expectedRequest);
    }

    @Test
    public void testOrderByNameMapsToEmail() {
        // Name attribute should map to email orderBy for Groups
        OperationOptions options = new OperationOptionsBuilder()
                .setSortKeys(new SortKey("__NAME__", true))
                .build();

        connectorFacade.search(ObjectClass.GROUP, null, obj -> true, options);

        RequestPatternBuilder expectedRequest = getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("orderBy", equalTo("email"))
                .withQueryParam("sortOrder", equalTo("ASCENDING"));

        GoogleApiMockServer.verifyRequest(expectedRequest);
    }

    @Test
    public void testUnsupportedSortKeyIgnored() {
        // Groups API only supports email orderBy, other attributes should be ignored
        OperationOptions options = new OperationOptionsBuilder()
                .setSortKeys(new SortKey("description", true))
                .build();

        connectorFacade.search(ObjectClass.GROUP, null, obj -> true, options);

        RequestPatternBuilder expectedRequest = getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("orderBy", absent())
                .withQueryParam("sortOrder", absent());

        GoogleApiMockServer.verifyRequest(expectedRequest);
    }

    @Test
    public void testMultipleSortKeysFirstValidUsed() {
        // Only first valid sort key should be used
        OperationOptions options = new OperationOptionsBuilder()
                .setSortKeys(
                        new SortKey("description", true), // unsupported, ignored
                        new SortKey(Name.NAME, false),    // supported, should be used
                        new SortKey("name", true)         // would be supported but ignored due to first match
                )
                .build();

        connectorFacade.search(ObjectClass.GROUP, null, obj -> true, options);

        RequestPatternBuilder expectedRequest = getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("orderBy", equalTo("email"))
                .withQueryParam("sortOrder", equalTo("DESCENDING"));

        GoogleApiMockServer.verifyRequest(expectedRequest);
    }

    @Test
    public void testNoSortKeysNoOrderBy() {
        // No sort keys should result in no orderBy parameters
        OperationOptions options = new OperationOptionsBuilder().build();

        connectorFacade.search(ObjectClass.GROUP, null, obj -> true, options);

        RequestPatternBuilder expectedRequest = getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("orderBy", absent())
                .withQueryParam("sortOrder", absent());

        GoogleApiMockServer.verifyRequest(expectedRequest);
    }
}