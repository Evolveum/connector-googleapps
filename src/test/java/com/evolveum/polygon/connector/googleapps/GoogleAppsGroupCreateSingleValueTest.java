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
 * Tests for GoogleAppsConnector Group create operations with single-valued attributes
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsGroupCreateSingleValueTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test group creation with description attribute")
    public void testCreateGroupWithDescription() {
        // Setup mock response for group creation with description
        stubFor(post(urlPathEqualTo("/admin/directory/v1/groups"))
                .withRequestBody(containing("descgroup@example.com"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"group003\",\n" +
                                "  \"etag\": \"\\\"group003-etag\\\"\",\n" +
                                "  \"email\": \"descgroup@example.com\",\n" +
                                "  \"name\": \"Description Test Group\",\n" +
                                "  \"description\": \"Test group with description\"\n" +
                                "}")));

        // Prepare attributes with description
        Set<Attribute> attributes = new HashSet<>();
        attributes.add(AttributeBuilder.build(Name.NAME, "descgroup@example.com")); // email
        attributes.add(AttributeBuilder.build("name", "Description Test Group")); // name
        attributes.add(AttributeBuilder.build(PredefinedAttributes.DESCRIPTION, "Test group with description")); // description

        // Execute group creation
        Uid createdUid = connectorFacade.create(ObjectClass.GROUP, attributes, null);

        // Verify response
        assertThat(createdUid).isNotNull();
        assertThat(createdUid.getUidValue()).isEqualTo("group003");

        // Verify request was sent with correct JSON
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withRequestBody(equalToJson("{\n" +
                        "  \"email\": \"descgroup@example.com\",\n" +
                        "  \"name\": \"Description Test Group\",\n" +
                        "  \"description\": \"Test group with description\"\n" +
                        "}")));
    }

    @Test
    @DisplayName("Test group creation with empty description")
    public void testCreateGroupWithEmptyDescription() {
        // Setup mock response for group creation with empty description
        stubFor(post(urlPathEqualTo("/admin/directory/v1/groups"))
                .withRequestBody(containing("emptydesc@example.com"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"group004\",\n" +
                                "  \"etag\": \"\\\"group004-etag\\\"\",\n" +
                                "  \"email\": \"emptydesc@example.com\",\n" +
                                "  \"name\": \"Empty Description Group\"\n" +
                                "}")));

        // Prepare attributes with empty description
        Set<Attribute> attributes = new HashSet<>();
        attributes.add(AttributeBuilder.build(Name.NAME, "emptydesc@example.com")); // email
        attributes.add(AttributeBuilder.build("name", "Empty Description Group")); // name
        attributes.add(AttributeBuilder.build(PredefinedAttributes.DESCRIPTION, "")); // empty description

        // Execute group creation
        Uid createdUid = connectorFacade.create(ObjectClass.GROUP, attributes, null);

        // Verify response
        assertThat(createdUid).isNotNull();
        assertThat(createdUid.getUidValue()).isEqualTo("group004");

        // Verify request was sent with correct JSON (empty string for description)
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/groups"))
                .withRequestBody(equalToJson("{\n" +
                        "  \"email\": \"emptydesc@example.com\",\n" +
                        "  \"name\": \"Empty Description Group\",\n" +
                        "  \"description\": \"\"\n" +
                        "}")));
    }
}