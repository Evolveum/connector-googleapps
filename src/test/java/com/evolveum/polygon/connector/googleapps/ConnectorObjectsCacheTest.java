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

import com.evolveum.polygon.connector.googleapps.cache.ConnectorObjectWrapper;
import com.evolveum.polygon.connector.googleapps.cache.ConnectorObjectsCache;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.joda.time.Duration;
import org.testng.annotations.Test;

import java.util.UUID;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class ConnectorObjectsCacheTest {

    @Test
    public void enabledCacheReturnsStoredObjectAndRemovesIt() {
        ConnectorObjectsCache cache = cache(true);
        ConnectorObject user = user();

        cache.addUser(user);

        assertSame(cache.getUser(user.getUid().getUidValue()), user);
        cache.removeUser(user.getUid().getUidValue());
        assertNull(cache.getUser(user.getUid().getUidValue()));
    }

    @Test
    public void cacheBypassesAnObjectImmediatelyAfterUpdate() {
        ConnectorObjectsCache cache = cache(true);
        ConnectorObject user = user();
        cache.addUser(user);

        cache.markUserAsUpdatedNow(user.getUid().getUidValue());

        assertNull(cache.getUser(user.getUid().getUidValue()));
        cache.removeUser(user.getUid().getUidValue());
    }

    @Test
    public void disabledCacheDoesNotStoreObjects() {
        ConnectorObjectsCache cache = cache(false);
        ConnectorObject user = user();

        cache.addUser(user);

        assertNull(cache.getUser(user.getUid().getUidValue()));
    }

    @Test
    public void enabledCacheStoresGroupsAndHonorsThePostUpdateIgnoreWindow() {
        GoogleAppsConfiguration configuration = GoogleAppsConfigurationTest.validConfiguration();
        configuration.setAllowCache(true);
        configuration.setMaxCacheTTL(60_000L);
        configuration.setIgnoreCacheAfterUpdateTTL(60_000L);
        ConnectorObjectsCache cache = ConnectorObjectsCache.getInstance(configuration,
                Log.getLog(ConnectorObjectsCacheTest.class));
        ConnectorObject group = group();

        cache.addGroup(group);
        assertSame(cache.getGroup(group.getUid().getUidValue()), group);

        cache.markGroupAsUpdatedNow(group.getUid().getUidValue());
        assertNull(cache.getGroup(group.getUid().getUidValue()));

        cache.addGroup(group);
        assertNull(cache.getGroup(group.getUid().getUidValue()));
        cache.removeGroup(group.getUid().getUidValue());
        assertNull(cache.getGroup(group.getUid().getUidValue()));
    }

    @Test
    public void cacheStoresAGroupAgainAfterThePostUpdateIgnoreWindow() throws InterruptedException {
        GoogleAppsConfiguration configuration = GoogleAppsConfigurationTest.validConfiguration();
        configuration.setAllowCache(true);
        configuration.setMaxCacheTTL(60_000L);
        configuration.setIgnoreCacheAfterUpdateTTL(1L);
        ConnectorObjectsCache cache = ConnectorObjectsCache.getInstance(configuration,
                Log.getLog(ConnectorObjectsCacheTest.class));
        ConnectorObject group = group();

        cache.addGroup(group);
        cache.markGroupAsUpdatedNow(group.getUid().getUidValue());
        Thread.sleep(10L);
        cache.addGroup(group);
        assertSame(cache.getGroup(group.getUid().getUidValue()), group);
        cache.removeGroup(group.getUid().getUidValue());
        assertNull(cache.getGroup(group.getUid().getUidValue()));
    }

    @Test
    public void cacheExpiresOldEntriesAndReconfigurationCanDisableIt() throws InterruptedException {
        GoogleAppsConfiguration configuration = GoogleAppsConfigurationTest.validConfiguration();
        configuration.setAllowCache(true);
        configuration.setMaxCacheTTL(1L);
        configuration.setIgnoreCacheAfterUpdateTTL(0L);
        ConnectorObjectsCache cache = ConnectorObjectsCache.getInstance(configuration,
                Log.getLog(ConnectorObjectsCacheTest.class));
        ConnectorObject user = user();
        cache.addUser(user);

        Thread.sleep(10L);
        assertNull(cache.getUser(user.getUid().getUidValue()));

        configuration.setAllowCache(false);
        ConnectorObjectsCache.getInstance(configuration, Log.getLog(GoogleAppsConfigurationTest.class));
        cache.addUser(user);
        assertNull(cache.getUser(user.getUid().getUidValue()));
    }

    @Test
    public void wrapperRecognizesThePostUpdateIgnorePeriod() {
        ConnectorObjectWrapper wrapper = new ConnectorObjectWrapper(user());

        assertFalse(wrapper.isIgnoredAfterUpdate(Duration.standardSeconds(1)));
        wrapper.markAsUpdatedNow();
        assertTrue(wrapper.isIgnoredAfterUpdate(Duration.standardMinutes(1)));
        assertFalse(wrapper.isExpired(Duration.standardMinutes(1), Duration.standardMinutes(1)));
    }

    private static ConnectorObjectsCache cache(boolean enabled) {
        GoogleAppsConfiguration configuration = GoogleAppsConfigurationTest.validConfiguration();
        configuration.setAllowCache(enabled);
        return ConnectorObjectsCache.getInstance(configuration, Log.getLog(ConnectorObjectsCacheTest.class));
    }

    private static ConnectorObject user() {
        String uid = UUID.randomUUID().toString();
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(ObjectClass.ACCOUNT);
        builder.setUid(new Uid(uid));
        builder.setName(new Name(uid + "@example.com"));
        return builder.build();
    }

    private static ConnectorObject group() {
        String uid = UUID.randomUUID().toString();
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(ObjectClass.GROUP);
        builder.setUid(new Uid(uid));
        builder.setName(new Name(uid + "@example.com"));
        return builder.build();
    }
}
