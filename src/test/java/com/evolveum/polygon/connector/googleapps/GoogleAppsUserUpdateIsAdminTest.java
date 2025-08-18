package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GoogleAppsConnector User isAdmin attribute update operations
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsUserUpdateIsAdminTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Update isAdmin to true")
    public void testUpdateIsAdminToTrue() {
        // Setup mock for makeAdmin API call
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users/user001/makeAdmin"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"status\": \"adminUserMade\"\n" +
                                "}")));

        // Prepare delta modifications
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("isAdmin", true));

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("user001"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify makeAdmin request with status=true
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users/user001/makeAdmin"))
                .withRequestBody(containing("\"status\":true")));
    }

    @Test
    @DisplayName("Update isAdmin to false")
    public void testUpdateIsAdminToFalse() {
        // Setup mock for makeAdmin API call (removing admin privileges)
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users/user002/makeAdmin"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"status\": \"adminPrivilegesRevoked\"\n" +
                                "}")));

        // Prepare delta modifications
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("isAdmin", false));

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("user002"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify makeAdmin request with status=false
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users/user002/makeAdmin"))
                .withRequestBody(containing("\"status\":false")));
    }

    @Test
    @DisplayName("Update isAdmin with other attributes")
    public void testUpdateIsAdminWithOtherAttributes() {
        // Setup mock for user update
        stubFor(put(urlPathEqualTo("/admin/directory/v1/users/user003"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"user003\",\n" +
                                "  \"etag\": \"\\\"user003-updated-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"user003@example.com\"\n" +
                                "}")));

        // Setup mock for makeAdmin API call
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users/user003/makeAdmin"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"status\": \"adminUserMade\"\n" +
                                "}")));

        // Prepare delta modifications - isAdmin and regular attributes
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("givenName", "NewAdmin"));
        modifications.add(AttributeDeltaBuilder.build("familyName", "UpdatedUser"));
        modifications.add(AttributeDeltaBuilder.build("isAdmin", true));

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("user003"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify user update request
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathEqualTo("/admin/directory/v1/users/user003"))
                .withRequestBody(containing("\"givenName\":\"NewAdmin\""))
                .withRequestBody(containing("\"familyName\":\"UpdatedUser\"")));

        // Verify makeAdmin request
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users/user003/makeAdmin"))
                .withRequestBody(containing("\"status\":true")));
    }
}