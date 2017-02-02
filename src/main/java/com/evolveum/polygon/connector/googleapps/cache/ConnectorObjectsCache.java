/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evolveum.polygon.connector.googleapps.cache;

import com.evolveum.polygon.connector.googleapps.GoogleAppsConfiguration;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.joda.time.Duration;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Cache of the connector objects retrieved from Google Apps Connector.
 *
 * @author oskar.butovic
 * @author jiri.vitinger
 */
public class ConnectorObjectsCache {

    private static ConnectorObjectsCache instance;

    private final Log logger;
    private final Map<String, ConnectorObjectWrapper> usersMap;
    private final Map<String, ConnectorObjectWrapper> groupsMap;

    private boolean allowCache;
    private Duration maxCacheTTL;
    private Duration ignoreCacheAfterUpdateTTL;

    private ConnectorObjectsCache(GoogleAppsConfiguration configuration, Log connectorLogger) {
        usersMap = new HashMap<>();
        groupsMap = new HashMap<>();
        logger = connectorLogger;

        configure(configuration);
        logger.ok("Cache() - created");
    }

    private void configure(GoogleAppsConfiguration configuration) {
        final boolean oldAllowCache = allowCache;
        final Duration oldMaxCacheTTL = maxCacheTTL;
        final Duration oldIgnoreCacheAfterUpdateTTL = ignoreCacheAfterUpdateTTL;

        maxCacheTTL = new Duration(configuration.getMaxCacheTTL());
        ignoreCacheAfterUpdateTTL = new Duration(configuration.getIgnoreCacheAfterUpdateTTL());
        allowCache = Boolean.TRUE.equals(configuration.getAllowCache());

        if (allowCache != oldAllowCache ||
                !maxCacheTTL.equals(oldMaxCacheTTL) ||
                !ignoreCacheAfterUpdateTTL.equals(oldIgnoreCacheAfterUpdateTTL)) {
            logger.ok("Cache() - configured with allowCache: " + allowCache +
                    ", maxCacheTTL: " + maxCacheTTL.getStandardSeconds() + " s" +
                    ", ignoreCacheAfterUpdateTTL: " + ignoreCacheAfterUpdateTTL.getStandardSeconds() + " s");
        }
    }

    public static ConnectorObjectsCache getInstance(GoogleAppsConfiguration configuration, Log connectorLogger) {
        if (instance == null) {
            instance = new ConnectorObjectsCache(configuration, connectorLogger);
        } else {
            instance.configure(configuration);
        }
        return instance;
    }

    /**
     * Returns the user from cache or null if there is none (or expired).
     */
    @Nullable
    public ConnectorObject getUser(String uid) {
        return getObject(uid, ObjectType.USER);
    }

    public void removeUser(String uid) {
        removeObject(uid, ObjectType.USER);
    }

    public void addUser(ConnectorObject user) {
        addObject(user, ObjectType.USER);
    }

    public void markUserAsUpdatedNow(String uid) {
        markObjectAsUpdatedNow(uid, ObjectType.USER);
    }

    /**
     * Returns the group from cache or null if there is none (or expired).
     */
    @Nullable
    public ConnectorObject getGroup(String uid) {
        return getObject(uid, ObjectType.GROUP);
    }

    public void removeGroup(String uid) {
        removeObject(uid, ObjectType.GROUP);
    }

    public void addGroup(ConnectorObject object) {
        addObject(object, ObjectType.GROUP);
    }

    public void markGroupAsUpdatedNow(String uid) {
        markObjectAsUpdatedNow(uid, ObjectType.GROUP);
    }

    @Nullable
    private ConnectorObject getObject(String uid, ObjectType type) {
        if (!allowCache) {
            return null;
        }
        removeExpiredObject(uid, type);
        ConnectorObjectWrapper objectWrapper = getMap(type).get(uid);
        if (objectWrapper != null) {
            if (objectWrapper.isIgnoredAfterUpdate(ignoreCacheAfterUpdateTTL)) {
                logger.ok("Cache.getObject() - " + type.name() + " - uid " + uid + " found but ignored after update, time added " + objectWrapper.getTimeAdded()
                        + ", time updated " + objectWrapper.getTimeUpdated());
                // In this case we don't want to return an object from cache but ignore the cache
                return null;
            } else {
                logger.ok("Cache.getObject() - " + type.name() + " - uid " + uid + " found, time added " + objectWrapper.getTimeAdded());
                return objectWrapper.getObject();
            }
        } else {
            logger.info("Cache.getObject() - " + type.name() + " - uid " + uid + " not found");
            return null;
        }
    }

    private void removeObject(String uid, ObjectType type) {
        if (!allowCache) {
            return;
        }
        if (getMap(type).remove(uid) != null) {
            logger.ok("Cache.removeObject() - " + type.name() + " - uid " + uid + " removed");
        } else {
            logger.warn("Cache.removeObject() - " + type.name() + " - uid " + uid + " not found in cache");
        }
    }

    private void addObject(ConnectorObject object, ObjectType type) {
        if (!allowCache) {
            return;
        }
        ConnectorObjectWrapper existingObjectWrapper = getMap(type).get(getUid(object));
        if (existingObjectWrapper != null && existingObjectWrapper.isIgnoredAfterUpdate(ignoreCacheAfterUpdateTTL)) {
            // In this case we don't want to add the object into the cache
            logger.ok("Cache.addObject() - " + type.name() + " - uid " + getUid(object) + " found but ignored after update, time added " + existingObjectWrapper.getTimeAdded()
                    + ", time updated " + existingObjectWrapper.getTimeUpdated());
            return;
        }
        ConnectorObjectWrapper objectWrapper = new ConnectorObjectWrapper(object);
        logger.ok("Cache.addObject() - " + type.name() + " - uid " + getUid(object) + ", time added " + objectWrapper.getTimeAdded());
        getMap(type).put(getUid(object), new ConnectorObjectWrapper(object));
    }

    private void markObjectAsUpdatedNow(String uid, ObjectType type) {
        if (!allowCache) {
            return;
        }
        ConnectorObjectWrapper objectWrapper = getMap(type).get(uid);
        if (objectWrapper != null) {
            objectWrapper.markAsUpdatedNow();
            logger.ok("Cache.markAsUpdatedNow() - " + type.name() + " - uid " + uid);
        }
    }

    private String getUid(ConnectorObject object) {
        return object.getUid().getUidValue();
    }

    /**
     * Removes old expired object from cache but keeps the object that is ignored in short period after update.
     */
    private void removeExpiredObject(String uid, ObjectType type) {
        ConnectorObjectWrapper objectWrapper = getMap(type).get(uid);
        if (objectWrapper != null &&
                objectWrapper.isExpired(maxCacheTTL, ignoreCacheAfterUpdateTTL) &&
                !objectWrapper.isIgnoredAfterUpdate(ignoreCacheAfterUpdateTTL)) {
            logger.ok("Cache.removeExpiredObject() - " + type.name() + " - uid " + uid + " expired, time added " + objectWrapper.getTimeAdded());
            removeObject(uid, type);
        }
    }

    private Map<String, ConnectorObjectWrapper> getMap(ObjectType objectType) {
        switch (objectType) {
            case USER:
                return usersMap;
            case GROUP:
                return groupsMap;
            default:
                throw new IllegalArgumentException("Cache.getMap() - unknown object type: " + objectType);
        }
    }

    /**
     * Type of cached objects.
     */
    private enum ObjectType {
        USER,
        GROUP
    }
}
