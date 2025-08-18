package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GoogleAppsConnector User basic Create operations
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsUserCreateBasicTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test user creation with required attributes (primaryEmail, password, givenName, familyName)")
    public void testCreateUserWithRequiredAttributes() {
        // Setup mock response for user creation with required attributes
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users"))
                .withRequestBody(containing("required@example.com"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"required001\",\n" +
                                "  \"etag\": \"\\\"required001-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"required@example.com\",\n" +
                                "  \"name\": {\n" +
                                "    \"givenName\": \"Required\",\n" +
                                "    \"familyName\": \"User\"\n" +
                                "  }\n" +
                                "}")));

        // Prepare all required attributes per connector schema
        Set<Attribute> attributes = new HashSet<>();
        attributes.add(AttributeBuilder.build(Name.NAME, "required@example.com")); // primaryEmail
        attributes.add(AttributeBuilder.buildPassword("TestPassword123".toCharArray())); // password
        attributes.add(AttributeBuilder.build("givenName", "Required")); // Required per connector
        attributes.add(AttributeBuilder.build("familyName", "User")); // Required per connector

        // Execute user creation with required attributes
        Uid createdUid = connectorFacade.create(ObjectClass.ACCOUNT, attributes, null);

        // Verify response was as expected
        assertThat(createdUid).isNotNull();
        assertThat(createdUid.getUidValue()).isEqualTo("required001");

        // Verify request JSON contains all required fields
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withRequestBody(equalToJson("{\n" +
                        "  \"primaryEmail\": \"required@example.com\",\n" +
                        "  \"password\": \"TestPassword123\",\n" +
                        "  \"name\": {\n" +
                        "    \"givenName\": \"Required\",\n" +
                        "    \"familyName\": \"User\"\n" +
                        "  }\n" +
                        "}")));
    }

    @Test
    @DisplayName("Test create user with all possible creatable attributes")
    public void testCreateUserWithAllAttributes() {
        // Setup mock response for full user creation
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"fulluser001\",\n" +
                                "  \"etag\": \"\\\"fulluser001-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"fulluser@example.com\",\n" +
                                "  \"name\": {\n" +
                                "    \"givenName\": \"Full\",\n" +
                                "    \"familyName\": \"User\"\n" +
                                "  },\n" +
                                "  \"suspended\": false,\n" +
                                "  \"changePasswordAtNextLogin\": true,\n" +
                                "  \"ipWhitelisted\": false,\n" +
                                "  \"orgUnitPath\": \"/TestOU\",\n" +
                                "  \"includeInGlobalAddressList\": true\n" +
                                "}")));

        // Prepare attributes with all creatable fields
        Set<Attribute> attributes = new HashSet<>();

        // Required attributes
        attributes.add(AttributeBuilder.build(Name.NAME, "fulluser@example.com"));
        attributes.add(AttributeBuilder.build("givenName", "Full"));
        attributes.add(AttributeBuilder.build("familyName", "User"));
        attributes.add(AttributeBuilder.buildPassword("ComplexPassword123".toCharArray()));

        // Optional basic attributes
        attributes.add(AttributeBuilder.build(OperationalAttributes.ENABLE_NAME, true));
        attributes.add(AttributeBuilder.build("suspended", false));
        attributes.add(AttributeBuilder.build("changePasswordAtNextLogin", true));
        attributes.add(AttributeBuilder.build("ipWhitelisted", false));
        attributes.add(AttributeBuilder.build("orgUnitPath", "/TestOU"));
        attributes.add(AttributeBuilder.build("includeInGlobalAddressList", true));

        // Multi-valued structured attributes
        List<String> emails = java.util.Arrays.asList(
                "{\"address\":\"alt@example.com\",\"type\":\"work\",\"primary\":false}");
        attributes.add(AttributeBuilder.build("emails", emails));

        List<String> phones = java.util.Arrays.asList(
                "{\"value\":\"+1-555-123-4567\",\"type\":\"work\",\"primary\":true}");
        attributes.add(AttributeBuilder.build("phones", phones));

        List<String> addresses = java.util.Arrays.asList(
                "{\"type\":\"work\",\"formatted\":\"123 Work St, City, State 12345\",\"primary\":true}");
        attributes.add(AttributeBuilder.build("addresses", addresses));

        List<String> organizations = java.util.Arrays.asList(
                "{\"name\":\"Test Company\",\"title\":\"Developer\",\"primary\":true,\"type\":\"work\"}");
        attributes.add(AttributeBuilder.build("organizations", organizations));

        List<String> ims = java.util.Arrays.asList(
                "{\"im\":\"user@example.org\",\"protocol\":\"jabber\",\"type\":\"work\",\"primary\":true}");
        attributes.add(AttributeBuilder.build("ims", ims));

        List<String> externalIds = java.util.Arrays.asList(
                "{\"value\":\"EMP123\",\"type\":\"organization\"}");
        attributes.add(AttributeBuilder.build("externalIds", externalIds));

        List<String> relations = java.util.Arrays.asList(
                "{\"value\":\"manager@example.com\",\"type\":\"manager\"}");
        attributes.add(AttributeBuilder.build("relations", relations));

        // Execute user creation
        Uid createdUid = connectorFacade.create(ObjectClass.ACCOUNT, attributes, null);

        // Verify response
        assertThat(createdUid).isNotNull();
        assertThat(createdUid.getUidValue()).isEqualTo("fulluser001");

        // Verify request contains all expected fields with complete JSON objects
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withRequestBody(containing("\"primaryEmail\":\"fulluser@example.com\""))
                .withRequestBody(containing("\"givenName\":\"Full\""))
                .withRequestBody(containing("\"familyName\":\"User\""))
                .withRequestBody(containing("\"password\":\"ComplexPassword123\""))
                .withRequestBody(containing("\"changePasswordAtNextLogin\":true"))
                .withRequestBody(containing("\"ipWhitelisted\":false"))
                .withRequestBody(containing("\"orgUnitPath\":\"/TestOU\""))
                .withRequestBody(containing("\"includeInGlobalAddressList\":true"))
                .withRequestBody(containing("\"suspended\":false"))
                .withRequestBody(containing("\"emails\":[{\"address\":\"alt@example.com\",\"type\":\"work\",\"primary\":\"false\"}]"))
                .withRequestBody(containing("\"phones\":[{\"value\":\"+1-555-123-4567\",\"type\":\"work\",\"primary\":\"true\"}]"))
                .withRequestBody(containing("\"addresses\":[{\"type\":\"work\",\"formatted\":\"123 Work St, City, State 12345\",\"primary\":\"true\"}]"))
                .withRequestBody(containing("\"organizations\":[{\"name\":\"Test Company\",\"title\":\"Developer\",\"primary\":\"true\",\"type\":\"work\"}]"))
                .withRequestBody(containing("\"ims\":[{\"im\":\"user@example.org\",\"protocol\":\"jabber\",\"type\":\"work\",\"primary\":\"true\"}]"))
                .withRequestBody(containing("\"externalIds\":[{\"value\":\"EMP123\",\"type\":\"organization\"}]"))
                .withRequestBody(containing("\"relations\":[{\"value\":\"manager@example.com\",\"type\":\"manager\"}]")));
    }
}