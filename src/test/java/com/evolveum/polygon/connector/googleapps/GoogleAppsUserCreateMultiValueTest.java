package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GoogleAppsConnector User multi-valued attribute create operations
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsUserCreateMultiValueTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test create user with all multi-valued attributes")
    public void testCreateUserWithAllMultiValuedAttributes() {
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
                                "  }\n" +
                                "}")));

        // Prepare attributes with all creatable fields
        Set<Attribute> attributes = new HashSet<>();

        // Required attributes
        attributes.add(AttributeBuilder.build(Name.NAME, "fulluser@example.com"));
        attributes.add(AttributeBuilder.build("givenName", "Full"));
        attributes.add(AttributeBuilder.build("familyName", "User"));
        attributes.add(AttributeBuilder.buildPassword("ComplexPassword123".toCharArray()));

        // Multi-valued structured attributes
        List<String> emails = Arrays.asList(
                "{\"address\":\"alt@example.com\",\"type\":\"work\",\"primary\":false}");
        attributes.add(AttributeBuilder.build("emails", emails));

        List<String> phones = Arrays.asList(
                "{\"value\":\"+1-555-123-4567\",\"type\":\"work\",\"primary\":true}");
        attributes.add(AttributeBuilder.build("phones", phones));

        List<String> addresses = Arrays.asList(
                "{\"type\":\"work\",\"formatted\":\"123 Work St, City, State 12345\",\"primary\":true}");
        attributes.add(AttributeBuilder.build("addresses", addresses));

        List<String> organizations = Arrays.asList(
                "{\"name\":\"Test Company\",\"title\":\"Developer\",\"primary\":true,\"type\":\"work\"}");
        attributes.add(AttributeBuilder.build("organizations", organizations));

        List<String> ims = Arrays.asList(
                "{\"im\":\"user@example.org\",\"protocol\":\"jabber\",\"type\":\"work\",\"primary\":true}");
        attributes.add(AttributeBuilder.build("ims", ims));

        List<String> externalIds = Arrays.asList(
                "{\"value\":\"EMP123\",\"type\":\"organization\"}");
        attributes.add(AttributeBuilder.build("externalIds", externalIds));

        List<String> relations = Arrays.asList(
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
                .withRequestBody(containing("\"emails\":[{\"address\":\"alt@example.com\",\"type\":\"work\",\"primary\":\"false\"}]"))
                .withRequestBody(containing("\"phones\":[{\"value\":\"+1-555-123-4567\",\"type\":\"work\",\"primary\":\"true\"}]"))
                .withRequestBody(containing("\"addresses\":[{\"type\":\"work\",\"formatted\":\"123 Work St, City, State 12345\",\"primary\":\"true\"}]"))
                .withRequestBody(containing("\"organizations\":[{\"name\":\"Test Company\",\"title\":\"Developer\",\"primary\":\"true\",\"type\":\"work\"}]"))
                .withRequestBody(containing("\"ims\":[{\"im\":\"user@example.org\",\"protocol\":\"jabber\",\"type\":\"work\",\"primary\":\"true\"}]"))
                .withRequestBody(containing("\"externalIds\":[{\"value\":\"EMP123\",\"type\":\"organization\"}]"))
                .withRequestBody(containing("\"relations\":[{\"value\":\"manager@example.com\",\"type\":\"manager\"}]")));
    }

    @Test
    @DisplayName("Test multi-valued attributes with multiple values")
    public void testMultiValuedAttributesWithMultipleValues() {
        // Setup mock response for multi-valued attributes
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"multivalue001\",\n" +
                                "  \"etag\": \"\\\"multivalue001-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"multivalue@example.com\"\n" +
                                "}")));

        // Prepare attributes with multiple values for multi-valued fields
        Set<Attribute> attributes = new HashSet<>();

        // Required attributes
        attributes.add(AttributeBuilder.build(Name.NAME, "multivalue@example.com"));
        attributes.add(AttributeBuilder.build("givenName", "MultiValue"));
        attributes.add(AttributeBuilder.build("familyName", "User"));
        attributes.add(AttributeBuilder.buildPassword("TestPassword123".toCharArray()));

        // Multiple emails
        List<String> multipleEmails = Arrays.asList(
                "{\"address\":\"work@example.com\",\"type\":\"work\",\"primary\":false}",
                "{\"address\":\"home@example.com\",\"type\":\"home\",\"primary\":false}",
                "{\"address\":\"other@example.com\",\"type\":\"other\",\"primary\":false}");
        attributes.add(AttributeBuilder.build("emails", multipleEmails));

        // Multiple phones
        List<String> multiplePhones = Arrays.asList(
                "{\"value\":\"+1-555-111-1111\",\"type\":\"work\",\"primary\":true}",
                "{\"value\":\"+1-555-222-2222\",\"type\":\"home\",\"primary\":false}",
                "{\"value\":\"+1-555-333-3333\",\"type\":\"mobile\",\"primary\":false}");
        attributes.add(AttributeBuilder.build("phones", multiplePhones));

        // Multiple organizations
        List<String> multipleOrganizations = Arrays.asList(
                "{\"name\":\"Current Company\",\"title\":\"Senior Developer\",\"primary\":true,\"type\":\"work\"}",
                "{\"name\":\"Previous Company\",\"title\":\"Developer\",\"primary\":false,\"type\":\"work\"}");
        attributes.add(AttributeBuilder.build("organizations", multipleOrganizations));

        // Execute user creation
        Uid createdUid = connectorFacade.create(ObjectClass.ACCOUNT, attributes, null);

        // Verify response
        assertThat(createdUid).isNotNull();
        assertThat(createdUid.getUidValue()).isEqualTo("multivalue001");

        // Verify request contains all multiple values (individual elements since array order may vary)
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                // Verify individual email entries exist
                .withRequestBody(containing("{\"address\":\"work@example.com\",\"type\":\"work\",\"primary\":\"false\"}"))
                .withRequestBody(containing("{\"address\":\"home@example.com\",\"type\":\"home\",\"primary\":\"false\"}"))
                .withRequestBody(containing("{\"address\":\"other@example.com\",\"type\":\"other\",\"primary\":\"false\"}"))
                // Verify individual phone entries exist
                .withRequestBody(containing("{\"value\":\"+1-555-111-1111\",\"type\":\"work\",\"primary\":\"true\"}"))
                .withRequestBody(containing("{\"value\":\"+1-555-222-2222\",\"type\":\"home\",\"primary\":\"false\"}"))
                .withRequestBody(containing("{\"value\":\"+1-555-333-3333\",\"type\":\"mobile\",\"primary\":\"false\"}"))
                // Verify individual organization entries exist
                .withRequestBody(containing("{\"name\":\"Current Company\",\"title\":\"Senior Developer\",\"primary\":\"true\",\"type\":\"work\"}"))
                .withRequestBody(containing("{\"name\":\"Previous Company\",\"title\":\"Developer\",\"primary\":\"false\",\"type\":\"work\"}")));
    }
}