package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test class for GoogleAppsConnector.test() method.
 * Tests connection validation functionality.
 *
 * @author Hiroyuki Wada
 */
@DisplayName("Google Apps Connection Test")
public class GoogleAppsTestTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test connection - success")
    public void testConnectionSuccess() {
        // Setup mock for test connection endpoint
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("1"))
                .withQueryParam("fields", equalTo("users(id)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"users\":[{\"id\":\"test\"}]}")));

        // Execute connection test - should succeed
        assertThatNoException().isThrownBy(() -> connectorFacade.test());

        // Verify the test connection request was made
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("customer", equalTo("my_customer"))
                .withQueryParam("maxResults", equalTo("1"))
                .withQueryParam("fields", equalTo("users(id)")));
    }

    @Test
    @DisplayName("Test connection - failure (network error)")
    public void testConnectionFailureNetworkError() {
        // Setup mock for test connection endpoint to return network error
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("1"))
                .withQueryParam("fields", equalTo("users(id)"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"code\":500,\"message\":\"Internal Server Error\"}}")));

        // Execute connection test - should fail
        assertThatThrownBy(() -> connectorFacade.test())
                .isInstanceOf(ConnectorException.class);

        // Verify the test connection request was attempted
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("customer", equalTo("my_customer"))
                .withQueryParam("maxResults", equalTo("1"))
                .withQueryParam("fields", equalTo("users(id)")));
    }

    @Test
    @DisplayName("Test connection - failure (authentication error)")
    public void testConnectionFailureAuthError() {
        // Setup mock for test connection endpoint to return authentication error
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("1"))
                .withQueryParam("fields", equalTo("users(id)"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"code\":401,\"message\":\"Unauthorized\"}}")));

        // Execute connection test - should fail
        assertThatThrownBy(() -> connectorFacade.test())
                .isInstanceOf(ConnectorException.class);

        // Verify the test connection request was attempted
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("customer", equalTo("my_customer"))
                .withQueryParam("maxResults", equalTo("1"))
                .withQueryParam("fields", equalTo("users(id)")));
    }

    @Test
    @DisplayName("Test connection - failure (forbidden)")
    public void testConnectionFailureForbidden() {
        // Setup mock for test connection endpoint to return forbidden error
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("1"))
                .withQueryParam("fields", equalTo("users(id)"))
                .willReturn(aResponse()
                        .withStatus(403)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"code\":403,\"message\":\"Forbidden\"}}")));

        // Execute connection test - should fail
        assertThatThrownBy(() -> connectorFacade.test())
                .isInstanceOf(ConnectorException.class);

        // Verify the test connection request was attempted
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("customer", equalTo("my_customer"))
                .withQueryParam("maxResults", equalTo("1"))
                .withQueryParam("fields", equalTo("users(id)")));
    }

    @Test
    @DisplayName("Test connection - empty response handling")
    public void testConnectionEmptyResponse() {
        // Setup mock for test connection endpoint to return empty users list
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("maxResults", equalTo("1"))
                .withQueryParam("fields", equalTo("users(id)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"users\":[]}")));

        // Execute connection test - should succeed even with empty response
        assertThatNoException().isThrownBy(() -> connectorFacade.test());

        // Verify the test connection request was made
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("customer", equalTo("my_customer"))
                .withQueryParam("maxResults", equalTo("1"))
                .withQueryParam("fields", equalTo("users(id)")));
    }
}