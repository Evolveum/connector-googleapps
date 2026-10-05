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

import com.google.api.client.util.ArrayMap;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collection;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestGoogleAppsUtil {

    @Test
    public void getNameReturnsEmail() {
        assertEquals(GoogleAppsUtil.getName(new Name("ada@example.com")), "ada@example.com");
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void getNameRejectsMissingValue() {
        GoogleAppsUtil.getName(null);
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void getNameRejectsBlankValue() {
        GoogleAppsUtil.getName(new Name("  "));
    }

    @Test
    public void scalarValueHelpersConvertOnlySupportedValues() {
        assertEquals(GoogleAppsUtil.getStringValueWithDefault(AttributeBuilder.build("label", "value"), "default"), "value");
        assertEquals(GoogleAppsUtil.getStringValueWithDefault(AttributeBuilder.build("label"), "default"), "default");
        assertEquals(GoogleAppsUtil.getBooleanValueWithDefault(AttributeBuilder.build("flag", "TrUe"), false), Boolean.TRUE);
        assertEquals(GoogleAppsUtil.getBooleanValueWithDefault(AttributeBuilder.build("flag", false), true), Boolean.FALSE);
        assertEquals(GoogleAppsUtil.getBooleanValueWithDefault(AttributeBuilder.build("flag"), true), Boolean.TRUE);
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void stringValueHelperRejectsNonStringValue() {
        GoogleAppsUtil.getStringValueWithDefault(AttributeBuilder.build("label", 42), null);
    }

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void booleanValueHelperRejectsNonBooleanValue() {
        GoogleAppsUtil.getBooleanValueWithDefault(AttributeBuilder.build("flag", 42), null);
    }

    @Test
    public void structAttributesConvertPlainAndJsonValuesAndOmitNullValue() {
        Attribute values = AttributeBuilder.build("phones",
                Arrays.asList("+421123", "{\"type\":\"work\",\"value\":\"+421456\"}", "{\"value\":\"null\"}"));

        Collection<?> converted = (Collection<?>) GoogleAppsUtil.getStructAttr(values);

        assertEquals(converted.size(), 3);
        assertTrue(converted.stream().anyMatch(value -> value instanceof ArrayMap
                && "+421123".equals(((ArrayMap<?, ?>) value).get("value"))));
        assertTrue(converted.stream().anyMatch(value -> value instanceof ArrayMap
                && "work".equals(((ArrayMap<?, ?>) value).get("type"))));
        assertTrue(converted.stream().anyMatch(value -> value instanceof ArrayMap
                && "null".equals(((ArrayMap<?, ?>) value).get("value"))));
    }

    @Test
    public void structAttributesReturnNullForNoValues() {
        assertNull(GoogleAppsUtil.getStructAttr(AttributeBuilder.build("phones")));
        assertNull(GoogleAppsUtil.structAttrToString(null));
    }
}
