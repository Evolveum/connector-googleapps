package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GoogleAppsConnector User multi-valued attribute Add operations
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsUserUpdateMultiValueAddTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Add single value to empty multi-valued attributes (0→1)")
    public void testAddSingleValueToEmpty() {
        // Setup mock response for getting current user data (empty multi-valued attributes)
        stubFor(get(urlPathMatching("/admin/directory/v1/users/addtest001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"addtest001\",\n" +
                                "  \"etag\": \"\\\"addtest001-empty-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"addtest001@example.com\"\n" +
                                "}")));

        // Setup mock response for user update
        stubFor(put(urlPathMatching("/admin/directory/v1/users/addtest001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"addtest001\",\n" +
                                "  \"etag\": \"\\\"addtest001-updated-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"addtest001@example.com\"\n" +
                                "}")));

        // Prepare modifications using add operations (0→1 for all multi-valued attributes)
        Set<AttributeDelta> modifications = new HashSet<>();

        // Add single email
        AttributeDeltaBuilder emailsDelta = new AttributeDeltaBuilder();
        emailsDelta.setName("emails");
        emailsDelta.addValueToAdd("{\"address\":\"new-email@example.com\",\"type\":\"work\",\"primary\":false}");
        modifications.add(emailsDelta.build());

        // Add single phone
        AttributeDeltaBuilder phonesDelta = new AttributeDeltaBuilder();
        phonesDelta.setName("phones");
        phonesDelta.addValueToAdd("{\"value\":\"+1-555-111-0001\",\"type\":\"work\",\"primary\":true}");
        modifications.add(phonesDelta.build());

        // Add single address
        AttributeDeltaBuilder addressesDelta = new AttributeDeltaBuilder();
        addressesDelta.setName("addresses");
        addressesDelta.addValueToAdd("{\"type\":\"work\",\"formatted\":\"123 New Work St\",\"primary\":true}");
        modifications.add(addressesDelta.build());

        // Add single organization
        AttributeDeltaBuilder organizationsDelta = new AttributeDeltaBuilder();
        organizationsDelta.setName("organizations");
        organizationsDelta.addValueToAdd("{\"name\":\"First Company\",\"title\":\"Developer\",\"primary\":true,\"type\":\"work\"}");
        modifications.add(organizationsDelta.build());

        // Add single im
        AttributeDeltaBuilder imsDelta = new AttributeDeltaBuilder();
        imsDelta.setName("ims");
        imsDelta.addValueToAdd("{\"im\":\"first@example.org\",\"protocol\":\"jabber\",\"type\":\"work\",\"primary\":true}");
        modifications.add(imsDelta.build());

        // Add single externalId
        AttributeDeltaBuilder externalIdsDelta = new AttributeDeltaBuilder();
        externalIdsDelta.setName("externalIds");
        externalIdsDelta.addValueToAdd("{\"value\":\"FIRST-EMP001\",\"type\":\"organization\"}");
        modifications.add(externalIdsDelta.build());

        // Add single relation
        AttributeDeltaBuilder relationsDelta = new AttributeDeltaBuilder();
        relationsDelta.setName("relations");
        relationsDelta.addValueToAdd("{\"value\":\"first-manager@example.com\",\"type\":\"manager\"}");
        modifications.add(relationsDelta.build());

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("addtest001"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify that current user data was fetched first
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathMatching("/admin/directory/v1/users/addtest001")));

        // Verify request contains single added values for all attributes
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathMatching("/admin/directory/v1/users/addtest001"))
                .withHeader("Content-Type", matching("application/json.*"))
                // Verify single email added
                .withRequestBody(containing("{\"address\":\"new-email@example.com\",\"type\":\"work\",\"primary\":\"false\"}"))
                // Verify single phone added
                .withRequestBody(containing("{\"value\":\"+1-555-111-0001\",\"type\":\"work\",\"primary\":\"true\"}"))
                // Verify single address added
                .withRequestBody(containing("{\"type\":\"work\",\"formatted\":\"123 New Work St\",\"primary\":\"true\"}"))
                // Verify single organization added
                .withRequestBody(containing("{\"name\":\"First Company\",\"title\":\"Developer\",\"primary\":\"true\",\"type\":\"work\"}"))
                // Verify single im added
                .withRequestBody(containing("{\"im\":\"first@example.org\",\"protocol\":\"jabber\",\"type\":\"work\",\"primary\":\"true\"}"))
                // Verify single externalId added
                .withRequestBody(containing("{\"value\":\"FIRST-EMP001\",\"type\":\"organization\"}"))
                // Verify single relation added
                .withRequestBody(containing("{\"value\":\"first-manager@example.com\",\"type\":\"manager\"}")));
    }

    @Test
    @DisplayName("Add single value to existing multi-valued attributes (1→2)")
    public void testAddSingleValueToExisting() {
        // Setup mock response for getting current user data (1 value for each multi-valued attribute)
        stubFor(get(urlPathMatching("/admin/directory/v1/users/addtest002"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"addtest002\",\n" +
                                "  \"etag\": \"\\\"addtest002-existing-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"addtest002@example.com\",\n" +
                                "  \"emails\": [\n" +
                                "    {\"address\": \"existing-email@example.com\", \"type\": \"home\", \"primary\": false}\n" +
                                "  ],\n" +
                                "  \"phones\": [\n" +
                                "    {\"value\": \"+1-555-222-0001\", \"type\": \"home\", \"primary\": true}\n" +
                                "  ],\n" +
                                "  \"addresses\": [\n" +
                                "    {\"type\": \"home\", \"formatted\": \"456 Existing Home St\", \"primary\": true}\n" +
                                "  ],\n" +
                                "  \"organizations\": [\n" +
                                "    {\"name\": \"Existing Company\", \"title\": \"Junior Developer\", \"primary\": true, \"type\": \"work\"}\n" +
                                "  ],\n" +
                                "  \"ims\": [\n" +
                                "    {\"im\": \"existing@example.org\", \"protocol\": \"jabber\", \"type\": \"home\", \"primary\": true}\n" +
                                "  ],\n" +
                                "  \"externalIds\": [\n" +
                                "    {\"value\": \"EXISTING-EMP001\", \"type\": \"organization\"}\n" +
                                "  ],\n" +
                                "  \"relations\": [\n" +
                                "    {\"value\": \"existing-manager@example.com\", \"type\": \"manager\"}\n" +
                                "  ]\n" +
                                "}")));

        // Setup mock response for user update
        stubFor(put(urlPathMatching("/admin/directory/v1/users/addtest002"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"addtest002\",\n" +
                                "  \"etag\": \"\\\"addtest002-updated-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"addtest002@example.com\"\n" +
                                "}")));

        // Prepare modifications using add operations (1→2 for all multi-valued attributes)
        Set<AttributeDelta> modifications = new HashSet<>();

        // Add second email
        AttributeDeltaBuilder emailsDelta = new AttributeDeltaBuilder();
        emailsDelta.setName("emails");
        emailsDelta.addValueToAdd("{\"address\":\"second-email@example.com\",\"type\":\"work\",\"primary\":false}");
        modifications.add(emailsDelta.build());

        // Add second phone
        AttributeDeltaBuilder phonesDelta = new AttributeDeltaBuilder();
        phonesDelta.setName("phones");
        phonesDelta.addValueToAdd("{\"value\":\"+1-555-222-0002\",\"type\":\"work\",\"primary\":false}");
        modifications.add(phonesDelta.build());

        // Add second address
        AttributeDeltaBuilder addressesDelta = new AttributeDeltaBuilder();
        addressesDelta.setName("addresses");
        addressesDelta.addValueToAdd("{\"type\":\"work\",\"formatted\":\"789 Second Work St\",\"primary\":false}");
        modifications.add(addressesDelta.build());

        // Add second organization
        AttributeDeltaBuilder organizationsDelta = new AttributeDeltaBuilder();
        organizationsDelta.setName("organizations");
        organizationsDelta.addValueToAdd("{\"name\":\"Second Company\",\"title\":\"Senior Developer\",\"primary\":false,\"type\":\"work\"}");
        modifications.add(organizationsDelta.build());

        // Add second im
        AttributeDeltaBuilder imsDelta = new AttributeDeltaBuilder();
        imsDelta.setName("ims");
        imsDelta.addValueToAdd("{\"im\":\"second@example.org\",\"protocol\":\"jabber\",\"type\":\"work\",\"primary\":false}");
        modifications.add(imsDelta.build());

        // Add second externalId
        AttributeDeltaBuilder externalIdsDelta = new AttributeDeltaBuilder();
        externalIdsDelta.setName("externalIds");
        externalIdsDelta.addValueToAdd("{\"value\":\"SECOND-EMP002\",\"type\":\"organization\"}");
        modifications.add(externalIdsDelta.build());

        // Add second relation
        AttributeDeltaBuilder relationsDelta = new AttributeDeltaBuilder();
        relationsDelta.setName("relations");
        relationsDelta.addValueToAdd("{\"value\":\"second-manager@example.com\",\"type\":\"manager\"}");
        modifications.add(relationsDelta.build());

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("addtest002"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify that current user data was fetched first
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathMatching("/admin/directory/v1/users/addtest002")));

        // Verify request contains both existing and new values for all attributes
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathMatching("/admin/directory/v1/users/addtest002"))
                .withHeader("Content-Type", matching("application/json.*"))
                // Verify emails: existing + new
                .withRequestBody(containing("{\"address\":\"existing-email@example.com\",\"type\":\"home\",\"primary\":\"false\"}"))
                .withRequestBody(containing("{\"address\":\"second-email@example.com\",\"type\":\"work\",\"primary\":\"false\"}"))
                // Verify phones: existing + new
                .withRequestBody(containing("{\"value\":\"+1-555-222-0001\",\"type\":\"home\",\"primary\":\"true\"}"))
                .withRequestBody(containing("{\"value\":\"+1-555-222-0002\",\"type\":\"work\",\"primary\":\"false\"}"))
                // Verify addresses: existing + new
                .withRequestBody(containing("{\"type\":\"home\",\"formatted\":\"456 Existing Home St\",\"primary\":\"true\"}"))
                .withRequestBody(containing("{\"type\":\"work\",\"formatted\":\"789 Second Work St\",\"primary\":\"false\"}"))
                // Verify organizations: existing + new
                .withRequestBody(containing("{\"name\":\"Existing Company\",\"title\":\"Junior Developer\",\"primary\":\"true\",\"type\":\"work\"}"))
                .withRequestBody(containing("{\"name\":\"Second Company\",\"title\":\"Senior Developer\",\"primary\":\"false\",\"type\":\"work\"}"))
                // Verify ims: existing + new
                .withRequestBody(containing("{\"im\":\"existing@example.org\",\"protocol\":\"jabber\",\"type\":\"home\",\"primary\":\"true\"}"))
                .withRequestBody(containing("{\"im\":\"second@example.org\",\"protocol\":\"jabber\",\"type\":\"work\",\"primary\":\"false\"}"))
                // Verify externalIds: existing + new
                .withRequestBody(containing("{\"value\":\"EXISTING-EMP001\",\"type\":\"organization\"}"))
                .withRequestBody(containing("{\"value\":\"SECOND-EMP002\",\"type\":\"organization\"}"))
                // Verify relations: existing + new
                .withRequestBody(containing("{\"value\":\"existing-manager@example.com\",\"type\":\"manager\"}"))
                .withRequestBody(containing("{\"value\":\"second-manager@example.com\",\"type\":\"manager\"}")));
    }

    @Test
    @DisplayName("Add two values to empty multi-valued attributes (0→2)")
    public void testAddTwoValuesToEmpty() {
        // Setup mock response for getting current user data (empty multi-valued attributes)
        stubFor(get(urlPathMatching("/admin/directory/v1/users/addtest003"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"addtest003\",\n" +
                                "  \"etag\": \"\\\"addtest003-empty-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"addtest003@example.com\"\n" +
                                "}")));

        // Setup mock response for user update
        stubFor(put(urlPathMatching("/admin/directory/v1/users/addtest003"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"addtest003\",\n" +
                                "  \"etag\": \"\\\"addtest003-updated-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"addtest003@example.com\"\n" +
                                "}")));

        // Prepare modifications using add operations (0→2 for all multi-valued attributes)
        Set<AttributeDelta> modifications = new HashSet<>();

        // Add two emails
        AttributeDeltaBuilder emailsDelta = new AttributeDeltaBuilder();
        emailsDelta.setName("emails");
        emailsDelta.addValueToAdd("{\"address\":\"first-email@example.com\",\"type\":\"work\",\"primary\":false}");
        emailsDelta.addValueToAdd("{\"address\":\"second-email@example.com\",\"type\":\"home\",\"primary\":false}");
        modifications.add(emailsDelta.build());

        // Add two phones
        AttributeDeltaBuilder phonesDelta = new AttributeDeltaBuilder();
        phonesDelta.setName("phones");
        phonesDelta.addValueToAdd("{\"value\":\"+1-555-333-0001\",\"type\":\"work\",\"primary\":true}");
        phonesDelta.addValueToAdd("{\"value\":\"+1-555-333-0002\",\"type\":\"mobile\",\"primary\":false}");
        modifications.add(phonesDelta.build());

        // Add two addresses
        AttributeDeltaBuilder addressesDelta = new AttributeDeltaBuilder();
        addressesDelta.setName("addresses");
        addressesDelta.addValueToAdd("{\"type\":\"work\",\"formatted\":\"111 First Work St\",\"primary\":true}");
        addressesDelta.addValueToAdd("{\"type\":\"home\",\"formatted\":\"222 First Home St\",\"primary\":false}");
        modifications.add(addressesDelta.build());

        // Add two organizations
        AttributeDeltaBuilder organizationsDelta = new AttributeDeltaBuilder();
        organizationsDelta.setName("organizations");
        organizationsDelta.addValueToAdd("{\"name\":\"First Company\",\"title\":\"Developer\",\"primary\":true,\"type\":\"work\"}");
        organizationsDelta.addValueToAdd("{\"name\":\"Second Company\",\"title\":\"Consultant\",\"primary\":false,\"type\":\"work\"}");
        modifications.add(organizationsDelta.build());

        // Add two ims
        AttributeDeltaBuilder imsDelta = new AttributeDeltaBuilder();
        imsDelta.setName("ims");
        imsDelta.addValueToAdd("{\"im\":\"first@example.org\",\"protocol\":\"jabber\",\"type\":\"work\",\"primary\":true}");
        imsDelta.addValueToAdd("{\"im\":\"second@example.org\",\"protocol\":\"jabber\",\"type\":\"home\",\"primary\":false}");
        modifications.add(imsDelta.build());

        // Add two externalIds
        AttributeDeltaBuilder externalIdsDelta = new AttributeDeltaBuilder();
        externalIdsDelta.setName("externalIds");
        externalIdsDelta.addValueToAdd("{\"value\":\"FIRST-EMP001\",\"type\":\"organization\"}");
        externalIdsDelta.addValueToAdd("{\"value\":\"SECOND-EMP002\",\"type\":\"organization\"}");
        modifications.add(externalIdsDelta.build());

        // Add two relations
        AttributeDeltaBuilder relationsDelta = new AttributeDeltaBuilder();
        relationsDelta.setName("relations");
        relationsDelta.addValueToAdd("{\"value\":\"first-manager@example.com\",\"type\":\"manager\"}");
        relationsDelta.addValueToAdd("{\"value\":\"second-manager@example.com\",\"type\":\"manager\"}");
        modifications.add(relationsDelta.build());

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("addtest003"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify that current user data was fetched first
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathMatching("/admin/directory/v1/users/addtest003")));

        // Verify request contains two added values for all attributes
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathMatching("/admin/directory/v1/users/addtest003"))
                .withHeader("Content-Type", matching("application/json.*"))
                // Verify two emails added
                .withRequestBody(containing("{\"address\":\"first-email@example.com\",\"type\":\"work\",\"primary\":\"false\"}"))
                .withRequestBody(containing("{\"address\":\"second-email@example.com\",\"type\":\"home\",\"primary\":\"false\"}"))
                // Verify two phones added
                .withRequestBody(containing("{\"value\":\"+1-555-333-0001\",\"type\":\"work\",\"primary\":\"true\"}"))
                .withRequestBody(containing("{\"value\":\"+1-555-333-0002\",\"type\":\"mobile\",\"primary\":\"false\"}"))
                // Verify two addresses added
                .withRequestBody(containing("{\"type\":\"work\",\"formatted\":\"111 First Work St\",\"primary\":\"true\"}"))
                .withRequestBody(containing("{\"type\":\"home\",\"formatted\":\"222 First Home St\",\"primary\":\"false\"}"))
                // Verify two organizations added
                .withRequestBody(containing("{\"name\":\"First Company\",\"title\":\"Developer\",\"primary\":\"true\",\"type\":\"work\"}"))
                .withRequestBody(containing("{\"name\":\"Second Company\",\"title\":\"Consultant\",\"primary\":\"false\",\"type\":\"work\"}"))
                // Verify two ims added
                .withRequestBody(containing("{\"im\":\"first@example.org\",\"protocol\":\"jabber\",\"type\":\"work\",\"primary\":\"true\"}"))
                .withRequestBody(containing("{\"im\":\"second@example.org\",\"protocol\":\"jabber\",\"type\":\"home\",\"primary\":\"false\"}"))
                // Verify two externalIds added
                .withRequestBody(containing("{\"value\":\"FIRST-EMP001\",\"type\":\"organization\"}"))
                .withRequestBody(containing("{\"value\":\"SECOND-EMP002\",\"type\":\"organization\"}"))
                // Verify two relations added
                .withRequestBody(containing("{\"value\":\"first-manager@example.com\",\"type\":\"manager\"}"))
                .withRequestBody(containing("{\"value\":\"second-manager@example.com\",\"type\":\"manager\"}")));
    }

    @Test
    @DisplayName("Add two values to existing multi-valued attributes (2→4)")
    public void testAddTwoValuesToExisting() {
        // Setup mock response for getting current user data (2 values for each multi-valued attribute)
        stubFor(get(urlPathMatching("/admin/directory/v1/users/addtest004"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"addtest004\",\n" +
                                "  \"etag\": \"\\\"addtest004-existing-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"addtest004@example.com\",\n" +
                                "  \"emails\": [\n" +
                                "    {\"address\": \"existing1-email@example.com\", \"type\": \"work\", \"primary\": false},\n" +
                                "    {\"address\": \"existing2-email@example.com\", \"type\": \"home\", \"primary\": false}\n" +
                                "  ],\n" +
                                "  \"phones\": [\n" +
                                "    {\"value\": \"+1-555-444-0001\", \"type\": \"work\", \"primary\": true},\n" +
                                "    {\"value\": \"+1-555-444-0002\", \"type\": \"home\", \"primary\": false}\n" +
                                "  ],\n" +
                                "  \"addresses\": [\n" +
                                "    {\"type\": \"work\", \"formatted\": \"333 Existing Work St\", \"primary\": true},\n" +
                                "    {\"type\": \"home\", \"formatted\": \"444 Existing Home St\", \"primary\": false}\n" +
                                "  ],\n" +
                                "  \"organizations\": [\n" +
                                "    {\"name\": \"Existing Company A\", \"title\": \"Developer\", \"primary\": true, \"type\": \"work\"},\n" +
                                "    {\"name\": \"Existing Company B\", \"title\": \"Consultant\", \"primary\": false, \"type\": \"work\"}\n" +
                                "  ],\n" +
                                "  \"ims\": [\n" +
                                "    {\"im\": \"existing1@example.org\", \"protocol\": \"jabber\", \"type\": \"work\", \"primary\": true},\n" +
                                "    {\"im\": \"existing2@example.org\", \"protocol\": \"jabber\", \"type\": \"home\", \"primary\": false}\n" +
                                "  ],\n" +
                                "  \"externalIds\": [\n" +
                                "    {\"value\": \"EXISTING1-EMP001\", \"type\": \"organization\"},\n" +
                                "    {\"value\": \"EXISTING2-EMP002\", \"type\": \"organization\"}\n" +
                                "  ],\n" +
                                "  \"relations\": [\n" +
                                "    {\"value\": \"existing1-manager@example.com\", \"type\": \"manager\"},\n" +
                                "    {\"value\": \"existing2-manager@example.com\", \"type\": \"manager\"}\n" +
                                "  ]\n" +
                                "}")));

        // Setup mock response for user update
        stubFor(put(urlPathMatching("/admin/directory/v1/users/addtest004"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"addtest004\",\n" +
                                "  \"etag\": \"\\\"addtest004-updated-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"addtest004@example.com\"\n" +
                                "}")));

        // Prepare modifications using add operations (2→4 for all multi-valued attributes)
        Set<AttributeDelta> modifications = new HashSet<>();

        // Add two more emails
        AttributeDeltaBuilder emailsDelta = new AttributeDeltaBuilder();
        emailsDelta.setName("emails");
        emailsDelta.addValueToAdd("{\"address\":\"new1-email@example.com\",\"type\":\"other\",\"primary\":false}");
        emailsDelta.addValueToAdd("{\"address\":\"new2-email@example.com\",\"type\":\"custom\",\"primary\":false}");
        modifications.add(emailsDelta.build());

        // Add two more phones
        AttributeDeltaBuilder phonesDelta = new AttributeDeltaBuilder();
        phonesDelta.setName("phones");
        phonesDelta.addValueToAdd("{\"value\":\"+1-555-444-0003\",\"type\":\"mobile\",\"primary\":false}");
        phonesDelta.addValueToAdd("{\"value\":\"+1-555-444-0004\",\"type\":\"other\",\"primary\":false}");
        modifications.add(phonesDelta.build());

        // Add two more addresses
        AttributeDeltaBuilder addressesDelta = new AttributeDeltaBuilder();
        addressesDelta.setName("addresses");
        addressesDelta.addValueToAdd("{\"type\":\"other\",\"formatted\":\"555 New Work St\",\"primary\":false}");
        addressesDelta.addValueToAdd("{\"type\":\"custom\",\"formatted\":\"666 New Custom St\",\"primary\":false}");
        modifications.add(addressesDelta.build());

        // Add two more organizations
        AttributeDeltaBuilder organizationsDelta = new AttributeDeltaBuilder();
        organizationsDelta.setName("organizations");
        organizationsDelta.addValueToAdd("{\"name\":\"New Company C\",\"title\":\"Senior Developer\",\"primary\":false,\"type\":\"work\"}");
        organizationsDelta.addValueToAdd("{\"name\":\"New Company D\",\"title\":\"Architect\",\"primary\":false,\"type\":\"work\"}");
        modifications.add(organizationsDelta.build());

        // Add two more ims
        AttributeDeltaBuilder imsDelta = new AttributeDeltaBuilder();
        imsDelta.setName("ims");
        imsDelta.addValueToAdd("{\"im\":\"new1@example.org\",\"protocol\":\"jabber\",\"type\":\"other\",\"primary\":false}");
        imsDelta.addValueToAdd("{\"im\":\"new2@example.org\",\"protocol\":\"jabber\",\"type\":\"custom\",\"primary\":false}");
        modifications.add(imsDelta.build());

        // Add two more externalIds
        AttributeDeltaBuilder externalIdsDelta = new AttributeDeltaBuilder();
        externalIdsDelta.setName("externalIds");
        externalIdsDelta.addValueToAdd("{\"value\":\"NEW1-EMP003\",\"type\":\"organization\"}");
        externalIdsDelta.addValueToAdd("{\"value\":\"NEW2-EMP004\",\"type\":\"organization\"}");
        modifications.add(externalIdsDelta.build());

        // Add two more relations
        AttributeDeltaBuilder relationsDelta = new AttributeDeltaBuilder();
        relationsDelta.setName("relations");
        relationsDelta.addValueToAdd("{\"value\":\"new1-manager@example.com\",\"type\":\"manager\"}");
        relationsDelta.addValueToAdd("{\"value\":\"new2-manager@example.com\",\"type\":\"manager\"}");
        modifications.add(relationsDelta.build());

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("addtest004"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify that current user data was fetched first
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathMatching("/admin/directory/v1/users/addtest004")));

        // Verify request contains all four values (2 existing + 2 new) for all attributes
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathMatching("/admin/directory/v1/users/addtest004"))
                .withHeader("Content-Type", matching("application/json.*"))
                // Verify emails: 2 existing + 2 new
                .withRequestBody(containing("{\"address\":\"existing1-email@example.com\",\"type\":\"work\",\"primary\":\"false\"}"))
                .withRequestBody(containing("{\"address\":\"existing2-email@example.com\",\"type\":\"home\",\"primary\":\"false\"}"))
                .withRequestBody(containing("{\"address\":\"new1-email@example.com\",\"type\":\"other\",\"primary\":\"false\"}"))
                .withRequestBody(containing("{\"address\":\"new2-email@example.com\",\"type\":\"custom\",\"primary\":\"false\"}"))
                // Verify phones: 2 existing + 2 new
                .withRequestBody(containing("{\"value\":\"+1-555-444-0001\",\"type\":\"work\",\"primary\":\"true\"}"))
                .withRequestBody(containing("{\"value\":\"+1-555-444-0002\",\"type\":\"home\",\"primary\":\"false\"}"))
                .withRequestBody(containing("{\"value\":\"+1-555-444-0003\",\"type\":\"mobile\",\"primary\":\"false\"}"))
                .withRequestBody(containing("{\"value\":\"+1-555-444-0004\",\"type\":\"other\",\"primary\":\"false\"}"))
                // Verify addresses: 2 existing + 2 new
                .withRequestBody(containing("{\"type\":\"work\",\"formatted\":\"333 Existing Work St\",\"primary\":\"true\"}"))
                .withRequestBody(containing("{\"type\":\"home\",\"formatted\":\"444 Existing Home St\",\"primary\":\"false\"}"))
                .withRequestBody(containing("{\"type\":\"other\",\"formatted\":\"555 New Work St\",\"primary\":\"false\"}"))
                .withRequestBody(containing("{\"type\":\"custom\",\"formatted\":\"666 New Custom St\",\"primary\":\"false\"}"))
                // Verify organizations: 2 existing + 2 new
                .withRequestBody(containing("{\"name\":\"Existing Company A\",\"title\":\"Developer\",\"primary\":\"true\",\"type\":\"work\"}"))
                .withRequestBody(containing("{\"name\":\"Existing Company B\",\"title\":\"Consultant\",\"primary\":\"false\",\"type\":\"work\"}"))
                .withRequestBody(containing("{\"name\":\"New Company C\",\"title\":\"Senior Developer\",\"primary\":\"false\",\"type\":\"work\"}"))
                .withRequestBody(containing("{\"name\":\"New Company D\",\"title\":\"Architect\",\"primary\":\"false\",\"type\":\"work\"}"))
                // Verify ims: 2 existing + 2 new
                .withRequestBody(containing("{\"im\":\"existing1@example.org\",\"protocol\":\"jabber\",\"type\":\"work\",\"primary\":\"true\"}"))
                .withRequestBody(containing("{\"im\":\"existing2@example.org\",\"protocol\":\"jabber\",\"type\":\"home\",\"primary\":\"false\"}"))
                .withRequestBody(containing("{\"im\":\"new1@example.org\",\"protocol\":\"jabber\",\"type\":\"other\",\"primary\":\"false\"}"))
                .withRequestBody(containing("{\"im\":\"new2@example.org\",\"protocol\":\"jabber\",\"type\":\"custom\",\"primary\":\"false\"}"))
                // Verify externalIds: 2 existing + 2 new
                .withRequestBody(containing("{\"value\":\"EXISTING1-EMP001\",\"type\":\"organization\"}"))
                .withRequestBody(containing("{\"value\":\"EXISTING2-EMP002\",\"type\":\"organization\"}"))
                .withRequestBody(containing("{\"value\":\"NEW1-EMP003\",\"type\":\"organization\"}"))
                .withRequestBody(containing("{\"value\":\"NEW2-EMP004\",\"type\":\"organization\"}"))
                // Verify relations: 2 existing + 2 new
                .withRequestBody(containing("{\"value\":\"existing1-manager@example.com\",\"type\":\"manager\"}"))
                .withRequestBody(containing("{\"value\":\"existing2-manager@example.com\",\"type\":\"manager\"}"))
                .withRequestBody(containing("{\"value\":\"new1-manager@example.com\",\"type\":\"manager\"}"))
                .withRequestBody(containing("{\"value\":\"new2-manager@example.com\",\"type\":\"manager\"}")));
    }
}