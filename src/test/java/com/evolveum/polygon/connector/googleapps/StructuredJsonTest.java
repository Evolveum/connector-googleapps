package com.evolveum.polygon.connector.googleapps;
import com.google.api.client.util.ArrayMap;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.testng.annotations.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
public class StructuredJsonTest {
    @Test public void escapedValuesAndNativeTypesRoundTrip() {
        Map<String,Object> value=new HashMap<>();value.put("value","quote\"\n\\");value.put("primary",true);value.put("number",42);
        Collection<?> encoded=(Collection<?>)GoogleAppsUtil.structAttrToString(Collections.singletonList(value));
        ArrayMap<String,Object> decoded=GoogleAppsUtil.parseJson(encoded.iterator().next().toString());
        assertThat(decoded.get("value")).isEqualTo(value.get("value"));assertThat(decoded.get("primary")).isEqualTo(true);
        assertThat(decoded.get("number").toString()).isEqualTo("42");
    }
    @Test public void parsedStructuredValueIsNotDuplicated() {
        assertThat((Collection<?>)GoogleAppsUtil.getStructAttr(AttributeBuilder.build("phones","{\"value\":\"123\",\"type\":\"work\"}"))).hasSize(1);
    }
    @Test public void oldStringBooleansAreAccepted() {
        assertThat(GoogleAppsUtil.parseJson("{\"primary\":\"true\"}").get("primary")).isEqualTo(true);
    }
}
