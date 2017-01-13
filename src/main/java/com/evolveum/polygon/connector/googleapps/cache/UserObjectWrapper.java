/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evolveum.polygon.connector.googleapps.cache;

import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.joda.time.DateTime;

/**
 * Wrapper for the user objects in cache. Contains the user and the time when th user was added into the cache.
 *
 * @author oskar.butovic
 */
public class UserObjectWrapper {

    private final ConnectorObject userObject;
    private final DateTime timeAdded;

    public UserObjectWrapper(ConnectorObject userObject) {
        this.userObject = userObject;
        this.timeAdded = DateTime.now();
    }

    public ConnectorObject getUserObject() {
        return userObject;
    }

    public DateTime getTimeAdded() {
        return timeAdded;
    }
}
