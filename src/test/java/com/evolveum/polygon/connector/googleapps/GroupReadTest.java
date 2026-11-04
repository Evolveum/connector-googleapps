package com.evolveum.polygon.connector.googleapps;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.PredefinedAttributes;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class GroupReadTest extends GoogleAppsConnectorTestBase {

    @DataProvider
    public Object[][] errorResponses() {
        return new Object[][] {
                {404, "application/json", "{\"error\":{\"code\":404,\"message\":\"Not found\"}}"},
                {404, "text/html", "<html>Not found</html>"}, {404, "text/plain", ""},
                {403, "application/json", "{\"error\":{\"code\":403,\"message\":\"Forbidden\"}}"}, {500, "text/plain", ""}};
    }

    @DataProvider
    public Object[][] emptyResponses() {
        return new Object[][] {{"{}", "{}"}, {"{\"members\":[]}", "{\"groups\":[]}"}};
    }

    @DataProvider
    public Object[][] failedUpdates() {
        return new Object[][] {{false, 404, "text/plain", ""},
                {true, 404, "application/json", "{\"error\":{\"code\":404,\"message\":\"Not found\"}}"}, {true, 500, "text/plain", "Read failed"}};
    }

    @Test(dataProvider = "errorResponses")
    public void testMemberFailure(int status, String mediaType, String body) {
        // Given: Failed member request
        stubMembers(null, status, mediaType, body);

        // When: Read group membership
        Throwable failure = catchThrowable(() -> GroupConverter.listAllMembers(configuration.getDirectory().members(), "group001", "MEMBER"));

        // Then: Propagate original HTTP failure
        assertFailure(failure, status);
        verifyMembers(null);
    }

    @Test(dataProvider = "errorResponses")
    public void testGroupFailure(int status, String mediaType, String body) {
        // Given: Failed group request
        stubGroups(null, status, mediaType, body);

        // When: Read user group memberships
        Throwable failure = catchThrowable(() -> GroupConverter.listGroups(configuration.getDirectory().groups(), "user001"));

        // Then: Propagate original HTTP failure
        assertFailure(failure, status);
        verifyGroups(null);
    }

    @Test(dataProvider = "errorResponses")
    public void testMemberPageFailure(int status, String mediaType, String body) {
        // Given: Valid page then HTTP failure
        stubMembers(null, 200, "application/json", "{\"members\":[{\"id\":\"user001\",\"email\":\"first@example.com\"}],\"nextPageToken\":\"next\"}");
        stubMembers("next", status, mediaType, body);

        // When: Read all membership pages
        Throwable failure = catchThrowable(() -> GroupConverter.listAllMembers(configuration.getDirectory().members(), "group001", "MEMBER"));

        // Then: Reject incomplete membership result
        assertFailure(failure, status);
        verifyMembers(null);
        verifyMembers("next");
    }

    @Test(dataProvider = "errorResponses")
    public void testGroupPageFailure(int status, String mediaType, String body) {
        // Given: Valid page then HTTP failure
        stubGroups(null, 200, "application/json", "{\"groups\":[{\"id\":\"old-group\"}],\"nextPageToken\":\"next\"}");
        stubGroups("next", status, mediaType, body);

        // When: Read all user group pages
        Throwable failure = catchThrowable(() -> GroupConverter.listGroups(configuration.getDirectory().groups(), "user001"));

        // Then: Reject incomplete group result
        assertFailure(failure, status);
        verifyGroups(null);
        verifyGroups("next");
    }

    @Test(dataProvider = "failedUpdates")
    public void testReplaceFailure(boolean paged, int status, String mediaType, String body) {
        // Given: Membership replacement with failed read
        if (paged) {
            stubGroups(null, 200, "application/json", "{\"groups\":[{\"id\":\"old-group\"}],\"nextPageToken\":\"next\"}");
        }
        stubGroups(paged ? "next" : null, status, mediaType, body);
        AttributeDelta change = AttributeDeltaBuilder.build(PredefinedAttributes.GROUPS_NAME, List.of("new-group"));

        // When: Replace user group memberships
        Throwable failure = catchThrowable(() -> connectorFacade.updateDelta(ObjectClass.ACCOUNT, new Uid("user001"), Set.of(change), null));

        // Then: Fail before modifying memberships
        assertFailure(failure, status);
        verifyGroups(null);
        if (paged) {
            verifyGroups("next");
        }
        GoogleApiMockServer.verify(0, postRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/new-group/members")));
        GoogleApiMockServer.verify(0, deleteRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/old-group/members/user001")));
        assertThat(GoogleApiMockServer.getAllRequests().stream().filter(request -> request.getUrl().startsWith("/admin/")))
                .hasSize(paged ? 2 : 1).allSatisfy(request -> assertThat(request.getMethod().getName()).isEqualTo("GET"));
    }

    @Test(dataProvider = "emptyResponses")
    public void testEmptyLists(String membersBody, String groupsBody) {
        // Given: Successful empty membership responses
        stubMembers(null, 200, "application/json", membersBody);
        stubGroups(null, 200, "application/json", groupsBody);

        // When: Read empty membership lists
        List<Map<String, String>> members = GroupConverter.listAllMembers(configuration.getDirectory().members(), "group001", "MEMBER");
        Set<String> groups = GroupConverter.listGroups(configuration.getDirectory().groups(), "user001");

        // Then: Accept successful empty results
        assertThat(members).isEmpty();
        assertThat(groups).isEmpty();
        verifyMembers(null);
        verifyGroups(null);
    }

    @Test
    public void testMemberPages() {
        // Given: Two successful membership pages
        String first = "{\"id\":\"user001\",\"email\":\"first@example.com\",\"role\":\"MEMBER\",\"type\":\"USER\"}";
        String second = "{\"id\":\"user002\",\"email\":\"second@example.com\",\"role\":\"MEMBER\",\"type\":\"USER\"}";
        stubMembers(null, 200, "application/json", "{\"members\":[" + first + "],\"nextPageToken\":\"next\"}");
        stubMembers("next", 200, "application/json", "{\"members\":[" + second + "]}");

        // When: Read all membership pages
        List<Map<String, String>> members = GroupConverter.listAllMembers(configuration.getDirectory().members(), "group001", "MEMBER");

        // Then: Return complete ordered membership data
        assertThat(members).containsExactly(
                Map.of("id", "user001", "email", "first@example.com", "role", "MEMBER", "type", "USER"),
                Map.of("id", "user002", "email", "second@example.com", "role", "MEMBER", "type", "USER"));
        verifyMembers(null);
        verifyMembers("next");
    }

    @Test
    public void testGroupPages() {
        // Given: Two successful group pages
        stubGroups(null, 200, "application/json", "{\"groups\":[{\"id\":\"group001\"}],\"nextPageToken\":\"next\"}");
        stubGroups("next", 200, "application/json", "{\"groups\":[{\"id\":\"group002\"}]}");

        // When: Read all user group pages
        Set<String> groups = GroupConverter.listGroups(configuration.getDirectory().groups(), "user001");

        // Then: Return complete group identifiers
        assertThat(groups).containsExactlyInAnyOrder("group001", "group002");
        verifyGroups(null);
        verifyGroups("next");
    }

    private void stubMembers(String page, int status, String mediaType, String body) {
        stubFor(get(urlPathEqualTo("/admin/directory/v1/groups/group001/members")).withQueryParam("roles", equalTo("MEMBER"))
                .withQueryParam("pageToken", page == null ? absent() : equalTo(page))
                .willReturn(aResponse().withStatus(status).withHeader("Content-Type", mediaType).withBody(body)));
    }

    private void stubGroups(String page, int status, String mediaType, String body) {
        stubFor(get(urlPathEqualTo("/admin/directory/v1/groups")).withQueryParam("userKey", equalTo("user001"))
                .withQueryParam("pageToken", page == null ? absent() : equalTo(page))
                .willReturn(aResponse().withStatus(status).withHeader("Content-Type", mediaType).withBody(body)));
    }

    private void verifyMembers(String page) {
        GoogleApiMockServer.verify(1, getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group001/members"))
                .withQueryParam("roles", equalTo("MEMBER")).withQueryParam("pageToken", page == null ? absent() : equalTo(page)));
    }

    private void verifyGroups(String page) {
        GoogleApiMockServer.verify(1, getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withQueryParam("userKey", equalTo("user001")).withQueryParam("pageToken", page == null ? absent() : equalTo(page)));
    }

    private void assertFailure(Throwable failure, int status) {
        assertThat(failure).isInstanceOf(ConnectorException.class).hasCauseInstanceOf(GoogleJsonResponseException.class);
        GoogleJsonResponseException response = (GoogleJsonResponseException) failure.getCause();
        assertThat(response.getStatusCode()).isEqualTo(status);
    }
}
