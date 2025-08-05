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
 * Tests for GoogleAppsConnector User Name attribute (primaryEmail) update operations
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsUserUpdateNameTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test user name (__NAME__) update - primaryEmail change")
    public void testUpdateUserPrimaryEmail() {
        // Setup mock response for primaryEmail update
        stubFor(put(urlPathMatching("/admin/directory/v1/users/test001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"test001\",\n" +
                                "  \"etag\": \"\\\"test001-renamed-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"renamed-user@example.com\",\n" +
                                "  \"name\": {\n" +
                                "    \"givenName\": \"Test\",\n" +
                                "    \"familyName\": \"User\"\n" +
                                "  }\n" +
                                "}")));

        // Prepare __NAME__ (primaryEmail) update modification
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build(Name.NAME, "renamed-user@example.com"));

        // Execute primaryEmail update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("test001"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify request was sent with correct JSON
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathMatching("/admin/directory/v1/users/test001"))
                .withRequestBody(equalToJson("{\n" +
                        "  \"primaryEmail\": \"renamed-user@example.com\"\n" +
                        "}")));
    }
}