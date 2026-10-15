package com.evolveum.polygon.connector.googleapps;

import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.directory.Directory;
import com.google.api.services.directory.model.Member;
import org.testng.annotations.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

public class MembershipIdentityTest {
    private Directory directory(String response) {
        return new Directory.Builder(new MockHttpTransport.Builder()
                .setLowLevelHttpResponse(new MockLowLevelHttpResponse().setContentType("application/json").setContent(response)).build(),
                new GsonFactory(), null).setApplicationName("unit-test").build();
    }
    @Test public void immutableUserIdIsSentAsId() {
        Member body=(Member) GroupHandler.createMemberById(directory("{} ").members(), "group-id", "user-id", null).getJsonContent();
        assertThat(body.getId()).isEqualTo("user-id");
        assertThat(body.getEmail()).isNull();
    }
    @Test public void emailIsSentAsEmail() {
        Member body=(Member) GroupHandler.createMemberByEmail(directory("{} ").members(), "group-id", "user@example.com", "OWNER").getJsonContent();
        assertThat(body.getEmail()).isEqualTo("user@example.com");
        assertThat(body.getId()).isNull();
        assertThat(body.getRole()).isEqualTo("OWNER");
    }
    @Test public void membershipReadReturnsImmutableGroupIds() {
        GoogleAppsConfiguration config=new GoogleAppsConfiguration();
        config.setDomain("example.com"); config.setAllowCache(false);
        GoogleAppsConnector connector=new GoogleAppsConnector(); connector.init(config);
        Directory directory=directory("{\"groups\":[{\"id\":\"group-id\",\"email\":\"group@example.com\"}]}");
        assertThat(connector.listGroups(directory.groups(), "user-id")).containsExactly("group-id");
    }
}
