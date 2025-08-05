package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GoogleAppsConnector Group single object retrieval (get operations).
 * <p>
 * This class tests single group retrieval by UID and Name, which uses different
 * API endpoints compared to search operations:
 * - UID: GET /admin/directory/v1/groups/{id}
 * - Name: GET /admin/directory/v1/groups/{email}
 * <p>
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsGroupGetTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test get group by UID")
    public void testGetGroupByUid() {
        // Setup mock response for single group get by UID
        stubFor(get(urlPathEqualTo("/admin/directory/v1/groups/group001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"group001\",\n" +
                                "  \"etag\": \"\\\"group001-etag\\\"\",\n" +
                                "  \"email\": \"testgroup@example.com\",\n" +
                                "  \"name\": \"Test Group\",\n" +
                                "  \"description\": \"Test group description\",\n" +
                                "  \"aliases\": [],\n" +
                                "  \"nonEditableAliases\": [],\n" +
                                "  \"adminCreated\": true,\n" +
                                "  \"directMembersCount\": \"0\"\n" +
                                "}")));

        List<ConnectorObject> results = new ArrayList<>();

        // Execute get by UID
        Filter filter = FilterBuilder.equalTo(new Uid("group001"));
        connectorFacade.search(ObjectClass.GROUP, filter, results::add, null);

        // Verify results
        assertThat(results).hasSize(1);
        ConnectorObject group = results.get(0);

        // Verify basic attributes
        assertThat(group.getUid().getUidValue()).isEqualTo("group001");
        assertThat(group.getName().getNameValue()).isEqualTo("testgroup@example.com");

        // Verify group-specific attributes
        assertThat(group.getAttributeByName("name").getValue().get(0)).isEqualTo("Test Group");
        assertThat(group.getAttributeByName("__DESCRIPTION__").getValue().get(0)).isEqualTo("Test group description");

        // Verify the request was made to the correct endpoint
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group001")));
    }

    @Test
    @DisplayName("Test get group by Name (email)")
    public void testGetGroupByName() {
        // Setup mock response for single group get by Name (email)
        stubFor(get(urlPathEqualTo("/admin/directory/v1/groups/testgroup@example.com"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"group002\",\n" +
                                "  \"etag\": \"\\\"group002-etag\\\"\",\n" +
                                "  \"email\": \"testgroup@example.com\",\n" +
                                "  \"name\": \"Test Group by Name\",\n" +
                                "  \"description\": \"Group retrieved by name\",\n" +
                                "  \"aliases\": [],\n" +
                                "  \"nonEditableAliases\": [],\n" +
                                "  \"adminCreated\": true,\n" +
                                "  \"directMembersCount\": \"5\"\n" +
                                "}")));

        List<ConnectorObject> results = new ArrayList<>();

        // Execute get by Name
        Filter filter = FilterBuilder.equalTo(new Name("testgroup@example.com"));
        connectorFacade.search(ObjectClass.GROUP, filter, results::add, null);

        // Verify results
        assertThat(results).hasSize(1);
        ConnectorObject group = results.get(0);

        // Verify basic attributes
        assertThat(group.getUid().getUidValue()).isEqualTo("group002");
        assertThat(group.getName().getNameValue()).isEqualTo("testgroup@example.com");

        // Verify group-specific attributes
        assertThat(group.getAttributeByName("name").getValue().get(0)).isEqualTo("Test Group by Name");
        assertThat(group.getAttributeByName("__DESCRIPTION__").getValue().get(0)).isEqualTo("Group retrieved by name");

        // Verify the request was made to the correct endpoint (using email as identifier)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/testgroup@example.com")));
    }

    @Test
    @DisplayName("Test get non-existent group - 404 error handling")
    public void testGetNonExistentGroup() {
        // Setup mock response for 404 error
        stubFor(get(urlPathEqualTo("/admin/directory/v1/groups/nonexistent"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"error\": {\n" +
                                "    \"code\": 404,\n" +
                                "    \"message\": \"Resource not found.\",\n" +
                                "    \"errors\": [\n" +
                                "      {\n" +
                                "        \"domain\": \"global\",\n" +
                                "        \"reason\": \"notFound\",\n" +
                                "        \"message\": \"Resource not found.\"\n" +
                                "      }\n" +
                                "    ]\n" +
                                "  }\n" +
                                "}")));

        List<ConnectorObject> results = new ArrayList<>();

        // Execute get by UID for non-existent group
        Filter filter = FilterBuilder.equalTo(new Uid("nonexistent"));
        connectorFacade.search(ObjectClass.GROUP, filter, results::add, null);

        // Verify no results returned (404 errors should result in empty results, not exceptions)
        assertThat(results).isEmpty();

        // Verify the request was made to the correct endpoint
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/nonexistent")));
    }

    @Test
    @DisplayName("Test get non-existent group by Name - 404 error handling")
    public void testGetNonExistentGroupByName() {
        // Setup mock response for 404 error when getting by email
        stubFor(get(urlPathEqualTo("/admin/directory/v1/groups/nonexistent@example.com"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"error\": {\n" +
                                "    \"code\": 404,\n" +
                                "    \"message\": \"Resource not found.\",\n" +
                                "    \"errors\": [\n" +
                                "      {\n" +
                                "        \"domain\": \"global\",\n" +
                                "        \"reason\": \"notFound\",\n" +
                                "        \"message\": \"Resource not found.\"\n" +
                                "      }\n" +
                                "    ]\n" +
                                "  }\n" +
                                "}")));

        List<ConnectorObject> results = new ArrayList<>();

        // Execute get by Name for non-existent group
        Filter filter = FilterBuilder.equalTo(new Name("nonexistent@example.com"));
        connectorFacade.search(ObjectClass.GROUP, filter, results::add, null);

        // Verify no results returned (404 errors should result in empty results, not exceptions)
        assertThat(results).isEmpty();

        // Verify the request was made to the correct endpoint
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/nonexistent@example.com")));
    }
}