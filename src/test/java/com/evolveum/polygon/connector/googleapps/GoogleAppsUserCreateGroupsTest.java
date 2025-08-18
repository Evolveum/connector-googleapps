package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GoogleAppsConnector User groups attribute create operations
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsUserCreateGroupsTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Create user with single group")
    public void testCreateUserWithSingleGroup() {
        // Setup mock for user creation
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"user001\",\n" +
                                "  \"etag\": \"\\\"user001-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"user001@example.com\"\n" +
                                "}")));

        // Setup mock for group membership addition
        stubFor(post(urlPathEqualTo("/admin/directory/v1/groups/group001@example.com/members"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"user001\",\n" +
                                "  \"email\": \"user001@example.com\",\n" +
                                "  \"role\": \"MEMBER\",\n" +
                                "  \"etag\": \"\\\"member001-etag\\\"\"\n" +
                                "}")));

        // Prepare attributes
        Set<Attribute> attributes = new HashSet<>();
        attributes.add(AttributeBuilder.build(Name.NAME, "user001@example.com"));
        attributes.add(AttributeBuilder.build(OperationalAttributes.PASSWORD_NAME, new GuardedString("password123".toCharArray())));
        attributes.add(AttributeBuilder.build("givenName", "Test"));
        attributes.add(AttributeBuilder.build("familyName", "User"));
        attributes.add(AttributeBuilder.build("__GROUPS__", "group001@example.com"));

        // Create user
        Uid createdUid = connectorFacade.create(ObjectClass.ACCOUNT, attributes, null);

        // Verify user creation
        assertThat(createdUid).isNotNull();
        assertThat(createdUid.getUidValue()).isEqualTo("user001");

        // Verify API calls
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users")));
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group001@example.com/members"))
                .withRequestBody(containing("\"id\":\"user001\""))
                .withRequestBody(containing("\"role\":\"MEMBER\"")));
    }

    @Test
    @DisplayName("Create user with multiple groups")
    public void testCreateUserWithMultipleGroups() {
        // Setup mock for user creation
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"user002\",\n" +
                                "  \"etag\": \"\\\"user002-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"user002@example.com\"\n" +
                                "}")));

        // Setup mock for first group membership addition
        stubFor(post(urlPathEqualTo("/admin/directory/v1/groups/group001@example.com/members"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"user002\",\n" +
                                "  \"email\": \"user002@example.com\",\n" +
                                "  \"role\": \"MEMBER\",\n" +
                                "  \"etag\": \"\\\"member002-etag\\\"\"\n" +
                                "}")));

        // Setup mock for second group membership addition
        stubFor(post(urlPathEqualTo("/admin/directory/v1/groups/group002@example.com/members"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"user002\",\n" +
                                "  \"email\": \"user002@example.com\",\n" +
                                "  \"role\": \"MEMBER\",\n" +
                                "  \"etag\": \"\\\"member003-etag\\\"\"\n" +
                                "}")));

        // Prepare attributes
        Set<Attribute> attributes = new HashSet<>();
        attributes.add(AttributeBuilder.build(Name.NAME, "user002@example.com"));
        attributes.add(AttributeBuilder.build(OperationalAttributes.PASSWORD_NAME, new GuardedString("password123".toCharArray())));
        attributes.add(AttributeBuilder.build("givenName", "Test"));
        attributes.add(AttributeBuilder.build("familyName", "User"));
        attributes.add(AttributeBuilder.build("__GROUPS__", Arrays.asList("group001@example.com", "group002@example.com")));

        // Create user
        Uid createdUid = connectorFacade.create(ObjectClass.ACCOUNT, attributes, null);

        // Verify user creation
        assertThat(createdUid).isNotNull();
        assertThat(createdUid.getUidValue()).isEqualTo("user002");

        // Verify API calls
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users")));
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group001@example.com/members")));
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group002@example.com/members")));
    }

    @Test
    @DisplayName("Create user with empty groups list - no group operations")
    public void testCreateUserWithEmptyGroups() {
        // Setup mock for user creation
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"user003\",\n" +
                                "  \"etag\": \"\\\"user003-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"user003@example.com\"\n" +
                                "}")));

        // Prepare attributes
        Set<Attribute> attributes = new HashSet<>();
        attributes.add(AttributeBuilder.build(Name.NAME, "user003@example.com"));
        attributes.add(AttributeBuilder.build(OperationalAttributes.PASSWORD_NAME, new GuardedString("password123".toCharArray())));
        attributes.add(AttributeBuilder.build("givenName", "Test"));
        attributes.add(AttributeBuilder.build("familyName", "User"));
        attributes.add(AttributeBuilder.build("__GROUPS__", Collections.emptyList()));

        // Create user
        Uid createdUid = connectorFacade.create(ObjectClass.ACCOUNT, attributes, null);

        // Verify user creation
        assertThat(createdUid).isNotNull();
        assertThat(createdUid.getUidValue()).isEqualTo("user003");

        // Verify user creation API call
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users")));

        // Verify no group membership operations were called
        GoogleApiMockServer.verify(0, postRequestedFor(urlPathMatching("/admin/directory/v1/groups/.*/members")));
    }
}