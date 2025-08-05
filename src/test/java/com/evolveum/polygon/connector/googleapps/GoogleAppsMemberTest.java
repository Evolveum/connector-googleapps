package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Test class for GoogleApps Member operations
 *
 * @author Hiroyuki Wada
 */
@DisplayName("GoogleApps Member Tests")
public class GoogleAppsMemberTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test member creation")
    public void testCreateMember() {
        // Setup stub for member creation
        GoogleApiMockServer.stubFor(post(urlPathEqualTo("/admin/directory/v1/groups/group001/members"))
                .withRequestBody(containing("member001@example.com"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"member001@example.com\",\n" +
                                "  \"email\": \"member001@example.com\",\n" +
                                "  \"role\": \"MEMBER\",\n" +
                                "  \"type\": \"USER\",\n" +
                                "  \"etag\": \"\\\"member001-etag\\\"\"\n" +
                                "}")));

        // Execute member creation
        Set<Attribute> attributes = new HashSet<>();
        attributes.add(AttributeBuilder.build(GROUP_KEY_ATTR, "group001"));
        attributes.add(AttributeBuilder.build(EMAIL_ATTR, "member001@example.com"));
        attributes.add(AttributeBuilder.build(ROLE_ATTR, "MEMBER"));

        Uid createdUid = connectorFacade.create(MEMBER, attributes, null);

        // Verify request was sent correctly
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group001/members"))
                .withRequestBody(equalToJson("{\n" +
                        "  \"email\": \"member001@example.com\",\n" +
                        "  \"role\": \"MEMBER\"\n" +
                        "}")));

        // Verify response
        assertThat(createdUid).isNotNull();
        assertThat(createdUid.getUidValue()).isEqualTo("group001/member001@example.com");
    }

    @Test
    @DisplayName("Test member deletion")
    public void testDeleteMember() {
        // Setup stub for member deletion
        GoogleApiMockServer.stubFor(delete(urlPathEqualTo("/admin/directory/v1/groups/group001/members/member001@example.com"))
                .willReturn(aResponse()
                        .withStatus(204)));

        // Execute member deletion
        Uid uid = new Uid("group001/member001@example.com");

        assertThatNoException().isThrownBy(() ->
                connectorFacade.delete(MEMBER, uid, null));

        // Verify request was sent correctly
        GoogleApiMockServer.verifyRequest(deleteRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group001/members/member001@example.com")));
    }

    @Test
    @DisplayName("Test member role update")
    public void testUpdateMemberRole() {
        // Setup stub for member update (PATCH request)
        GoogleApiMockServer.stubFor(patch(urlPathEqualTo("/admin/directory/v1/groups/group001/members/member001@example.com"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"member001@example.com\",\n" +
                                "  \"email\": \"member001@example.com\",\n" +
                                "  \"role\": \"MANAGER\",\n" +
                                "  \"type\": \"USER\",\n" +
                                "  \"etag\": \"\\\"member001-updated-etag\\\"\"\n" +
                                "}")));

        // Execute member role update
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build(ROLE_ATTR, "MANAGER"));

        Uid uid = new Uid("group001/member001@example.com");

        assertThatNoException().isThrownBy(() ->
                connectorFacade.updateDelta(MEMBER, uid, modifications, null));

        // Verify PATCH request was sent correctly
        // The implementation also sends email along with role
        GoogleApiMockServer.verifyRequest(patchRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group001/members/member001@example.com"))
                .withRequestBody(equalToJson("{\n" +
                        "  \"email\": \"member001@example.com\",\n" +
                        "  \"role\": \"MANAGER\"\n" +
                        "}")));
    }

    @Test
    @DisplayName("Test get member by UID")
    public void testGetMember() {
        // Setup stub for member get (no query parameters)
        GoogleApiMockServer.stubFor(get(urlPathEqualTo("/admin/directory/v1/groups/group001/members/member001@example.com"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"member001@example.com\",\n" +
                                "  \"email\": \"member001@example.com\",\n" +
                                "  \"role\": \"MEMBER\",\n" +
                                "  \"type\": \"USER\",\n" +
                                "  \"etag\": \"\\\"member001-etag\\\"\"\n" +
                                "}")));

        // Execute member get
        Uid uid = new Uid("group001/member001@example.com");
        final ConnectorObject[] result = new ConnectorObject[1];

        connectorFacade.search(MEMBER, FilterBuilder.equalTo(uid), obj -> {
            result[0] = obj;
            return true;
        }, null);

        // Verify request was sent correctly
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group001/members/member001@example.com")));

        // Verify response
        assertThat(result[0]).isNotNull();
        assertThat(result[0].getUid().getUidValue()).isEqualTo("group001/member001@example.com");
        assertThat(result[0].getAttributeByName(EMAIL_ATTR).getValue().get(0)).isEqualTo("member001@example.com");
        assertThat(result[0].getAttributeByName(ROLE_ATTR).getValue().get(0)).isEqualTo("MEMBER");
        assertThat(result[0].getAttributeByName(TYPE_ATTR).getValue().get(0)).isEqualTo("USER");
    }

    @Test
    @DisplayName("Test search members")
    public void testSearchMembers() {
        // Setup stub for member search (group members list)
        GoogleApiMockServer.stubFor(get(urlPathEqualTo("/admin/directory/v1/groups/group001/members"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"members\": [\n" +
                                "    {\n" +
                                "      \"id\": \"member001@example.com\",\n" +
                                "      \"email\": \"member001@example.com\",\n" +
                                "      \"role\": \"MEMBER\",\n" +
                                "      \"type\": \"USER\",\n" +
                                "      \"etag\": \"\\\"member001-etag\\\"\"\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"id\": \"member002@example.com\",\n" +
                                "      \"email\": \"member002@example.com\",\n" +
                                "      \"role\": \"MANAGER\",\n" +
                                "      \"type\": \"USER\",\n" +
                                "      \"etag\": \"\\\"member002-etag\\\"\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Execute member search with group filter
        final java.util.List<ConnectorObject> results = new java.util.ArrayList<>();
        Filter filter = FilterBuilder.equalTo(AttributeBuilder.build(GROUP_KEY_ATTR, "group001"));

        connectorFacade.search(MEMBER, filter, obj -> {
            results.add(obj);
            return true;
        }, null);

        // Verify request was sent correctly
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group001/members")));

        // Verify response - should return 2 members
        assertThat(results).hasSize(2);

        ConnectorObject member1 = results.get(0);
        assertThat(member1.getUid().getUidValue()).isEqualTo("group001/member001@example.com");
        assertThat(member1.getAttributeByName(ROLE_ATTR).getValue().get(0)).isEqualTo("MEMBER");

        ConnectorObject member2 = results.get(1);
        assertThat(member2.getUid().getUidValue()).isEqualTo("group001/member002@example.com");
        assertThat(member2.getAttributeByName(ROLE_ATTR).getValue().get(0)).isEqualTo("MANAGER");
    }

    @Test
    @DisplayName("Test member not found error")
    public void testMemberNotFound() {
        // Setup stub for member not found
        GoogleApiMockServer.stubFor(get(urlPathEqualTo("/admin/directory/v1/groups/group001/members/nonexistent@example.com"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"error\": {\n" +
                                "    \"code\": 404,\n" +
                                "    \"message\": \"Member not found\",\n" +
                                "    \"errors\": [\n" +
                                "      {\n" +
                                "        \"domain\": \"global\",\n" +
                                "        \"reason\": \"notFound\",\n" +
                                "        \"message\": \"Member not found\"\n" +
                                "      }\n" +
                                "    ]\n" +
                                "  }\n" +
                                "}")));

        // Setup stub for member deletion not found
        GoogleApiMockServer.stubFor(delete(urlPathEqualTo("/admin/directory/v1/groups/group001/members/nonexistent@example.com"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"error\": {\n" +
                                "    \"code\": 404,\n" +
                                "    \"message\": \"Member not found\",\n" +
                                "    \"errors\": [\n" +
                                "      {\n" +
                                "        \"domain\": \"global\",\n" +
                                "        \"reason\": \"notFound\",\n" +
                                "        \"message\": \"Member not found\"\n" +
                                "      }\n" +
                                "    ]\n" +
                                "  }\n" +
                                "}")));

        // Test get member not found
        Uid uid = new Uid("group001/nonexistent@example.com");
        final ConnectorObject[] result = new ConnectorObject[1];

        connectorFacade.search(MEMBER, FilterBuilder.equalTo(uid), obj -> {
            result[0] = obj;
            return true;
        }, null);

        // Should return null/empty result for not found
        assertThat(result[0]).isNull();

        // Test delete member not found - should throw UnknownUidException
        assertThatThrownBy(() ->
                connectorFacade.delete(MEMBER, uid, null))
                .isInstanceOf(UnknownUidException.class);

        // Verify requests were sent
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group001/members/nonexistent@example.com")));
        GoogleApiMockServer.verifyRequest(deleteRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group001/members/nonexistent@example.com")));
    }
}