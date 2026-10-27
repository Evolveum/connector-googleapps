package com.evolveum.polygon.connector.googleapps;

import com.google.api.client.util.ArrayMap;
import com.google.api.services.directory.model.User;
import com.google.gson.JsonParser;
import org.identityconnectors.framework.common.objects.*;
import org.testng.annotations.Test;
import java.util.*;
import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

public class StructuredContractTest extends GoogleAppsConnectorTestBase {
    @Test public void legacyBooleanStringsMustRemainRemovable() {
        User current = new User().setPhones(List.of(Map.of("value", "+421123", "primary", true)));
        AttributeDelta remove = AttributeDeltaBuilder.build("phones", null,
                List.of("{\"value\":\"+421123\",\"primary\":\"true\"}"));
        User body = (User) UserHandler.buildUserUpdateRequest(configuration.getDirectory().users(), new Uid("u-legacy"),
                Set.of(remove), Optional.of(current)).getJsonContent();
        assertThat((Collection<?>) body.getPhones()).isEmpty();
    }

    @Test public void structuredStringsMustEscapeQuotesAndBackslashes() {
        ArrayMap<String,Object> map = new ArrayMap<>();
        map.put("title", "Engineer \"Senior\" \\ HQ\nRoom 1");
        List<?> values = (List<?>) GoogleAppsUtil.structAttrToString(List.of(map));
        assertThatCode(() -> JsonParser.parseString((String) values.get(0))).doesNotThrowAnyException();
    }

    @Test public void removeStructuredValueReturnedByReadMustActuallyRemoveIt() {
        ArrayMap<String,Object> phone = new ArrayMap<>();
        phone.put("type", "work"); phone.put("value", "+421123"); phone.put("primary", true);
        List<?> readValues = (List<?>) GoogleAppsUtil.structAttrToString(List.of(phone));
        User current = new User().setPhones(List.of(phone));
        AttributeDelta remove = AttributeDeltaBuilder.build("phones", null, List.of(readValues.get(0)));
        User body = (User) UserHandler.buildUserUpdateRequest(configuration.getDirectory().users(), new Uid("u-roundtrip"),
            Set.of(remove), Optional.of(current)).getJsonContent();
        assertThat((Collection<?>) body.getPhones()).isEmpty();
    }
}
