package com.evolveum.polygon.connector.googleapps.integration;

import com.evolveum.polygon.connector.googleapps.GoogleAppsConfiguration;
import org.identityconnectors.common.security.GuardedString;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** Local integration settings. Never print this configuration or its source properties. */
final class IntegrationConfiguration implements AutoCloseable {
    private final GoogleAppsConfiguration connectorConfiguration;
    private final String orgUnitPath;

    private IntegrationConfiguration(GoogleAppsConfiguration configuration, String orgUnitPath) {
        this.connectorConfiguration = configuration;
        this.orgUnitPath = orgUnitPath;
    }

    static IntegrationConfiguration load() throws IOException {
        String explicitPath = System.getProperty("gws.it.config");
        Path path = Paths.get(explicitPath == null ? "gws-it.local.properties" : explicitPath);
        Properties properties = new Properties();
        if (explicitPath != null || Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        try {
            return from(properties, System.getenv());
        } finally {
            properties.clear();
        }
    }

    static IntegrationConfiguration from(Properties properties, Map<String, String> environment) {
        String domain = required(properties, environment, "domain", "GWS_IT_DOMAIN")
                .trim().toLowerCase(Locale.ROOT);
        if (!domain.matches("(?=.{1,253}$)[a-z0-9](?:[a-z0-9-]*[a-z0-9])?"
                + "(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+")) {
            throw new IllegalArgumentException("domain must be a DNS domain, without https://, a path or @");
        }
        String productId = required(properties, environment, "productId", "GWS_IT_PRODUCT_ID").trim();
        String skuId = required(properties, environment, "skuId", "GWS_IT_SKU_ID").trim();
        String clientId = required(properties, environment, "clientId", "GWS_IT_CLIENT_ID").trim();
        String clientSecret = required(properties, environment, "clientSecret", "GWS_IT_CLIENT_SECRET");
        String refreshToken = required(properties, environment, "refreshToken", "GWS_IT_REFRESH_TOKEN");
        String licenseFlag = value(properties, environment, "autoAddLicense", "GWS_IT_AUTO_ADD_LICENSE", "false").trim();
        if (!"true".equalsIgnoreCase(licenseFlag) && !"false".equalsIgnoreCase(licenseFlag)) {
            throw new IllegalArgumentException("autoAddLicense must be true or false");
        }
        String orgUnitPath = value(properties, environment, "orgUnitPath", "GWS_IT_ORG_UNIT_PATH", "").trim();
        if (!orgUnitPath.isEmpty() && !orgUnitPath.startsWith("/")) {
            throw new IllegalArgumentException("orgUnitPath must start with / or be empty");
        }

        GoogleAppsConfiguration configuration = new GoogleAppsConfiguration();
        configuration.setDomain(domain);
        configuration.setProductId(productId);
        configuration.setSkuId(skuId);
        configuration.setAutoAddLicense(Boolean.parseBoolean(licenseFlag));
        configuration.setClientId(clientId);
        configuration.setAllowCache(false);
        // Validate ordinary settings before allocating guarded credentials. No network call here.
        configuration.validate();
        configuration.setClientSecret(new GuardedString(clientSecret.toCharArray()));
        configuration.setRefreshToken(new GuardedString(refreshToken.toCharArray()));
        return new IntegrationConfiguration(configuration, orgUnitPath);
    }

    private static String required(Properties properties, Map<String, String> environment,
            String property, String variable) {
        String value = value(properties, environment, property, variable, "");
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing integration setting: " + property + " (or " + variable + ")");
        }
        return value;
    }

    private static String value(Properties properties, Map<String, String> environment,
            String property, String variable, String defaultValue) {
        return environment.containsKey(variable) ? environment.get(variable)
                : properties.getProperty(property, defaultValue);
    }

    GoogleAppsConfiguration connectorConfiguration() {
        return connectorConfiguration;
    }

    String orgUnitPath() {
        return orgUnitPath;
    }

    @Override
    public void close() {
        connectorConfiguration.getClientSecret().dispose();
        connectorConfiguration.getRefreshToken().dispose();
        connectorConfiguration.release();
    }
}
