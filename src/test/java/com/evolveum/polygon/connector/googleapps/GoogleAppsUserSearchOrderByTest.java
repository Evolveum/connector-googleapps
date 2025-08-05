package com.evolveum.polygon.connector.googleapps;

import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import org.identityconnectors.framework.common.objects.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Tests for GoogleAppsConnector User search orderBy functionality
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsUserSearchOrderByTest extends GoogleAppsConnectorTestBase {

    @BeforeEach
    public void setUp() {
        super.setUp();

        // Setup basic user list endpoint stub
        setupUserListStub();
    }

    private void setupUserListStub() {
        GoogleApiMockServer.stubFor(get(urlPathEqualTo("/admin/directory/v1/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"kind\": \"admin#directory#users\",\n" +
                                "  \"users\": [\n" +
                                "    {\n" +
                                "      \"kind\": \"admin#directory#user\",\n" +
                                "      \"id\": \"user001\",\n" +
                                "      \"primaryEmail\": \"alice@example.com\",\n" +
                                "      \"name\": {\n" +
                                "        \"givenName\": \"Alice\",\n" +
                                "        \"familyName\": \"Anderson\"\n" +
                                "      }\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"kind\": \"admin#directory#user\",\n" +
                                "      \"id\": \"user002\",\n" +
                                "      \"primaryEmail\": \"bob@example.com\",\n" +
                                "      \"name\": {\n" +
                                "        \"givenName\": \"Bob\",\n" +
                                "        \"familyName\": \"Brown\"\n" +
                                "      }\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));
    }

    @Test
    public void testOrderByNameAscending() {
        // Test basic name (email) sorting - ascending
        OperationOptions options = new OperationOptionsBuilder()
                .setSortKeys(new SortKey("__NAME__", true))
                .build();

        connectorFacade.search(ObjectClass.ACCOUNT, null, obj -> true, options);

        RequestPatternBuilder expectedRequest = getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("orderBy", equalTo("email"))
                .withQueryParam("sortOrder", equalTo("ASCENDING"));

        GoogleApiMockServer.verifyRequest(expectedRequest);
    }

    @Test
    public void testOrderByNameDescending() {
        // Test basic name (email) sorting - descending
        OperationOptions options = new OperationOptionsBuilder()
                .setSortKeys(new SortKey("__NAME__", false))
                .build();

        connectorFacade.search(ObjectClass.ACCOUNT, null, obj -> true, options);

        RequestPatternBuilder expectedRequest = getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("orderBy", equalTo("email"))
                .withQueryParam("sortOrder", equalTo("DESCENDING"));

        GoogleApiMockServer.verifyRequest(expectedRequest);
    }

    @Test
    public void testEmailRelatedAttributesMapping() {
        // Test that __NAME__ and aliases both map to email orderBy
        // This tests the mapping logic in UserHandler
        OperationOptions options1 = new OperationOptionsBuilder()
                .setSortKeys(new SortKey(Name.NAME, true))
                .build();

        OperationOptions options2 = new OperationOptionsBuilder()
                .setSortKeys(new SortKey("aliases", true))
                .build();

        // All should result in the same orderBy=email parameter
        connectorFacade.search(ObjectClass.ACCOUNT, null, obj -> true, options1);
        connectorFacade.search(ObjectClass.ACCOUNT, null, obj -> true, options2);

        RequestPatternBuilder expectedRequest = getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("orderBy", equalTo("email"))
                .withQueryParam("sortOrder", equalTo("ASCENDING"));

        // Verify both requests used email orderBy
        GoogleApiMockServer.verify(2, expectedRequest);
    }

    @Test
    public void testOrderByGivenNameAscending() {
        OperationOptions options = new OperationOptionsBuilder()
                .setSortKeys(new SortKey("givenName", true))
                .build();

        connectorFacade.search(ObjectClass.ACCOUNT, null, obj -> true, options);

        RequestPatternBuilder expectedRequest = getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("orderBy", equalTo("givenName"))
                .withQueryParam("sortOrder", equalTo("ASCENDING"));

        GoogleApiMockServer.verifyRequest(expectedRequest);
    }

    @Test
    public void testOrderByGivenNameDescending() {
        OperationOptions options = new OperationOptionsBuilder()
                .setSortKeys(new SortKey("givenName", false))
                .build();

        connectorFacade.search(ObjectClass.ACCOUNT, null, obj -> true, options);

        RequestPatternBuilder expectedRequest = getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("orderBy", equalTo("givenName"))
                .withQueryParam("sortOrder", equalTo("DESCENDING"));

        GoogleApiMockServer.verifyRequest(expectedRequest);
    }

    @Test
    public void testOrderByFamilyNameAscending() {
        OperationOptions options = new OperationOptionsBuilder()
                .setSortKeys(new SortKey("familyName", true))
                .build();

        connectorFacade.search(ObjectClass.ACCOUNT, null, obj -> true, options);

        RequestPatternBuilder expectedRequest = getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("orderBy", equalTo("familyName"))
                .withQueryParam("sortOrder", equalTo("ASCENDING"));

        GoogleApiMockServer.verifyRequest(expectedRequest);
    }

    @Test
    public void testOrderByFamilyNameDescending() {
        OperationOptions options = new OperationOptionsBuilder()
                .setSortKeys(new SortKey("familyName", false))
                .build();

        connectorFacade.search(ObjectClass.ACCOUNT, null, obj -> true, options);

        RequestPatternBuilder expectedRequest = getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("orderBy", equalTo("familyName"))
                .withQueryParam("sortOrder", equalTo("DESCENDING"));

        GoogleApiMockServer.verifyRequest(expectedRequest);
    }

    @Test
    public void testUnsupportedSortKey() {
        // Test using an attribute that exists in schema but not supported for orderBy (e.g., orgUnitPath)
        OperationOptions options = new OperationOptionsBuilder()
                .setSortKeys(new SortKey("orgUnitPath", true))
                .build();

        connectorFacade.search(ObjectClass.ACCOUNT, null, obj -> true, options);

        // When unsupported sort key is provided, no orderBy or sortOrder parameters should be added
        RequestPatternBuilder expectedRequest = getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("orderBy", absent())
                .withQueryParam("sortOrder", absent());

        GoogleApiMockServer.verifyRequest(expectedRequest);
    }

    @Test
    public void testMultipleSortKeys() {
        // Google API only supports single orderBy, so the first supported sort key should be used
        OperationOptions options = new OperationOptionsBuilder()
                .setSortKeys(
                        new SortKey("orgUnitPath", true),       // This should be skipped (unsupported for orderBy)
                        new SortKey("givenName", false),        // This should be used
                        new SortKey("familyName", true)         // This should be ignored (after first valid one)
                )
                .build();

        connectorFacade.search(ObjectClass.ACCOUNT, null, obj -> true, options);

        RequestPatternBuilder expectedRequest = getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("orderBy", equalTo("givenName"))
                .withQueryParam("sortOrder", equalTo("DESCENDING"));

        GoogleApiMockServer.verifyRequest(expectedRequest);
    }
}