package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Tests for GoogleAppsConnector Group search filter translation to Google API query parameters.
 * <p>
 * This test class focuses on verifying that ConnID filters are correctly translated
 * to Google API query parameters and request parameters for Group operations:
 * - UID EqualsFilter: GET /admin/directory/v1/groups/{id}
 * - Name EqualsFilter: GET /admin/directory/v1/groups/{email}
 * - Domain search filter: GET /admin/directory/v1/groups?domain=...
 *
 * @author Hiroyuki Wada
 */
@DisplayName("GoogleApps Group Search - Filter Translation Tests")
public class GoogleAppsGroupSearchFilterTest extends GoogleAppsConnectorTestBase {

    // ========== EQUALS FILTER TESTS ==========

    @Test
    @DisplayName("Test EqualsFilter with UID - should trigger single group GET")
    public void testFilterByUid() {
        // Setup mock response for single group get by UID
        stubFor(get(urlPathEqualTo("/admin/directory/v1/groups/filter001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"filter001\",\n" +
                                "  \"etag\": \"\\\"filter001-etag\\\"\",\n" +
                                "  \"email\": \"filtertest@example.com\",\n" +
                                "  \"name\": \"Filter Test Group\",\n" +
                                "  \"description\": \"Group retrieved by UID filter\",\n" +
                                "  \"aliases\": [],\n" +
                                "  \"nonEditableAliases\": [],\n" +
                                "  \"adminCreated\": true,\n" +
                                "  \"directMembersCount\": \"12\"\n" +
                                "}")));

        // Create EqualsFilter for UID
        Filter filter = FilterBuilder.equalTo(new Uid("filter001"));

        // Execute search with UID filter
        connectorFacade.search(ObjectClass.GROUP, filter, obj -> true, null);

        // Verify request was sent to single group GET endpoint (not list endpoint)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/filter001")));

        // Verify no list request was made
        GoogleApiMockServer.verify(0, getRequestedFor(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("customer", equalTo("my_customer")));
    }

    @Test
    @DisplayName("Test EqualsFilter with Name (email) - should trigger single group GET")
    public void testFilterByName() {
        // Setup mock response for single group get by Name (email)
        stubFor(get(urlPathEqualTo("/admin/directory/v1/groups/namefilter@example.com"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"filter002\",\n" +
                                "  \"etag\": \"\\\"filter002-etag\\\"\",\n" +
                                "  \"email\": \"namefilter@example.com\",\n" +
                                "  \"name\": \"Name Filter Test Group\",\n" +
                                "  \"description\": \"Group retrieved by Name filter\",\n" +
                                "  \"aliases\": [],\n" +
                                "  \"nonEditableAliases\": [],\n" +
                                "  \"adminCreated\": false,\n" +
                                "  \"directMembersCount\": \"25\"\n" +
                                "}")));

        // Create EqualsFilter for Name (email)
        Filter filter = FilterBuilder.equalTo(new Name("namefilter@example.com"));

        // Execute search with Name filter
        connectorFacade.search(ObjectClass.GROUP, filter, obj -> true, null);

        // Verify request was sent to single group GET endpoint by email
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/namefilter@example.com")));

        // Verify no list request was made
        GoogleApiMockServer.verify(0, getRequestedFor(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("customer", equalTo("my_customer")));
    }

    @Test
    @DisplayName("Test EqualsFilter with domain attribute - should trigger search with domain parameter")
    public void testFilterByDomain() {
        // Setup mock response for groups search with domain parameter
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("domain", equalTo("example.com"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [\n" +
                                "    {\n" +
                                "      \"id\": \"filter003\",\n" +
                                "      \"etag\": \"\\\"filter003-etag\\\"\",\n" +
                                "      \"email\": \"domainfilter@example.com\",\n" +
                                "      \"name\": \"Domain Filter Test Group\",\n" +
                                "      \"description\": \"Group found by domain search\",\n" +
                                "      \"aliases\": [],\n" +
                                "      \"nonEditableAliases\": [],\n" +
                                "      \"adminCreated\": true,\n" +
                                "      \"directMembersCount\": \"8\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Create EqualsFilter for domain attribute
        Filter filter = FilterBuilder.equalTo(AttributeBuilder.build("domain", "example.com"));

        // Execute search with domain filter
        connectorFacade.search(ObjectClass.GROUP, filter, obj -> true, null);

        // Verify request was sent to groups list endpoint with domain parameter
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("domain", equalTo("example.com")));

        // Verify customer parameter was NOT used (domain and customer are mutually exclusive)
        GoogleApiMockServer.verify(0, getRequestedFor(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("customer", equalTo("my_customer")));
    }

    @Test
    @DisplayName("Test ContainsFilter with __NAME__ attribute - should use query parameter with prefix search")
    public void testContainsFilterByNameWithQuery() {
        // Setup mock response for groups search with query parameter
        stubFor(get(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("customer", equalTo("my_customer"))
                .withQueryParam("query", equalTo("email:admin*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [\n" +
                                "    {\n" +
                                "      \"id\": \"contains001\",\n" +
                                "      \"etag\": \"\\\"contains001-etag\\\"\",\n" +
                                "      \"email\": \"admin-group@example.com\",\n" +
                                "      \"name\": \"Admin Group\",\n" +
                                "      \"description\": \"Group found by contains search\",\n" +
                                "      \"aliases\": [],\n" +
                                "      \"nonEditableAliases\": [],\n" +
                                "      \"adminCreated\": true,\n" +
                                "      \"directMembersCount\": \"3\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Create ContainsFilter for __NAME__ attribute
        Filter filter = FilterBuilder.contains(AttributeBuilder.build(Name.NAME, "admin"));

        // Execute search with contains name filter
        connectorFacade.search(ObjectClass.GROUP, filter, obj -> true, null);

        // Verify request was sent to groups search endpoint with both customer and query parameters
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathMatching("/admin/directory/v1/groups"))
                .withQueryParam("customer", equalTo("my_customer"))
                .withQueryParam("query", equalTo("email:admin*")));
    }

}