package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.test.common.TestHelpers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Base class for GoogleAppsConnector tests
 * Automatically starts mock server and provides test ConnectorFacade
 *
 * @author Hiroyuki Wada
 */
@ExtendWith(GoogleApiMockServer.class)
public abstract class GoogleAppsConnectorTestBase {

    protected ConnectorFacade connectorFacade;
    protected GoogleAppsConfiguration configuration;

    @BeforeEach
    public void setUp() {
        // Reset WireMock request history before each test to ensure test isolation
        GoogleApiMockServer.resetRequests();

        configuration = createTestConfiguration();
        connectorFacade = createConnectorFacade(configuration);

        // Skip connection test in unit tests to avoid stub conflicts
        // Each test will provide its own API stubs as needed
    }

    /**
     * Create test Configuration
     */
    protected GoogleAppsConfiguration createTestConfiguration() {
        GoogleAppsConfiguration config = new GoogleAppsConfiguration();

        // Basic settings
        config.setDomain("example.com");
        config.setProductId("Google-Apps");
        config.setSkuId("Google-Apps-For-Business");
        config.setAutoAddLicense(false);
        config.setClientId("test-client-id");

        // Set mock server URLs
        String mockBaseUrl = GoogleApiMockServer.getBaseUrl();
        config.setDirectoryBaseUrl(mockBaseUrl);
        config.setLicensingBaseUrl(mockBaseUrl);
        config.setOauth2TokenServerUrl(mockBaseUrl + "token");

        // OAuth2 refresh token settings (dummy)
        config.setClientSecret(new GuardedString("test-client-secret".toCharArray()));
        config.setRefreshToken(new GuardedString("test-refresh-token".toCharArray()));

        // Cache settings
        config.setAllowCache(false); // Disable cache for tests
        config.setMaxCacheTTL(0L);
        config.setIgnoreCacheAfterUpdateTTL(0L);

        return config;
    }

    /**
     * Create ConnectorFacade
     */
    protected ConnectorFacade createConnectorFacade(GoogleAppsConfiguration configuration) {
        ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();
        APIConfiguration apiConfiguration = TestHelpers.createTestConfiguration(GoogleAppsConnector.class, configuration);

        // Disable ConnID ResultsHandlers that interfere with RETURN_DEFAULT_ATTRIBUTES testing
        apiConfiguration.getResultsHandlerConfiguration().setEnableNormalizingResultsHandler(false);
        apiConfiguration.getResultsHandlerConfiguration().setEnableFilteredResultsHandler(false);
        apiConfiguration.getResultsHandlerConfiguration().setEnableAttributesToGetSearchResultsHandler(false);

        return factory.newInstance(apiConfiguration);
    }
}