/*
 * Copyright (c) 2026 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.polygon.connector.googleapps;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TestGoogleAppsConfiguration {

    @Test
    public void defaultsAndCompleteConfigurationAreValid() {
        GoogleAppsConfiguration configuration = validConfiguration();

        configuration.validate();

        assertEquals(configuration.getAutoAddLicense(), Boolean.FALSE);
        assertEquals(configuration.getMaxCacheTTL(), Long.valueOf(300000L));
        assertEquals(configuration.getIgnoreCacheAfterUpdateTTL(), Long.valueOf(5000L));
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Domain cannot be null or empty\\.")
    public void validateRequiresDomain() {
        GoogleAppsConfiguration configuration = validConfiguration();
        configuration.setDomain(" ");
        configuration.validate();
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Product ID cannot be null or empty\\.")
    public void validateRequiresProductId() {
        GoogleAppsConfiguration configuration = validConfiguration();
        configuration.setProductId(null);
        configuration.validate();
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "SKU ID cannot be null or empty\\.")
    public void validateRequiresSkuId() {
        GoogleAppsConfiguration configuration = validConfiguration();
        configuration.setSkuId("");
        configuration.validate();
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Client Id cannot be null or empty\\.")
    public void validateRequiresClientId() {
        GoogleAppsConfiguration configuration = validConfiguration();
        configuration.setClientId(" ");
        configuration.validate();
    }

    static GoogleAppsConfiguration validConfiguration() {
        GoogleAppsConfiguration configuration = new GoogleAppsConfiguration();
        configuration.setDomain("example.com");
        configuration.setProductId("Google-Apps");
        configuration.setSkuId("Google-Apps-For-Business");
        configuration.setClientId("client-id");
        return configuration;
    }
}
