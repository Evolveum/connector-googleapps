package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GoogleAppsConnector User aliases attribute create operations
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsUserCreateAliasesTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Create user with single alias")
    public void testCreateUserWithSingleAlias() {
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

        // Setup mock for alias creation
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users/user001/aliases"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"alias\": \"alias1@example.com\",\n" +
                                "  \"id\": \"alias001\",\n" +
                                "  \"etag\": \"\\\"alias001-etag\\\"\"\n" +
                                "}")));

        // Prepare attributes
        Set<Attribute> attributes = new HashSet<>();
        attributes.add(AttributeBuilder.build(Name.NAME, "user001@example.com"));
        attributes.add(AttributeBuilder.build(OperationalAttributes.PASSWORD_NAME, new GuardedString("password123".toCharArray())));
        attributes.add(AttributeBuilder.build("givenName", "Test"));
        attributes.add(AttributeBuilder.build("familyName", "User"));
        attributes.add(AttributeBuilder.build("aliases", Arrays.asList("alias1@example.com")));

        // Execute user creation
        Uid createdUid = connectorFacade.create(ObjectClass.ACCOUNT, attributes, null);

        // Verify response
        assertThat(createdUid).isNotNull();
        assertThat(createdUid.getUidValue()).isEqualTo("user001");

        // Verify user creation request
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withRequestBody(containing("\"primaryEmail\":\"user001@example.com\""))
                .withRequestBody(containing("\"givenName\":\"Test\""))
                .withRequestBody(containing("\"familyName\":\"User\"")));

        // Verify alias creation request
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users/user001/aliases"))
                .withRequestBody(containing("\"alias\":\"alias1@example.com\"")));
    }

    @Test
    @DisplayName("Create user with multiple aliases")
    public void testCreateUserWithMultipleAliases() {
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

        // Setup mocks for multiple alias creations
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users/user002/aliases"))
                .withRequestBody(containing("alias1@example.com"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"alias\": \"alias1@example.com\",\n" +
                                "  \"id\": \"alias001\",\n" +
                                "  \"etag\": \"\\\"alias001-etag\\\"\"\n" +
                                "}")));

        stubFor(post(urlPathEqualTo("/admin/directory/v1/users/user002/aliases"))
                .withRequestBody(containing("alias2@example.com"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"alias\": \"alias2@example.com\",\n" +
                                "  \"id\": \"alias002\",\n" +
                                "  \"etag\": \"\\\"alias002-etag\\\"\"\n" +
                                "}")));

        // Prepare attributes
        Set<Attribute> attributes = new HashSet<>();
        attributes.add(AttributeBuilder.build(Name.NAME, "user002@example.com"));
        attributes.add(AttributeBuilder.build(OperationalAttributes.PASSWORD_NAME, new GuardedString("password123".toCharArray())));
        attributes.add(AttributeBuilder.build("givenName", "Test"));
        attributes.add(AttributeBuilder.build("familyName", "User"));
        attributes.add(AttributeBuilder.build("aliases", Arrays.asList("alias1@example.com", "alias2@example.com")));

        // Execute user creation
        Uid createdUid = connectorFacade.create(ObjectClass.ACCOUNT, attributes, null);

        // Verify response
        assertThat(createdUid).isNotNull();
        assertThat(createdUid.getUidValue()).isEqualTo("user002");

        // Verify both alias creation requests were made
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users/user002/aliases"))
                .withRequestBody(containing("\"alias\":\"alias1@example.com\"")));
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users/user002/aliases"))
                .withRequestBody(containing("\"alias\":\"alias2@example.com\"")));
    }
}