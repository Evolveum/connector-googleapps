package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Tests for GoogleAppsConnector Group delete operations
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsGroupDeleteTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test group deletion")
    public void testDeleteGroup() {
        // Setup mock response for group deletion
        stubFor(delete(urlPathEqualTo("/admin/directory/v1/groups/group001"))
                .willReturn(aResponse()
                        .withStatus(204))); // 204 No Content for successful deletion

        // Execute group deletion
        connectorFacade.delete(ObjectClass.GROUP, new Uid("group001"), null);

        // Verify request was sent
        GoogleApiMockServer.verifyRequest(deleteRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group001")));
    }
}