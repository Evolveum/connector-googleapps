package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GoogleAppsConnector User __ENABLED__ attribute behavior
 * Note: __ENABLED__ maps to Google's "suspended" field in reverse logic
 * - __ENABLED__ = true means suspended = false (user is active)
 * - __ENABLED__ = false means suspended = true (user is suspended)
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsUserEnabledAttributeTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test create user with __ENABLED__ = false (suspended)")
    public void testCreateDisabledUser() {
        // Setup mock response for disabled user creation
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users"))
                .withRequestBody(containing("\"suspended\":true"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"disabled001\",\n" +
                                "  \"etag\": \"\\\"disabled001-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"disabled@example.com\",\n" +
                                "  \"name\": {\n" +
                                "    \"givenName\": \"Disabled\",\n" +
                                "    \"familyName\": \"User\"\n" +
                                "  },\n" +
                                "  \"suspended\": true\n" +
                                "}")));

        // Prepare attributes for disabled user creation
        Set<Attribute> attributes = new HashSet<>();
        attributes.add(AttributeBuilder.build(Name.NAME, "disabled@example.com"));
        attributes.add(AttributeBuilder.build("givenName", "Disabled"));
        attributes.add(AttributeBuilder.build("familyName", "User"));
        attributes.add(AttributeBuilder.buildPassword("TestPassword123".toCharArray()));
        attributes.add(AttributeBuilder.build(OperationalAttributes.ENABLE_NAME, false)); // __ENABLED__ = false

        // Execute user creation
        Uid createdUid = connectorFacade.create(ObjectClass.ACCOUNT, attributes, null);

        // Verify response
        assertThat(createdUid).isNotNull();
        assertThat(createdUid.getUidValue()).isEqualTo("disabled001");

        // Verify request JSON contains suspended = true when __ENABLED__ = false
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withRequestBody(equalToJson("{\n" +
                        "  \"primaryEmail\": \"disabled@example.com\",\n" +
                        "  \"name\": {\n" +
                        "    \"givenName\": \"Disabled\",\n" +
                        "    \"familyName\": \"User\"\n" +
                        "  },\n" +
                        "  \"password\": \"TestPassword123\",\n" +
                        "  \"suspended\": true\n" +
                        "}")));
    }

    @Test
    @DisplayName("Test create user with __ENABLED__ = true (not suspended)")
    public void testCreateEnabledUser() {
        // Setup mock response for enabled user creation
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"enabled001\",\n" +
                                "  \"etag\": \"\\\"enabled001-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"enabled@example.com\",\n" +
                                "  \"name\": {\n" +
                                "    \"givenName\": \"Enabled\",\n" +
                                "    \"familyName\": \"User\"\n" +
                                "  },\n" +
                                "  \"suspended\": false\n" +
                                "}")));

        // Prepare attributes for enabled user creation
        Set<Attribute> attributes = new HashSet<>();
        attributes.add(AttributeBuilder.build(Name.NAME, "enabled@example.com"));
        attributes.add(AttributeBuilder.build("givenName", "Enabled"));
        attributes.add(AttributeBuilder.build("familyName", "User"));
        attributes.add(AttributeBuilder.buildPassword("TestPassword123".toCharArray()));
        attributes.add(AttributeBuilder.build(OperationalAttributes.ENABLE_NAME, true)); // __ENABLED__ = true

        // Execute user creation
        Uid createdUid = connectorFacade.create(ObjectClass.ACCOUNT, attributes, null);

        // Verify response
        assertThat(createdUid).isNotNull();
        assertThat(createdUid.getUidValue()).isEqualTo("enabled001");

        // Verify request JSON does NOT contain suspended field when __ENABLED__ = true
        // (Google API defaults to suspended = false when not specified)
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withRequestBody(not(containing("suspended"))));  // Verify suspended field is NOT present
    }

    @Test
    @DisplayName("Test update user to disable (__ENABLED__ = false)")
    public void testUpdateUserToDisable() {
        // Setup mock response for disable update
        stubFor(put(urlPathMatching("/admin/directory/v1/users/test001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"test001\",\n" +
                                "  \"etag\": \"\\\"test001-disabled-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"test001@example.com\",\n" +
                                "  \"suspended\": true\n" +
                                "}")));

        // Prepare attributes for disable update
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build(OperationalAttributes.ENABLE_NAME, false));

        // Execute update to disable user
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("test001"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify request JSON contains suspended = true when __ENABLED__ = false
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathMatching("/admin/directory/v1/users/test001"))
                .withHeader("Content-Type", matching("application/json.*"))
                .withRequestBody(equalToJson("{\n" +
                        "  \"suspended\": true\n" +
                        "}")));
    }

    @Test
    @DisplayName("Test update user to enable (__ENABLED__ = true)")
    public void testUpdateUserToEnable() {
        // Setup mock response for enable update
        stubFor(put(urlPathMatching("/admin/directory/v1/users/test001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"test001\",\n" +
                                "  \"etag\": \"\\\"test001-enabled-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"test001@example.com\",\n" +
                                "  \"suspended\": false\n" +
                                "}")));

        // Prepare attributes for enable update
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build(OperationalAttributes.ENABLE_NAME, true));

        // Execute update to enable user
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("test001"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify request JSON contains suspended = false when __ENABLED__ = true
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathMatching("/admin/directory/v1/users/test001"))
                .withHeader("Content-Type", matching("application/json.*"))
                .withRequestBody(equalToJson("{\n" +
                        "  \"suspended\": false\n" +
                        "}")));
    }

    @Test
    @DisplayName("Test read user __ENABLED__ attribute from suspended field")
    public void testReadEnabledAttributeFromSuspended() {
        // Setup mock response for suspended user
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users/suspended001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"suspended001\",\n" +
                                "  \"etag\": \"\\\"suspended001-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"suspended001@example.com\",\n" +
                                "  \"name\": {\n" +
                                "    \"givenName\": \"Suspended\",\n" +
                                "    \"familyName\": \"User\"\n" +
                                "  },\n" +
                                "  \"suspended\": true\n" +
                                "}")));

        List<ConnectorObject> results = new ArrayList<>();

        // Create filter to get specific user
        Filter filter = FilterBuilder.equalTo(new Uid("suspended001"));

        // Execute search with __ENABLED__ attribute requested
        OperationOptions options = new OperationOptionsBuilder()
                .setAttributesToGet(OperationalAttributes.ENABLE_NAME, "givenName", "familyName")
                .build();

        connectorFacade.search(ObjectClass.ACCOUNT, filter, results::add, options);

        // Verify results
        assertThat(results).hasSize(1);
        ConnectorObject user = results.get(0);

        // Verify __ENABLED__ = false when suspended = true
        Attribute enabledAttr = user.getAttributeByName(OperationalAttributes.ENABLE_NAME);
        assertThat(enabledAttr).isNotNull();
        assertThat(AttributeUtil.getBooleanValue(enabledAttr)).isFalse();

        // Verify request was sent
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users/suspended001")));
    }

    @Test
    @DisplayName("Test read user __ENABLED__ attribute when not suspended")
    public void testReadEnabledAttributeWhenNotSuspended() {
        // Setup mock response for active (not suspended) user
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users/active001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"active001\",\n" +
                                "  \"etag\": \"\\\"active001-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"active001@example.com\",\n" +
                                "  \"name\": {\n" +
                                "    \"givenName\": \"Active\",\n" +
                                "    \"familyName\": \"User\"\n" +
                                "  },\n" +
                                "  \"suspended\": false\n" +
                                "}")));

        List<ConnectorObject> results = new ArrayList<>();

        // Create filter to get specific user
        Filter filter = FilterBuilder.equalTo(new Uid("active001"));

        // Execute search with __ENABLED__ attribute requested
        OperationOptions options = new OperationOptionsBuilder()
                .setAttributesToGet(OperationalAttributes.ENABLE_NAME, "givenName", "familyName")
                .build();

        connectorFacade.search(ObjectClass.ACCOUNT, filter, results::add, options);

        // Verify results
        assertThat(results).hasSize(1);
        ConnectorObject user = results.get(0);

        // Verify __ENABLED__ = true when suspended = false
        Attribute enabledAttr = user.getAttributeByName(OperationalAttributes.ENABLE_NAME);
        assertThat(enabledAttr).isNotNull();
        assertThat(AttributeUtil.getBooleanValue(enabledAttr)).isTrue();

        // Verify request was sent
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users/active001")));
    }

    @Test
    @DisplayName("Test interaction between __ENABLED__ and suspended attributes")
    public void testEnabledSuspendedAttributeInteraction() {
        // Setup mock response for update with both __ENABLED__ and suspended
        stubFor(put(urlPathMatching("/admin/directory/v1/users/test001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"test001\",\n" +
                                "  \"etag\": \"\\\"test001-interaction-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"test001@example.com\",\n" +
                                "  \"suspended\": true\n" +
                                "}")));

        // Prepare attributes with both __ENABLED__ and suspended
        // Note: When both are provided, suspended attribute should take precedence
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build(OperationalAttributes.ENABLE_NAME, true)); // This says enable
        modifications.add(AttributeDeltaBuilder.build("suspended", true)); // This says suspend

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("test001"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify that suspended attribute takes precedence over __ENABLED__
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathMatching("/admin/directory/v1/users/test001"))
                .withHeader("Content-Type", matching("application/json.*"))
                .withRequestBody(equalToJson("{\n" +
                        "  \"suspended\": true\n" +
                        "}")));
    }
}