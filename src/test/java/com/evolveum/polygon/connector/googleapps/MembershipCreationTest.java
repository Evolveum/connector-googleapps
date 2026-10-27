package com.evolveum.polygon.connector.googleapps;

import com.google.api.client.testing.http.*;
import com.google.api.client.http.*;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.directory.Directory;
import org.identityconnectors.framework.common.exceptions.*;
import org.identityconnectors.framework.common.objects.*;
import org.testng.annotations.Test;
import java.io.IOException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

public class MembershipCreationTest {
    private final List<MockLowLevelHttpRequest> requests = new ArrayList<>();

    private GoogleAppsConnector connector(boolean denyMembership) {
        requests.clear();
        MockHttpTransport transport = new MockHttpTransport() {
            @Override public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
                boolean membership = url.contains("/groups/");
                String body = membership
                        ? "{\"id\":\"user-id\",\"email\":\"user@example.com\",\"etag\":\"member-etag\"}"
                        : "{\"id\":\"user-id\",\"primaryEmail\":\"user@example.com\",\"etag\":\"user-etag\"}";
                int status = 200;
                if (membership && denyMembership) {
                    status = 403;
                    body = "{\"error\":{\"code\":403,\"message\":\"denied\",\"errors\":[{\"reason\":\"forbidden\"}]}}";
                }
                MockLowLevelHttpRequest request = new MockLowLevelHttpRequest(url).setResponse(
                        new MockLowLevelHttpResponse().setStatusCode(status).setContentType("application/json").setContent(body));
                requests.add(request);
                return request;
            }
        };
        Directory directory = new Directory.Builder(transport, new GsonFactory(), null).setApplicationName("test").build();
        GoogleAppsConfiguration configuration = new GoogleAppsConfiguration() {
            @Override public Directory getDirectory() { return directory; }
        };
        configuration.setDomain("example.com");
        configuration.setAllowCache(false);
        GoogleAppsConnector connector = new GoogleAppsConnector();
        connector.init(configuration);
        return connector;
    }

    private Set<Attribute> attributes() {
        return new HashSet<>(Arrays.asList(new Name("user@example.com"),
                AttributeBuilder.build("givenName", "Test"), AttributeBuilder.build("familyName", "User"),
                AttributeBuilder.buildPassword("secret".toCharArray()), AttributeBuilder.build("__GROUPS__", "group-id")));
    }

    @Test public void createAssignsRequestedGroupUsingNewUserId() throws Exception {
        Uid uid = connector(false).create(ObjectClass.ACCOUNT, attributes(), new OperationOptionsBuilder().build());
        assertThat(uid.getUidValue()).isEqualTo("user-id");
        assertThat(requests).hasSize(2);
        assertThat(requests.get(1).getUrl()).contains("/groups/group-id/members");
        com.google.gson.JsonObject body = new com.google.gson.JsonParser().parse(requests.get(1).getContentAsString()).getAsJsonObject();
        assertThat(body.get("id").getAsString()).isEqualTo("user-id");
        assertThat(body.has("email")).isFalse();
    }

    @Test public void failedMembershipRetainsCreatedUserIdentifier() {
        Throwable failure = catchThrowable(() -> connector(true).create(ObjectClass.ACCOUNT, attributes(), new OperationOptionsBuilder().build()));
        assertThat(failure).isInstanceOf(RetryableException.class);
        assertThat(failure.getCause()).isInstanceOf(AlreadyExistsException.class);
        assertThat(((AlreadyExistsException) failure.getCause()).getUid().getUidValue()).isEqualTo("user-id");
        assertThat(failure.getSuppressed()).hasSize(1);
        assertThat(requests).hasSize(2);
    }
}
