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
 * Tests for GoogleAppsConnector User single-valued attribute create operations
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsUserCreateSingleValueTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test create user with basic single-valued attributes")
    public void testCreateUserWithBasicAttributes() {
        // Setup mock response for basic user creation
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"basicuser001\",\n" +
                                "  \"etag\": \"\\\"basicuser001-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"basicuser@example.com\",\n" +
                                "  \"name\": {\n" +
                                "    \"givenName\": \"Basic\",\n" +
                                "    \"familyName\": \"User\"\n" +
                                "  },\n" +
                                "  \"suspended\": false,\n" +
                                "  \"changePasswordAtNextLogin\": true,\n" +
                                "  \"ipWhitelisted\": false,\n" +
                                "  \"orgUnitPath\": \"/TestOU\",\n" +
                                "  \"includeInGlobalAddressList\": true\n" +
                                "}")));

        // Prepare attributes with single-valued fields only
        Set<Attribute> attributes = new HashSet<>();

        // Required attributes
        attributes.add(AttributeBuilder.build(Name.NAME, "basicuser@example.com"));
        attributes.add(AttributeBuilder.build("givenName", "Basic"));
        attributes.add(AttributeBuilder.build("familyName", "User"));
        attributes.add(AttributeBuilder.buildPassword("BasicPassword123".toCharArray()));

        // Optional single-valued attributes
        attributes.add(AttributeBuilder.build(OperationalAttributes.ENABLE_NAME, true));
        attributes.add(AttributeBuilder.build("suspended", false));
        attributes.add(AttributeBuilder.build("changePasswordAtNextLogin", true));
        attributes.add(AttributeBuilder.build("ipWhitelisted", false));
        attributes.add(AttributeBuilder.build("orgUnitPath", "/TestOU"));
        attributes.add(AttributeBuilder.build("includeInGlobalAddressList", true));

        // Execute user creation
        Uid createdUid = connectorFacade.create(ObjectClass.ACCOUNT, attributes, null);

        // Verify response
        assertThat(createdUid).isNotNull();
        assertThat(createdUid.getUidValue()).isEqualTo("basicuser001");

        // Verify request contains all expected single-valued fields
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withRequestBody(containing("\"primaryEmail\":\"basicuser@example.com\""))
                .withRequestBody(containing("\"givenName\":\"Basic\""))
                .withRequestBody(containing("\"familyName\":\"User\""))
                .withRequestBody(containing("\"password\":\"BasicPassword123\""))
                .withRequestBody(containing("\"changePasswordAtNextLogin\":true"))
                .withRequestBody(containing("\"ipWhitelisted\":false"))
                .withRequestBody(containing("\"orgUnitPath\":\"/TestOU\""))
                .withRequestBody(containing("\"includeInGlobalAddressList\":true"))
                .withRequestBody(containing("\"suspended\":false")));
    }
}