package com.evolveum.polygon.connector.googleapps;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.List;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * WireMock-based Google API mock server
 * Automatically starts and stops as JUnit 5 extension
 *
 * @author Hiroyuki Wada
 */
public class GoogleApiMockServer implements BeforeAllCallback, AfterAllCallback {

    private static WireMockServer wireMockServer;
    private static int port;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (wireMockServer == null) {
            // Start WireMock server on random port
            wireMockServer = new WireMockServer(WireMockConfiguration.options()
                    .dynamicPort()
                    .notifier(new ConsoleNotifier(false)) // Suppress logs
            );
            wireMockServer.start();
            port = wireMockServer.port();

            // Setup API endpoint mappings
            setupEssentialEndpoints();

            System.out.println("Google API Mock Server started on port: " + port);
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        if (wireMockServer != null) {
            wireMockServer.stop();
            wireMockServer = null;
            System.out.println("Google API Mock Server stopped");
        }
    }

    public static int getPort() {
        return port;
    }

    public static String getBaseUrl() {
        return "http://localhost:" + port + "/";
    }


    /**
     * Setup only essential endpoints required for all tests
     */
    private void setupEssentialEndpoints() {
        setupOAuth2Endpoints();
    }

    /**
     * Setup OAuth2 endpoints
     */
    private void setupOAuth2Endpoints() {
        // OAuth2 token refresh endpoint
        wireMockServer.stubFor(post(urlEqualTo("/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"access_token\": \"test-access-token\",\n" +
                                "  \"expires_in\": 3600,\n" +
                                "  \"token_type\": \"Bearer\"\n" +
                                "}")));
    }

    /**
     * Helper method for stubbing requests
     */
    public static void stubFor(MappingBuilder mappingBuilder) {
        wireMockServer.stubFor(mappingBuilder);
    }

    /**
     * Helper method for verifying requests
     */
    public static void verifyRequest(RequestPatternBuilder requestPattern) {
        wireMockServer.verify(requestPattern);
    }

    /**
     * Helper method for verifying requests with count
     */
    public static void verify(int count, RequestPatternBuilder requestPattern) {
        wireMockServer.verify(count, requestPattern);
    }

    /**
     * Helper method to reset request history
     */
    public static void resetRequests() {
        if (wireMockServer != null) {
            wireMockServer.resetRequests();
        }
    }

    /**
     * Helper method to get all logged requests
     */
    public static List<com.github.tomakehurst.wiremock.verification.LoggedRequest> getAllRequests() {
        return wireMockServer.getAllServeEvents().stream()
                .map(event -> event.getRequest())
                .collect(Collectors.toList());
    }

    /**
     * Helper method to print the last request body for debugging
     */
    public static void printLastRequestBody() {
        if (wireMockServer != null) {
            wireMockServer.getAllServeEvents().stream()
                    .findFirst()
                    .ifPresent(event -> {
                        System.out.println("Last request body: " + event.getRequest().getBodyAsString());
                    });
        }
    }
}