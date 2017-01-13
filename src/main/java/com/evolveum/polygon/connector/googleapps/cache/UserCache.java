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
 * Cache of the user objects retrieved from Google Apps Connector.
 *
 * @author oskar.butovic
 */
public class UserCache {

    private static UserCache instance;

    private final Log logger;
    private final Map<String, UserObjectWrapper> usersMap;
    private final Duration maxCacheTTL;
    private final boolean allowCache;

    private UserCache(GoogleAppsConfiguration configuration, Log connectorLogger) {
        usersMap = new HashMap<String, UserObjectWrapper>();
        logger = connectorLogger;
        maxCacheTTL = new Duration(configuration.getMaxCacheTTL());
        allowCache = Boolean.TRUE.equals(configuration.getAllowCache());

        logger.ok("UserCache() - created with allowCache: " + allowCache + ", maxCacheTTL: " + maxCacheTTL.toString());
    }

    public static UserCache getInstance(GoogleAppsConfiguration configuration, Log connectorLogger) {
        if (instance == null) {
            instance = new UserCache(configuration, connectorLogger);
        }
        return instance;
    }

    /**
     * Returns the user from cache or null if there is none (or expired).
     */
    @Nullable
    public ConnectorObject getUser(String uid) {
        if (!allowCache) {
            return null;
        }
        removeExpired(uid);
        UserObjectWrapper resultWrapper = usersMap.get(uid);
        if (resultWrapper != null) {
            logger.ok("UserCache.getUser() - uid " + uid + " found, time added " + resultWrapper.getTimeAdded());
            return resultWrapper.getUserObject();
        } else {
            logger.info("UserCache.getUser() - uid " + uid + " not found");
            return null;
        }
    }

    public void removeUser(String uid) {
        if (!allowCache) {
            return;
        }
        if (usersMap.remove(uid) != null) {
            logger.ok("UserCache.removeUser() - uid " + uid + " removed");
        }
        else {
            logger.warn("UserCache.removeUser() - uid " + uid + " not found in cache");
        }
    }

    public void addUser(ConnectorObject user) {
        if (!allowCache) {
            return;
        }
        UserObjectWrapper userWrapper = new UserObjectWrapper(user);
        logger.ok("UserCache.addUser() - uid " + getUid(user) + ", time added " + userWrapper.getTimeAdded());
        usersMap.put(getUid(user), new UserObjectWrapper(user));
    }

    private String getUid(ConnectorObject user) {
        return user.getUid().getUidValue();
    }

    public void removeAllExpired() {
        for (String uid : usersMap.keySet()) {
            removeExpired(uid);
        }
    }

    public void removeExpired(String uid) {
        UserObjectWrapper userWrapper = usersMap.get(uid);
        if (userWrapper != null && userWrapper.getTimeAdded().plus(maxCacheTTL).isBeforeNow()) {
            logger.ok("UserCache.removeExpired() - uid " + uid + " expired, time added " + userWrapper.getTimeAdded());
            removeUser(uid);
        }
    }
}
