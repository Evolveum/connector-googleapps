package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.*;
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
 * Tests for GoogleAppsConnector User basic Get operations (getByUid, getByName)
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsUserGetTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test get user by UID")
    public void testGetUserByUid() {
        // Setup mock response for specific user data
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users/test001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"test001\",\n" +
                                "  \"etag\": \"\\\"test001-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"test001@example.com\",\n" +
                                "  \"name\": {\n" +
                                "    \"givenName\": \"Test\",\n" +
                                "    \"familyName\": \"User\"\n" +
                                "  }\n" +
                                "}")));

        List<ConnectorObject> results = new ArrayList<>();

        // Create UID filter
        Filter filter = FilterBuilder.equalTo(new Uid("test001"));

        // Execute search
        connectorFacade.search(ObjectClass.ACCOUNT, filter, results::add, null);

        // Verify results
        assertThat(results).hasSize(1);
        ConnectorObject user = results.get(0);
        assertThat(user.getUid().getUidValue()).isEqualTo("test001");
        assertThat(user.getName().getNameValue()).isEqualTo("test001@example.com");

        // Verify attributes
        Attribute givenName = user.getAttributeByName("givenName");
        assertThat(givenName).isNotNull();
        assertThat(AttributeUtil.getStringValue(givenName)).isEqualTo("Test");

        Attribute familyName = user.getAttributeByName("familyName");
        assertThat(familyName).isNotNull();
        assertThat(AttributeUtil.getStringValue(familyName)).isEqualTo("User");

        // Verify request was sent via WireMock
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users/test001")));
    }

    @Test
    @DisplayName("Test get user by Name (primaryEmail)")
    public void testGetUserByName() {
        // Setup mock response for Name search (Google uses direct user lookup for primaryEmail)
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users/name001@example.com"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"name001\",\n" +
                                "  \"etag\": \"\\\"name001-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"name001@example.com\",\n" +
                                "  \"name\": {\n" +
                                "    \"givenName\": \"Name\",\n" +
                                "    \"familyName\": \"User\"\n" +
                                "  }\n" +
                                "}")));

        List<ConnectorObject> results = new ArrayList<>();

        // Create Name filter for primaryEmail search
        Filter filter = FilterBuilder.equalTo(new Name("name001@example.com"));

        // Execute search
        connectorFacade.search(ObjectClass.ACCOUNT, filter, results::add, null);

        // Verify results
        assertThat(results).hasSize(1);
        ConnectorObject user = results.get(0);
        assertThat(user.getUid().getUidValue()).isEqualTo("name001");
        assertThat(user.getName().getNameValue()).isEqualTo("name001@example.com");

        // Verify Name attributes
        Attribute givenName = user.getAttributeByName("givenName");
        assertThat(givenName).isNotNull();
        assertThat(AttributeUtil.getStringValue(givenName)).isEqualTo("Name");

        Attribute familyName = user.getAttributeByName("familyName");
        assertThat(familyName).isNotNull();
        assertThat(AttributeUtil.getStringValue(familyName)).isEqualTo("User");

        // Verify request was sent via WireMock (direct user lookup)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users/name001@example.com")));
    }

    @Test
    @DisplayName("Test get non-existent user - 404 error handling")
    public void testGetNonExistentUser() {
        // Setup mock response for 404 error
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users/nonexistent"))
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

        // Execute get by UID for non-existent user
        Filter filter = FilterBuilder.equalTo(new Uid("nonexistent"));
        connectorFacade.search(ObjectClass.ACCOUNT, filter, results::add, null);

        // Verify no results returned (404 errors should result in empty results, not exceptions)
        assertThat(results).isEmpty();

        // Verify the request was made to the correct endpoint
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users/nonexistent")));
    }

    @Test
    @DisplayName("Test get non-existent user by Name - 404 error handling")
    public void testGetNonExistentUserByName() {
        // Setup mock response for 404 error when getting by email
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users/nonexistent@example.com"))
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

        // Execute get by Name for non-existent user
        Filter filter = FilterBuilder.equalTo(new Name("nonexistent@example.com"));
        connectorFacade.search(ObjectClass.ACCOUNT, filter, results::add, null);

        // Verify no results returned (404 errors should result in empty results, not exceptions)
        assertThat(results).isEmpty();

        // Verify the request was made to the correct endpoint
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users/nonexistent@example.com")));
    }
}