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
 * Tests for GoogleAppsConnector User isAdmin attribute create operations
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsUserCreateIsAdminTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Create user with isAdmin=true")
    public void testCreateUserWithIsAdminTrue() {
        // Setup mock for user creation
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"admin001\",\n" +
                                "  \"etag\": \"\\\"admin001-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"admin001@example.com\"\n" +
                                "}")));

        // Setup mock for makeAdmin API call
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users/admin001/makeAdmin"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"status\": \"adminUserMade\"\n" +
                                "}")));

        // Prepare attributes
        Set<Attribute> attributes = new HashSet<>();
        attributes.add(AttributeBuilder.build(Name.NAME, "admin001@example.com"));
        attributes.add(AttributeBuilder.build(OperationalAttributes.PASSWORD_NAME, new GuardedString("password123".toCharArray())));
        attributes.add(AttributeBuilder.build("givenName", "Admin"));
        attributes.add(AttributeBuilder.build("familyName", "User"));
        attributes.add(AttributeBuilder.build("isAdmin", true));

        // Execute user creation
        Uid createdUid = connectorFacade.create(ObjectClass.ACCOUNT, attributes, null);

        // Verify response
        assertThat(createdUid).isNotNull();
        assertThat(createdUid.getUidValue()).isEqualTo("admin001");

        // Verify user creation request
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withRequestBody(containing("\"primaryEmail\":\"admin001@example.com\"")));

        // Verify makeAdmin request with status=true
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users/admin001/makeAdmin"))
                .withRequestBody(containing("\"status\":true")));
    }

    @Test
    @DisplayName("Create user with isAdmin=false")
    public void testCreateUserWithIsAdminFalse() {
        // Setup mock for user creation
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"regular001\",\n" +
                                "  \"etag\": \"\\\"regular001-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"regular001@example.com\"\n" +
                                "}")));

        // Prepare attributes - isAdmin=false
        Set<Attribute> attributes = new HashSet<>();
        attributes.add(AttributeBuilder.build(Name.NAME, "regular001@example.com"));
        attributes.add(AttributeBuilder.build(OperationalAttributes.PASSWORD_NAME, new GuardedString("password123".toCharArray())));
        attributes.add(AttributeBuilder.build("givenName", "Regular"));
        attributes.add(AttributeBuilder.build("familyName", "User"));
        attributes.add(AttributeBuilder.build("isAdmin", false));

        // Execute user creation
        Uid createdUid = connectorFacade.create(ObjectClass.ACCOUNT, attributes, null);

        // Verify response
        assertThat(createdUid).isNotNull();
        assertThat(createdUid.getUidValue()).isEqualTo("regular001");

        // Verify user creation request
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withRequestBody(containing("\"primaryEmail\":\"regular001@example.com\"")));

        // Verify makeAdmin was NOT called for isAdmin=false
        GoogleApiMockServer.verify(0, postRequestedFor(urlPathEqualTo("/admin/directory/v1/users/regular001/makeAdmin")));
    }

    @Test
    @DisplayName("Create user with all special attributes including isAdmin")
    public void testCreateUserWithAllSpecialAttributesIncludingAdmin() {
        // Setup mock for user creation
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"special001\",\n" +
                                "  \"etag\": \"\\\"special001-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"special001@example.com\"\n" +
                                "}")));

        // Setup mock for alias creation
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users/special001/aliases"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"alias\": \"special.alias@example.com\",\n" +
                                "  \"id\": \"alias-special001\",\n" +
                                "  \"etag\": \"\\\"alias-special001-etag\\\"\"\n" +
                                "}")));

        // Setup mock for photo upload
        stubFor(put(urlPathEqualTo("/admin/directory/v1/users/special001/photos/thumbnail"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"special001\",\n" +
                                "  \"etag\": \"\\\"special001-photo-etag\\\"\"\n" +
                                "}")));

        // Setup mock for makeAdmin API call
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users/special001/makeAdmin"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"status\": \"adminUserMade\"\n" +
                                "}")));

        // Prepare attributes with all special attributes
        Set<Attribute> attributes = new HashSet<>();
        attributes.add(AttributeBuilder.build(Name.NAME, "special001@example.com"));
        attributes.add(AttributeBuilder.build(OperationalAttributes.PASSWORD_NAME, new GuardedString("password123".toCharArray())));
        attributes.add(AttributeBuilder.build("givenName", "Special"));
        attributes.add(AttributeBuilder.build("familyName", "AdminUser"));
        attributes.add(AttributeBuilder.build("aliases", Arrays.asList("special.alias@example.com")));
        attributes.add(AttributeBuilder.build("__PHOTO__", "special photo data".getBytes()));
        attributes.add(AttributeBuilder.build("isAdmin", true));

        // Execute user creation
        Uid createdUid = connectorFacade.create(ObjectClass.ACCOUNT, attributes, null);

        // Verify response
        assertThat(createdUid).isNotNull();
        assertThat(createdUid.getUidValue()).isEqualTo("special001");

        // Verify all special attribute API calls were made
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users/special001/aliases")));
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathEqualTo("/admin/directory/v1/users/special001/photos/thumbnail")));
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users/special001/makeAdmin")));
    }
}