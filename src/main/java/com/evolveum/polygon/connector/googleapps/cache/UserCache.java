/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evolveum.polygon.connector.googleapps.cache;

import com.evolveum.polygon.connector.googleapps.GoogleAppsConfiguration;
import java.util.HashMap;
import java.util.Map;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.joda.time.Duration;

/**
 *
 * @author oskar.butovic
 */
public class UserCache {
    
    private Map<UserKey, UserObjectWrapper> usersMap;
    private Duration maxCacheTTL;
    private Boolean allowCache;
    
    public UserCache(){
        usersMap = new HashMap<UserKey, UserObjectWrapper>();
    }

    public void setMaxCacheTTL(Duration maxCacheTTL) {
        this.maxCacheTTL = maxCacheTTL;
    }

    public Duration getMaxCacheTTL() {
        return maxCacheTTL;
    }

    public Boolean getAllowCache() {
        return allowCache;
    }

    public void setAllowCache(Boolean allowCache) {
        this.allowCache = allowCache;
    }
    
    
    
    public UserKey prepareKey(String uid){
        UserKey result = new UserKey();
        result.setUid(uid);
        return result;
    }
    
    public boolean isPresent(UserKey userKey){
        boolean result = false;
        UserObjectWrapper resultWrapper = usersMap.get(userKey);
        if(resultWrapper != null){
            
        }
        return result;
    } 
    
    public boolean isPresent(String uid){
        boolean result = false;
        UserKey userKey = prepareKey(uid);
        cleanup(userKey);
        result = usersMap.containsKey(userKey);
        return result;
    } 
    
    public ConnectorObject getUser(String uid){
        UserKey userKey = prepareKey(uid);
        return getUser(userKey);
    }
    
    public ConnectorObject getUser(UserKey userKey){
        cleanup(userKey);
        UserObjectWrapper resultWrapper = usersMap.get(userKey);
        if(resultWrapper != null){
            return resultWrapper.getUserObject();
        }else{
            return null;
        }
    }
    
    public void removeUser(UserKey userKey){
        usersMap.remove(userKey);
    }
    public void addUser(ConnectorObject user){
        if(!allowCache){
            return;
        }
        UserKey userKey = prepareKey(user.getUid().toString());
        UserObjectWrapper userWrapper = new UserObjectWrapper();
        userWrapper.setUserObject(user);
        usersMap.put(userKey, userWrapper);
    }
    
    public void cleanup(){
        for(UserKey userKey : usersMap.keySet()){
            cleanup(userKey);
        }
    }
    
    public void cleanup(UserKey userKey){
        UserObjectWrapper userWrapper = usersMap.get(userKey);
        if(userWrapper != null && userWrapper.getTimeAdded().plus(maxCacheTTL).isBeforeNow()){
            usersMap.remove(userKey);
        }
    }
    
    public static UserCache init(GoogleAppsConfiguration configuration){
        UserCache result = new UserCache();
        Duration maxCacheTTLdur = new Duration(configuration.getMaxCacheTTL());
        result.setMaxCacheTTL(maxCacheTTLdur);
        result.setAllowCache(configuration.getAllowCache());
        return result;
    }
    
}
