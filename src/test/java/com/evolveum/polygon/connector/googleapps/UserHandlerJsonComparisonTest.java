package com.evolveum.polygon.connector.googleapps;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for UserHandler JSON comparison logic (matchesJsonValue method)
 *
 * @author Hiroyuki Wada
 */
public class UserHandlerJsonComparisonTest {

    /**
     * Helper method to access the private matchesJsonValue method via reflection
     */
    private boolean matchesJsonValue(String currentJsonValue, String valueToRemove) throws Exception {
        Method method = UserHandler.class.getDeclaredMethod("matchesJsonValue", String.class, String.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, currentJsonValue, valueToRemove);
    }

    @Test
    @DisplayName("Identical JSON objects should match")
    public void testIdenticalJsonMatch() throws Exception {
        String json1 = "{\"address\":\"test@example.com\",\"type\":\"work\",\"primary\":true}";
        String json2 = "{\"address\":\"test@example.com\",\"type\":\"work\",\"primary\":true}";

        assertThat(matchesJsonValue(json1, json2)).isTrue();
    }

    @Test
    @DisplayName("Different field ordering should still match")
    public void testDifferentFieldOrderingMatch() throws Exception {
        String json1 = "{\"address\":\"test@example.com\",\"type\":\"work\",\"primary\":true}";
        String json2 = "{\"primary\":true,\"address\":\"test@example.com\",\"type\":\"work\"}";

        assertThat(matchesJsonValue(json1, json2)).isTrue();
    }

    @Test
    @DisplayName("Different formatting should still match")
    public void testDifferentFormattingMatch() throws Exception {
        String json1 = "{\"address\":\"test@example.com\",\"type\":\"work\"}";
        String json2 = "{ \"address\" : \"test@example.com\" , \"type\" : \"work\" }";

        assertThat(matchesJsonValue(json1, json2)).isTrue();
    }

    @Test
    @DisplayName("Null field vs absent field should NOT match")
    public void testNullVsAbsentFieldNoMatch() throws Exception {
        String jsonWithNull = "{\"address\":\"test@example.com\",\"type\":\"work\",\"customType\":null}";
        String jsonWithoutField = "{\"address\":\"test@example.com\",\"type\":\"work\"}";

        assertThat(matchesJsonValue(jsonWithNull, jsonWithoutField)).isFalse();
        assertThat(matchesJsonValue(jsonWithoutField, jsonWithNull)).isFalse();
    }

    @Test
    @DisplayName("Different field sets should NOT match")
    public void testDifferentFieldSetsNoMatch() throws Exception {
        String json1 = "{\"address\":\"test@example.com\",\"type\":\"work\",\"primary\":true}";
        String json2 = "{\"address\":\"test@example.com\",\"type\":\"work\",\"customType\":\"business\"}";

        assertThat(matchesJsonValue(json1, json2)).isFalse();
    }

    @Test
    @DisplayName("Partial match should NOT match")
    public void testPartialMatchNoMatch() throws Exception {
        String fullJson = "{\"address\":\"test@example.com\",\"type\":\"work\",\"primary\":true}";
        String partialJson = "{\"type\":\"work\"}";

        assertThat(matchesJsonValue(fullJson, partialJson)).isFalse();
        assertThat(matchesJsonValue(partialJson, fullJson)).isFalse();
    }

    @Test
    @DisplayName("Different values should NOT match")
    public void testDifferentValuesNoMatch() throws Exception {
        String json1 = "{\"address\":\"test1@example.com\",\"type\":\"work\"}";
        String json2 = "{\"address\":\"test2@example.com\",\"type\":\"work\"}";

        assertThat(matchesJsonValue(json1, json2)).isFalse();
    }

    @Test
    @DisplayName("Empty objects should match")
    public void testEmptyObjectsMatch() throws Exception {
        String json1 = "{}";
        String json2 = "{}";

        assertThat(matchesJsonValue(json1, json2)).isTrue();
    }
}