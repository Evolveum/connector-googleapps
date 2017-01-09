/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evolveum.polygon.connector.googleapps.cache;

import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.joda.time.DateTime;

/**
 *
 * @author oskar.butovic
 */
public class UserObjectWrapper {
    
    private ConnectorObject userObject;
    private DateTime timeAdded;

    public ConnectorObject getUserObject() {
        return userObject;
    }

    public void setUserObject(ConnectorObject userObject) {
        this.userObject = userObject;
    }

    public DateTime getTimeAdded() {
        return timeAdded;
    }

    public void setTimeAdded(DateTime timeAdded) {
        this.timeAdded = timeAdded;
    }
    
    
}
