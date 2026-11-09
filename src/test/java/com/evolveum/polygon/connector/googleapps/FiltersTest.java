/*
 * Copyright (c) 2026 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.polygon.connector.googleapps;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.directory.Directory;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.expectThrows;

public class FiltersTest {

    @Test
    public void userFiltersTranslateValuesAndEscapeWhitespaceAndQuotes() throws Exception {
        // Given: Names containing whitespace and apostrophes
        Directory.Users.List request = directory().users().list();
        Filter startsWith = FilterBuilder.startsWith(AttributeBuilder.build(Name.NAME, "Ada Lovelace"));
        Filter quoted = FilterBuilder.equalTo(AttributeBuilder.build(GoogleAppsConstants.FAMILY_NAME_ATTR, "Anne O'Connor"));

        // When: Build Google search queries
        String startsWithQuery = new GoogleFilter(startsWith).buildUserQuery(request);
        String quotedQuery = new GoogleFilter(quoted).buildUserQuery(request);

        // Then: Verify quoting and apostrophe escaping
        assertEquals(startsWithQuery, "email:'Ada Lovelace*'");
        assertEquals(quotedQuery, "familyName='Anne O\\'Connor'");
    }

    @Test
    public void userFiltersSetCustomerOrDomainButNotBoth() throws Exception {
        // Given: Mutually exclusive customer and domain filters
        Directory.Users.List request = directory().users().list();
        GoogleFilter customer = new GoogleFilter(FilterBuilder.equalTo(AttributeBuilder.build("customer", "my_customer")));
        GoogleFilter domain = new GoogleFilter(FilterBuilder.equalTo(AttributeBuilder.build("domain", "example.com")));

        // When: Apply the customer filter
        String query = customer.buildUserQuery(request);

        // Then: Reject the conflicting domain filter
        assertNull(query);
        assertEquals(request.getCustomer(), "my_customer");
        expectThrows(InvalidAttributeValueException.class, () -> domain.buildUserQuery(request));
        assertNull(request.getDomain());
    }

    @Test
    public void groupMemberFilterUsesUserKey() throws Exception {
        Directory.Groups.List request = directory().groups().list();
        Filter members = FilterBuilder.contains(AttributeBuilder.build(GoogleAppsConnector.MEMBERS_ATTR, "ada@example.com"));

        members.accept(new GroupHandler(), request);

        assertEquals(request.getUserKey(), "ada@example.com");
    }

    @Test
    public void groupFiltersRejectMutuallyExclusiveSelectionCriteria() throws Exception {
        Directory.Groups.List request = directory().groups().list();
        Filter customer = FilterBuilder.equalTo(AttributeBuilder.build("customer", "my_customer"));
        Filter userKey = FilterBuilder.equalTo(AttributeBuilder.build("userKey", "ada@example.com"));

        customer.accept(new GroupHandler(), request);
        expectThrows(InvalidAttributeValueException.class, () -> userKey.accept(new GroupHandler(), request));
    }

    @Test
    public void userFilterVisitorsRejectUnsupportedAttributes() throws Exception {
        // Given: Unsupported fields for user searches
        Directory.Users.List request = directory().users().list();
        Filter[] filters = {
            FilterBuilder.contains(AttributeBuilder.build("unsupported", "x")),
            FilterBuilder.startsWith(AttributeBuilder.build("unsupported", "x")),
            FilterBuilder.equalTo(AttributeBuilder.build("unsupported", "x")),
            FilterBuilder.contains(AttributeBuilder.build("description", "x"))
        };

        for (Filter filter : filters) {
            // When: Build a query with unsupported fields
            // Then: Reject the invalid attribute
            expectThrows(InvalidAttributeValueException.class, () -> new GoogleFilter(filter).buildUserQuery(request));
        }
    }

    @Test
    public void groupFilterVisitorsRejectUnsupportedOperatorsAndAttributes() throws Exception {
        Directory.Groups.List request = directory().groups().list();
        GroupHandler handler = new GroupHandler();

        expectThrows(UnsupportedOperationException.class, () -> FilterBuilder.startsWith(
                AttributeBuilder.build("domain", "example.com")).accept(handler, request));
        expectThrows(UnsupportedOperationException.class, () -> FilterBuilder.contains(
                AttributeBuilder.build("email", "ada@example.com")).accept(handler, request));
        expectThrows(UnsupportedOperationException.class, () -> FilterBuilder.equalTo(
                AttributeBuilder.build("email", "ada@example.com")).accept(handler, request));
    }

    @Test
    public void userFilterVisitorsCombineSupportedFiltersAndIgnoreUnsupportedOperators() throws Exception {
        // Given: Supported conjunction and unsupported operators
        Directory.Users.List request = directory().users().list();
        Filter conjunction = FilterBuilder.and(FilterBuilder.equalTo(AttributeBuilder.build(Name.NAME, "ada@example.com")),
                FilterBuilder.startsWith(AttributeBuilder.build(GoogleAppsConstants.GIVEN_NAME_ATTR, "Ada")));
        Filter base = FilterBuilder.equalTo(AttributeBuilder.build(Name.NAME, "ada@example.com"));
        Filter[] unsupported = {
            FilterBuilder.containsAllValues(AttributeBuilder.build("email", java.util.Arrays.asList("a", "b"))),
            FilterBuilder.greaterThan(AttributeBuilder.build("customer", "x")),
            FilterBuilder.greaterThanOrEqualTo(AttributeBuilder.build("customer", "x")),
            FilterBuilder.lessThan(AttributeBuilder.build("customer", "x")),
            FilterBuilder.lessThanOrEqualTo(AttributeBuilder.build("customer", "x")), FilterBuilder.not(base),
            FilterBuilder.or(base, FilterBuilder.equalTo(AttributeBuilder.build(Name.NAME, "grace@example.com"))),
            FilterBuilder.endsWith(AttributeBuilder.build(Name.NAME, "example.com")),
            FilterBuilder.equalsIgnoreCase(AttributeBuilder.build(Name.NAME, "ADA@example.com"))
        };

        // When: Build the supported search query
        String query = new GoogleFilter(conjunction).buildUserQuery(request);

        // Then: Combine supported terms without unsupported expressions
        assertEquals(query, "email=ada@example.com givenName:Ada*");
        for (Filter filter : unsupported) {
            assertNull(new GoogleFilter(filter).buildUserQuery(request), filter.toString());
        }
    }

    @Test
    public void groupFilterVisitorsRejectEveryUnsupportedFilterShape() throws Exception {
        Directory.Groups.List request = directory().groups().list();
        GroupHandler handler = new GroupHandler();
        Filter base = FilterBuilder.equalTo(AttributeBuilder.build("customer", "my_customer"));

        expectThrows(UnsupportedOperationException.class, () -> FilterBuilder.and(base,
                FilterBuilder.equalTo(AttributeBuilder.build("domain", "example.com"))).accept(handler, request));
        expectThrows(UnsupportedOperationException.class, () -> FilterBuilder.containsAllValues(
                AttributeBuilder.build(GoogleAppsConnector.MEMBERS_ATTR, java.util.Arrays.asList("a", "b")))
                .accept(handler, request));
        expectThrows(UnsupportedOperationException.class, () -> FilterBuilder.greaterThan(
                AttributeBuilder.build("customer", "x")).accept(handler, request));
        expectThrows(UnsupportedOperationException.class, () -> FilterBuilder.greaterThanOrEqualTo(
                AttributeBuilder.build("customer", "x")).accept(handler, request));
        expectThrows(UnsupportedOperationException.class, () -> FilterBuilder.lessThan(
                AttributeBuilder.build("customer", "x")).accept(handler, request));
        expectThrows(UnsupportedOperationException.class, () -> FilterBuilder.lessThanOrEqualTo(
                AttributeBuilder.build("customer", "x")).accept(handler, request));
        expectThrows(UnsupportedOperationException.class, () -> FilterBuilder.not(base).accept(handler, request));
        expectThrows(UnsupportedOperationException.class, () -> FilterBuilder.or(base,
                FilterBuilder.equalTo(AttributeBuilder.build("domain", "example.com"))).accept(handler, request));
        expectThrows(UnsupportedOperationException.class, () -> FilterBuilder.endsWith(
                AttributeBuilder.build("domain", "example.com")).accept(handler, request));
        expectThrows(UnsupportedOperationException.class, () -> FilterBuilder.equalsIgnoreCase(
                AttributeBuilder.build("domain", "example.com")).accept(handler, request));
    }

    private static Directory directory() {
        return new Directory.Builder(new NetHttpTransport(), new GsonFactory(), request -> { })
                .setApplicationName("connector-googleapps-test")
                .build();
    }
}
