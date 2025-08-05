package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GoogleAppsConnector User aliases attribute update operations
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsUserUpdateAliasesTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Update aliases - Replace all aliases")
    public void testUpdateAliasesReplace() {
        // Setup mock for aliases list (current state)
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users/user003/aliases"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"aliases\": [\n" +
                                "    {\"alias\": \"old1@example.com\"},\n" +
                                "    {\"alias\": \"old2@example.com\"}\n" +
                                "  ]\n" +
                                "}")));

        // Setup mocks for alias deletions
        stubFor(delete(urlPathEqualTo("/admin/directory/v1/users/user003/aliases/old1@example.com"))
                .willReturn(aResponse().withStatus(204)));
        stubFor(delete(urlPathEqualTo("/admin/directory/v1/users/user003/aliases/old2@example.com"))
                .willReturn(aResponse().withStatus(204)));

        // Setup mocks for new alias creations
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users/user003/aliases"))
                .withRequestBody(containing("new1@example.com"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"alias\": \"new1@example.com\",\n" +
                                "  \"id\": \"alias003\",\n" +
                                "  \"etag\": \"\\\"alias003-etag\\\"\"\n" +
                                "}")));

        // Prepare delta modifications - replace operation
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("aliases",
                Arrays.asList("new1@example.com"))); // valuesToReplace

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("user003"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify current aliases were fetched
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users/user003/aliases")));

        // Verify old aliases were deleted
        GoogleApiMockServer.verifyRequest(deleteRequestedFor(urlPathEqualTo("/admin/directory/v1/users/user003/aliases/old1@example.com")));
        GoogleApiMockServer.verifyRequest(deleteRequestedFor(urlPathEqualTo("/admin/directory/v1/users/user003/aliases/old2@example.com")));

        // Verify new alias was created
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users/user003/aliases"))
                .withRequestBody(containing("\"alias\":\"new1@example.com\"")));
    }

    @Test
    @DisplayName("Update aliases - Add aliases")
    public void testUpdateAliasesAdd() {
        // Setup mocks for alias additions
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users/user004/aliases"))
                .withRequestBody(containing("added1@example.com"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"alias\": \"added1@example.com\",\n" +
                                "  \"id\": \"alias004\",\n" +
                                "  \"etag\": \"\\\"alias004-etag\\\"\"\n" +
                                "}")));

        stubFor(post(urlPathEqualTo("/admin/directory/v1/users/user004/aliases"))
                .withRequestBody(containing("added2@example.com"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"alias\": \"added2@example.com\",\n" +
                                "  \"id\": \"alias005\",\n" +
                                "  \"etag\": \"\\\"alias005-etag\\\"\"\n" +
                                "}")));

        // Prepare delta modifications - add operation
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("aliases",
                Arrays.asList("added1@example.com", "added2@example.com"), // valuesToAdd
                null)); // valuesToRemove

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("user004"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify alias additions
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users/user004/aliases"))
                .withRequestBody(containing("\"alias\":\"added1@example.com\"")));
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users/user004/aliases"))
                .withRequestBody(containing("\"alias\":\"added2@example.com\"")));
    }

    @Test
    @DisplayName("Update aliases - Remove aliases")
    public void testUpdateAliasesRemove() {
        // Setup mocks for alias deletions
        stubFor(delete(urlPathEqualTo("/admin/directory/v1/users/user005/aliases/remove1@example.com"))
                .willReturn(aResponse().withStatus(204)));
        stubFor(delete(urlPathEqualTo("/admin/directory/v1/users/user005/aliases/remove2@example.com"))
                .willReturn(aResponse().withStatus(204)));

        // Prepare delta modifications - remove operation
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("aliases",
                null, // valuesToAdd
                Arrays.asList("remove1@example.com", "remove2@example.com"))); // valuesToRemove

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("user005"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify alias deletions
        GoogleApiMockServer.verifyRequest(deleteRequestedFor(urlPathEqualTo("/admin/directory/v1/users/user005/aliases/remove1@example.com")));
        GoogleApiMockServer.verifyRequest(deleteRequestedFor(urlPathEqualTo("/admin/directory/v1/users/user005/aliases/remove2@example.com")));
    }

    @Test
    @DisplayName("Update aliases - Clear all aliases")
    public void testUpdateAliasesClear() {
        // Setup mock for aliases list (current state)
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users/user006/aliases"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"aliases\": [\n" +
                                "    {\"alias\": \"clear1@example.com\"},\n" +
                                "    {\"alias\": \"clear2@example.com\"}\n" +
                                "  ]\n" +
                                "}")));

        // Setup mocks for alias deletions
        stubFor(delete(urlPathEqualTo("/admin/directory/v1/users/user006/aliases/clear1@example.com"))
                .willReturn(aResponse().withStatus(204)));
        stubFor(delete(urlPathEqualTo("/admin/directory/v1/users/user006/aliases/clear2@example.com"))
                .willReturn(aResponse().withStatus(204)));

        // Prepare delta modifications - replace with empty list
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("aliases",
                Collections.emptyList())); // valuesToReplace - clear all aliases

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("user006"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify current aliases were fetched
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users/user006/aliases")));

        // Verify all aliases were deleted
        GoogleApiMockServer.verifyRequest(deleteRequestedFor(urlPathEqualTo("/admin/directory/v1/users/user006/aliases/clear1@example.com")));
        GoogleApiMockServer.verifyRequest(deleteRequestedFor(urlPathEqualTo("/admin/directory/v1/users/user006/aliases/clear2@example.com")));
    }

    @Test
    @DisplayName("Update aliases - Mixed add and remove operations")
    public void testUpdateAliasesMixedAddRemove() {
        // Setup mock for alias addition
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users/user007/aliases"))
                .withRequestBody(containing("mixed-add@example.com"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"alias\": \"mixed-add@example.com\",\n" +
                                "  \"id\": \"alias007\",\n" +
                                "  \"etag\": \"\\\"alias007-etag\\\"\"\n" +
                                "}")));

        // Setup mock for alias deletion
        stubFor(delete(urlPathEqualTo("/admin/directory/v1/users/user007/aliases/mixed-remove@example.com"))
                .willReturn(aResponse().withStatus(204)));

        // Prepare delta modifications - mixed operations
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("aliases",
                Arrays.asList("mixed-add@example.com"), // valuesToAdd
                Arrays.asList("mixed-remove@example.com"))); // valuesToRemove

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("user007"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify alias addition
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users/user007/aliases"))
                .withRequestBody(containing("\"alias\":\"mixed-add@example.com\"")));

        // Verify alias deletion
        GoogleApiMockServer.verifyRequest(deleteRequestedFor(urlPathEqualTo("/admin/directory/v1/users/user007/aliases/mixed-remove@example.com")));
    }
}