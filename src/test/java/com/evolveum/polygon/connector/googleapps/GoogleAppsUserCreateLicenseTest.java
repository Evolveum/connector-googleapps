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
 * Test class for Google Apps user license assignment during user creation.
 * Tests the autoAddLicense configuration option.
 *
 * @author Hiroyuki Wada
 */
@DisplayName("Google Apps User Create License Tests")
public class GoogleAppsUserCreateLicenseTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Create user with autoAddLicense enabled")
    public void testCreateUserWithAutoAddLicenseEnabled() {
        // Enable autoAddLicense in configuration
        configuration.setAutoAddLicense(true);
        connectorFacade = createConnectorFacade(configuration);

        // Setup mock for user creation
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"license001\",\n" +
                                "  \"etag\": \"\\\"license001-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"license001@example.com\"\n" +
                                "}")));

        // Setup mock for license assignment
        stubFor(post(urlPathEqualTo("/apps/licensing/v1/product/Google-Apps/sku/Google-Apps-For-Business/user"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"kind\": \"licensing#licenseAssignment\",\n" +
                                "  \"etag\": \"\\\"license-assignment-etag\\\"\",\n" +
                                "  \"productId\": \"Google-Apps\",\n" +
                                "  \"skuId\": \"Google-Apps-For-Business\",\n" +
                                "  \"userId\": \"license001@example.com\"\n" +
                                "}")));

        // Prepare attributes
        Set<Attribute> attributes = new HashSet<>();
        attributes.add(AttributeBuilder.build(Name.NAME, "license001@example.com"));
        attributes.add(AttributeBuilder.build(OperationalAttributes.PASSWORD_NAME, new GuardedString("password123".toCharArray())));
        attributes.add(AttributeBuilder.build("givenName", "License"));
        attributes.add(AttributeBuilder.build("familyName", "User"));

        // Execute user creation
        Uid createdUid = connectorFacade.create(ObjectClass.ACCOUNT, attributes, null);

        // Verify response
        assertThat(createdUid).isNotNull();
        assertThat(createdUid.getUidValue()).isEqualTo("license001");

        // Verify user creation request
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withRequestBody(containing("\"primaryEmail\":\"license001@example.com\"")));

        // Verify license assignment request was made
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/apps/licensing/v1/product/Google-Apps/sku/Google-Apps-For-Business/user"))
                .withRequestBody(containing("\"userId\":\"license001@example.com\"")));
    }

    @Test
    @DisplayName("Create user with autoAddLicense disabled")
    public void testCreateUserWithAutoAddLicenseDisabled() {
        // Ensure autoAddLicense is disabled (default)
        configuration.setAutoAddLicense(false);
        connectorFacade = createConnectorFacade(configuration);

        // Setup mock for user creation
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"nolicense001\",\n" +
                                "  \"etag\": \"\\\"nolicense001-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"nolicense001@example.com\"\n" +
                                "}")));

        // Prepare attributes
        Set<Attribute> attributes = new HashSet<>();
        attributes.add(AttributeBuilder.build(Name.NAME, "nolicense001@example.com"));
        attributes.add(AttributeBuilder.build(OperationalAttributes.PASSWORD_NAME, new GuardedString("password123".toCharArray())));
        attributes.add(AttributeBuilder.build("givenName", "NoLicense"));
        attributes.add(AttributeBuilder.build("familyName", "User"));

        // Execute user creation
        Uid createdUid = connectorFacade.create(ObjectClass.ACCOUNT, attributes, null);

        // Verify response
        assertThat(createdUid).isNotNull();
        assertThat(createdUid.getUidValue()).isEqualTo("nolicense001");

        // Verify user creation request
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withRequestBody(containing("\"primaryEmail\":\"nolicense001@example.com\"")));

        // Verify NO license assignment request was made when autoAddLicense=false
        GoogleApiMockServer.verify(0, postRequestedFor(urlPathMatching("/apps/licensing/.*")));
    }

    @Test
    @DisplayName("Create user with license assignment failure handling")
    public void testLicenseAssignmentFailureHandling() {
        // Enable autoAddLicense
        configuration.setAutoAddLicense(true);
        connectorFacade = createConnectorFacade(configuration);

        // Setup mock for user creation
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"licensefail001\",\n" +
                                "  \"etag\": \"\\\"licensefail001-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"licensefail001@example.com\"\n" +
                                "}")));

        // Setup mock for license assignment failure (e.g., no available licenses)
        stubFor(post(urlPathEqualTo("/apps/licensing/v1/product/Google-Apps/sku/Google-Apps-For-Business/user"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"error\": {\n" +
                                "    \"code\": 400,\n" +
                                "    \"message\": \"No available licenses\",\n" +
                                "    \"errors\": [{\n" +
                                "      \"message\": \"No available licenses\",\n" +
                                "      \"domain\": \"global\",\n" +
                                "      \"reason\": \"invalid\"\n" +
                                "    }]\n" +
                                "  }\n" +
                                "}")));

        // Prepare attributes
        Set<Attribute> attributes = new HashSet<>();
        attributes.add(AttributeBuilder.build(Name.NAME, "licensefail001@example.com"));
        attributes.add(AttributeBuilder.build(OperationalAttributes.PASSWORD_NAME, new GuardedString("password123".toCharArray())));
        attributes.add(AttributeBuilder.build("givenName", "LicenseFail"));
        attributes.add(AttributeBuilder.build("familyName", "User"));

        // Execute user creation - should succeed even if license assignment fails
        Uid createdUid = connectorFacade.create(ObjectClass.ACCOUNT, attributes, null);

        // Verify response - user creation should succeed
        assertThat(createdUid).isNotNull();
        assertThat(createdUid.getUidValue()).isEqualTo("licensefail001");

        // Verify user creation request
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withRequestBody(containing("\"primaryEmail\":\"licensefail001@example.com\"")));

        // Verify license assignment was attempted
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/apps/licensing/v1/product/Google-Apps/sku/Google-Apps-For-Business/user"))
                .withRequestBody(containing("\"userId\":\"licensefail001@example.com\"")));
    }

    @Test
    @DisplayName("Create user with license assignment - duplicate license handling")
    public void testLicenseAssignmentDuplicateHandling() {
        // Enable autoAddLicense
        configuration.setAutoAddLicense(true);
        connectorFacade = createConnectorFacade(configuration);

        // Setup mock for user creation
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"licensedup001\",\n" +
                                "  \"etag\": \"\\\"licensedup001-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"licensedup001@example.com\"\n" +
                                "}")));

        // Setup mock for license assignment - already exists (409 Conflict)
        stubFor(post(urlPathEqualTo("/apps/licensing/v1/product/Google-Apps/sku/Google-Apps-For-Business/user"))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"error\": {\n" +
                                "    \"code\": 409,\n" +
                                "    \"message\": \"License already assigned\",\n" +
                                "    \"errors\": [{\n" +
                                "      \"message\": \"License already assigned\",\n" +
                                "      \"domain\": \"global\",\n" +
                                "      \"reason\": \"duplicate\"\n" +
                                "    }]\n" +
                                "  }\n" +
                                "}")));

        // Prepare attributes
        Set<Attribute> attributes = new HashSet<>();
        attributes.add(AttributeBuilder.build(Name.NAME, "licensedup001@example.com"));
        attributes.add(AttributeBuilder.build(OperationalAttributes.PASSWORD_NAME, new GuardedString("password123".toCharArray())));
        attributes.add(AttributeBuilder.build("givenName", "LicenseDup"));
        attributes.add(AttributeBuilder.build("familyName", "User"));

        // Execute user creation - should succeed even if license already exists
        Uid createdUid = connectorFacade.create(ObjectClass.ACCOUNT, attributes, null);

        // Verify response - user creation should succeed
        assertThat(createdUid).isNotNull();
        assertThat(createdUid.getUidValue()).isEqualTo("licensedup001");

        // Verify license assignment was attempted
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/apps/licensing/v1/product/Google-Apps/sku/Google-Apps-For-Business/user")));
    }
}