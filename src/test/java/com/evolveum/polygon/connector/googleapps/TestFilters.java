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
import static org.testng.Assert.expectThrows;

public class TestFilters {

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

    private static Directory directory() {
        return new Directory.Builder(new NetHttpTransport(), new GsonFactory(), request -> { })
                .setApplicationName("connector-googleapps-test")
                .build();
    }
}
