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
import com.google.gson.JsonSyntaxException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class GoogleAppsUtilTest {

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

    @Test(expectedExceptions = InvalidAttributeValueException.class)
    public void booleanValueHelperRejectsUnrecognizedString() {
        GoogleAppsUtil.getBooleanValueWithDefault(AttributeBuilder.build("flag", "sometimes"), null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void stringValueHelperRejectsMultipleValues() {
        GoogleAppsUtil.getStringValueWithDefault(
                AttributeBuilder.build("label", Arrays.asList("one", "two")), null);
    }

    @Test
    public void structAttributesConvertPlainAndJsonValuesAndOmitNullValue() {
        // Given: Plain, JSON and null-valued structured entries
        Attribute values = AttributeBuilder.build("phones",
                Arrays.asList("+421123", "{\"type\":\"work\",\"value\":\"+421456\"}", "{\"value\":\"null\"}"));

        // When: Parse the structured attribute values
        Collection<?> converted = (Collection<?>) GoogleAppsUtil.getStructAttr(values);

        // Then: Preserve complete entries and omit nulls
        assertEquals(converted.size(), 2);
        assertEquals(Set.copyOf(converted), Set.of(Map.of("value", "+421123"), Map.of("type", "work", "value", "+421456")));
    }

    @Test
    public void structAttributesReturnNullForNoValues() {
        assertNull(GoogleAppsUtil.getStructAttr(AttributeBuilder.build("phones")));
        assertNull(GoogleAppsUtil.structAttrToString(null));
    }

    @Test
    public void structAttributesHandleNullAndNonStringValues() {
        assertNull(GoogleAppsUtil.getStructAttr(null));

        ArrayMap<String, Object> map = new ArrayMap<>();
        map.put("type", "work");
        Collection<?> converted = (Collection<?>) GoogleAppsUtil.getStructAttr(
                AttributeBuilder.build("phones", map));

        assertEquals(converted.size(), 1);
        assertTrue(converted.contains(map));
    }

    @Test
    public void structAttributesSerializeMapsAndScalarValuesAndSkipEmptyValues() {
        ArrayMap<String, Object> value = new ArrayMap<>();
        value.put("type", "work");
        value.put("value", "+421123");
        ArrayMap<String, Object> nullValue = new ArrayMap<>();
        nullValue.put("value", "null");
        ArrayMap<String, Object> emptyValue = new ArrayMap<>();
        emptyValue.put("value", "");

        Collection<?> serialized = (Collection<?>) GoogleAppsUtil.structAttrToString(
                Arrays.asList(value, nullValue, emptyValue, "plain"));

        assertEquals(serialized.size(), 4);
        assertTrue(serialized.contains("{\"type\":\"work\",\"value\":\"+421123\"}"));
        assertTrue(serialized.contains("{\"value\":\"null\"}"));
        assertTrue(serialized.contains("{\"value\":\"\"}"));
        assertTrue(serialized.contains("plain"));
        assertNull(GoogleAppsUtil.structAttrToString(List.of()));
    }

    @Test
    public void nestedJsonStructAttributeIsParsedRecursively() {
        // Given: Nested metadata with a boolean value
        Attribute attribute = AttributeBuilder.build("addresses", "{\"type\":\"work\",\"metadata\":{\"primary\":\"true\"}}");

        // When: Parse nested structured values
        Collection<?> converted = (Collection<?>) GoogleAppsUtil.getStructAttr(attribute);

        // Then: Preserve nesting and the boolean type
        assertEquals(converted.size(), 1);
        ArrayMap<?, ?> address = (ArrayMap<?, ?>) converted.iterator().next();
        assertEquals(address.get("type"), "work");
        assertTrue(address.get("metadata") instanceof ArrayMap);
        assertEquals(((ArrayMap<?, ?>) address.get("metadata")).get("primary"), Boolean.TRUE);
    }

    @Test(expectedExceptions = JsonSyntaxException.class)
    public void malformedJsonStructAttributeThrowsInsteadOfReturningCorruptedData() {
        GoogleAppsUtil.getStructAttr(AttributeBuilder.build("phones", "{not-json}"));
    }
}
