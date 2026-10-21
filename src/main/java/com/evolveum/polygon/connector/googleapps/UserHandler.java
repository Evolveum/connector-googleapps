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
import com.google.api.services.directory.model.*;
import com.google.api.services.licensing.Licensing;
import com.google.api.services.licensing.model.LicenseAssignment;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.identityconnectors.framework.spi.SearchResultsHandler;
import java.util.*;
import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.*;

import com.google.api.services.directory.Directory;
import com.google.api.services.directory.model.Alias;
import com.google.api.services.directory.model.User;
import com.google.api.services.directory.model.UserName;
import com.google.api.services.directory.model.UserPhoto;
import com.google.common.base.CharMatcher;
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.common.security.SecurityUtil;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.*;
import org.identityconnectors.framework.common.objects.AttributeInfo.Flags;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.EnumSet;

import static com.evolveum.polygon.connector.googleapps.GoogleAppsConnector.ID_ATTR;
import static com.evolveum.polygon.connector.googleapps.GoogleAppsConnector.PHOTO_ATTR;
import static org.identityconnectors.framework.common.objects.OperationalAttributes.ENABLE_NAME;

/**
 *
 * @author Laszlo Hordos
 */
public class UserHandler implements FilterVisitor<StringBuilder, Directory.Users.List> {

    /**
     * Setup logging for the {@link UserHandler}.
     */
    private static final Log logger = Log.getLog(UserHandler.class);

    public static final String ID_ETAG = "id,etag";
    public static final String NAME = "name";
    public static final String ADMIN_CREATED_ATTR = "adminCreated";
    public static final String NON_EDITABLE_ALIASES_ATTR = "nonEditableAliases";
    public static final String DIRECT_MEMBERS_COUNT_ATTR = "directMembersCount";
    public static final String MY_CUSTOMER_ID = "my_customer";
    public static final String SUSPENDED_ATTR = "suspended";
    public static final String CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR = "changePasswordAtNextLogin";
    public static final String IP_WHITELISTED_ATTR = "ipWhitelisted";
    public static final String ORG_UNIT_PATH_ATTR = "orgUnitPath";
    public static final String INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR = "includeInGlobalAddressList";
    public static final String IMS_ATTR = "ims";
    public static final String EMAILS_ATTR = "emails";
    public static final String EXTERNAL_IDS_ATTR = "externalIds";
    public static final String RELATIONS_ATTR = "relations";
    public static final String ADDRESSES_ATTR = "addresses";
    public static final String ORGANIZATIONS_ATTR = "organizations";
    public static final String PHONES_ATTR = "phones";
    public static final String GIVEN_NAME_ATTR = "givenName";
    public static final String FAMILY_NAME_ATTR = "familyName";
    public static final String FULL_NAME_ATTR = "fullName";
    public static final String IS_ADMIN_ATTR = "isAdmin";
    public static final String IS_DELEGATED_ADMIN_ATTR = "isDelegatedAdmin";
    public static final String LAST_LOGIN_TIME_ATTR = "lastLoginTime";
    public static final String CREATION_TIME_ATTR = "creationTime";
    public static final String AGREED_TO_TERMS_ATTR = "agreedToTerms";
    public static final String SUSPENSION_REASON_ATTR = "suspensionReason";
    public static final String ALIASES_ATTR = "aliases";
    public static final String CUSTOMER_ID_ATTR = "customerId";
    public static final String IS_MAILBOX_SETUP_ATTR = "isMailboxSetup";
    public static final String THUMBNAIL_PHOTO_URL_ATTR = "thumbnailPhotoUrl";
    public static final String DELETION_TIME_ATTR = "deletionTime";
    public static final String LOCATIONS_ATTR = "locations";

    private static final Map<String, String> NAME_DICTIONARY;
    private static final Set<String> S;
    private static final Set<String> SW;

    static {
        Map<String, String> dictionary = CollectionUtil.newCaseInsensitiveMap();
        dictionary.put(NAME, NAME);
        dictionary.put("email", "email");
        dictionary.put(Name.NAME, "email");
        dictionary.put(GIVEN_NAME_ATTR, GIVEN_NAME_ATTR);
        dictionary.put(FAMILY_NAME_ATTR, FAMILY_NAME_ATTR);
        dictionary.put(IS_ADMIN_ATTR, IS_ADMIN_ATTR);
        dictionary.put(IS_DELEGATED_ADMIN_ATTR, IS_DELEGATED_ADMIN_ATTR);
        dictionary.put("isSuspended", "isSuspended");
        dictionary.put("im", "im");
        dictionary.put("externalId", "externalId");
        dictionary.put("manager", "manager");
        dictionary.put("managerId", "managerId");
        dictionary.put("directManager", "directManager");
        dictionary.put("directManagerId", "directManagerId");
        dictionary.put("address", "address");
        dictionary.put("addressPoBox", "addressPoBox");
        dictionary.put("address/poBox", "addressPoBox");
        dictionary.put("addressExtended", "addressExtended");
        dictionary.put("address/extended", "addressExtended");
        dictionary.put("addressStreet", "addressStreet");
        dictionary.put("address/street", "addressStreet");
        dictionary.put("addressLocality", "addressLocality");
        dictionary.put("address/locality", "addressLocality");
        dictionary.put("addressRegion", "addressRegion");
        dictionary.put("address/region", "addressRegion");
        dictionary.put("addressPostalCode", "addressPostalCode");
        dictionary.put("address/postalCode", "addressPostalCode");
        dictionary.put("addressCountry", "addressCountry");
        dictionary.put("address/country", "addressCountry");
        dictionary.put("orgName", "orgName");
        dictionary.put("organizations/name", "orgName");
        NAME_DICTIONARY = dictionary;

        Set<String> s = CollectionUtil.newCaseInsensitiveSet();
        s.add(NAME);
        s.add("email");
        s.add("givenName");
        s.add("familyName");
        SW = CollectionUtil.newCaseInsensitiveSet();
        SW.addAll(s);

        s.add("im");
        s.add("externalId");
        s.add("address");
        s.add("addressPoBox");
        s.add("addressExtended");
        s.add("addressStreet");
        s.add("addressLocality");
        s.add("addressRegion");
        s.add("addressPostalCode");
        s.add("addressCountry");
        s.add("orgName");
        s.add("orgTitle");
        s.add("orgDepartment");
        s.add("orgDescription");
        s.add("orgCostCenter");
        S = s;
    }

    private static final Escaper STRING_ESCAPER = Escapers.builder().addEscape('\'', "\\'").build();

    public StringBuilder visitAndFilter(Directory.Users.List list, AndFilter andFilter) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Filter filter : andFilter.getFilters()) {
            if (filter == null) {
                continue;
            }
            StringBuilder sb = filter.accept(this, list);
            if (null != sb) {
                if (!first) {
                    builder.append(' ');
                } else {
                    first = false;
                }
                builder.append(sb);
            }
        }
        return builder;
    }

    public StringBuilder visitContainsFilter(Directory.Users.List list, ContainsFilter filter) {
        String filedName = NAME_DICTIONARY.get(filter.getName());
        if (null != filedName && S.contains(filedName)) {
            return getStringBuilder(filter.getAttribute(), ':', null, filedName);
        } else {
            // Warning: not supported field name
            throw new InvalidAttributeValueException("");
        }
    }

    /**
     * Surround with single quotes ' if the query contains whitespace. Escape
     * single quotes in queries with \', for example 'Valentine\'s Day'.
     *
     * @param attribute
     * @param operator
     * @param postfix
     * @param filedName
     * @return
     */
    protected StringBuilder getStringBuilder(Attribute attribute, char operator, Character postfix,
            String filedName) {
        StringBuilder builder = new StringBuilder();
        builder.append(filedName).append(operator);
        String stringValue = AttributeUtil.getAsStringValue(attribute);
        if (StringUtil.isNotBlank(stringValue)) {
            stringValue = STRING_ESCAPER.escape(stringValue);
            if (CharMatcher.whitespace().matchesAnyOf(stringValue)) {
                builder.append('\'').append(stringValue);
                if (null != postfix) {
                    builder.append(postfix);
                }
                builder.append('\'');
            } else {
                builder.append(stringValue);
                if (null != postfix) {
                    builder.append(postfix);
                }
            }
        }
        return builder;
    }

    public StringBuilder visitStartsWithFilter(Directory.Users.List list, StartsWithFilter filter) {
        String filedName = NAME_DICTIONARY.get(filter.getName());
        if (null != filedName && SW.contains(filedName)) {
            return getStringBuilder(filter.getAttribute(), ':', '*', filedName);
        } else {
            // Warning: not supported field name
            throw new InvalidAttributeValueException("");
        }
    }

    public StringBuilder visitEqualsFilter(Directory.Users.List list, EqualsFilter equalsFilter) {
        if (AttributeUtil.namesEqual(equalsFilter.getName(), "customer")) {
            if (null != list.getDomain()) {
                throw new InvalidAttributeValueException(
                        "The 'customer' and 'domain' can not be in the same query");
            } else {
                list.setCustomer(AttributeUtil.getStringValue(equalsFilter.getAttribute()));
            }
        } else if (AttributeUtil.namesEqual(equalsFilter.getName(), "domain")) {
            if (null != list.getCustomer()) {
                throw new InvalidAttributeValueException(
                        "The 'customer' and 'domain' can not be in the same query");
            } else {
                list.setDomain(AttributeUtil.getStringValue(equalsFilter.getAttribute()));
            }
        } else {
            String filedName = NAME_DICTIONARY.get(equalsFilter.getName());
            if (null != filedName) {
                return getStringBuilder(equalsFilter.getAttribute(), '=', null, filedName);
            } else {
                // Warning: not supported field name
                throw new InvalidAttributeValueException("");
            }
        }

        return null;
    }

    public StringBuilder visitContainsAllValuesFilter(Directory.Users.List list,
            ContainsAllValuesFilter filter) {
        return null;
    }

    public StringBuilder visitExtendedFilter(Directory.Users.List list, Filter filter) {
        return null;
    }

    public StringBuilder visitGreaterThanFilter(Directory.Users.List list, GreaterThanFilter filter) {
        return null;
    }

    public StringBuilder visitGreaterThanOrEqualFilter(Directory.Users.List list,
            GreaterThanOrEqualFilter filter) {
        return null;
    }

    public StringBuilder visitLessThanFilter(Directory.Users.List list, LessThanFilter filter) {
        return null;
    }

    public StringBuilder visitLessThanOrEqualFilter(Directory.Users.List list,
            LessThanOrEqualFilter filter) {
        return null;
    }

    public StringBuilder visitNotFilter(Directory.Users.List list, NotFilter filter) {
        return null;
    }

    public StringBuilder visitOrFilter(Directory.Users.List list, OrFilter filter) {
        return null;
    }

    public StringBuilder visitEndsWithFilter(Directory.Users.List list, EndsWithFilter filter) {
        return null;
    }

    @Override
    public StringBuilder visitEqualsIgnoreCaseFilter(Directory.Users.List list, EqualsIgnoreCaseFilter filter)
    {
        return null;
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
        builder.setDescription("A user identity in the configured Google Workspace domain. "
                + "Represents the user's sign-in account, profile, status, organizational unit, aliases, "
                + "and contact information.");

        // primaryEmail
        builder.addAttributeInfo(AttributeInfoBuilder.define(Name.NAME).setRequired(true)
                .setSubtype(AttributeInfo.Subtypes.STRING_CASE_IGNORE).build());

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

        /* 
        builder.addAttributeInfo(PredefinedAttributeInfos.GROUPS.setReturnedByDefault(true));
        AttributeInfoBuilder subjectId = new AttributeInfoBuilder();
        subjectId.setName("subjectId");
        subjectId.setNativeName("subject-id");
        subjectId.setCreateable(true);
        subjectId.setUpdateable(true);
        subjectId.setReadable(true);
        subjectId.setRequired(false);
        subjectId.setMultiValued(false);
        attributes.add(subjectId.build());
        builder.addAttributeInfo(AttributeInfoBuilder.define("groups").setNativeName("__GROUPS__").setMultiValued(true).setReturnedByDefault(true).build());
        */
        AttributeInfo GROUPS = AttributeInfoBuilder.build( PredefinedAttributes.GROUPS_NAME, String.class, EnumSet.of(Flags.MULTIVALUED));
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
        user.setIms(GoogleAppsUtil.getStructAttr((Attribute) attributes.find(IMS_ATTR)));
        user.setEmails(GoogleAppsUtil.getStructAttr((Attribute) attributes.find(EMAILS_ATTR)));
        user.setExternalIds(GoogleAppsUtil.getStructAttr((Attribute) attributes.find(EXTERNAL_IDS_ATTR)));
        user.setRelations(GoogleAppsUtil.getStructAttr((Attribute) attributes.find(RELATIONS_ATTR)));
        user.setAddresses(GoogleAppsUtil.getStructAttr((Attribute) attributes.find(ADDRESSES_ATTR)));
        user.setOrganizations(GoogleAppsUtil.getStructAttr((Attribute) attributes.find(ORGANIZATIONS_ATTR)));
        user.setPhones(GoogleAppsUtil.getStructAttr((Attribute) attributes.find(PHONES_ATTR)));
        user.setLocations(GoogleAppsUtil.getStructAttr((Attribute) attributes.find(LOCATIONS_ATTR)));

        Boolean suspended = attributes.findBoolean(SUSPENDED_ATTR);
        Boolean enabled = attributes.findBoolean(ENABLE_NAME);
        user.setSuspended(suspended != null ? suspended : enabled == null ? null : !enabled);
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

    public static Directory.Users.Patch updateUser(Directory.Users users, Uid uid,
            AttributesAccessor attributes) {
        User content = null;

        Name email = attributes.getName();
        if (email != null && email.getNameValue() != null) {
            content = new User();
            content.setPrimaryEmail(email.getNameValue());
        }

        Attribute givenName = attributes.find(GIVEN_NAME_ATTR);
        if (null != givenName) {
            String stringValue = GoogleAppsUtil.getStringValueWithDefault(givenName, null);
            if (null != stringValue) {
                if (null == content) {
                    content = new User();
                }
                content.setName(new UserName());
                content.getName().setGivenName(stringValue);
            }
        }

        Attribute familyName = attributes.find(FAMILY_NAME_ATTR);
        if (null != familyName) {
            String stringValue = GoogleAppsUtil.getStringValueWithDefault(familyName, null);
            if (null != stringValue) {
                if (null == content) {
                    content = new User();
                }
                if (null == content.getName()) {
                    content.setName(new UserName());
                }
                content.getName().setFamilyName(stringValue);
            }
        }

        GuardedString password = attributes.getPassword();
        if (null != password) {
            if (null == content) {
                content = new User();
            }
            content.setPassword(SecurityUtil.decrypt(password));
        }

        Boolean enable = attributes.findBoolean(ENABLE_NAME);
        if (null != enable) {
            if (null == content) {
                content = new User();
            }
            content.setSuspended(!enable);
        }

        Attribute suspended = attributes.find(SUSPENDED_ATTR);
        if (null != suspended) {
            Boolean booleanValue = GoogleAppsUtil.getBooleanValueWithDefault(suspended, null);
            if (null != booleanValue) {
                if (null == content) {
                    content = new User();
                }
                content.setSuspended(booleanValue);
            }
        }

        Attribute changePasswordAtNextLogin = attributes.find(CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR);
        if (null != changePasswordAtNextLogin) {
            Boolean booleanValue
                    = GoogleAppsUtil.getBooleanValueWithDefault(changePasswordAtNextLogin, null);
            if (null != booleanValue) {
                if (null == content) {
                    content = new User();
                }
                content.setChangePasswordAtNextLogin(booleanValue);
            }
        }

        Attribute ipWhitelisted = attributes.find(IP_WHITELISTED_ATTR);
        if (null != ipWhitelisted) {
            Boolean booleanValue = GoogleAppsUtil.getBooleanValueWithDefault(ipWhitelisted, null);
            if (null != booleanValue) {
                if (null == content) {
                    content = new User();
                }
                content.setIpWhitelisted(booleanValue);
            }
        }

        // Maps
        Attribute ims = attributes.find(IMS_ATTR);
        if (null != ims) {
            if (null == content) {
                content = new User();
            }
            content.setIms(GoogleAppsUtil.getStructAttr(ims));
        }

        Attribute emails = attributes.find(EMAILS_ATTR);
        if (null != emails) {
            if (null == content) {
                content = new User();
            }
            content.setEmails(GoogleAppsUtil.getStructAttr(emails));
        }

        Attribute externalIds = attributes.find(EXTERNAL_IDS_ATTR);
        if (null != externalIds) {
            if (null == content) {
                content = new User();
            }
            content.setExternalIds(GoogleAppsUtil.getStructAttr(externalIds));
        }

        Attribute relations = attributes.find(RELATIONS_ATTR);
        if (null != relations) {
            if (null == content) {
                content = new User();
            }
            content.setRelations(GoogleAppsUtil.getStructAttr(relations));
        }

        Attribute addresses = attributes.find(ADDRESSES_ATTR);
        if (null != addresses) {
            if (null == content) {
                content = new User();
            }
            content.setAddresses(GoogleAppsUtil.getStructAttr(addresses));
        }

        Attribute organizations = attributes.find(ORGANIZATIONS_ATTR);
        if (null != organizations) {
            if (null == content) {
                content = new User();
            }
            content.setOrganizations(GoogleAppsUtil.getStructAttr(organizations));
        }

        Attribute phones = attributes.find(PHONES_ATTR);
        if (null != phones) {
            if (null == content) {
                content = new User();
            }
            content.setPhones(GoogleAppsUtil.getStructAttr(phones));
        }

        Attribute locations = attributes.find(LOCATIONS_ATTR);
        if (null != locations) {
            if (null == content) {
                content = new User();
            }
            content.setLocations(GoogleAppsUtil.getStructAttr(locations));
        }

        Attribute orgUnitPath = attributes.find(ORG_UNIT_PATH_ATTR);
        if (null != orgUnitPath) {
            String stringValue = GoogleAppsUtil.getStringValueWithDefault(orgUnitPath, null);
            if (null != stringValue) {
                if (null == content) {
                    content = new User();
                }
                content.setOrgUnitPath(stringValue);
            }
        }

        Attribute includeInGlobalAddressList = attributes.find(INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR);
        if (null != includeInGlobalAddressList) {
            Boolean booleanValue
                    = GoogleAppsUtil.getBooleanValueWithDefault(includeInGlobalAddressList, null);
            if (null != booleanValue) {
                if (null == content) {
                    content = new User();
                }
                content.setIncludeInGlobalAddressList(booleanValue);
            }
        }

        if (null == content) {
            return null;
        }
        try {
            return users.patch(uid.getUidValue(), content).setFields(ID_ETAG);
            // } catch (HttpResponseException e){
        } catch (IOException e) {
            logger.warn(e, "Failed to initialize Groups#Patch");
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
    public static Directory.Users.Photos.Delete createDeleteUserPhoto(
            Directory.Users.Photos service, String userKey) {
        try {
            return service.delete(userKey);
        } catch (IOException e) {
            logger.warn(e, "Failed to initialize delete user photo request for user: {0}", userKey);
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
                    String addedValue = (String) value;
                    if (newValues.stream().noneMatch(existing -> matchesJsonValue(existing, addedValue))) {
                        newValues.add(addedValue);
                    }
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
    private static boolean matchesJsonValue(String currentJsonValue, String valueToRemove) {
        try {
            // Parse both JSON strings into generic objects for structural comparison
            Object currentObj = GoogleAppsUtil.parseJson(currentJsonValue);
            Object removeObj = GoogleAppsUtil.parseJson(valueToRemove);
            
            // Compare the parsed objects (handles field ordering and null differences)
            return Objects.equals(currentObj, removeObj);
        } catch (Exception e) {
            // If parsing fails, fall back to string comparison
            return currentJsonValue.equals(valueToRemove);
        }
    }
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
                    new RequestResultHandler.IgnoreMissing<Directory.Users.Photos.Delete, Void>());
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
                                new RequestResultHandler.IgnoreMissing<Directory.Users.Aliases.Delete, Alias>());
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
                                new RequestResultHandler.IgnoreMissing<Directory.Users.Aliases.Delete, Alias>());
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
                                    new RequestResultHandler.IgnoreMissing<Directory.Members.Delete, Void>());
                        } catch (Exception e) {
                            logger.warn(e, "Failed to remove user {0} from group {1}", uid.getUidValue(), currentGroup);
                            throw ConnectorException.wrap(e);
                        }
                    }
                }
                
                // Add user to new groups
                for (String newGroup : newGroups) {
                    if (!currentGroups.contains(newGroup)) {
                        try {
                            executor.execute(createMemberById(service, newGroup, uid.getUidValue(), "MEMBER"),
                                    new RequestResultHandler.IgnoreDuplicate<Directory.Members.Insert, Member>());
                        } catch (Exception e) {
                            logger.warn(e, "Failed to add user {0} to group {1}", uid.getUidValue(), newGroup);
                            throw ConnectorException.wrap(e);
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
                                    new RequestResultHandler.IgnoreDuplicate<Directory.Members.Insert, Member>());
                        } catch (Exception e) {
                            logger.warn(e, "Failed to add user {0} to group {1}", uid.getUidValue(), groupId);
                            throw ConnectorException.wrap(e);
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
                                    new RequestResultHandler.IgnoreMissing<Directory.Members.Delete, Void>());
                        } catch (Exception e) {
                            logger.warn(e, "Failed to remove user {0} from group {1}", uid.getUidValue(), groupId);
                            throw ConnectorException.wrap(e);
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
}
