package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GoogleAppsConnector User photo attribute create operations
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsUserCreatePhotoTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Create user with photo")
    public void testCreateUserWithPhoto() {
        // Setup mock for user creation
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"photo001\",\n" +
                                "  \"etag\": \"\\\"photo001-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"photo001@example.com\"\n" +
                                "}")));

        // Setup mock for photo upload
        stubFor(put(urlPathEqualTo("/admin/directory/v1/users/photo001/photos/thumbnail"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"photo001\",\n" +
                                "  \"etag\": \"\\\"photo001-photo-etag\\\"\"\n" +
                                "}")));

        // Prepare attributes with photo data
        Set<Attribute> attributes = new HashSet<>();
        attributes.add(AttributeBuilder.build(Name.NAME, "photo001@example.com"));
        attributes.add(AttributeBuilder.build(OperationalAttributes.PASSWORD_NAME, new GuardedString("password123".toCharArray())));
        attributes.add(AttributeBuilder.build("givenName", "Photo"));
        attributes.add(AttributeBuilder.build("familyName", "User"));

        // Photo data (simple test data)
        byte[] photoData = "test photo data".getBytes();
        attributes.add(AttributeBuilder.build("__PHOTO__", photoData));

        // Execute user creation
        Uid createdUid = connectorFacade.create(ObjectClass.ACCOUNT, attributes, null);

        // Verify response
        assertThat(createdUid).isNotNull();
        assertThat(createdUid.getUidValue()).isEqualTo("photo001");

        // Verify user creation request
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withRequestBody(containing("\"primaryEmail\":\"photo001@example.com\"")));

        // Verify photo upload request
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathEqualTo("/admin/directory/v1/users/photo001/photos/thumbnail"))
                .withRequestBody(containing("\"photoData\"")));
    }
}