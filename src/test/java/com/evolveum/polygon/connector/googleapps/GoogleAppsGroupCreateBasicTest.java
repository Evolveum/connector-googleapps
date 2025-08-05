package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GoogleAppsConnector Group create operations
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsGroupCreateBasicTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test group creation with required attributes (email, name)")
    public void testCreateGroup() {
        // Setup mock response for group creation
        stubFor(post(urlPathEqualTo("/admin/directory/v1/groups"))
                .withRequestBody(containing("testgroup@example.com"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"group001\",\n" +
                                "  \"etag\": \"\\\"group001-etag\\\"\",\n" +
                                "  \"email\": \"testgroup@example.com\",\n" +
                                "  \"name\": \"Test Group\"\n" +
                                "}")));

        // Prepare required attributes
        Set<Attribute> attributes = new HashSet<>();
        attributes.add(AttributeBuilder.build(Name.NAME, "testgroup@example.com")); // email
        attributes.add(AttributeBuilder.build("name", "Test Group")); // name

        // Execute group creation
        Uid createdUid = connectorFacade.create(ObjectClass.GROUP, attributes, null);

        // Verify response
        assertThat(createdUid).isNotNull();
        assertThat(createdUid.getUidValue()).isEqualTo("group001");

        // Verify request was sent with correct JSON
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withRequestBody(equalToJson("{\n" +
                        "  \"email\": \"testgroup@example.com\",\n" +
                        "  \"name\": \"Test Group\"\n" +
                        "}")));
    }

    @Test
    @DisplayName("Test group creation with all attributes (email, name, description)")
    public void testCreateGroupWithAllAttributes() {
        // Setup mock response for group creation with all attributes
        stubFor(post(urlPathEqualTo("/admin/directory/v1/groups"))
                .withRequestBody(containing("fullgroup@example.com"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"group002\",\n" +
                                "  \"etag\": \"\\\"group002-etag\\\"\",\n" +
                                "  \"email\": \"fullgroup@example.com\",\n" +
                                "  \"name\": \"Full Test Group\",\n" +
                                "  \"description\": \"Complete test group with all attributes\"\n" +
                                "}")));

        // Prepare all attributes
        Set<Attribute> attributes = new HashSet<>();
        attributes.add(AttributeBuilder.build(Name.NAME, "fullgroup@example.com")); // email
        attributes.add(AttributeBuilder.build("name", "Full Test Group")); // name
        attributes.add(AttributeBuilder.build(PredefinedAttributes.DESCRIPTION, "Complete test group with all attributes")); // description

        // Execute group creation
        Uid createdUid = connectorFacade.create(ObjectClass.GROUP, attributes, null);

        // Verify response
        assertThat(createdUid).isNotNull();
        assertThat(createdUid.getUidValue()).isEqualTo("group002");

        // Verify request was sent with correct JSON
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withRequestBody(equalToJson("{\n" +
                        "  \"email\": \"fullgroup@example.com\",\n" +
                        "  \"name\": \"Full Test Group\",\n" +
                        "  \"description\": \"Complete test group with all attributes\"\n" +
                        "}")));
    }
}