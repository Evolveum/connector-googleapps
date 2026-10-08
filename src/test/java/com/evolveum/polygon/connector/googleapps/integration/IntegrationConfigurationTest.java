package com.evolveum.polygon.connector.googleapps.integration;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import static org.testng.Assert.*;

/** Offline checks: these use dummy credentials and never initialize the Google client. */
public class IntegrationConfigurationTest {
    @Test
    public void defaultsDisableLicensingAndCache() {
        try (IntegrationConfiguration settings = IntegrationConfiguration.from(validProperties(), Collections.emptyMap())) {
            assertFalse(settings.connectorConfiguration().getAutoAddLicense());
            assertFalse(settings.connectorConfiguration().getAllowCache());
            assertEquals(settings.connectorConfiguration().getDomain(), "example.test");
            assertNotNull(settings.connectorConfiguration().getClientSecret());
            assertNotNull(settings.connectorConfiguration().getRefreshToken());
        }
    }

    @Test
    public void environmentOverridesFile() {
        Properties properties = validProperties();
        properties.remove("clientSecret");
        properties.remove("refreshToken");
        try (IntegrationConfiguration settings = IntegrationConfiguration.from(properties, Map.of(
                "GWS_IT_DOMAIN", "override.test", "GWS_IT_AUTO_ADD_LICENSE", "true",
                "GWS_IT_CLIENT_SECRET", "dummy-environment-secret", "GWS_IT_REFRESH_TOKEN", "dummy-environment-token"))) {
            assertEquals(settings.connectorConfiguration().getDomain(), "override.test");
            assertTrue(settings.connectorConfiguration().getAutoAddLicense());
        }
    }

    @DataProvider
    public Object[][] requiredSettings() {
        return new Object[][] {{"domain"}, {"productId"}, {"skuId"}, {"clientId"}, {"clientSecret"}, {"refreshToken"}};
    }

    @Test(dataProvider = "requiredSettings")
    public void missingSettingFailsWithoutExposingCredentials(String setting) {
        Properties properties = validProperties();
        properties.remove(setting);
        IllegalArgumentException error = expectThrows(IllegalArgumentException.class,
                () -> IntegrationConfiguration.from(properties, Collections.emptyMap()));
        assertTrue(error.getMessage().contains(setting));
        assertFalse(error.getMessage().contains("dummy-secret"));
        assertFalse(error.getMessage().contains("dummy-token"));
    }

    @DataProvider
    public Object[][] invalidSettings() {
        return new Object[][] {{"domain", "https://example.test"}, {"domain", "user@example.test"},
                {"autoAddLicense", "yes"}, {"orgUnitPath", "ConnectorTests"}};
    }

    @Test(dataProvider = "invalidSettings")
    public void rejectsInvalidSettings(String setting, String value) {
        Properties properties = validProperties();
        properties.setProperty(setting, value);
        expectThrows(IllegalArgumentException.class,
                () -> IntegrationConfiguration.from(properties, Collections.emptyMap()));
    }

    @Test
    public void emptyEnvironmentSecretDoesNotFallBackToFile() {
        expectThrows(IllegalArgumentException.class,
                () -> IntegrationConfiguration.from(validProperties(), Map.of("GWS_IT_CLIENT_SECRET", "")));
    }

    private Properties validProperties() {
        Properties properties = new Properties();
        properties.setProperty("domain", "Example.TEST");
        properties.setProperty("productId", "dummy-product");
        properties.setProperty("skuId", "dummy-sku");
        properties.setProperty("clientId", "dummy-client");
        properties.setProperty("clientSecret", "dummy-secret");
        properties.setProperty("refreshToken", "dummy-token");
        return properties;
    }
}
