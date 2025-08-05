package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GoogleAppsConnector User multi-valued attribute update operations with replace
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsUserUpdateMultiValueReplaceTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test update user multi-valued attributes with replace operations")
    public void testUpdateUserMultiValuedAttributesReplace() {
        // Setup mock response for multi-valued attributes update
        stubFor(put(urlPathMatching("/admin/directory/v1/users/test003"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"test003\",\n" +
                                "  \"etag\": \"\\\"test003-multivalue-updated-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"test003@example.com\"\n" +
                                "}")));

        // Prepare multi-valued attributes for replace operations
        Set<AttributeDelta> modifications = new HashSet<>();

        // Replace emails with new values
        List<String> updatedEmails = Arrays.asList(
                "{\"address\":\"new-work@example.com\",\"type\":\"work\",\"primary\":false}",
                "{\"address\":\"new-home@example.com\",\"type\":\"home\",\"primary\":false}");
        modifications.add(AttributeDeltaBuilder.build("emails", updatedEmails));

        // Replace phones with new values
        List<String> updatedPhones = Arrays.asList(
                "{\"value\":\"+1-555-111-0000\",\"type\":\"work\",\"primary\":true}",
                "{\"value\":\"+1-555-222-0000\",\"type\":\"mobile\",\"primary\":false}");
        modifications.add(AttributeDeltaBuilder.build("phones", updatedPhones));

        // Replace addresses with new values
        List<String> updatedAddresses = Arrays.asList(
                "{\"type\":\"home\",\"formatted\":\"456 Home Ave, City, State 67890\",\"primary\":true}");
        modifications.add(AttributeDeltaBuilder.build("addresses", updatedAddresses));

        // Replace organizations with new values
        List<String> updatedOrganizations = Arrays.asList(
                "{\"name\":\"New Company\",\"title\":\"Senior Engineer\",\"primary\":true,\"type\":\"work\"}");
        modifications.add(AttributeDeltaBuilder.build("organizations", updatedOrganizations));

        // Replace ims with new values
        List<String> updatedIms = Arrays.asList(
                "{\"im\":\"new-user@example.org\",\"protocol\":\"jabber\",\"type\":\"work\",\"primary\":true}");
        modifications.add(AttributeDeltaBuilder.build("ims", updatedIms));

        // Replace externalIds with new values
        List<String> updatedExternalIds = Arrays.asList(
                "{\"value\":\"NEW-EMP456\",\"type\":\"organization\"}");
        modifications.add(AttributeDeltaBuilder.build("externalIds", updatedExternalIds));

        // Replace relations with new values
        List<String> updatedRelations = Arrays.asList(
                "{\"value\":\"new-manager@example.com\",\"type\":\"manager\"}");
        modifications.add(AttributeDeltaBuilder.build("relations", updatedRelations));

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("test003"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify request contains updated multi-valued fields with full JSON verification
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathMatching("/admin/directory/v1/users/test003"))
                .withHeader("Content-Type", matching("application/json.*"))
                // Verify emails array contains both new entries
                .withRequestBody(containing("{\"address\":\"new-work@example.com\",\"type\":\"work\",\"primary\":\"false\"}"))
                .withRequestBody(containing("{\"address\":\"new-home@example.com\",\"type\":\"home\",\"primary\":\"false\"}"))
                // Verify phones array contains both new entries
                .withRequestBody(containing("{\"value\":\"+1-555-111-0000\",\"type\":\"work\",\"primary\":\"true\"}"))
                .withRequestBody(containing("{\"value\":\"+1-555-222-0000\",\"type\":\"mobile\",\"primary\":\"false\"}"))
                // Verify addresses array contains new entry
                .withRequestBody(containing("{\"type\":\"home\",\"formatted\":\"456 Home Ave, City, State 67890\",\"primary\":\"true\"}"))
                // Verify organizations array contains new entry
                .withRequestBody(containing("{\"name\":\"New Company\",\"title\":\"Senior Engineer\",\"primary\":\"true\",\"type\":\"work\"}"))
                // Verify ims array contains new entry
                .withRequestBody(containing("{\"im\":\"new-user@example.org\",\"protocol\":\"jabber\",\"type\":\"work\",\"primary\":\"true\"}"))
                // Verify externalIds array contains new entry
                .withRequestBody(containing("{\"value\":\"NEW-EMP456\",\"type\":\"organization\"}"))
                // Verify relations array contains new entry
                .withRequestBody(containing("{\"value\":\"new-manager@example.com\",\"type\":\"manager\"}")));
    }

    @Test
    @DisplayName("Test clearing multi-valued attributes with replace operations")
    public void testClearMultiValuedAttributesWithReplace() {
        // Setup mock response for clearing multi-valued attributes
        stubFor(put(urlPathMatching("/admin/directory/v1/users/test001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"test001\",\n" +
                                "  \"etag\": \"\\\"test001-cleared-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"test001@example.com\"\n" +
                                "}")));

        // Prepare attributes to clear multi-valued fields (empty lists)
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("emails", Collections.emptyList()));
        modifications.add(AttributeDeltaBuilder.build("phones", Collections.emptyList()));
        modifications.add(AttributeDeltaBuilder.build("addresses", Collections.emptyList()));
        modifications.add(AttributeDeltaBuilder.build("organizations", Collections.emptyList()));
        modifications.add(AttributeDeltaBuilder.build("ims", Collections.emptyList()));
        modifications.add(AttributeDeltaBuilder.build("externalIds", Collections.emptyList()));
        modifications.add(AttributeDeltaBuilder.build("relations", Collections.emptyList()));

        // Execute update to clear multi-valued attributes
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("test001"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify request contains empty arrays to clear multi-valued fields
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathMatching("/admin/directory/v1/users/test001"))
                .withHeader("Content-Type", matching("application/json.*"))
                .withRequestBody(containing("\"emails\":[]"))
                .withRequestBody(containing("\"phones\":[]"))
                .withRequestBody(containing("\"addresses\":[]"))
                .withRequestBody(containing("\"organizations\":[]"))
                .withRequestBody(containing("\"ims\":[]"))
                .withRequestBody(containing("\"externalIds\":[]"))
                .withRequestBody(containing("\"relations\":[]")));
    }
}