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
 *
 * Portions Copyrighted 2025 Nomura Research Institute, Ltd.
 */

package com.evolveum.polygon.connector.googleapps;

import com.google.api.services.directory.Directory;
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.MEMBERS_ATTR;

/**
 * Represents a parsed Google filter that can be either a read query (by Uid or Name)
 * or a search query with a filter. This class consolidates all filter processing
 * for Google API queries.
 *
 * @author Hiroyuki Wada
 */
public class GoogleFilter {
    private final Uid uid;
    private final Name name;
    private final Filter filter;
    private final GoogleFilter leftFilter;
    private final GoogleFilter rightFilter;

    /**
     * Empty GoogleFilter instance for Null Object pattern.
     * Used instead of null to avoid null checks in handler classes.
     */
    public static final GoogleFilter EMPTY = new GoogleFilter();

    /**
     * Private constructor for EMPTY instance.
     */
    private GoogleFilter() {
        this.uid = null;
        this.name = null;
        this.filter = null;
        this.leftFilter = null;
        this.rightFilter = null;
    }

    /**
     * Create a read query by Uid.
     */
    public GoogleFilter(Uid uid) {
        this.uid = uid;
        this.name = null;
        this.filter = null;
        this.leftFilter = null;
        this.rightFilter = null;
    }

    /**
     * Create a read query by Name.
     */
    public GoogleFilter(Name name) {
        this.uid = null;
        this.name = name;
        this.filter = null;
        this.leftFilter = null;
        this.rightFilter = null;
    }

    /**
     * Create a search query with filter.
     */
    public GoogleFilter(Filter filter) {
        this.uid = null;
        this.name = null;
        this.filter = filter;
        this.leftFilter = null;
        this.rightFilter = null;
    }

    /**
     * Create an AND query with two GoogleFilter expressions.
     * Used for combining multiple search criteria with implicit AND operation.
     */
    public GoogleFilter(GoogleFilter leftFilter, GoogleFilter rightFilter) {
        this.uid = null;
        this.name = null;
        this.filter = null;
        this.leftFilter = leftFilter;
        this.rightFilter = rightFilter;
    }

    /**
     * @return true if this is a read query by Uid
     */
    public boolean isReadByUid() {
        return uid != null;
    }

    /**
     * @return true if this is a read query by Name
     */
    public boolean isReadByName() {
        return name != null;
    }

    /**
     * @return true if this is an AND query with two filters
     */
    public boolean isAndFilter() {
        return leftFilter != null && rightFilter != null;
    }

    /**
     * @return true if this filter can generate a search query (has filter or AND filter)
     */
    public boolean hasSearchQuery() {
        return filter != null || isAndFilter();
    }

    /**
     * @return the Uid for read queries, null for search queries
     */
    public Uid getUid() {
        return uid;
    }

    /**
     * @return the Name for read queries, null for search queries
     */
    public Name getName() {
        return name;
    }

    /**
     * @return the Filter for search queries, null for read queries
     */
    public Filter getFilter() {
        return filter;
    }

    /**
     * Builds a Google API query string from the filter for User searches.
     *
     * @param request the Directory.Users.List request to configure
     * @return the query string, or null if no query is needed
     */
    public String buildUserQuery(Directory.Users.List request) {
        if (isAndFilter()) {
            // Handle AND filter by combining queries with space (implicit AND)
            String leftQuery = leftFilter.buildUserQuery(request);
            String rightQuery = rightFilter.buildUserQuery(request);

            if (leftQuery != null && rightQuery != null) {
                return leftQuery + " " + rightQuery;
            } else if (leftQuery != null) {
                return leftQuery;
            } else if (rightQuery != null) {
                return rightQuery;
            } else {
                return null;
            }
        }

        if (filter == null) {
            return null;
        }

        StringBuilder queryBuilder = filter.accept(new UserFilterVisitor(), request);
        return queryBuilder != null ? queryBuilder.toString() : null;
    }

    /**
     * Inner class to handle User-specific filter processing.
     */
    private static class UserFilterVisitor implements FilterVisitor<StringBuilder, Directory.Users.List> {

        // Filter field name mappings for Users
        private static final Map<String, String> USER_NAME_DICTIONARY;
        private static final Set<String> USER_CONTAINS_FIELDS;
        private static final Set<String> USER_STARTSWITH_FIELDS;
        private static final Escaper STRING_ESCAPER = Escapers.builder().addEscape('\'', "\\'").build();

        static {
            Map<String, String> userNames = new HashMap<>();
            userNames.put("__NAME__", "email");  // Name attribute maps to email search
            userNames.put("givenName", "givenName");
            userNames.put("familyName", "familyName");
            userNames.put("email", "email");
            userNames.put("emails", "email");  // emails attribute maps to email search
            userNames.put("isAdmin", "isAdmin");
            userNames.put("manager", "manager");
            userNames.put("directManager", "directManager");
            userNames.put("orgName", "orgName");
            userNames.put("orgCostCenter", "orgCostCenter");
            userNames.put("orgDepartment", "orgDepartment");
            userNames.put("orgDescription", "orgDescription");
            userNames.put("orgTitle", "orgTitle");
            USER_NAME_DICTIONARY = userNames;

            Set<String> containsFields = new HashSet<>();
            containsFields.add("givenName");
            containsFields.add("familyName");
            containsFields.add("email");
            USER_CONTAINS_FIELDS = containsFields;

            Set<String> startsWithFields = new HashSet<>();
            startsWithFields.add("givenName");
            startsWithFields.add("familyName");
            startsWithFields.add("email");
            USER_STARTSWITH_FIELDS = startsWithFields;
        }

        @Override
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

        @Override
        public StringBuilder visitContainsFilter(Directory.Users.List list, ContainsFilter filter) {
            String fieldName = USER_NAME_DICTIONARY.get(filter.getName());
            if (null != fieldName && USER_CONTAINS_FIELDS.contains(fieldName)) {
                return getStringBuilder(filter.getAttribute(), ':', null, fieldName);
            } else {
                throw new InvalidAttributeValueException("Field not supported for contains search: " + filter.getName());
            }
        }

        @Override
        public StringBuilder visitStartsWithFilter(Directory.Users.List list, StartsWithFilter filter) {
            String fieldName = USER_NAME_DICTIONARY.get(filter.getName());
            if (null != fieldName && USER_STARTSWITH_FIELDS.contains(fieldName)) {
                return getStringBuilder(filter.getAttribute(), ':', '*', fieldName);
            } else {
                throw new InvalidAttributeValueException("Field not supported for starts-with search: " + filter.getName());
            }
        }

        @Override
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
                String fieldName = USER_NAME_DICTIONARY.get(equalsFilter.getName());
                if (null != fieldName) {
                    return getStringBuilder(equalsFilter.getAttribute(), '=', null, fieldName);
                } else {
                    throw new InvalidAttributeValueException("Field not supported for equals search: " + equalsFilter.getName());
                }
            }
            return null;
        }

        // Unsupported filter types return null
        @Override
        public StringBuilder visitContainsAllValuesFilter(Directory.Users.List list, ContainsAllValuesFilter filter) {
            return null;
        }

        @Override
        public StringBuilder visitExtendedFilter(Directory.Users.List list, Filter filter) {
            return null;
        }

        @Override
        public StringBuilder visitGreaterThanFilter(Directory.Users.List list, GreaterThanFilter filter) {
            return null;
        }

        @Override
        public StringBuilder visitGreaterThanOrEqualFilter(Directory.Users.List list, GreaterThanOrEqualFilter filter) {
            return null;
        }

        @Override
        public StringBuilder visitLessThanFilter(Directory.Users.List list, LessThanFilter filter) {
            return null;
        }

        @Override
        public StringBuilder visitLessThanOrEqualFilter(Directory.Users.List list, LessThanOrEqualFilter filter) {
            return null;
        }

        @Override
        public StringBuilder visitNotFilter(Directory.Users.List list, NotFilter filter) {
            return null;
        }

        @Override
        public StringBuilder visitOrFilter(Directory.Users.List list, OrFilter filter) {
            return null;
        }

        @Override
        public StringBuilder visitEndsWithFilter(Directory.Users.List list, EndsWithFilter filter) {
            return null;
        }

        @Override
        public StringBuilder visitEqualsIgnoreCaseFilter(Directory.Users.List list, EqualsIgnoreCaseFilter filter) {
            return null;
        }

        /**
         * Helper method to build query string components.
         * Surround with single quotes ' if the query contains whitespace. Escape
         * single quotes in queries with \', for example 'Valentine\'s Day'.
         */
        protected StringBuilder getStringBuilder(Attribute attribute, char operator, Character postfix, String fieldName) {
            StringBuilder builder = new StringBuilder();
            builder.append(fieldName).append(operator);
            String stringValue = AttributeUtil.getStringValue(attribute);
            if (StringUtil.isNotBlank(stringValue)) {
                if (null != postfix) {
                    stringValue = stringValue + postfix;
                }
                if (stringValue.contains(" ")) {
                    builder.append('\'').append(STRING_ESCAPER.escape(stringValue)).append('\'');
                } else {
                    builder.append(stringValue);
                }
            }
            return builder;
        }
    }

    /**
     * Configures a Google API Groups.List request from the filter for Group searches.
     * Groups have limited search capabilities and use request parameters instead of query strings.
     *
     * @param request the Directory.Groups.List request to configure
     */
    public void configureGroupRequest(Directory.Groups.List request) {
        if (filter == null) {
            return;
        }

        try {
            filter.accept(new GroupFilterVisitor(), request);
        } catch (UnsupportedOperationException e) {
            throw new InvalidAttributeValueException(e.getMessage());
        }
    }

    /**
     * Inner class to handle Group-specific filter processing.
     * Groups API only supports specific filter types.
     */
    private static class GroupFilterVisitor implements FilterVisitor<Void, Directory.Groups.List> {

        @Override
        public Void visitEqualsFilter(Directory.Groups.List list, EqualsFilter equalsFilter) {
            if (AttributeUtil.namesEqual(equalsFilter.getName(), "customer")) {
                if (null != list.getDomain() || null != list.getUserKey()) {
                    throw new InvalidAttributeValueException(
                            "The 'customer', 'domain' and 'userKey' can not be in the same query");
                }
                list.setCustomer(AttributeUtil.getStringValue(equalsFilter.getAttribute()));
            } else if (AttributeUtil.namesEqual(equalsFilter.getName(), "domain")) {
                if (null != list.getCustomer() || null != list.getUserKey()) {
                    throw new InvalidAttributeValueException(
                            "The 'customer', 'domain' and 'userKey' can not be in the same query");
                }
                list.setDomain(AttributeUtil.getStringValue(equalsFilter.getAttribute()));
            } else if (AttributeUtil.namesEqual(equalsFilter.getName(), "userKey")) {
                if (null != list.getDomain() || null != list.getCustomer()) {
                    throw new InvalidAttributeValueException(
                            "The 'customer', 'domain' and 'userKey' can not be in the same query");
                }
                list.setUserKey(AttributeUtil.getStringValue(equalsFilter.getAttribute()));
            } else {
                throw new UnsupportedOperationException(
                        "Only EqualsFilter(['domain','customer','userKey']) and ContainsFilter('members') are supported");
            }
            return null;
        }

        @Override
        public Void visitContainsFilter(Directory.Groups.List list, ContainsFilter containsFilter) {
            if (AttributeUtil.namesEqual(containsFilter.getName(), MEMBERS_ATTR)) {
                list.setUserKey(AttributeUtil.getStringValue(containsFilter.getAttribute()));
            } else if (AttributeUtil.namesEqual(containsFilter.getName(), Name.NAME)) {
                // Map contains search to prefix search using query parameter
                // Google Groups API supports email:prefix* and name:prefix* queries
                String searchValue = AttributeUtil.getStringValue(containsFilter.getAttribute());
                if (searchValue != null) {
                    // Use email prefix search since Group Name maps to email
                    list.setQuery("email:" + searchValue + "*");
                    // Customer parameter is required when using query parameter
                    // Set customer if no scope parameter (customer/domain/userKey) is already set
                    if (list.getCustomer() == null && list.getDomain() == null && list.getUserKey() == null) {
                        list.setCustomer("my_customer");
                    }
                }
            } else {
                throw new UnsupportedOperationException(
                        "Only EqualsFilter(['domain','customer','userKey']) and ContainsFilter('members') are supported");
            }
            return null;
        }

        // All other filter types are unsupported for Groups
        @Override
        public Void visitAndFilter(Directory.Groups.List list, AndFilter andFilter) {
            throw new UnsupportedOperationException(
                    "Only EqualsFilter(['domain','customer','userKey']) and ContainsFilter('members') are supported");
        }

        @Override
        public Void visitContainsAllValuesFilter(Directory.Groups.List list, ContainsAllValuesFilter filter) {
            throw new UnsupportedOperationException(
                    "Only EqualsFilter(['domain','customer','userKey']) and ContainsFilter('members') are supported");
        }

        @Override
        public Void visitExtendedFilter(Directory.Groups.List list, Filter filter) {
            throw new UnsupportedOperationException(
                    "Only EqualsFilter(['domain','customer','userKey']) and ContainsFilter('members') are supported");
        }

        @Override
        public Void visitGreaterThanFilter(Directory.Groups.List list, GreaterThanFilter filter) {
            throw new UnsupportedOperationException(
                    "Only EqualsFilter(['domain','customer','userKey']) and ContainsFilter('members') are supported");
        }

        @Override
        public Void visitGreaterThanOrEqualFilter(Directory.Groups.List list, GreaterThanOrEqualFilter filter) {
            throw new UnsupportedOperationException(
                    "Only EqualsFilter(['domain','customer','userKey']) and ContainsFilter('members') are supported");
        }

        @Override
        public Void visitLessThanFilter(Directory.Groups.List list, LessThanFilter filter) {
            throw new UnsupportedOperationException(
                    "Only EqualsFilter(['domain','customer','userKey']) and ContainsFilter('members') are supported");
        }

        @Override
        public Void visitLessThanOrEqualFilter(Directory.Groups.List list, LessThanOrEqualFilter filter) {
            throw new UnsupportedOperationException(
                    "Only EqualsFilter(['domain','customer','userKey']) and ContainsFilter('members') are supported");
        }

        @Override
        public Void visitNotFilter(Directory.Groups.List list, NotFilter filter) {
            throw new UnsupportedOperationException(
                    "Only EqualsFilter(['domain','customer','userKey']) and ContainsFilter('members') are supported");
        }

        @Override
        public Void visitOrFilter(Directory.Groups.List list, OrFilter filter) {
            throw new UnsupportedOperationException(
                    "Only EqualsFilter(['domain','customer','userKey']) and ContainsFilter('members') are supported");
        }

        @Override
        public Void visitStartsWithFilter(Directory.Groups.List list, StartsWithFilter filter) {
            throw new UnsupportedOperationException(
                    "Only EqualsFilter(['domain','customer','userKey']) and ContainsFilter('members') are supported");
        }

        @Override
        public Void visitEndsWithFilter(Directory.Groups.List list, EndsWithFilter filter) {
            throw new UnsupportedOperationException(
                    "Only EqualsFilter(['domain','customer','userKey']) and ContainsFilter('members') are supported");
        }

        @Override
        public Void visitEqualsIgnoreCaseFilter(Directory.Groups.List list, EqualsIgnoreCaseFilter filter) {
            throw new UnsupportedOperationException(
                    "Only EqualsFilter(['domain','customer','userKey']) and ContainsFilter('members') are supported");
        }
    }
}