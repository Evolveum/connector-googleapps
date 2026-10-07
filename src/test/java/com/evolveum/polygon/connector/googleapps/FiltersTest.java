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
        Directory.Users.List request = directory().users().list();
        Filter startsWith = FilterBuilder.startsWith(AttributeBuilder.build(Name.NAME, "Ada Lovelace"));
        Filter equals = FilterBuilder.equalTo(AttributeBuilder.build(UserHandler.FAMILY_NAME_ATTR, "O'Connor"));

        String startsWithQuery = startsWith.accept(new UserHandler(), request).toString();
        String equalsQuery = equals.accept(new UserHandler(), request).toString();

        assertEquals(startsWithQuery, "email:'Ada Lovelace*'");
        assertEquals(equalsQuery, "familyName=O\\'Connor");
    }

    @Test
    public void userFiltersSetCustomerOrDomainButNotBoth() throws Exception {
        Directory.Users.List request = directory().users().list();
        Filter customer = FilterBuilder.equalTo(AttributeBuilder.build("customer", "my_customer"));
        Filter domain = FilterBuilder.equalTo(AttributeBuilder.build("domain", "example.com"));

        customer.accept(new UserHandler(), request);
        assertEquals(request.getCustomer(), "my_customer");
        expectThrows(InvalidAttributeValueException.class, () -> domain.accept(new UserHandler(), request));
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
        Directory.Users.List request = directory().users().list();
        UserHandler handler = new UserHandler();

        expectThrows(InvalidAttributeValueException.class, () -> FilterBuilder.contains(
                AttributeBuilder.build("unsupported", "x")).accept(handler, request));
        expectThrows(InvalidAttributeValueException.class, () -> FilterBuilder.startsWith(
                AttributeBuilder.build("unsupported", "x")).accept(handler, request));
        expectThrows(InvalidAttributeValueException.class, () -> FilterBuilder.equalTo(
                AttributeBuilder.build("unsupported", "x")).accept(handler, request));
        expectThrows(InvalidAttributeValueException.class, () -> FilterBuilder.contains(
                AttributeBuilder.build("description", "x")).accept(handler, request));
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
        Directory.Users.List request = directory().users().list();
        UserHandler handler = new UserHandler();
        Filter conjunction = FilterBuilder.and(
                FilterBuilder.equalTo(AttributeBuilder.build(Name.NAME, "ada@example.com")),
                FilterBuilder.startsWith(AttributeBuilder.build(UserHandler.GIVEN_NAME_ATTR, "Ada")));

        assertEquals(conjunction.accept(handler, request).toString(),
                "email=ada@example.com givenName:Ada*");
        assertEquals(FilterBuilder.containsAllValues(AttributeBuilder.build("email",
                java.util.Arrays.asList("a", "b"))).accept(handler, request), null);
        assertEquals(FilterBuilder.greaterThan(AttributeBuilder.build("customer", "x")).accept(handler, request), null);
        assertEquals(FilterBuilder.greaterThanOrEqualTo(AttributeBuilder.build("customer", "x")).accept(handler, request), null);
        assertEquals(FilterBuilder.lessThan(AttributeBuilder.build("customer", "x")).accept(handler, request), null);
        assertEquals(FilterBuilder.lessThanOrEqualTo(AttributeBuilder.build("customer", "x")).accept(handler, request), null);
        assertEquals(FilterBuilder.not(FilterBuilder.equalTo(AttributeBuilder.build(Name.NAME, "ada@example.com")))
                .accept(handler, request), null);
        assertEquals(FilterBuilder.or(
                FilterBuilder.equalTo(AttributeBuilder.build(Name.NAME, "ada@example.com")),
                FilterBuilder.equalTo(AttributeBuilder.build(Name.NAME, "grace@example.com")))
                .accept(handler, request), null);
        assertEquals(FilterBuilder.endsWith(AttributeBuilder.build(Name.NAME, "example.com"))
                .accept(handler, request), null);
        assertEquals(FilterBuilder.equalsIgnoreCase(AttributeBuilder.build(Name.NAME, "ADA@example.com"))
                .accept(handler, request), null);
    }

    @Test
    public void groupFilterVisitorsRejectEveryUnsupportedFilterShape() throws Exception {
        Directory.Groups.List request = directory().groups().list();
        GroupHandler handler = new GroupHandler();
        Filter base = FilterBuilder.equalTo(AttributeBuilder.build("customer", "my_customer"));

        expectThrows(UnsupportedOperationException.class, () -> FilterBuilder.and(base,
                FilterBuilder.equalTo(AttributeBuilder.build("domain", "example.com"))).accept(handler, request));
        assertNull(FilterBuilder.containsAllValues(AttributeBuilder.build(GoogleAppsConnector.MEMBERS_ATTR, java.util.Arrays.asList("a", "b")))
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
