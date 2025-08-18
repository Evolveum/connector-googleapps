package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GoogleAppsConnector Group single-valued attribute update operations
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsGroupUpdateSingleValueTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test group update with basic attributes")
    public void testUpdateGroup() {
        // Setup mock response for group update
        stubFor(patch(urlPathEqualTo("/admin/directory/v1/groups/group001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"group001\",\n" +
                                "  \"etag\": \"\\\"group001-updated-etag\\\"\",\n" +
                                "  \"email\": \"group001@example.com\",\n" +
                                "  \"name\": \"Updated Group Name\",\n" +
                                "  \"description\": \"Updated description\"\n" +
                                "}")));

        // Prepare update modifications
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("name", "Updated Group Name"));
        modifications.add(AttributeDeltaBuilder.build(PredefinedAttributes.DESCRIPTION, "Updated description"));

        // Execute group update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.GROUP,
                new Uid("group001"), modifications, null);

        // Verify response
        assertThat(result).isNotNull();

        // Verify request was sent with correct JSON and PATCH method
        GoogleApiMockServer.verifyRequest(patchRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group001"))
                .withRequestBody(equalToJson("{\n" +
                        "  \"name\": \"Updated Group Name\",\n" +
                        "  \"description\": \"Updated description\"\n" +
                        "}")));
    }

    @Test
    @DisplayName("Test update group name attribute")
    public void testUpdateGroupName() {
        // Setup mock response for group name update
        stubFor(patch(urlPathEqualTo("/admin/directory/v1/groups/group011"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"group011\",\n" +
                                "  \"etag\": \"\\\"group011-name-updated-etag\\\"\",\n" +
                                "  \"email\": \"namegroup@example.com\",\n" +
                                "  \"name\": \"New Group Name\",\n" +
                                "  \"description\": \"Original description\"\n" +
                                "}")));

        // Prepare update modifications - name only
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("name", "New Group Name"));

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.GROUP,
                new Uid("group011"), modifications, null);

        // Verify response
        assertThat(result).isNotNull();

        // Verify request was sent with correct JSON and PATCH method
        GoogleApiMockServer.verifyRequest(patchRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group011"))
                .withRequestBody(containing("\"name\":\"New Group Name\"")));
    }

    @Test
    @DisplayName("Test update group description attribute")
    public void testUpdateGroupDescription() {
        // Setup mock response for group description update
        stubFor(patch(urlPathEqualTo("/admin/directory/v1/groups/group012"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"group012\",\n" +
                                "  \"etag\": \"\\\"group012-desc-updated-etag\\\"\",\n" +
                                "  \"email\": \"descgroup@example.com\",\n" +
                                "  \"name\": \"Test Group\",\n" +
                                "  \"description\": \"New description for testing\"\n" +
                                "}")));

        // Prepare update modifications - description only
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build(PredefinedAttributes.DESCRIPTION, "New description for testing"));

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.GROUP,
                new Uid("group012"), modifications, null);

        // Verify response
        assertThat(result).isNotNull();

        // Verify request was sent with correct JSON and PATCH method
        GoogleApiMockServer.verifyRequest(patchRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group012"))
                .withRequestBody(containing("\"description\":\"New description for testing\"")));
    }

    @Test
    @DisplayName("Test clear group description")
    public void testClearGroupDescription() {
        // Setup mock response for group description clearing
        stubFor(patch(urlPathEqualTo("/admin/directory/v1/groups/group013"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"group013\",\n" +
                                "  \"etag\": \"\\\"group013-desc-cleared-etag\\\"\",\n" +
                                "  \"email\": \"cleargroup@example.com\",\n" +
                                "  \"name\": \"Clear Test Group\"\n" +
                                "}")));

        // Prepare update modifications - clear description using empty list
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build(PredefinedAttributes.DESCRIPTION, Collections.emptyList()));

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.GROUP,
                new Uid("group013"), modifications, null);

        // Verify response
        assertThat(result).isNotNull();

        // Verify request was sent with correct JSON and PATCH method - expect null value for clearing
        GoogleApiMockServer.verifyRequest(patchRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group013"))
                .withRequestBody(containing("\"description\":null")));
    }
}