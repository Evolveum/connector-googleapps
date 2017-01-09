/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evolveum.polygon.connector.googleapps.cache;

import org.joda.time.DateTime;


/**
 *
 * @author oskar.butovic
 */
public class UserKey {
    
    private String uid;

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    @Override
    public boolean equals(Object obj) {
        boolean result = false;
        if(obj != null && obj instanceof UserKey){
            result = ((UserKey) obj).getUid() != null && ((UserKey) obj).getUid().equals(uid);
        }
        return result;
    }

    @Override
    public int hashCode() {
        return uid.hashCode(); //To change body of generated methods, choose Tools | Templates.
    }
    
}
