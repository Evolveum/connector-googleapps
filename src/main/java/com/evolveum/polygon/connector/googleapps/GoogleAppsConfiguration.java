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

import java.net.URI;
import java.security.GeneralSecurityException;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.directory.Directory;
import com.google.auth.Credentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.common.security.SecurityUtil;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;
import org.identityconnectors.framework.spi.StatefulConfiguration;

import com.google.api.client.googleapis.auth.oauth2.GoogleOAuthConstants;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.licensing.Licensing;
import java.io.IOException;
import org.identityconnectors.common.logging.Log;

/**
 * Extends the {@link AbstractConfiguration} class to provide all the necessary
 * parameters to initialize the GoogleApps Connector.
 *
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
    private static final Log logger = Log.getLog(GoogleAppsConfiguration.class);
    /**
     * caching
     */
    private Long maxCacheTTL = 300000L;
    private Long ignoreCacheAfterUpdateTTL = 5000L;
    private Boolean allowCache;

    /**
     * Constructor.
     */
    public GoogleAppsConfiguration() {
    }

    @ConfigurationProperty(order = 1, displayMessageKey = "domain.display",
    groupMessageKey = "basic.group", helpMessageKey = "domain.help", required = true,
    confidential = false)
    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

   @ConfigurationProperty(order = 2, displayMessageKey = "productid.display",
    groupMessageKey = "basic.group", helpMessageKey = "productid.help", required = true,
    confidential = false)
    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

   @ConfigurationProperty(order = 3, displayMessageKey = "skuid.display",
    groupMessageKey = "basic.group", helpMessageKey = "skuid.help", required = true,
    confidential = false)
    public String getSkuId() {
        return skuId;
    }

    public void setSkuId(String skuId) {
        this.skuId = skuId;
    }

  @ConfigurationProperty(order = 4, displayMessageKey = "autoaddlic.display",
    groupMessageKey = "basic.group", helpMessageKey = "autoaddlic.help", required = true,
    confidential = false)
    public Boolean getAutoAddLicense() {
        return autoAddLicense;
    }

    public void setAutoAddLicense(Boolean autoAddLicense) {
        this.autoAddLicense = autoAddLicense;
    }

    @ConfigurationProperty(order = 5, displayMessageKey = "clientid.display",
    groupMessageKey = "basic.group", helpMessageKey = "clientid.help", required = true,
    confidential = false)
    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    @ConfigurationProperty(order = 6, displayMessageKey = "clientsecret.display",
    groupMessageKey = "basic.group", helpMessageKey = "clientsecret.help", required = true,
    confidential = true)
    public GuardedString getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(GuardedString clientSecret) {
        this.clientSecret = clientSecret;
    }

    @ConfigurationProperty(order = 7, displayMessageKey = "refreshtoken.display",
    groupMessageKey = "basic.group", helpMessageKey = "refreshtoken.help", required = true,
    confidential = true)
    public GuardedString getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(GuardedString refreshToken) {
        this.refreshToken = refreshToken;
    }
    
    @ConfigurationProperty(order = 8, displayMessageKey = "allowCache.display",
    groupMessageKey = "basic.group", helpMessageKey = "allowCache.help", required = true,
    confidential = false)
    public Boolean getAllowCache() {
        return allowCache;
    }

    public void setAllowCache(Boolean allowCache) {
        this.allowCache = allowCache;
    }

    @ConfigurationProperty(order = 9, displayMessageKey = "maxCacheTTL.display",
            groupMessageKey = "basic.group", helpMessageKey = "maxCacheTTL.help", required = true,
            confidential = false)
    public Long getMaxCacheTTL() {
        return maxCacheTTL;
    }

    public void setMaxCacheTTL(Long maxCacheTTL) {
        this.maxCacheTTL = maxCacheTTL;
    }

    @ConfigurationProperty(order = 10, displayMessageKey = "ignoreCacheAfterUpdateTTL.display",
            groupMessageKey = "basic.group", helpMessageKey = "ignoreCacheAfterUpdateTTL.help", required = true,
            confidential = false)
    public Long getIgnoreCacheAfterUpdateTTL() {
        return ignoreCacheAfterUpdateTTL;
    }

    public void setIgnoreCacheAfterUpdateTTL(Long ignoreCacheAfterUpdateTTL) {
        this.ignoreCacheAfterUpdateTTL = ignoreCacheAfterUpdateTTL;
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
        if (null == clientSecret) {
            throw new IllegalArgumentException("Client Secret cannot be null or empty.");
        }
        if (null == refreshToken) {
            throw new IllegalArgumentException("Refresh Token cannot be null or empty.");
        }
    }
    private Credentials credentials = null;

    public void getGoogleCredential() {
        if (null == credentials) {
            synchronized (this) {
                if (null == credentials) {
                    System.setProperty("https.protocols", "TLSv1.2");
                    credentials = UserCredentials.newBuilder()
                            .setHttpTransportFactory(() -> HTTP_TRANSPORT)
                            .setTokenServerUri(URI.create(GoogleOAuthConstants.TOKEN_SERVER_URL))
                            .setClientId(getClientId())
                            .setClientSecret(SecurityUtil.decrypt(getClientSecret()))
                            .setRefreshToken(SecurityUtil.decrypt(getRefreshToken()))
                            .build();

                    HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

                    try {
                        credentials.refresh();
                    } catch (IOException ex) {
                        logger.error("Token refresh error: {0}", ex.getMessage());
                    }

                    directory =
                            new Directory.Builder(HTTP_TRANSPORT, JSON_FACTORY, requestInitializer)
                            .setApplicationName("GoogleAppsConnector").build();
                    licensing =
                            new Licensing.Builder(HTTP_TRANSPORT, JSON_FACTORY, requestInitializer)
                            .setApplicationName("GoogleAppsConnector").build();
                }
            }
        }
    }

    @Override
    public void release() {
    }
    /**
     * Global instance of the HTTP transport.
     */
    private static final HttpTransport HTTP_TRANSPORT;
    /**
     * Global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY = new GsonFactory();

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

    static {
        HttpTransport t = null;
        try {
            t = GoogleNetHttpTransport.newTrustedTransport();
        } catch (Exception e) {
            try {
                t = new NetHttpTransport.Builder().doNotValidateCertificate().build();
            } catch (GeneralSecurityException e1) {
            }
        }
        HTTP_TRANSPORT = t;
    }
}
