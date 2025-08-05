/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 ForgeRock AS. All rights reserved.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://opensource.org/licenses/CDDL-1.0
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://opensource.org/licenses/CDDL-1.0
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */
package com.evolveum.polygon.connector.googleapps;

import com.google.api.client.googleapis.auth.oauth2.GoogleOAuthConstants;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.apache.v2.ApacheHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.directory.Directory;
import com.google.api.services.directory.DirectoryScopes;
import com.google.api.services.licensing.Licensing;
import com.google.api.services.licensing.LicensingScopes;
import com.google.auth.Credentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.auth.oauth2.UserCredentials;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.common.security.SecurityUtil;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;
import org.identityconnectors.framework.spi.StatefulConfiguration;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.ProxyAuthenticationStrategy;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;


/**
 * Extends the {@link AbstractConfiguration} class to provide all the necessary
 * parameters to initialize the GoogleApps Connector.
 */
public class GoogleAppsConfiguration extends AbstractConfiguration implements StatefulConfiguration {

    private String domain = null;
    private String productId = null;
    private String skuId = null;
    private Boolean autoAddLicense = false;
    /**
     * Client identifier issued to the client during the registration process.
     */
    private String clientId;
    /**
     * Client secret or {@code null} for none.
     */
    private GuardedString clientSecret = null;
    private GuardedString refreshToken = null;
    /**
     * Service Account Key in JSON format.
     */
    private GuardedString serviceAccountKeyJson = null;
    private String serviceAccountUser = null;
    private String[] serviceAccountScopes = null;
    private static final Log logger = Log.getLog(GoogleAppsConfiguration.class);
    /**
     * caching
     */
    private Long maxCacheTTL = 300000L;
    private Long ignoreCacheAfterUpdateTTL = 5000L;
    private Boolean allowCache;

    /**
     * HTTP Proxy settings
     */
    private String httpProxyHost;
    private Integer httpProxyPort;
    private String httpProxyUser;
    private GuardedString httpProxyPassword;

    /**
     * HTTP Timeout settings (in milliseconds)
     */
    private Integer httpConnectTimeout = 10000; // Default 10 seconds
    private Integer httpSocketTimeout = 10000;    // Default 10 seconds (read timeout)
    private Integer httpConnectionRequestTimeout = 10000;   // Default 10 seconds (connection manager request timeout)


    /**
     * Paging settings
     */
    private Integer userPagingMaxResults = 500;  // Default and max 500 for Users API
    private Integer groupPagingMaxResults = 200; // Default and max 200 for Groups API

    /**
     * API Base URL settings
     */
    private String directoryBaseUrl; // Will be set to Directory.DEFAULT_ROOT_URL in constructor
    private String licensingBaseUrl; // Will be set to Licensing.DEFAULT_ROOT_URL in constructor
    private String oauth2TokenServerUrl; // Will be set to GoogleOAuthConstants.TOKEN_SERVER_URL in constructor


    /**
     * Constructor.
     */
    public GoogleAppsConfiguration() {
        // Initialize default base URLs using the constants from Google API client libraries
        this.directoryBaseUrl = Directory.DEFAULT_ROOT_URL;
        this.licensingBaseUrl = Licensing.DEFAULT_ROOT_URL;
        this.oauth2TokenServerUrl = GoogleOAuthConstants.TOKEN_SERVER_URL;
    }

    @ConfigurationProperty(order = 1, displayMessageKey = "domain.display",
            groupMessageKey = "basic.group", helpMessageKey = "domain.help", required = false,
            confidential = false)
    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    @ConfigurationProperty(order = 2, displayMessageKey = "productid.display",
            groupMessageKey = "basic.group", helpMessageKey = "productid.help", required = false,
            confidential = false)
    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    @ConfigurationProperty(order = 3, displayMessageKey = "skuid.display",
            groupMessageKey = "basic.group", helpMessageKey = "skuid.help", required = false,
            confidential = false)
    public String getSkuId() {
        return skuId;
    }

    public void setSkuId(String skuId) {
        this.skuId = skuId;
    }

    @ConfigurationProperty(order = 4, displayMessageKey = "autoaddlic.display",
            groupMessageKey = "basic.group", helpMessageKey = "autoaddlic.help", required = false,
            confidential = false)
    public Boolean getAutoAddLicense() {
        return autoAddLicense;
    }

    public void setAutoAddLicense(Boolean autoAddLicense) {
        this.autoAddLicense = autoAddLicense;
    }

    @ConfigurationProperty(order = 5, displayMessageKey = "clientid.display",
            groupMessageKey = "basic.group", helpMessageKey = "clientid.help", required = false,
            confidential = false)
    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    @ConfigurationProperty(order = 6, displayMessageKey = "clientsecret.display",
            groupMessageKey = "basic.group", helpMessageKey = "clientsecret.help", required = false,
            confidential = true)
    public GuardedString getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(GuardedString clientSecret) {
        this.clientSecret = clientSecret;
    }

    @ConfigurationProperty(order = 7, displayMessageKey = "refreshtoken.display",
            groupMessageKey = "basic.group", helpMessageKey = "refreshtoken.help", required = false,
            confidential = true)
    public GuardedString getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(GuardedString refreshToken) {
        this.refreshToken = refreshToken;
    }

    @ConfigurationProperty(order = 8, displayMessageKey = "serviceaccountkeyjson.display",
            groupMessageKey = "basic.group", helpMessageKey = "serviceaccountkeyjson.help", required = false,
            confidential = true)
    public GuardedString getServiceAccountKeyJson() {
        return serviceAccountKeyJson;
    }

    public void setServiceAccountKeyJson(GuardedString serviceAccountKeyJson) {
        this.serviceAccountKeyJson = serviceAccountKeyJson;
    }

    @ConfigurationProperty(order = 9, displayMessageKey = "serviceaccountdelegateduser.display",
            groupMessageKey = "basic.group", helpMessageKey = "serviceaccountdelegateduser.help", required = false,
            confidential = false)
    public String getServiceAccountUser() {
        return serviceAccountUser;
    }

    public void setServiceAccountUser(String serviceAccountUser) {
        this.serviceAccountUser = serviceAccountUser;
    }

    @ConfigurationProperty(order = 10, displayMessageKey = "serviceaccountscopes.display",
            groupMessageKey = "basic.group", helpMessageKey = "serviceaccountscopes.help", required = false,
            confidential = false)
    public String[] getServiceAccountScopes() {
        return serviceAccountScopes;
    }

    public void setServiceAccountScopes(String[] serviceAccountScopes) {
        this.serviceAccountScopes = serviceAccountScopes;
    }

    @ConfigurationProperty(order = 11, displayMessageKey = "allowCache.display",
            groupMessageKey = "basic.group", helpMessageKey = "allowCache.help", required = false,
            confidential = false)
    public Boolean getAllowCache() {
        return allowCache;
    }

    public void setAllowCache(Boolean allowCache) {
        this.allowCache = allowCache;
    }

    @ConfigurationProperty(order = 12, displayMessageKey = "maxCacheTTL.display",
            groupMessageKey = "basic.group", helpMessageKey = "maxCacheTTL.help", required = false,
            confidential = false)
    public Long getMaxCacheTTL() {
        return maxCacheTTL;
    }

    public void setMaxCacheTTL(Long maxCacheTTL) {
        this.maxCacheTTL = maxCacheTTL;
    }

    @ConfigurationProperty(order = 13, displayMessageKey = "ignoreCacheAfterUpdateTTL.display",
            groupMessageKey = "basic.group", helpMessageKey = "ignoreCacheAfterUpdateTTL.help", required = false,
            confidential = false)
    public Long getIgnoreCacheAfterUpdateTTL() {
        return ignoreCacheAfterUpdateTTL;
    }

    public void setIgnoreCacheAfterUpdateTTL(Long ignoreCacheAfterUpdateTTL) {
        this.ignoreCacheAfterUpdateTTL = ignoreCacheAfterUpdateTTL;
    }

    @ConfigurationProperty(order = 14, displayMessageKey = "httpProxyHost.display",
            groupMessageKey = "proxy.group", helpMessageKey = "httpProxyHost.help", required = false,
            confidential = false)
    public String getHttpProxyHost() {
        return httpProxyHost;
    }

    public void setHttpProxyHost(String httpProxyHost) {
        this.httpProxyHost = httpProxyHost;
    }

    @ConfigurationProperty(order = 15, displayMessageKey = "httpProxyPort.display",
            groupMessageKey = "proxy.group", helpMessageKey = "httpProxyPort.help", required = false,
            confidential = false)
    public Integer getHttpProxyPort() {
        return httpProxyPort;
    }

    public void setHttpProxyPort(Integer httpProxyPort) {
        this.httpProxyPort = httpProxyPort;
    }

    @ConfigurationProperty(order = 16, displayMessageKey = "httpProxyUser.display",
            groupMessageKey = "proxy.group", helpMessageKey = "httpProxyUser.help", required = false,
            confidential = false)
    public String getHttpProxyUser() {
        return httpProxyUser;
    }

    public void setHttpProxyUser(String httpProxyUser) {
        this.httpProxyUser = httpProxyUser;
    }

    @ConfigurationProperty(order = 17, displayMessageKey = "httpProxyPassword.display",
            groupMessageKey = "proxy.group", helpMessageKey = "httpProxyPassword.help", required = false,
            confidential = true)
    public GuardedString getHttpProxyPassword() {
        return httpProxyPassword;
    }

    public void setHttpProxyPassword(GuardedString httpProxyPassword) {
        this.httpProxyPassword = httpProxyPassword;
    }

    @ConfigurationProperty(order = 18, displayMessageKey = "httpConnectTimeout.display",
            groupMessageKey = "timeout.group", helpMessageKey = "httpConnectTimeout.help", required = false,
            confidential = false)
    public Integer getHttpConnectTimeout() {
        return httpConnectTimeout;
    }

    public void setHttpConnectTimeout(Integer httpConnectTimeout) {
        this.httpConnectTimeout = httpConnectTimeout;
    }

    @ConfigurationProperty(order = 19, displayMessageKey = "httpSocketTimeout.display",
            groupMessageKey = "timeout.group", helpMessageKey = "httpSocketTimeout.help", required = false,
            confidential = false)
    public Integer getHttpSocketTimeout() {
        return httpSocketTimeout;
    }

    public void setHttpSocketTimeout(Integer httpSocketTimeout) {
        this.httpSocketTimeout = httpSocketTimeout;
    }

    @ConfigurationProperty(order = 20, displayMessageKey = "httpConnectionRequestTimeout.display",
            groupMessageKey = "timeout.group", helpMessageKey = "httpConnectionRequestTimeout.help", required = false,
            confidential = false)
    public Integer getHttpConnectionRequestTimeout() {
        return httpConnectionRequestTimeout;
    }

    public void setHttpConnectionRequestTimeout(Integer httpConnectionRequestTimeout) {
        this.httpConnectionRequestTimeout = httpConnectionRequestTimeout;
    }

    @ConfigurationProperty(order = 21, displayMessageKey = "userPagingMaxResults.display",
            groupMessageKey = "paging.group", helpMessageKey = "userPagingMaxResults.help", required = false,
            confidential = false)
    public Integer getUserPagingMaxResults() {
        return userPagingMaxResults;
    }

    public void setUserPagingMaxResults(Integer userPagingMaxResults) {
        if (userPagingMaxResults != null && userPagingMaxResults > 0 && userPagingMaxResults <= 500) {
            this.userPagingMaxResults = userPagingMaxResults;
        } else if (userPagingMaxResults != null) {
            throw new IllegalArgumentException("userPagingMaxResults must be between 1 and 500");
        }
    }

    @ConfigurationProperty(order = 22, displayMessageKey = "groupPagingMaxResults.display",
            groupMessageKey = "paging.group", helpMessageKey = "groupPagingMaxResults.help", required = false,
            confidential = false)
    public Integer getGroupPagingMaxResults() {
        return groupPagingMaxResults;
    }

    public void setGroupPagingMaxResults(Integer groupPagingMaxResults) {
        if (groupPagingMaxResults != null && groupPagingMaxResults > 0 && groupPagingMaxResults <= 200) {
            this.groupPagingMaxResults = groupPagingMaxResults;
        } else if (groupPagingMaxResults != null) {
            throw new IllegalArgumentException("groupPagingMaxResults must be between 1 and 200");
        }
    }

    @ConfigurationProperty(order = 24, displayMessageKey = "directoryBaseUrl.display",
            groupMessageKey = "advanced.group", helpMessageKey = "directoryBaseUrl.help", required = false,
            confidential = false)
    public String getDirectoryBaseUrl() {
        return directoryBaseUrl;
    }

    public void setDirectoryBaseUrl(String directoryBaseUrl) {
        // Ensure URL ends with slash
        if (StringUtil.isNotBlank(directoryBaseUrl)) {
            this.directoryBaseUrl = directoryBaseUrl.endsWith("/") ? directoryBaseUrl : directoryBaseUrl + "/";
        } else {
            this.directoryBaseUrl = Directory.DEFAULT_ROOT_URL;
        }
    }

    @ConfigurationProperty(order = 25, displayMessageKey = "licensingBaseUrl.display",
            groupMessageKey = "advanced.group", helpMessageKey = "licensingBaseUrl.help", required = false,
            confidential = false)
    public String getLicensingBaseUrl() {
        return licensingBaseUrl;
    }

    public void setLicensingBaseUrl(String licensingBaseUrl) {
        // Ensure URL ends with slash
        if (StringUtil.isNotBlank(licensingBaseUrl)) {
            this.licensingBaseUrl = licensingBaseUrl.endsWith("/") ? licensingBaseUrl : licensingBaseUrl + "/";
        } else {
            this.licensingBaseUrl = Licensing.DEFAULT_ROOT_URL;
        }
    }

    @ConfigurationProperty(order = 26, displayMessageKey = "oauth2TokenServerUrl.display",
            groupMessageKey = "advanced.group", helpMessageKey = "oauth2TokenServerUrl.help", required = false,
            confidential = false)
    public String getOauth2TokenServerUrl() {
        return oauth2TokenServerUrl;
    }

    public void setOauth2TokenServerUrl(String oauth2TokenServerUrl) {
        if (StringUtil.isNotBlank(oauth2TokenServerUrl)) {
            this.oauth2TokenServerUrl = oauth2TokenServerUrl;
        } else {
            this.oauth2TokenServerUrl = GoogleOAuthConstants.TOKEN_SERVER_URL;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void validate() {
        if (StringUtil.isBlank(domain)) {
            throw new IllegalArgumentException("Domain cannot be null or empty.");
        }
        if (StringUtil.isBlank(productId)) {
            throw new IllegalArgumentException("Product ID cannot be null or empty.");
        }
        if (StringUtil.isBlank(skuId)) {
            throw new IllegalArgumentException("SKU ID cannot be null or empty.");
        }
        if (StringUtil.isBlank(clientId)) {
            throw new IllegalArgumentException("Client Id cannot be null or empty.");
        }
    }

    private Credentials credentials = null;

    public void getGoogleCredential() {
        if (null == credentials) {
            synchronized (this) {
                if (null == credentials) {
                    System.setProperty("https.protocols", "TLSv1.2");

                    if (getClientSecret() != null) {
                        // Using OAuth 2.0 client with authorization code flow
                        credentials = UserCredentials.newBuilder()
                                .setHttpTransportFactory(() -> createHttpTransport())
                                .setTokenServerUri(URI.create(getOauth2TokenServerUrl()))
                                .setClientId(getClientId())
                                .setClientSecret(SecurityUtil.decrypt(getClientSecret()))
                                .setRefreshToken(SecurityUtil.decrypt(getRefreshToken()))
                                .build();
                    } else {
                        // Using Service Account
                        getServiceAccountKeyJson().access(c -> {
                            String keyJson = String.valueOf(c);
                            try (InputStream inputStream = new ByteArrayInputStream(keyJson.getBytes(StandardCharsets.UTF_8))) {
                                // Use custom scopes if provided, otherwise use default scopes
                                String[] scopes = getServiceAccountScopes();
                                if (scopes == null || scopes.length == 0) {
                                    // Default scopes
                                    scopes = new String[]{
                                            DirectoryScopes.ADMIN_DIRECTORY_USER,
                                            DirectoryScopes.ADMIN_DIRECTORY_GROUP,
                                            LicensingScopes.APPS_LICENSING
                                    };
                                }
                                credentials = ServiceAccountCredentials.fromStream(inputStream, () -> createHttpTransport())
                                        .createScoped(scopes)
                                        .createDelegated(getServiceAccountUser());
                            } catch (IOException e) {
                                throw new ConfigurationException("Invalid Service Account Key", e);
                            }
                        });
                    }

                    try {
                        credentials.refresh();
                    } catch (IOException ex) {
                        logger.error("Token refresh error: {0}", ex.getMessage());
                    }

                    HttpCredentialsAdapter credentialsAdapter = new HttpCredentialsAdapter(credentials);
                    HttpRequestInitializer requestInitializer = createHttpRequestInitializer(credentialsAdapter);

                    directory =
                            new Directory.Builder(createHttpTransport(), JSON_FACTORY, requestInitializer)
                                    .setApplicationName("GoogleAppsConnector")
                                    .setRootUrl(directoryBaseUrl)
                                    .build();
                    licensing =
                            new Licensing.Builder(createHttpTransport(), JSON_FACTORY, requestInitializer)
                                    .setApplicationName("GoogleAppsConnector")
                                    .setRootUrl(licensingBaseUrl)
                                    .build();
                }
            }
        }
    }

    @Override
    public void release() {
    }

    /**
     * Instance of the HTTP transport.
     */
    private HttpTransport httpTransport;
    /**
     * Global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY = new GsonFactory();

    /**
     * Create HttpTransport with proxy support if configured.
     */
    private HttpTransport createHttpTransport() {
        if (httpTransport != null) {
            return httpTransport;
        }

        try {
            // Use Apache HttpClient
            HttpClientBuilder clientBuilder = ApacheHttpTransport.newDefaultHttpClientBuilder();

            // Configure connection pool with minimal size (considering ConnID pooling)
            // Each connector instance is typically used sequentially
            PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
            connectionManager.setMaxTotal(10);           // Limit total connections to 10
            connectionManager.setDefaultMaxPerRoute(5);  // Limit per-route connections to 5
            // Keep default validation (2000ms) for connection staleness check
            clientBuilder.setConnectionManager(connectionManager);

            // Configure timeouts
            RequestConfig.Builder requestConfigBuilder = RequestConfig.custom()
                    .setConnectTimeout(httpConnectTimeout != null ? httpConnectTimeout : 10000)
                    .setSocketTimeout(httpSocketTimeout != null ? httpSocketTimeout : 10000)
                    .setConnectionRequestTimeout(httpConnectionRequestTimeout != null ? httpConnectionRequestTimeout : 10000);

            // Configure proxy if specified
            if (StringUtil.isNotBlank(httpProxyHost) && httpProxyPort != null && httpProxyPort > 0) {
                logger.info("Configuring HTTP proxy: {0}:{1}", httpProxyHost, httpProxyPort);
                HttpHost proxy = new HttpHost(httpProxyHost, httpProxyPort);
                requestConfigBuilder.setProxy(proxy);

                // Configure proxy authentication if credentials provided
                if (StringUtil.isNotBlank(httpProxyUser) && httpProxyPassword != null) {
                    logger.info("Configuring proxy authentication for user: {0}", httpProxyUser);
                    CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                    httpProxyPassword.access(c -> {
                        credentialsProvider.setCredentials(
                                new AuthScope(httpProxyHost, httpProxyPort),
                                new UsernamePasswordCredentials(httpProxyUser, String.valueOf(c))
                        );
                    });
                    clientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    clientBuilder.setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy());
                }

                clientBuilder.setRoutePlanner(new DefaultProxyRoutePlanner(proxy));
            }

            clientBuilder.setDefaultRequestConfig(requestConfigBuilder.build());

            httpTransport = new ApacheHttpTransport(clientBuilder.build());
        } catch (Exception e) {
            logger.error(e, "Failed to create HTTP transport: {0}", e.getMessage());
            throw new ConnectorException("Failed to create HTTP transport", e);
        }

        return httpTransport;
    }


    /**
     * Create HttpRequestInitializer with credentials only.
     */
    private HttpRequestInitializer createHttpRequestInitializer(HttpCredentialsAdapter credentialsAdapter) {
        return new HttpRequestInitializer() {
            @Override
            public void initialize(HttpRequest httpRequest) throws IOException {
                credentialsAdapter.initialize(httpRequest);
            }
        };
    }

    public Directory getDirectory() {
        getGoogleCredential();
        return directory;
    }

    public Licensing getLicensing() {
        getGoogleCredential();
        if (null == licensing) {
            throw new ConnectorException("Licensing is not enabled");
        }
        return licensing;
    }

    private Directory directory;
    private Licensing licensing;

}
