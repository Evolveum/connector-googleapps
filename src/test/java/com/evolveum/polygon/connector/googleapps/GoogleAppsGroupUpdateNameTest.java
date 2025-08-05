package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GoogleAppsConnector Group Name attribute (email) update operations
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsGroupUpdateNameTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test group name (__NAME__) update - email address change")
    public void testUpdateGroupEmail() {
        // Setup mock response for group email update
        stubFor(patch(urlPathEqualTo("/admin/directory/v1/groups/group001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"group001\",\n" +
                                "  \"etag\": \"\\\"group001-renamed-etag\\\"\",\n" +
                                "  \"email\": \"renamed-group@example.com\",\n" +
                                "  \"name\": \"Test Group\"\n" +
                                "}")));

        // Prepare __NAME__ (email) update modification
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build(Name.NAME, "renamed-group@example.com"));

        // Execute group email update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.GROUP,
                new Uid("group001"), modifications, null);

        // Verify response
        assertThat(result).isNotNull();

        // Verify request was sent with correct JSON and PATCH method
        GoogleApiMockServer.verifyRequest(patchRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group001"))
                .withRequestBody(equalToJson("{\n" +
                        "  \"email\": \"renamed-group@example.com\"\n" +
                        "}")));
    }
}