package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GoogleAppsConnector User single-valued attribute update operations
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsUserUpdateSingleValueTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test user update with basic attributes")
    public void testUpdateUser() {
        // Setup mock response for update
        stubFor(put(urlPathMatching("/admin/directory/v1/users/test001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"test001\",\n" +
                                "  \"etag\": \"\\\"test001-updated-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"test001@example.com\",\n" +
                                "  \"name\": {\n" +
                                "    \"givenName\": \"Updated\",\n" +
                                "    \"familyName\": \"User\"\n" +
                                "  }\n" +
                                "}")));

        // Prepare attributes for update
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build("givenName", "Updated"));
        modifications.add(AttributeDeltaBuilder.build("familyName", "User"));

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("test001"), modifications, null);

        // Verify results (return value is usually null or empty set)
        assertThat(result).isNotNull();

        // Verify request JSON was as expected
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathMatching("/admin/directory/v1/users/test001"))
                .withHeader("Content-Type", matching("application/json.*"))
                .withRequestBody(equalToJson("{\n" +
                        "  \"name\": {\n" +
                        "    \"givenName\": \"Updated\",\n" +
                        "    \"familyName\": \"User\"\n" +
                        "  }\n" +
                        "}")));
    }

    @Test
    @DisplayName("Test update user with basic single-valued attributes")
    public void testUpdateUserWithBasicAttributes() {
        // Setup mock response for basic user update
        stubFor(put(urlPathMatching("/admin/directory/v1/users/test001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"test001\",\n" +
                                "  \"etag\": \"\\\"test001-basic-updated-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"test001@example.com\",\n" +
                                "  \"name\": {\n" +
                                "    \"givenName\": \"Updated\",\n" +
                                "    \"familyName\": \"Full\"\n" +
                                "  }\n" +
                                "}")));

        // Prepare basic attributes for update
        Set<AttributeDelta> modifications = new HashSet<>();

        // Basic attributes
        modifications.add(AttributeDeltaBuilder.build("givenName", "Updated"));
        modifications.add(AttributeDeltaBuilder.build("familyName", "Full"));
        modifications.add(AttributeDeltaBuilder.build(OperationalAttributes.ENABLE_NAME, true));
        modifications.add(AttributeDeltaBuilder.build("suspended", false));
        modifications.add(AttributeDeltaBuilder.build("changePasswordAtNextLogin", false));
        modifications.add(AttributeDeltaBuilder.build("ipWhitelisted", true));
        modifications.add(AttributeDeltaBuilder.build("orgUnitPath", "/UpdatedOU"));
        modifications.add(AttributeDeltaBuilder.build("includeInGlobalAddressList", false));

        // Password update
        modifications.add(AttributeDeltaBuilder.build(OperationalAttributes.PASSWORD_NAME,
                new GuardedString("UpdatedPassword123".toCharArray())));

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("test001"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify request contains updated basic fields
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathMatching("/admin/directory/v1/users/test001"))
                .withHeader("Content-Type", matching("application/json.*"))
                .withRequestBody(containing("\"givenName\":\"Updated\""))
                .withRequestBody(containing("\"familyName\":\"Full\""))
                .withRequestBody(containing("\"suspended\":false"))
                .withRequestBody(containing("\"changePasswordAtNextLogin\":false"))
                .withRequestBody(containing("\"ipWhitelisted\":true"))
                .withRequestBody(containing("\"orgUnitPath\":\"/UpdatedOU\""))
                .withRequestBody(containing("\"includeInGlobalAddressList\":false"))
                .withRequestBody(containing("\"password\":\"UpdatedPassword123\"")));
    }

    @Test
    @DisplayName("Test clear single-valued attributes")
    public void testClearSingleValuedAttributes() {
        // Setup mock response for clearing single-valued attributes
        stubFor(put(urlPathMatching("/admin/directory/v1/users/test005"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"test005\",\n" +
                                "  \"etag\": \"\\\"test005-cleared-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"test005@example.com\"\n" +
                                "}")));

        // Prepare attributes to clear single-valued fields
        Set<AttributeDelta> modifications = new HashSet<>();

        // Test different ways to clear single-valued attributes
        modifications.add(AttributeDeltaBuilder.build("givenName", Collections.emptyList()));
        modifications.add(AttributeDeltaBuilder.build("familyName", Collections.emptyList()));

        // Test a single String attribute clear
        modifications.add(AttributeDeltaBuilder.build("orgUnitPath", Collections.emptyList()));

        // Test boolean attributes clear  
        modifications.add(AttributeDeltaBuilder.build("changePasswordAtNextLogin", Collections.emptyList()));
        modifications.add(AttributeDeltaBuilder.build("ipWhitelisted", Collections.emptyList()));

        // Execute update to clear single-valued attributes
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("test005"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Print the actual request body to understand the behavior
        GoogleApiMockServer.printLastRequestBody();

        // Verify request was made and contains expected null values for clearing attributes
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathMatching("/admin/directory/v1/users/test005"))
                .withHeader("Content-Type", matching("application/json.*"))
                .withRequestBody(containing("\"givenName\":null"))
                .withRequestBody(containing("\"familyName\":null"))
                .withRequestBody(containing("\"orgUnitPath\":null"))
                .withRequestBody(containing("\"changePasswordAtNextLogin\":null"))
                .withRequestBody(containing("\"ipWhitelisted\":null")));
    }
}