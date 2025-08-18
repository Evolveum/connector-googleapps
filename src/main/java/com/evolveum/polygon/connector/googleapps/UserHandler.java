/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 ForgeRock AS. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */
package com.evolveum.polygon.connector.googleapps;

import com.evolveum.polygon.connector.googleapps.cache.ConnectorObjectsCache;
import com.google.api.client.googleapis.services.json.AbstractGoogleJsonClientRequest;
import com.google.api.client.util.Data;
import com.google.api.services.directory.Directory;
import com.google.api.services.directory.model.*;
import com.google.api.services.licensing.Licensing;
import com.google.api.services.licensing.model.LicenseAssignment;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.common.security.SecurityUtil;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.spi.SearchResultsHandler;

import java.io.IOException;
import java.util.*;

import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.*;
import static org.identityconnectors.framework.common.objects.OperationalAttributes.ENABLE_NAME;

/**
 *
 * @author Laszlo Hordos
 */
public class UserHandler {

    /**
     * Setup logging for the {@link UserHandler}.
     */
    private static final Log logger = Log.getLog(UserHandler.class);

    /**
     * Create a Member by ID for group membership operations.
     * This is a helper method used internally by UserHandler for group operations.
     */
    private static Directory.Members.Insert createMemberById(Directory.Members service, String groupKey,
            String memberId, String role) {
        try {
            Member content = new Member();
            content.setId(memberId);

            if (StringUtil.isNotBlank(role)) {
                content.setRole(role);
            }

            return service.insert(groupKey, content);
        } catch (IOException e) {
            logger.warn(e, "Failed to initialize Members#Insert");
            throw ConnectorException.wrap(e);
        }
    }

    // /////////////
    //
    // USER https://developers.google.com/admin-sdk/directory/v1/reference/users
    //
    // /////////////
    public static ObjectClassInfo getUserClassInfo() {
        // @formatter:off
            /*
         {
         "kind": "admin#directory#user",
         "id": string,
         "etag": etag,
         "primaryEmail": string,
         "name": {
         "givenName": string,
         "familyName": string,
         "fullName": string
         },
         "isAdmin": boolean,
         "isDelegatedAdmin": boolean,
         "lastLoginTime": datetime,
         "creationTime": datetime,
         "deletionTime": datetime,
         "agreedToTerms": boolean,
         "password": string,
         "hashFunction": string,
         "suspended": boolean,
         "suspensionReason": string,
         "changePasswordAtNextLogin": boolean,
         "ipWhitelisted": boolean,
         "ims": [
         {
         "type": string,
         "customType": string,
         "protocol": string,
         "customProtocol": string,
         "im": string,
         "primary": boolean
         }
         ],
         "emails": [
         {
         "address": string,
         "type": string,
         "customType": string,
         "primary": boolean
         }
         ],
         "externalIds": [
         {
         "value": string,
         "type": string,
         "customType": string
         }
         ],
         "relations": [
         {
         "value": string,
         "type": string,
         "customType": string
         }
         ],
         "addresses": [
         {
         "type": string,
         "customType": string,
         "sourceIsStructured": boolean,
         "formatted": string,
         "poBox": string,
         "extendedAddress": string,
         "streetAddress": string,
         "locality": string,
         "region": string,
         "postalCode": string,
         "country": string,
         "primary": boolean,
         "countryCode": string
         }
         ],
         "organizations": [
         {
         "name": string,
         "title": string,
         "primary": boolean,
         "type": string,
         "customType": string,
         "department": string,
         "symbol": string,
         "location": string,
         "description": string,
         "domain": string,
         "costCenter": string
         }
         ],
         "phones": [
         {
         "value": string,
         "primary": boolean,
         "type": string,
         "customType": string
         }
         ],
         "aliases": [
         string
         ],
         "nonEditableAliases": [
         string
         ],
         "customerId": string,
         "orgUnitPath": string,
         "isMailboxSetup": boolean,
         "includeInGlobalAddressList": boolean,
         "thumbnailPhotoUrl": string
         }
         */
        // @formatter:on
        ObjectClassInfoBuilder builder = new ObjectClassInfoBuilder();

        // primaryEmail
        builder.addAttributeInfo(AttributeInfoBuilder.define(Name.NAME).setRequired(true)
                .setSubtype(AttributeInfo.Subtypes.STRING_CASE_IGNORE)
                .build());

        builder.addAttributeInfo(AttributeInfoBuilder.define(GIVEN_NAME_ATTR).setRequired(true)
                .build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(FAMILY_NAME_ATTR).setRequired(true)
                .build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(FULL_NAME_ATTR).setUpdateable(false)
                .setCreateable(false).build());

        // Virtual attribute Modify supported
        builder.addAttributeInfo(AttributeInfoBuilder.build(IS_ADMIN_ATTR, Boolean.TYPE));

        builder.addAttributeInfo(AttributeInfoBuilder.define(IS_DELEGATED_ADMIN_ATTR, Boolean.TYPE)
                .setUpdateable(false).setCreateable(false).build());

        builder.addAttributeInfo(AttributeInfoBuilder.define(LAST_LOGIN_TIME_ATTR).setUpdateable(
                false).setCreateable(false).setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(CREATION_TIME_ATTR).setUpdateable(
                false).setCreateable(false).setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(AGREED_TO_TERMS_ATTR, Boolean.TYPE)
                .setUpdateable(false).setCreateable(false).build());

        builder.addAttributeInfo(AttributeInfoBuilder.define(OperationalAttributes.PASSWORD_NAME,
                GuardedString.class).setRequired(true).setReadable(false).setReturnedByDefault(
                        false).build());

        // Support activation
        builder.addAttributeInfo(OperationalAttributeInfos.ENABLE);

        builder.addAttributeInfo(AttributeInfoBuilder.build(SUSPENDED_ATTR, Boolean.class));
        builder.addAttributeInfo(AttributeInfoBuilder.define(SUSPENSION_REASON_ATTR).setUpdateable(
                false).setCreateable(false).build());
        builder.addAttributeInfo(AttributeInfoBuilder.build(CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR,
                Boolean.class));
        builder.addAttributeInfo(AttributeInfoBuilder.build(IP_WHITELISTED_ATTR, Boolean.class));

        builder.addAttributeInfo(AttributeInfoBuilder.define(IMS_ATTR).setMultiValued(
                true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(EMAILS_ATTR)
                .setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(EXTERNAL_IDS_ATTR)
                .setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(RELATIONS_ATTR)
                .setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(ADDRESSES_ATTR)
                .setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(ORGANIZATIONS_ATTR)
                .setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(PHONES_ATTR)
                .setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(ALIASES_ATTR)
                .setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(LOCATIONS_ATTR)
                .setMultiValued(true).build());

        builder.addAttributeInfo(AttributeInfoBuilder.define(NON_EDITABLE_ALIASES_ATTR)
                .setUpdateable(false).setCreateable(false).setMultiValued(true).build());

        builder.addAttributeInfo(AttributeInfoBuilder.define(CUSTOMER_ID_ATTR).setUpdateable(false)
                .setCreateable(false).build());

        builder.addAttributeInfo(AttributeInfoBuilder.build(ORG_UNIT_PATH_ATTR));

        builder.addAttributeInfo(AttributeInfoBuilder.define(IS_MAILBOX_SETUP_ATTR, Boolean.class)
                .setUpdateable(false).setCreateable(false).build());

        builder.addAttributeInfo(AttributeInfoBuilder.build(INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR,
                Boolean.class));

        builder.addAttributeInfo(AttributeInfoBuilder.define(THUMBNAIL_PHOTO_URL_ATTR)
                .setUpdateable(false).setCreateable(false).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(DELETION_TIME_ATTR).setUpdateable(
                false).setCreateable(false).build());

        // Virtual Attribute
        builder.addAttributeInfo(AttributeInfoBuilder.define(PHOTO_ATTR, byte[].class)
                .setReturnedByDefault(false).build());

        // Association
        AttributeInfo GROUPS = AttributeInfoBuilder.define(PredefinedAttributes.GROUPS_NAME, String.class)
                .setMultiValued(true)
                .setReturnedByDefault(false)
                .build();
        builder.addAttributeInfo(GROUPS);

        return builder.build();
    }

    // https://support.google.com/a/answer/33386
    public static Directory.Users.Insert createUser(Directory.Users users,
            AttributesAccessor attributes) {
        User user = new User();
        user.setPrimaryEmail(GoogleAppsUtil.getName(attributes.getName()));
        GuardedString password = attributes.getPassword();
        if (null != password) {
            user.setPassword(SecurityUtil.decrypt(password));
        } else {
            throw new InvalidAttributeValueException("Missing required attribute '__PASSWORD__'");
        }

        user.setName(new UserName());
        // givenName The user's first name. Required when creating a user
        // account.
        String givenName = attributes.findString(GIVEN_NAME_ATTR);
        if (StringUtil.isNotBlank(givenName)) {
            user.getName().setGivenName(givenName);
        } else {
            throw new InvalidAttributeValueException(
                    "Missing required attribute 'givenName'. The user's first name. Required when creating a user account.");
        }

        // familyName The user's last name. Required when creating a user
        // account.
        String familyName = attributes.findString(FAMILY_NAME_ATTR);
        if (StringUtil.isNotBlank(familyName)) {
            user.getName().setFamilyName(familyName);
        } else {
            throw new InvalidAttributeValueException(
                    "Missing required attribute 'familyName'. The user's last name. Required when creating a user account.");
        }

        // Optional
        user.setIms(GoogleAppsUtil.getStructAttr(attributes.find(IMS_ATTR)));
        user.setEmails(GoogleAppsUtil.getStructAttr(attributes.find(EMAILS_ATTR)));
        user.setExternalIds(GoogleAppsUtil.getStructAttr(attributes.find(EXTERNAL_IDS_ATTR)));
        user.setRelations(GoogleAppsUtil.getStructAttr(attributes.find(RELATIONS_ATTR)));
        user.setAddresses(GoogleAppsUtil.getStructAttr(attributes.find(ADDRESSES_ATTR)));
        user.setOrganizations(GoogleAppsUtil.getStructAttr(attributes.find(ORGANIZATIONS_ATTR)));
        user.setPhones(GoogleAppsUtil.getStructAttr(attributes.find(PHONES_ATTR)));
        user.setLocations(GoogleAppsUtil.getStructAttr(attributes.find(LOCATIONS_ATTR)));

        // Handle suspended/enabled with priority: suspended takes precedence over __ENABLED__
        Boolean suspendedValue = attributes.findBoolean(SUSPENDED_ATTR);
        if (suspendedValue != null) {
            // suspended attribute is present - use it directly
            user.setSuspended(suspendedValue);
        } else if (Boolean.FALSE.equals(attributes.findBoolean(ENABLE_NAME))) {
            // No suspended attribute, but __ENABLED__ = false - set suspended to true
            user.setSuspended(true);
        }
        user.setChangePasswordAtNextLogin(attributes
                .findBoolean(CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR));
        user.setIpWhitelisted(attributes.findBoolean(IP_WHITELISTED_ATTR));
        user.setOrgUnitPath(attributes.findString(ORG_UNIT_PATH_ATTR));
        user.setIncludeInGlobalAddressList(attributes
                .findBoolean(INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR));

        try {
            return users.insert(user).setFields(ID_ETAG);
            // } catch (HttpResponseException e){
        } catch (IOException e) {
            logger.warn(e, "Failed to initialize Groups#Insert");
            throw ConnectorException.wrap(e);
        }
    }


    public static Directory.Users.Photos.Update createUpdateUserPhoto(
            Directory.Users.Photos service, String userKey, byte[] data) {
        UserPhoto content = new UserPhoto();
        // Required
        content.setPhotoData(com.google.api.client.util.Base64.encodeBase64URLSafeString(data));

        // @formatter:off
        /*
         content.setPhotoData(com.google.api.client.util.Base64
         .encodeBase64URLSafeString((byte[]) data.get("photoData")));
         content.setHeight((Integer) data.get("height"));
         content.setWidth((Integer) data.get("width"));

         // Allowed values are JPEG, PNG, GIF, BMP, TIFF,
         content.setMimeType((String) data.get("mimeType"));
         */
        // @formatter:on
        try {
            return service.update(userKey, content).setFields(ID_ATTR);
            // } catch (HttpResponseException e){
        } catch (IOException e) {
            logger.warn(e, "Failed to initialize Aliases#Insert");
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Users.Photos.Delete createDeleteUserPhoto(
            Directory.Users.Photos service, String userKey) {
        try {
            return service.delete(userKey);
        } catch (IOException e) {
            logger.warn(e, "Failed to initialize delete user photo request for user: {0}", userKey);
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Users.Aliases.Insert createUserAlias(Directory.Users.Aliases service,
            String userKey, String alias) {
        Alias content = new Alias();
        content.setAlias(alias);
        try {
            return service.insert(userKey, content).setFields(ID_ETAG);
            // } catch (HttpResponseException e){
        } catch (IOException e) {
            logger.warn(e, "Failed to initialize Aliases#Insert");
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Users.Aliases.Delete deleteUserAlias(Directory.Users.Aliases service,
            String userKey, String alias) {
        try {
            return service.delete(userKey, alias);
            // } catch (HttpResponseException e){
        } catch (IOException e) {
            logger.warn(e, "Failed to initialize Aliases#Delete");
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Users.Update buildUserUpdateRequest(Directory.Users users, Uid uid,
            Set<AttributeDelta> modifications, Optional<User> currentUser) {
        User content = new User();
        boolean hasChanges = false;

        // Check if suspended attribute is present - it takes precedence over __ENABLED__
        boolean hasSuspendedAttribute = modifications.stream()
            .anyMatch(delta -> SUSPENDED_ATTR.equals(delta.getName()));

        for (AttributeDelta delta : modifications) {
            String attributeName = delta.getName();
            
            // Handle Name (primaryEmail)
            if (AttributeUtil.namesEqual(Name.NAME, attributeName)) {
                List<Object> valuesToReplace = delta.getValuesToReplace();
                if (valuesToReplace != null && !valuesToReplace.isEmpty()) {
                    content.setPrimaryEmail((String) valuesToReplace.get(0));
                    hasChanges = true;
                }
            }
            // Handle givenName
            else if (GIVEN_NAME_ATTR.equals(attributeName)) {
                String value = RequestResultHandler.getSingleValue(delta, String.class);
                if (content.getName() == null) {
                    content.setName(new UserName());
                }
                // Google API requires Data.NULL_STRING to clear field
                content.getName().setGivenName(value != null ? value : Data.NULL_STRING);
                hasChanges = true;
            }
            // Handle familyName
            else if (FAMILY_NAME_ATTR.equals(attributeName)) {
                String value = RequestResultHandler.getSingleValue(delta, String.class);
                if (content.getName() == null) {
                    content.setName(new UserName());
                }
                // Google API requires Data.NULL_STRING to clear field
                content.getName().setFamilyName(value != null ? value : Data.NULL_STRING);
                hasChanges = true;
            }
            // Handle password - special processing for replace vs add/remove operations
            else if (AttributeUtil.namesEqual(OperationalAttributes.PASSWORD_NAME, attributeName)) {
                List<Object> valuesToReplace = delta.getValuesToReplace();
                List<Object> valuesToAdd = delta.getValuesToAdd();
                List<Object> valuesToRemove = delta.getValuesToRemove();
                
                if (valuesToReplace != null && !valuesToReplace.isEmpty()) {
                    // Password Reset operation (admin-initiated, no old password needed)
                    GuardedString newPassword = (GuardedString) valuesToReplace.get(0);
                    content.setPassword(SecurityUtil.decrypt(newPassword));
                    hasChanges = true;
                } else if (valuesToAdd != null && !valuesToAdd.isEmpty()) {
                    // Password Change operation (user-initiated in ConnID terms)
                    // Google Directory API doesn't require old password validation - it operates with admin privileges
                    // The API directly sets the new password regardless of the old one
                    GuardedString newPassword = (GuardedString) valuesToAdd.get(0);
                    content.setPassword(SecurityUtil.decrypt(newPassword));
                    hasChanges = true;
                    // Note: valuesToRemove contains old password but Google API ignores it completely
                }
            }
            // Handle enable/suspended
            else if (ENABLE_NAME.equals(attributeName)) {
                // If suspended attribute is present, ignore __ENABLED__ (suspended takes precedence)
                if (hasSuspendedAttribute) {
                    // Skip __ENABLED__ processing when suspended attribute is also present
                    continue;
                }
                Boolean enable = RequestResultHandler.getSingleValue(delta, Boolean.class);
                if (enable == null) {
                    // Clear request - ignore it as __ENABLE__ cannot be cleared
                    // Note: __ENABLE__ cannot be cleared (set to null) as users must be either enabled or disabled
                } else {
                    content.setSuspended(!enable);
                    hasChanges = true;
                }
            }
            else if (SUSPENDED_ATTR.equals(attributeName)) {
                Boolean value = RequestResultHandler.getSingleValue(delta, Boolean.class);
                content.setSuspended(value != null ? value : Data.NULL_BOOLEAN);
                hasChanges = true;
            }
            // Handle changePasswordAtNextLogin
            else if (CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR.equals(attributeName)) {
                Boolean value = RequestResultHandler.getSingleValue(delta, Boolean.class);
                content.setChangePasswordAtNextLogin(value != null ? value : Data.NULL_BOOLEAN);
                hasChanges = true;
            }
            // Handle ipWhitelisted
            else if (IP_WHITELISTED_ATTR.equals(attributeName)) {
                Boolean value = RequestResultHandler.getSingleValue(delta, Boolean.class);
                content.setIpWhitelisted(value != null ? value : Data.NULL_BOOLEAN);
                hasChanges = true;
            }
            // Handle multi-valued attributes
            else if (IMS_ATTR.equals(attributeName)) {
                Object newValue = processMultiValuedAttribute(delta, getCurrentAttributeValue(currentUser.orElse(null), IMS_ATTR));
                content.setIms(newValue); // null means clear all values
                hasChanges = true;
            }
            else if (EMAILS_ATTR.equals(attributeName)) {
                Object newValue = processMultiValuedAttribute(delta, getCurrentAttributeValue(currentUser.orElse(null), EMAILS_ATTR));
                content.setEmails(newValue); // null means clear all values
                hasChanges = true;
            }
            else if (EXTERNAL_IDS_ATTR.equals(attributeName)) {
                Object newValue = processMultiValuedAttribute(delta, getCurrentAttributeValue(currentUser.orElse(null), EXTERNAL_IDS_ATTR));
                content.setExternalIds(newValue); // null means clear all values
                hasChanges = true;
            }
            else if (RELATIONS_ATTR.equals(attributeName)) {
                Object newValue = processMultiValuedAttribute(delta, getCurrentAttributeValue(currentUser.orElse(null), RELATIONS_ATTR));
                content.setRelations(newValue); // null means clear all values
                hasChanges = true;
            }
            else if (ADDRESSES_ATTR.equals(attributeName)) {
                Object newValue = processMultiValuedAttribute(delta, getCurrentAttributeValue(currentUser.orElse(null), ADDRESSES_ATTR));
                content.setAddresses(newValue); // null means clear all values
                hasChanges = true;
            }
            else if (ORGANIZATIONS_ATTR.equals(attributeName)) {
                Object newValue = processMultiValuedAttribute(delta, getCurrentAttributeValue(currentUser.orElse(null), ORGANIZATIONS_ATTR));
                content.setOrganizations(newValue); // null means clear all values
                hasChanges = true;
            }
            else if (PHONES_ATTR.equals(attributeName)) {
                Object newValue = processMultiValuedAttribute(delta, getCurrentAttributeValue(currentUser.orElse(null), PHONES_ATTR));
                content.setPhones(newValue); // null means clear all values
                hasChanges = true;
            }
            else if (LOCATIONS_ATTR.equals(attributeName)) {
                Object newValue = processMultiValuedAttribute(delta, getCurrentAttributeValue(currentUser.orElse(null), LOCATIONS_ATTR));
                content.setLocations(newValue); // null means clear all values
                hasChanges = true;
            }
            // Handle simple string attributes
            else if (ORG_UNIT_PATH_ATTR.equals(attributeName)) {
                String value = RequestResultHandler.getSingleValue(delta, String.class);
                // Google API requires Data.NULL_STRING to clear field
                content.setOrgUnitPath(value != null ? value : Data.NULL_STRING);
                hasChanges = true;
            }
            else if (INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR.equals(attributeName)) {
                Boolean value = RequestResultHandler.getSingleValue(delta, Boolean.class);
                content.setIncludeInGlobalAddressList(value != null ? value : Data.NULL_BOOLEAN);
                hasChanges = true;
            }
        }

        if (!hasChanges) {
            return null;
        }

        try {
            return users.update(uid.getUidValue(), content).setFields(ID_ETAG);
        } catch (IOException e) {
            logger.warn(e, "Failed to initialize Users#Update");
            throw ConnectorException.wrap(e);
        }
    }

    public static boolean isMultiValuedAttribute(String attributeName) {
        return IMS_ATTR.equals(attributeName) ||
               EMAILS_ATTR.equals(attributeName) ||
               EXTERNAL_IDS_ATTR.equals(attributeName) ||
               RELATIONS_ATTR.equals(attributeName) ||
               ADDRESSES_ATTR.equals(attributeName) ||
               ORGANIZATIONS_ATTR.equals(attributeName) ||
               PHONES_ATTR.equals(attributeName) ||
               LOCATIONS_ATTR.equals(attributeName);
    }

    public static boolean hasAddOrRemoveOperations(AttributeDelta delta) {
        return (delta.getValuesToAdd() != null && !delta.getValuesToAdd().isEmpty()) ||
               (delta.getValuesToRemove() != null && !delta.getValuesToRemove().isEmpty());
    }

    private static Object getCurrentAttributeValue(User currentUser, String attributeName) {
        if (currentUser == null) {
            return null;
        }
        switch (attributeName) {
            case IMS_ATTR:
                return currentUser.getIms();
            case EMAILS_ATTR:
                return currentUser.getEmails();
            case EXTERNAL_IDS_ATTR:
                return currentUser.getExternalIds();
            case RELATIONS_ATTR:
                return currentUser.getRelations();
            case ADDRESSES_ATTR:
                return currentUser.getAddresses();
            case ORGANIZATIONS_ATTR:
                return currentUser.getOrganizations();
            case PHONES_ATTR:
                return currentUser.getPhones();
            case LOCATIONS_ATTR:
                return currentUser.getLocations();
            default:
                return null;
        }
    }

    private static Object processMultiValuedAttribute(AttributeDelta delta, Object currentValue) {
        // Check for replace operation first (if present, add/remove should not be present)
        List<Object> valuesToReplace = delta.getValuesToReplace();
        if (valuesToReplace != null) {
            // Replace operation - replace all existing values
            if (valuesToReplace.isEmpty()) {
                return new ArrayList<>(); // Empty list means clear all values - return empty array, not null
            }
            Attribute tempAttr = AttributeBuilder.build(delta.getName(), valuesToReplace);
            return GoogleAppsUtil.getStructAttr(tempAttr);
        }
        
        // Add/Remove operations - need to merge with current values
        List<Object> valuesToAdd = delta.getValuesToAdd();
        List<Object> valuesToRemove = delta.getValuesToRemove();

        // Convert current value to String list for processing
        List<String> currentStringValues = convertToStringList(currentValue);
        Set<String> newValues = new HashSet<>(currentStringValues);

        // Process add operations
        if (valuesToAdd != null) {
            for (Object value : valuesToAdd) {
                if (value instanceof String) {
                    newValues.add((String) value);
                }
            }
        }

        // Process remove operations
        if (valuesToRemove != null) {
            for (Object value : valuesToRemove) {
                if (value instanceof String) {
                    String valueToRemove = (String) value;
                    // Find and remove matching values by normalizing JSON comparison
                    newValues.removeIf(existingValue -> matchesJsonValue(existingValue, valueToRemove));
                }
            }
        }

        if (newValues.isEmpty()) {
            return new ArrayList<>(); // All values removed - return empty array, not null
        }

        // Convert back to Google API format
        Attribute tempAttr = AttributeBuilder.build(delta.getName(), new ArrayList<>(newValues));
        return GoogleAppsUtil.getStructAttr(tempAttr);
    }

    private static List<String> convertToStringList(Object googleValue) {
        List<String> result = new ArrayList<>();
        if (googleValue instanceof Collection) {
            Collection<?> collection = (Collection<?>) googleValue;
            Gson gson = new GsonBuilder().create();
            for (Object item : collection) {
                if (item != null) {
                    // Convert Google API object to JSON string for proper comparison
                    result.add(gson.toJson(item));
                }
            }
        }
        return result;
    }

    /**
     * Check if the current JSON value matches the target JSON value for removal.
     * This handles differences in field ordering and formatting, but requires exact field matching.
     * Note: Objects with different fields (including null vs absent fields) are considered NOT equal.
     */
    private static boolean matchesJsonValue(String currentJsonValue, String valueToRemove) {
        try {
            Gson gson = new GsonBuilder().create();
            
            // Parse both JSON strings into generic objects for structural comparison
            Object currentObj = gson.fromJson(currentJsonValue, Object.class);
            Object removeObj = gson.fromJson(valueToRemove, Object.class);
            
            // Compare the parsed objects (handles field ordering and null differences)
            return Objects.equals(currentObj, removeObj);
        } catch (Exception e) {
            // If parsing fails, fall back to string comparison
            return currentJsonValue.equals(valueToRemove);
        }
    }

    // UpdateDelta helper methods moved to RequestResultHandler for reuse

    public static User fetchCurrentUserData(GoogleApiExecutor executor, Uid uid, Set<String> requiredFields) {
        try {
            Directory.Users.Get request = executor.getDirectory().users().get(uid.getUidValue());
            if (!requiredFields.isEmpty()) {
                request.setFields("id," + String.join(",", requiredFields));
            }
            return executor.execute(request, new RequestResultHandler<Directory.Users.Get, User, User>() {
                public User handleResult(Directory.Users.Get request, User value) {
                    return value;
                }

                public User handleNotFound(Directory.Users.Get request) {
                    return null;
                }

                public User handleDuplicate(Directory.Users.Get request) {
                    return null;
                }
            });
        } catch (IOException e) {
            throw ConnectorException.wrap(e);
        }
    }

    public static Set<AttributeDelta> executeUserUpdateDelta(GoogleApiExecutor executor, Uid uid, 
            Set<AttributeDelta> modifications) {
        final Set<AttributeDelta> sideEffectDeltas = new HashSet<>();
        
        // Separate special attributes that need special handling
        Set<AttributeDelta> coreDeltas = new HashSet<>();
        AttributeDelta aliasesDelta = null;
        AttributeDelta photoDelta = null;
        AttributeDelta isAdminDelta = null;
        AttributeDelta groupsDelta = null;
        
        // Check if we need to fetch current user data for multi-valued attributes
        boolean needsCurrentUserData = false;
        Set<String> requiredFields = new HashSet<>();
        
        for (AttributeDelta delta : modifications) {
            String attrName = delta.getName();
            if (ALIASES_ATTR.equals(attrName)) {
                aliasesDelta = delta;
            } else if (PHOTO_ATTR.equals(attrName)) {
                photoDelta = delta;
            } else if (IS_ADMIN_ATTR.equals(attrName)) {
                isAdminDelta = delta;
            } else if (PredefinedAttributes.GROUPS_NAME.equals(attrName)) {
                groupsDelta = delta;
            } else {
                coreDeltas.add(delta);
                // Check if this is a multi-valued attribute with add/remove operations
                if (isMultiValuedAttribute(attrName) && hasAddOrRemoveOperations(delta)) {
                    needsCurrentUserData = true;
                    requiredFields.add(attrName);
                }
            }
        }
        
        // Fetch current user data if needed for multi-valued attributes
        User currentUser = null;
        if (needsCurrentUserData) {
            currentUser = fetchCurrentUserData(executor, uid, requiredFields);
        }
        
        // Update core user attributes
        Uid uidAfterUpdate = uid;
        if (!coreDeltas.isEmpty()) {
            final Directory.Users.Update update = buildUserUpdateRequest(executor.getDirectory().users(), uid, coreDeltas, Optional.ofNullable(currentUser));
            if (update != null) {
                uidAfterUpdate = executor.execute(update,
                        new RequestResultHandler<Directory.Users.Update, User, Uid>() {
                            public Uid handleResult(Directory.Users.Update request, User value) {
                                logger.ok("User is Updated:{0}", value.getId());
                                return new Uid(value.getId());
                            }

                            public Uid handleNotFound(Directory.Users.Update request) {
                                return uid;
                            }

                            public Uid handleDuplicate(Directory.Users.Update request) {
                                return uid;
                            }
                        });
            }
        }
        
        // Handle special attributes
        if (aliasesDelta != null) {
            handleAliasesDelta(executor, uidAfterUpdate, aliasesDelta);
        }
        
        if (photoDelta != null) {
            // Check for replace operation or add operation (photo is single-valued)
            List<Object> valuesToReplace = photoDelta.getValuesToReplace();
            List<Object> valuesToAdd = photoDelta.getValuesToAdd();
            
            boolean shouldDelete = false;
            byte[] photoData = null;
            
            if (valuesToReplace != null) {
                if (valuesToReplace.isEmpty()) {
                    // Empty list means delete the photo
                    shouldDelete = true;
                } else {
                    Object photoObject = valuesToReplace.get(0);
                    if (photoObject == null) {
                        // Null value means delete the photo
                        shouldDelete = true;
                    } else if (photoObject instanceof byte[]) {
                        photoData = (byte[]) photoObject;
                        if (photoData.length == 0) {
                            // Empty array means delete the photo
                            shouldDelete = true;
                        }
                    }
                }
            } else if (valuesToAdd != null && !valuesToAdd.isEmpty()) {
                Object photoObject = valuesToAdd.get(0);
                if (photoObject instanceof byte[]) {
                    photoData = (byte[]) photoObject;
                }
            }
            
            if (shouldDelete) {
                // Handle photo deletion
                handlePhotoDelete(executor, uidAfterUpdate);
            } else if (photoData != null && photoData.length > 0) {
                // Handle photo update
                handlePhoto(executor, uidAfterUpdate, photoData);
            }
        }
        
        if (isAdminDelta != null) {
            // isAdmin is single-valued boolean - only handle replace operation
            List<Object> valuesToReplace = isAdminDelta.getValuesToReplace();
            if (valuesToReplace != null && !valuesToReplace.isEmpty()) {
                Object adminObject = valuesToReplace.get(0);
                if (adminObject instanceof Boolean) {
                    handleIsAdmin(executor, uidAfterUpdate, (Boolean) adminObject);
                }
            }
        }
        
        if (groupsDelta != null) {
            handleGroupsDelta(executor, uidAfterUpdate, groupsDelta);
        }
        
        return sideEffectDeltas;
    }

    /**
     * Execute user creation with all special attribute processing.
     * This consolidates the create logic from GoogleAppsConnector to eliminate duplication.
     */
    public static Uid executeUserCreate(GoogleApiExecutor executor,
                                       Set<Attribute> createAttributes) {
        final AttributesAccessor accessor = new AttributesAccessor(createAttributes);
        
        // Create the core user
        Uid uid = executor.execute(createUser(executor.getDirectory().users(), accessor),
                new RequestResultHandler.Create<>(ObjectClass.ACCOUNT, 
                    (User user) -> new Uid(user.getId(), user.getEtag(), new Name(user.getPrimaryEmail()))));

        // Handle special attributes using existing delta handlers
        try {
            // Handle aliases
            List<Object> aliases = accessor.findList(ALIASES_ATTR);
            if (aliases != null && !aliases.isEmpty()) {
                handleAliasesCreate(executor, uid, aliases);
            }

            // Handle photo
            Attribute photo = accessor.find(PHOTO_ATTR);
            if (photo != null) {
                Object photoObject = AttributeUtil.getSingleValue(photo);
                if (photoObject instanceof byte[]) {
                    handlePhoto(executor, uid, (byte[]) photoObject);
                }
            }

            // Handle isAdmin
            Attribute isAdmin = accessor.find(IS_ADMIN_ATTR);
            if (isAdmin != null) {
                Boolean isAdminValue = AttributeUtil.getBooleanValue(isAdmin);
                if (isAdminValue != null && isAdminValue) {
                    handleIsAdmin(executor, uid, isAdminValue);
                }
            }

            // Handle groups
            Attribute groups = accessor.find(PredefinedAttributes.GROUPS_NAME);
            if (groups != null && groups.getValue() != null) {
                handleGroupsCreate(executor, uid, groups.getValue());
            }

            // Handle license assignment if configured
            if (Boolean.TRUE.equals(executor.getConfiguration().getAutoAddLicense())) {
                handleLicenseCreate(executor, uid, accessor);
            }

        } catch (Exception e) {
            logger.warn(e, "Failed to process special attributes during user creation: {0}", uid.getUidValue());
            // Note: In a production system, you might want to clean up the partially created user
        }

        return uid;
    }

    private static void handleAliasesCreate(GoogleApiExecutor executor,
                                           Uid uid, List<Object> aliases) {
        final Directory.Users.Aliases aliasesService = executor.getDirectory().users().aliases();
        for (Object member : aliases) {
            if (member instanceof String) {
                executor.execute(createUserAlias(aliasesService, uid.getUidValue(), (String) member),
                        new RequestResultHandler.NoOp<Directory.Users.Aliases.Insert, Alias>());
            }
        }
    }

    private static void handlePhoto(GoogleApiExecutor executor,
                                   Uid uid, byte[] photoData) {
        if (photoData == null || photoData.length == 0) {
            return;
        }
        
        try {
            executor.execute(createUpdateUserPhoto(executor.getDirectory().users().photos(),
                            uid.getUidValue(), photoData),
                    new RequestResultHandler.NoOp<Directory.Users.Photos.Update, UserPhoto>());
        } catch (Exception e) {
            logger.warn(e, "Failed to handle photo for user: {0}", uid.getUidValue());
            throw ConnectorException.wrap(e);
        }
    }

    private static void handlePhotoDelete(GoogleApiExecutor executor, Uid uid) {
        try {
            executor.execute(createDeleteUserPhoto(executor.getDirectory().users().photos(),
                            uid.getUidValue()),
                    new RequestResultHandler.NoOp<Directory.Users.Photos.Delete, Void>());
        } catch (Exception e) {
            logger.warn(e, "Failed to delete photo for user: {0}", uid.getUidValue());
            throw ConnectorException.wrap(e);
        }
    }

    private static void handleIsAdmin(GoogleApiExecutor executor,
                                      Uid uid, Boolean isAdminValue) {
        if (isAdminValue == null) {
            return;
        }
        
        try {
            UserMakeAdmin content = new UserMakeAdmin();
            content.setStatus(isAdminValue);
            executor.execute(executor.getDirectory().users().makeAdmin(uid.getUidValue(), content),
                    new RequestResultHandler.NoOp<Directory.Users.MakeAdmin, Void>());
        } catch (Exception e) {
            logger.warn(e, "Failed to set admin status for user: {0}", uid.getUidValue());
            throw ConnectorException.wrap(e);
        }
    }

    private static void handleGroupsCreate(GoogleApiExecutor executor,
                                          Uid uid, List<Object> groups) {
        final Directory.Members service = executor.getDirectory().members();
        for (Object groupId : groups) {
            if (groupId instanceof String) {
                executor.execute(createMemberById(service, (String) groupId, uid.getUidValue(), "MEMBER"),
                        new RequestResultHandler.NoOp<Directory.Members.Insert, Member>());
            }
        }
    }

    private static void handleLicenseCreate(GoogleApiExecutor executor,
                                           Uid uid, AttributesAccessor accessor) {
        GoogleAppsConfiguration configuration = executor.getConfiguration();
        try {
            // license assignments
            // add license https://developers.google.com/admin-sdk/licensing/v1/reference/licenseAssignments
            Uid licId = executor.execute(
                    LicenseAssignmentsHandler.createLicenseAssignment(configuration.getLicensing().licenseAssignments(),
                            configuration.getProductId(), configuration.getSkuId(), 
                            AttributeUtil.getAsStringValue(accessor.find("__NAME__"))),
                    new RequestResultHandler<Licensing.LicenseAssignments.Insert, LicenseAssignment, Uid>() {
                        public Uid handleResult(final Licensing.LicenseAssignments.Insert request,
                                              final LicenseAssignment value) {
                            logger.ok("LicenseAssignment is Created:{0}/{1}/{2}", value
                                    .getProductId(), value.getSkuId(), value.getUserId());
                            return LicenseAssignmentsHandler.generateLicenseAssignmentId(value);
                        }

                        @Override
                        public Uid handleNotFound(IOException e) {
                            logger.warn("License assignment not found: {0}", e.getMessage());
                            return null;
                        }

                        @Override
                        public Uid handleDuplicate(IOException e) {
                            logger.warn("License assignment already exists: {0}", e.getMessage());
                            return null;
                        }
                    });
        } catch (Exception e) {
            logger.warn(e, "Failed to assign license to user: {0}", uid.getUidValue());
            // License assignment failure shouldn't prevent user creation
        }
    }

    private static void handleAliasesDelta(GoogleApiExecutor executor, 
                                          Uid uid, AttributeDelta aliasesDelta) {
        try {
            final Directory.Users.Aliases aliasesService = executor.getDirectory().users().aliases();
            
            // Check for replace operation first
            List<Object> valuesToReplace = aliasesDelta.getValuesToReplace();
            if (valuesToReplace != null) {
                // Replace operation - get current aliases and replace completely
                Set<String> currentAliases = listAliases(executor, uid.getUidValue());
                Set<String> newAliases = new HashSet<>();
                for (Object alias : valuesToReplace) {
                    if (alias instanceof String) {
                        newAliases.add((String) alias);
                    }
                }
                
                // Delete aliases not in new set
                for (String currentAlias : currentAliases) {
                    if (!newAliases.contains(currentAlias)) {
                        executor.execute(deleteUserAlias(aliasesService, uid.getUidValue(), currentAlias),
                                new RequestResultHandler.NoOp<Directory.Users.Aliases.Delete, Alias>());
                    }
                }
                
                // Add new aliases
                for (String newAlias : newAliases) {
                    if (!currentAliases.contains(newAlias)) {
                        executor.execute(createUserAlias(aliasesService, uid.getUidValue(), newAlias),
                                new RequestResultHandler.NoOp<Directory.Users.Aliases.Insert, Alias>());
                    }
                }
                return;
            }
            
            // Handle add/remove operations
            List<Object> valuesToAdd = aliasesDelta.getValuesToAdd();
            List<Object> valuesToRemove = aliasesDelta.getValuesToRemove();
            
            // Add aliases
            if (valuesToAdd != null && !valuesToAdd.isEmpty()) {
                for (Object alias : valuesToAdd) {
                    if (alias instanceof String) {
                        executor.execute(createUserAlias(aliasesService, uid.getUidValue(), (String) alias),
                                new RequestResultHandler.NoOp<Directory.Users.Aliases.Insert, Alias>());
                    }
                }
            }
            
            // Remove aliases
            if (valuesToRemove != null && !valuesToRemove.isEmpty()) {
                for (Object alias : valuesToRemove) {
                    if (alias instanceof String) {
                        executor.execute(deleteUserAlias(aliasesService, uid.getUidValue(), (String) alias),
                                new RequestResultHandler.NoOp<Directory.Users.Aliases.Delete, Alias>());
                    }
                }
            }
            
        } catch (Exception e) {
            logger.warn(e, "Failed to handle aliases delta for user: {0}", uid.getUidValue());
            throw ConnectorException.wrap(e);
        }
    }

    private static void handleGroupsDelta(GoogleApiExecutor executor,
                                         Uid uid, AttributeDelta groupsDelta) {
        try {
            final Directory.Members service = executor.getDirectory().members();
            
            // Check for replace operation first
            List<Object> valuesToReplace = groupsDelta.getValuesToReplace();
            if (valuesToReplace != null) {
                // Replace operation - get current group memberships and replace completely
                Set<String> currentGroups = GroupConverter.listGroups(executor.getDirectory().groups(), uid.getUidValue());
                Set<String> newGroups = new HashSet<>();
                for (Object groupId : valuesToReplace) {
                    if (groupId instanceof String) {
                        newGroups.add((String) groupId);
                    }
                }
                
                // Remove user from groups not in new set
                for (String currentGroup : currentGroups) {
                    if (!newGroups.contains(currentGroup)) {
                        try {
                            executor.execute(service.delete(currentGroup, uid.getUidValue()),
                                    new RequestResultHandler.NoOp<Directory.Members.Delete, Void>());
                        } catch (Exception e) {
                            logger.warn(e, "Failed to remove user {0} from group {1}", uid.getUidValue(), currentGroup);
                        }
                    }
                }
                
                // Add user to new groups
                for (String newGroup : newGroups) {
                    if (!currentGroups.contains(newGroup)) {
                        try {
                            executor.execute(createMemberById(service, newGroup, uid.getUidValue(), "MEMBER"),
                                    new RequestResultHandler.NoOp<Directory.Members.Insert, Member>());
                        } catch (Exception e) {
                            logger.warn(e, "Failed to add user {0} to group {1}", uid.getUidValue(), newGroup);
                        }
                    }
                }
                return;
            }
            
            // Handle add/remove operations
            List<Object> valuesToAdd = groupsDelta.getValuesToAdd();
            List<Object> valuesToRemove = groupsDelta.getValuesToRemove();
            
            // Add group memberships
            if (valuesToAdd != null && !valuesToAdd.isEmpty()) {
                for (Object groupId : valuesToAdd) {
                    if (groupId instanceof String) {
                        try {
                            executor.execute(createMemberById(service, (String) groupId, 
                                    uid.getUidValue(), "MEMBER"),
                                    new RequestResultHandler.NoOp<Directory.Members.Insert, Member>());
                        } catch (Exception e) {
                            logger.warn(e, "Failed to add user {0} to group {1}", uid.getUidValue(), groupId);
                        }
                    }
                }
            }
            
            // Remove group memberships
            if (valuesToRemove != null && !valuesToRemove.isEmpty()) {
                for (Object groupId : valuesToRemove) {
                    if (groupId instanceof String) {
                        try {
                            executor.execute(service.delete((String) groupId, uid.getUidValue()),
                                    new RequestResultHandler.NoOp<Directory.Members.Delete, Void>());
                        } catch (Exception e) {
                            logger.warn(e, "Failed to remove user {0} from group {1}", uid.getUidValue(), groupId);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            logger.warn(e, "Failed to handle groups delta for user: {0}", uid.getUidValue());
            throw ConnectorException.wrap(e);
        }
    }

    public static Set<String> listAliases(GoogleApiExecutor executor, String userKey) {
        final Set<String> result = CollectionUtil.newCaseInsensitiveSet();
        final Gson gson = new GsonBuilder().create();
        try {

            final Directory.Users.Aliases aliasesService = executor.getDirectory().users().aliases();
            Directory.Users.Aliases.List request = aliasesService.list(userKey);

            String nextPageToken = null;
            do {
                //TODO user alias request has no page token, how to page?
                /*if(StringUtil.isNotBlank(nextPageToken)){
                    request.setPageToken(nextPageToken);
                }*/
                nextPageToken
                        = executor.execute(request,
                        new RequestResultHandler<Directory.Users.Aliases.List, Aliases, String>() {
                            public String handleResult(Directory.Users.Aliases.List request, Aliases value) {
                                if (null != value.getAliases()) {
                                    for (Object alias : value.getAliases()) {
                                        String toJson = gson.toJson(alias);
                                        Alias fromJson = gson.fromJson(toJson, Alias.class);
                                        result.add(fromJson.getAlias()); // return only alias parameter of json object
                                    }
                                }
                                return null;
                            }
                        });

            } while (StringUtil.isNotBlank(nextPageToken));
            // } catch (HttpResponseException e){
        } catch (IOException e) {
            logger.warn(e, "Failed to initialize Aliases#List");
            throw ConnectorException.wrap(e);
        }
        return result;
    }

    /**
     * Execute account read query by UID.
     */
    public static void executeAccountReadQuery(GoogleApiExecutor executor, ConnectorObjectsCache objectsCache,
                                             Uid uid, final ResultsHandler handler, OperationOptions options, 
                                             SchemaDefinition schemaDef) {
        try {
            // Get attributes to retrieve - call only once
            final Set<String> attributesToGet = schemaDef.createFullAttributesToGet(options);
            
            // Try the cache first
            ConnectorObject cachedUser = objectsCache.getUser(uid.getUidValue());
            if (cachedUser != null) {
                handler.handle(cachedUser);
                return;
            }

            // No success in cache, do the remote call
            Directory.Users.Get request = executor.getDirectory().users().get(uid.getUidValue());
            String fields = schemaDef.createGoogleApiFieldsString(attributesToGet, ID_ATTR, ETAG_ATTR, PRIMARY_EMAIL_ATTR);
            request.setFields(fields);

            executor.execute(request,
                    new RequestResultHandler.ReadQuery<>(
                            user -> UserConverter.fromUser(user, attributesToGet, executor.getDirectory().groups(), executor.getConfiguration(), options),
                            objectsCache::addUser,
                            handler));

        } catch (IOException e) {
            logger.warn(e, "Failed to initialize Users#Get");
            throw ConnectorException.wrap(e);
        }
    }

    /**
     * Execute account read query by Name.
     */
    public static void executeAccountReadQuery(GoogleApiExecutor executor, ConnectorObjectsCache objectsCache,
                                             Name name, final ResultsHandler handler, OperationOptions options, 
                                             SchemaDefinition schemaDef) {
        try {
            // Get attributes to retrieve - call only once
            final Set<String> attributesToGet = schemaDef.createFullAttributesToGet(options);
            
            Directory.Users.Get request = executor.getDirectory().users().get(name.getNameValue());
            String fields = schemaDef.createGoogleApiFieldsString(attributesToGet, ID_ATTR, ETAG_ATTR, PRIMARY_EMAIL_ATTR);
            request.setFields(fields);

            executor.execute(request,
                    new RequestResultHandler.ReadQuery<>(
                            user -> UserConverter.fromUser(user, attributesToGet, executor.getDirectory().groups(), executor.getConfiguration(), options),
                            objectsCache::addUser,
                            handler));

        } catch (IOException e) {
            logger.warn(e, "Failed to initialize Users#Get");
            throw ConnectorException.wrap(e);
        }
    }

    /**
     * Execute account search query.
     */
    public static void executeAccountSearchQuery(GoogleApiExecutor executor, GoogleFilter googleFilter,
                                               final ResultsHandler handler, OperationOptions options,
                                               SchemaDefinition schemaDef) {
        try {
            // Get attributes to retrieve - call only once
            final Set<String> attributesToGet = schemaDef.createFullAttributesToGet(options);

            // Create and configure base request with all common settings
            Directory.Users.List baseRequest = executor.getDirectory().users().list();
            if (googleFilter.hasSearchQuery()) {
                String queryString = googleFilter.buildUserQuery(baseRequest);
                if (null != queryString) {
                    logger.ok("Executing Query: {0}", queryString);
                    baseRequest.setQuery(queryString);
                }
                if (null == baseRequest.getDomain() && null == baseRequest.getCustomer()) {
                    baseRequest.setCustomer(MY_CUSTOMER_ID);
                }
            } else {
                baseRequest.setCustomer(MY_CUSTOMER_ID);
            }

            // Apply sort configuration to base request
            if (null != options.getSortKeys()) {
                for (SortKey sortKey : options.getSortKeys()) {
                    String orderBy = null;
                    if (sortKey.getField().equalsIgnoreCase(Name.NAME)
                            || sortKey.getField().equalsIgnoreCase(ALIASES_ATTR)) {
                        orderBy = "email";
                    } else if (sortKey.getField().equalsIgnoreCase(GIVEN_NAME_ATTR)) {
                        orderBy = GIVEN_NAME_ATTR;
                    } else if (sortKey.getField().equalsIgnoreCase(FAMILY_NAME_ATTR)) {
                        orderBy = FAMILY_NAME_ATTR;
                    } else {
                        logger.ok("Unsupported SortKey:{0}", sortKey);
                        continue;
                    }

                    baseRequest.setOrderBy(orderBy);
                    if (sortKey.isAscendingOrder()) {
                        baseRequest.setSortOrder(ASCENDING_ORDER);
                    } else {
                        baseRequest.setSortOrder(DESCENDING_ORDER);
                    }
                    break;
                }
            }

            // Apply showDeleted setting to base request
            if (options.getOptions().get(SHOW_DELETED_PARAM) instanceof Boolean) {
                baseRequest.setShowDeleted(options.getOptions().get(SHOW_DELETED_PARAM).toString());
            }

            // Get paging configuration and parameters
            int configMaxResults = executor.getConfiguration().getUserPagingMaxResults();
            Integer pagedResultsOffset = options.getPagedResultsOffset();
            Integer pageSize = options.getPageSize();
            String pagedResultsCookie = options.getPagedResultsCookie();

            // Route to appropriate search method based on paging parameters
            if (pagedResultsOffset != null && pagedResultsOffset > 0) {
                // Offset-based paging
                executeOffsetBasedSearch(executor, baseRequest, handler, attributesToGet,
                        configMaxResults, schemaDef, pagedResultsOffset, pageSize, options);
            } else if (pagedResultsCookie != null || (pageSize != null && pageSize > 0)) {
                // Cookie-based paging (with or without pageSize)
                executeCookieBasedSearch(executor, baseRequest, handler, attributesToGet,
                        configMaxResults, schemaDef, pageSize, pagedResultsCookie, options);
            } else {
                // No paging - fetch all with automatic pagination
                executeUnpagedSearch(executor, baseRequest, handler, attributesToGet,
                        configMaxResults, schemaDef, options);
            }

        } catch (IOException e) {
            logger.warn(e, "Failed to initialize Users#List");
            throw ConnectorException.wrap(e);
        }
    }

    /**
     * Execute offset-based search with two-phase approach.
     */
    private static void executeOffsetBasedSearch(GoogleApiExecutor executor, Directory.Users.List baseRequest,
                                                 ResultsHandler handler, Set<String> attributesToGet,
                                                 int configMaxResults, SchemaDefinition schemaDef,
                                                 int pagedResultsOffset, Integer pageSize, OperationOptions options) throws IOException {
        int skipCount = pagedResultsOffset - 1;  // Convert 1-based to 0-based
        int targetSize = pageSize != null ? pageSize : Integer.MAX_VALUE;

        String nextToken = null;

        // Phase 1: Skip to offset position (if needed)
        if (skipCount > 0) {
            Directory.Users.List skipRequest = executor.getDirectory().users().list();
            copyBaseRequestSettings(skipRequest, baseRequest);

            // Minimal fields for efficient skipping
            skipRequest.setFields("nextPageToken,users(id)");

            int skipped = 0;
            while (skipped < skipCount) {
                skipRequest.setPageToken(nextToken);

                // Optimize maxResults for skipping
                int remainingToSkip = skipCount - skipped;
                skipRequest.setMaxResults(Math.min(remainingToSkip, configMaxResults));

                Users result = executor.execute(skipRequest,
                        new RequestResultHandler<Directory.Users.List, Users, Users>() {
                            public Users handleResult(Directory.Users.List request, Users value) {
                                return value;
                            }
                        });

                if (result.getUsers() != null) {
                    skipped += result.getUsers().size();
                }

                nextToken = result.getNextPageToken();
                if (nextToken == null) {
                    // Reached end of data before offset
                    return;
                }
            }
        }

        // Phase 2: Fetch actual data with all required fields
        Directory.Users.List dataRequest = executor.getDirectory().users().list();
        copyBaseRequestSettings(dataRequest, baseRequest);
        dataRequest.setPageToken(nextToken);

        // Full fields for actual data
        String fields = schemaDef.createGoogleApiFieldsString(attributesToGet, ID_ATTR, ETAG_ATTR, PRIMARY_EMAIL_ATTR);
        dataRequest.setFields("nextPageToken,users(" + fields + ")");

        int fetched = 0;
        while (fetched < targetSize) {
            // Optimize maxResults for data fetching
            int remaining = targetSize - fetched;
            dataRequest.setMaxResults(Math.min(remaining, configMaxResults));

            Users result = executor.execute(dataRequest,
                    new RequestResultHandler<Directory.Users.List, Users, Users>() {
                        public Users handleResult(Directory.Users.List request, Users value) {
                            return value;
                        }
                    });

            if (result.getUsers() != null) {
                for (User user : result.getUsers()) {
                    if (fetched >= targetSize) break;
                    handler.handle(UserConverter.fromUser(user,
                            attributesToGet, executor.getDirectory().groups(),
                            executor.getConfiguration(), options));
                    fetched++;
                }
            }

            String nextPageToken = result.getNextPageToken();
            if (nextPageToken == null || fetched >= targetSize) {
                break;
            }
            dataRequest.setPageToken(nextPageToken);
        }
    }

    /**
     * Execute cookie-based search with single page retrieval.
     */
    private static void executeCookieBasedSearch(GoogleApiExecutor executor, Directory.Users.List baseRequest,
                                                 ResultsHandler handler, Set<String> attributesToGet,
                                                 int configMaxResults, SchemaDefinition schemaDef,
                                                 Integer pageSize, String pagedResultsCookie, OperationOptions options) throws IOException {
        Directory.Users.List request = executor.getDirectory().users().list();
        copyBaseRequestSettings(request, baseRequest);

        // Set maxResults: use pageSize if provided, otherwise use config max
        if (pageSize != null && pageSize > 0) {
            int effectiveMaxResults = Math.min(pageSize, configMaxResults);
            request.setMaxResults(effectiveMaxResults);
        } else {
            request.setMaxResults(configMaxResults);
        }
        request.setPageToken(pagedResultsCookie);

        // Set fields
        String fields = schemaDef.createGoogleApiFieldsString(attributesToGet, ID_ATTR, ETAG_ATTR, PRIMARY_EMAIL_ATTR);
        request.setFields("nextPageToken,users(" + fields + ")");

        // Execute and return one page with continuation token
        String nextPageToken = executor.execute(request,
                new RequestResultHandler<Directory.Users.List, Users, String>() {
                    public String handleResult(Directory.Users.List request, Users value) {
                        if (null != value.getUsers()) {
                            for (User user : value.getUsers()) {
                                handler.handle(UserConverter.fromUser(user,
                                        attributesToGet, executor.getDirectory().groups(),
                                        executor.getConfiguration(), options));
                            }
                        }
                        return value.getNextPageToken();
                    }
                });

        if (StringUtil.isNotBlank(nextPageToken)) {
            logger.info("Paged Search was requested and next token is:{0}", nextPageToken);
            ((SearchResultsHandler) handler).handleResult(new SearchResult(nextPageToken, 0));
        }
    }

    /**
     * Execute unpaged search with automatic pagination.
     */
    private static void executeUnpagedSearch(GoogleApiExecutor executor, Directory.Users.List baseRequest,
                                             ResultsHandler handler, Set<String> attributesToGet,
                                             int configMaxResults, SchemaDefinition schemaDef, OperationOptions options) throws IOException {
        Directory.Users.List request = executor.getDirectory().users().list();
        copyBaseRequestSettings(request, baseRequest);
        request.setMaxResults(configMaxResults);

        // Set fields
        String fields = schemaDef.createGoogleApiFieldsString(attributesToGet, ID_ATTR, ETAG_ATTR, PRIMARY_EMAIL_ATTR);
        request.setFields("nextPageToken,users(" + fields + ")");

        // Fetch all pages
        String nextPageToken = null;
        do {
            request.setPageToken(nextPageToken);
            nextPageToken = executor.execute(request,
                    new RequestResultHandler<Directory.Users.List, Users, String>() {
                        public String handleResult(Directory.Users.List request, Users value) {
                            if (null != value.getUsers()) {
                                for (User user : value.getUsers()) {
                                    handler.handle(UserConverter.fromUser(user,
                                            attributesToGet, executor.getDirectory().groups(),
                                            executor.getConfiguration(), options));
                                }
                            }
                            return value.getNextPageToken();
                        }
                    });
        } while (StringUtil.isNotBlank(nextPageToken));
    }

    /**
     * Copy all settings from baseRequest to targetRequest.
     */
    private static void copyBaseRequestSettings(Directory.Users.List targetRequest, Directory.Users.List baseRequest) {
        if (baseRequest.getCustomer() != null) {
            targetRequest.setCustomer(baseRequest.getCustomer());
        }
        if (baseRequest.getDomain() != null) {
            targetRequest.setDomain(baseRequest.getDomain());
        }
        if (baseRequest.getQuery() != null) {
            targetRequest.setQuery(baseRequest.getQuery());
        }
        if (baseRequest.getShowDeleted() != null) {
            targetRequest.setShowDeleted(baseRequest.getShowDeleted());
        }
        if (baseRequest.getOrderBy() != null) {
            targetRequest.setOrderBy(baseRequest.getOrderBy());
            targetRequest.setSortOrder(baseRequest.getSortOrder());
        }
    }

    /**
     * Execute Account delete operation.
     */
    public static void executeAccountDelete(GoogleApiExecutor executor, ConnectorObjectsCache objectsCache, Uid uid) {
        try {
            AbstractGoogleJsonClientRequest<Void> request = executor.getDirectory().users().delete(uid.getUidValue());

            executor.execute(request, new RequestResultHandler.Delete(uid, ObjectClass.ACCOUNT));
            
            // Remove from cache
            objectsCache.removeUser(uid.getUidValue());
            
        } catch (IOException e) {
            throw ConnectorException.wrap(e);
        }
    }

    /**
     * Unified query execution method that handles both read and search operations.
     */
    public static void executeQuery(GoogleApiExecutor executor, ConnectorObjectsCache objectsCache, 
                                   GoogleFilter googleFilter, ResultsHandler handler, OperationOptions options, 
                                   SchemaDefinition schemaDef) {
        if (googleFilter.isReadByUid()) {
            // Read request by UID
            executeAccountReadQuery(executor, objectsCache, googleFilter.getUid(), handler, options, schemaDef);
        } else if (googleFilter.isReadByName()) {
            // Read request by Name
            executeAccountReadQuery(executor, objectsCache, googleFilter.getName(), handler, options, schemaDef);
        } else {
            // Search query (including list all when filter has no search query)
            executeAccountSearchQuery(executor, googleFilter, handler, options, schemaDef);
        }
    }

}