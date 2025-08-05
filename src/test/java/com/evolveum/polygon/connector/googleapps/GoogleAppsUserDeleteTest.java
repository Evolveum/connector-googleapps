package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Tests for GoogleAppsConnector User Delete operations
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsUserDeleteTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test user deletion")
    public void testDeleteUser() {
        // Setup mock response for deletion (204 No Content)
        stubFor(delete(urlPathEqualTo("/admin/directory/v1/users/test001"))
                .willReturn(aResponse()
                        .withStatus(204))); // No content on successful deletion

        // Execute deletion
        connectorFacade.delete(ObjectClass.ACCOUNT, new Uid("test001"), null);

        // Verify delete request was sent via WireMock
        GoogleApiMockServer.verifyRequest(deleteRequestedFor(urlPathEqualTo("/admin/directory/v1/users/test001")));
    }
}