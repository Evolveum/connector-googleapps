/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evolveum.polygon.connector.googleapps.cache;

import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 * Wrapper for the connector objects in a cache. Contains the object and the time when it was added into the cache.
 *
 * @author oskar.butovic
 */
public class ConnectorObjectWrapper {

    private final ConnectorObject connectorObject;
    private final DateTime timeAdded;
    private DateTime timeUpdated;

    public ConnectorObjectWrapper(ConnectorObject connectorObject) {
        this.connectorObject = connectorObject;
        this.timeAdded = DateTime.now();
    }

    public ConnectorObject getObject() {
        return connectorObject;
    }

    public DateTime getTimeAdded() {
        return timeAdded;
    }

    public DateTime getTimeUpdated() {
        return timeUpdated;
    }

    public void markAsUpdatedNow() {
        this.timeUpdated = DateTime.now();
    }

    /**
     * Returns whether the object is expired - added to cache before longer time than given TTL. Special case of expiration
     * also happens after ignore period after update.
     */
    public boolean isExpired(Duration ttl, Duration ignoreAfterUpdate) {
        return timeAdded.plus(ttl).isBeforeNow() ||
                (timeUpdated != null && timeUpdated.plus(ignoreAfterUpdate).isBeforeNow());
    }

    /**
     * Returns whether the object is only short time (not longer than given period) after update operation.
     */
    public boolean isIgnoredAfterUpdate(Duration ignoreAfterUpdate) {
        return timeUpdated != null && timeUpdated.plus(ignoreAfterUpdate).isAfterNow();
    }
}
