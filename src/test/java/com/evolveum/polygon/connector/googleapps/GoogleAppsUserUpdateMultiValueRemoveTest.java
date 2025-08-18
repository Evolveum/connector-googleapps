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
 * Tests for GoogleAppsConnector User multi-valued attribute Remove operations
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsUserUpdateMultiValueRemoveTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Remove single value from empty multi-valued attributes (0→0, no error)")
    public void testRemoveSingleValueFromEmpty() {
        // Setup mock response for getting current user data (empty multi-valued attributes)
        stubFor(get(urlPathMatching("/admin/directory/v1/users/removetest001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"removetest001\",\n" +
                                "  \"etag\": \"\\\"removetest001-empty-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"removetest001@example.com\"\n" +
                                "}")));

        // Setup mock response for user update (empty result expected)
        stubFor(put(urlPathMatching("/admin/directory/v1/users/removetest001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"removetest001\",\n" +
                                "  \"etag\": \"\\\"removetest001-updated-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"removetest001@example.com\"\n" +
                                "}")));

        // Prepare modifications using remove operations (0→0 for all multi-valued attributes)
        Set<AttributeDelta> modifications = new HashSet<>();

        // Remove non-existent email
        AttributeDeltaBuilder emailsDelta = new AttributeDeltaBuilder();
        emailsDelta.setName("emails");
        emailsDelta.addValueToRemove("{\"address\":\"nonexistent-email@example.com\",\"type\":\"work\",\"primary\":false}");
        modifications.add(emailsDelta.build());

        // Remove non-existent phone
        AttributeDeltaBuilder phonesDelta = new AttributeDeltaBuilder();
        phonesDelta.setName("phones");
        phonesDelta.addValueToRemove("{\"value\":\"+1-555-999-0001\",\"type\":\"work\",\"primary\":true}");
        modifications.add(phonesDelta.build());

        // Remove non-existent address
        AttributeDeltaBuilder addressesDelta = new AttributeDeltaBuilder();
        addressesDelta.setName("addresses");
        addressesDelta.addValueToRemove("{\"type\":\"work\",\"formatted\":\"999 Nonexistent St\",\"primary\":true}");
        modifications.add(addressesDelta.build());

        // Remove non-existent organization
        AttributeDeltaBuilder organizationsDelta = new AttributeDeltaBuilder();
        organizationsDelta.setName("organizations");
        organizationsDelta.addValueToRemove("{\"name\":\"Nonexistent Company\",\"title\":\"Developer\",\"primary\":true,\"type\":\"work\"}");
        modifications.add(organizationsDelta.build());

        // Remove non-existent im
        AttributeDeltaBuilder imsDelta = new AttributeDeltaBuilder();
        imsDelta.setName("ims");
        imsDelta.addValueToRemove("{\"im\":\"nonexistent@example.org\",\"protocol\":\"jabber\",\"type\":\"work\",\"primary\":true}");
        modifications.add(imsDelta.build());

        // Remove non-existent externalId
        AttributeDeltaBuilder externalIdsDelta = new AttributeDeltaBuilder();
        externalIdsDelta.setName("externalIds");
        externalIdsDelta.addValueToRemove("{\"value\":\"NONEXISTENT-EMP001\",\"type\":\"organization\"}");
        modifications.add(externalIdsDelta.build());

        // Remove non-existent relation
        AttributeDeltaBuilder relationsDelta = new AttributeDeltaBuilder();
        relationsDelta.setName("relations");
        relationsDelta.addValueToRemove("{\"value\":\"nonexistent-manager@example.com\",\"type\":\"manager\"}");
        modifications.add(relationsDelta.build());

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("removetest001"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify that current user data was fetched first
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathMatching("/admin/directory/v1/users/removetest001")));

        // Verify request contains empty arrays for all attributes (no change, since all originals were empty)
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathMatching("/admin/directory/v1/users/removetest001"))
                .withHeader("Content-Type", matching("application/json.*"))
                .withRequestBody(containing("\"emails\":[]"))
                .withRequestBody(containing("\"phones\":[]"))
                .withRequestBody(containing("\"addresses\":[]"))
                .withRequestBody(containing("\"organizations\":[]"))
                .withRequestBody(containing("\"ims\":[]"))
                .withRequestBody(containing("\"externalIds\":[]"))
                .withRequestBody(containing("\"relations\":[]")));
    }

    @Test
    @DisplayName("Remove single value from single-valued attributes (1→0)")
    public void testRemoveSingleValueFromSingle() {
        // Setup mock response for getting current user data (1 value for each multi-valued attribute)
        stubFor(get(urlPathMatching("/admin/directory/v1/users/removetest002"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"removetest002\",\n" +
                                "  \"etag\": \"\\\"removetest002-single-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"removetest002@example.com\",\n" +
                                "  \"emails\": [\n" +
                                "    {\"address\": \"single-email@example.com\", \"type\": \"work\", \"primary\": false}\n" +
                                "  ],\n" +
                                "  \"phones\": [\n" +
                                "    {\"value\": \"+1-555-101-0001\", \"type\": \"work\", \"primary\": true}\n" +
                                "  ],\n" +
                                "  \"addresses\": [\n" +
                                "    {\"type\": \"work\", \"formatted\": \"101 Single Work St\", \"primary\": true}\n" +
                                "  ],\n" +
                                "  \"organizations\": [\n" +
                                "    {\"name\": \"Single Company\", \"title\": \"Developer\", \"primary\": true, \"type\": \"work\"}\n" +
                                "  ],\n" +
                                "  \"ims\": [\n" +
                                "    {\"im\": \"single@example.org\", \"protocol\": \"jabber\", \"type\": \"work\", \"primary\": true}\n" +
                                "  ],\n" +
                                "  \"externalIds\": [\n" +
                                "    {\"value\": \"SINGLE-EMP001\", \"type\": \"organization\"}\n" +
                                "  ],\n" +
                                "  \"relations\": [\n" +
                                "    {\"value\": \"single-manager@example.com\", \"type\": \"manager\"}\n" +
                                "  ]\n" +
                                "}")));

        // Setup mock response for user update
        stubFor(put(urlPathMatching("/admin/directory/v1/users/removetest002"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"removetest002\",\n" +
                                "  \"etag\": \"\\\"removetest002-updated-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"removetest002@example.com\"\n" +
                                "}")));

        // Prepare modifications using remove operations (1→0 for all multi-valued attributes)
        Set<AttributeDelta> modifications = new HashSet<>();

        // Remove the single email
        AttributeDeltaBuilder emailsDelta = new AttributeDeltaBuilder();
        emailsDelta.setName("emails");
        emailsDelta.addValueToRemove("{\"address\":\"single-email@example.com\",\"type\":\"work\",\"primary\":false}");
        modifications.add(emailsDelta.build());

        // Remove the single phone
        AttributeDeltaBuilder phonesDelta = new AttributeDeltaBuilder();
        phonesDelta.setName("phones");
        phonesDelta.addValueToRemove("{\"value\":\"+1-555-101-0001\",\"type\":\"work\",\"primary\":true}");
        modifications.add(phonesDelta.build());

        // Remove the single address
        AttributeDeltaBuilder addressesDelta = new AttributeDeltaBuilder();
        addressesDelta.setName("addresses");
        addressesDelta.addValueToRemove("{\"type\":\"work\",\"formatted\":\"101 Single Work St\",\"primary\":true}");
        modifications.add(addressesDelta.build());

        // Remove the single organization
        AttributeDeltaBuilder organizationsDelta = new AttributeDeltaBuilder();
        organizationsDelta.setName("organizations");
        organizationsDelta.addValueToRemove("{\"name\":\"Single Company\",\"title\":\"Developer\",\"primary\":true,\"type\":\"work\"}");
        modifications.add(organizationsDelta.build());

        // Remove the single im
        AttributeDeltaBuilder imsDelta = new AttributeDeltaBuilder();
        imsDelta.setName("ims");
        imsDelta.addValueToRemove("{\"im\":\"single@example.org\",\"protocol\":\"jabber\",\"type\":\"work\",\"primary\":true}");
        modifications.add(imsDelta.build());

        // Remove the single externalId
        AttributeDeltaBuilder externalIdsDelta = new AttributeDeltaBuilder();
        externalIdsDelta.setName("externalIds");
        externalIdsDelta.addValueToRemove("{\"value\":\"SINGLE-EMP001\",\"type\":\"organization\"}");
        modifications.add(externalIdsDelta.build());

        // Remove the single relation
        AttributeDeltaBuilder relationsDelta = new AttributeDeltaBuilder();
        relationsDelta.setName("relations");
        relationsDelta.addValueToRemove("{\"value\":\"single-manager@example.com\",\"type\":\"manager\"}");
        modifications.add(relationsDelta.build());

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("removetest002"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify that current user data was fetched first
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathMatching("/admin/directory/v1/users/removetest002")));

        // Verify request contains empty arrays for all attributes (all values removed)
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathMatching("/admin/directory/v1/users/removetest002"))
                .withHeader("Content-Type", matching("application/json.*"))
                .withRequestBody(containing("\"emails\":[]"))
                .withRequestBody(containing("\"phones\":[]"))
                .withRequestBody(containing("\"addresses\":[]"))
                .withRequestBody(containing("\"organizations\":[]"))
                .withRequestBody(containing("\"ims\":[]"))
                .withRequestBody(containing("\"externalIds\":[]"))
                .withRequestBody(containing("\"relations\":[]")));
    }

    @Test
    @DisplayName("Remove single value from dual-valued attributes (2→1)")
    public void testRemoveSingleValueFromDual() {
        // Setup mock response for getting current user data (2 values for each multi-valued attribute)
        stubFor(get(urlPathMatching("/admin/directory/v1/users/removetest003"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"removetest003\",\n" +
                                "  \"etag\": \"\\\"removetest003-dual-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"removetest003@example.com\",\n" +
                                "  \"emails\": [\n" +
                                "    {\"address\": \"keep-email@example.com\", \"type\": \"work\", \"primary\": false},\n" +
                                "    {\"address\": \"remove-email@example.com\", \"type\": \"home\", \"primary\": false}\n" +
                                "  ],\n" +
                                "  \"phones\": [\n" +
                                "    {\"value\": \"+1-555-201-0001\", \"type\": \"work\", \"primary\": true},\n" +
                                "    {\"value\": \"+1-555-201-0002\", \"type\": \"home\", \"primary\": false}\n" +
                                "  ],\n" +
                                "  \"addresses\": [\n" +
                                "    {\"type\": \"work\", \"formatted\": \"201 Keep Work St\", \"primary\": true},\n" +
                                "    {\"type\": \"home\", \"formatted\": \"202 Remove Home St\", \"primary\": false}\n" +
                                "  ],\n" +
                                "  \"organizations\": [\n" +
                                "    {\"name\": \"Keep Company\", \"title\": \"Developer\", \"primary\": true, \"type\": \"work\"},\n" +
                                "    {\"name\": \"Remove Company\", \"title\": \"Consultant\", \"primary\": false, \"type\": \"work\"}\n" +
                                "  ],\n" +
                                "  \"ims\": [\n" +
                                "    {\"im\": \"keep@example.org\", \"protocol\": \"jabber\", \"type\": \"work\", \"primary\": true},\n" +
                                "    {\"im\": \"remove@example.org\", \"protocol\": \"jabber\", \"type\": \"home\", \"primary\": false}\n" +
                                "  ],\n" +
                                "  \"externalIds\": [\n" +
                                "    {\"value\": \"KEEP-EMP001\", \"type\": \"organization\"},\n" +
                                "    {\"value\": \"REMOVE-EMP002\", \"type\": \"organization\"}\n" +
                                "  ],\n" +
                                "  \"relations\": [\n" +
                                "    {\"value\": \"keep-manager@example.com\", \"type\": \"manager\"},\n" +
                                "    {\"value\": \"remove-manager@example.com\", \"type\": \"manager\"}\n" +
                                "  ]\n" +
                                "}")));

        // Setup mock response for user update
        stubFor(put(urlPathMatching("/admin/directory/v1/users/removetest003"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"removetest003\",\n" +
                                "  \"etag\": \"\\\"removetest003-updated-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"removetest003@example.com\"\n" +
                                "}")));

        // Prepare modifications using remove operations (2→1 for all multi-valued attributes)
        Set<AttributeDelta> modifications = new HashSet<>();

        // Remove the second email (keep first)
        AttributeDeltaBuilder emailsDelta = new AttributeDeltaBuilder();
        emailsDelta.setName("emails");
        emailsDelta.addValueToRemove("{\"address\":\"remove-email@example.com\",\"type\":\"home\",\"primary\":false}");
        modifications.add(emailsDelta.build());

        // Remove the second phone (keep first)
        AttributeDeltaBuilder phonesDelta = new AttributeDeltaBuilder();
        phonesDelta.setName("phones");
        phonesDelta.addValueToRemove("{\"value\":\"+1-555-201-0002\",\"type\":\"home\",\"primary\":false}");
        modifications.add(phonesDelta.build());

        // Remove the second address (keep first)
        AttributeDeltaBuilder addressesDelta = new AttributeDeltaBuilder();
        addressesDelta.setName("addresses");
        addressesDelta.addValueToRemove("{\"type\":\"home\",\"formatted\":\"202 Remove Home St\",\"primary\":false}");
        modifications.add(addressesDelta.build());

        // Remove the second organization (keep first)
        AttributeDeltaBuilder organizationsDelta = new AttributeDeltaBuilder();
        organizationsDelta.setName("organizations");
        organizationsDelta.addValueToRemove("{\"name\":\"Remove Company\",\"title\":\"Consultant\",\"primary\":false,\"type\":\"work\"}");
        modifications.add(organizationsDelta.build());

        // Remove the second im (keep first)
        AttributeDeltaBuilder imsDelta = new AttributeDeltaBuilder();
        imsDelta.setName("ims");
        imsDelta.addValueToRemove("{\"im\":\"remove@example.org\",\"protocol\":\"jabber\",\"type\":\"home\",\"primary\":false}");
        modifications.add(imsDelta.build());

        // Remove the second externalId (keep first)
        AttributeDeltaBuilder externalIdsDelta = new AttributeDeltaBuilder();
        externalIdsDelta.setName("externalIds");
        externalIdsDelta.addValueToRemove("{\"value\":\"REMOVE-EMP002\",\"type\":\"organization\"}");
        modifications.add(externalIdsDelta.build());

        // Remove the second relation (keep first)
        AttributeDeltaBuilder relationsDelta = new AttributeDeltaBuilder();
        relationsDelta.setName("relations");
        relationsDelta.addValueToRemove("{\"value\":\"remove-manager@example.com\",\"type\":\"manager\"}");
        modifications.add(relationsDelta.build());

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("removetest003"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify that current user data was fetched first
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathMatching("/admin/directory/v1/users/removetest003")));

        // Verify request contains only the first (kept) values for all attributes
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathMatching("/admin/directory/v1/users/removetest003"))
                .withHeader("Content-Type", matching("application/json.*"))
                // Verify only kept email remains
                .withRequestBody(containing("{\"address\":\"keep-email@example.com\",\"type\":\"work\",\"primary\":\"false\"}"))
                .withRequestBody(not(containing("remove-email@example.com")))
                // Verify only kept phone remains
                .withRequestBody(containing("{\"value\":\"+1-555-201-0001\",\"type\":\"work\",\"primary\":\"true\"}"))
                .withRequestBody(not(containing("+1-555-201-0002")))
                // Verify only kept address remains
                .withRequestBody(containing("{\"type\":\"work\",\"formatted\":\"201 Keep Work St\",\"primary\":\"true\"}"))
                .withRequestBody(not(containing("202 Remove Home St")))
                // Verify only kept organization remains
                .withRequestBody(containing("{\"name\":\"Keep Company\",\"title\":\"Developer\",\"primary\":\"true\",\"type\":\"work\"}"))
                .withRequestBody(not(containing("Remove Company")))
                // Verify only kept im remains
                .withRequestBody(containing("{\"im\":\"keep@example.org\",\"protocol\":\"jabber\",\"type\":\"work\",\"primary\":\"true\"}"))
                .withRequestBody(not(containing("remove@example.org")))
                // Verify only kept externalId remains
                .withRequestBody(containing("{\"value\":\"KEEP-EMP001\",\"type\":\"organization\"}"))
                .withRequestBody(not(containing("REMOVE-EMP002")))
                // Verify only kept relation remains
                .withRequestBody(containing("{\"value\":\"keep-manager@example.com\",\"type\":\"manager\"}"))
                .withRequestBody(not(containing("remove-manager@example.com"))));
    }

    @Test
    @DisplayName("Remove two values from empty multi-valued attributes (0→0, no error)")
    public void testRemoveTwoValuesFromEmpty() {
        // Setup mock response for getting current user data (empty multi-valued attributes)
        stubFor(get(urlPathMatching("/admin/directory/v1/users/removetest004"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"removetest004\",\n" +
                                "  \"etag\": \"\\\"removetest004-empty-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"removetest004@example.com\"\n" +
                                "}")));

        // Setup mock response for user update (empty result expected)
        stubFor(put(urlPathMatching("/admin/directory/v1/users/removetest004"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"removetest004\",\n" +
                                "  \"etag\": \"\\\"removetest004-updated-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"removetest004@example.com\"\n" +
                                "}")));

        // Prepare modifications using remove operations (0→0 for all multi-valued attributes)
        Set<AttributeDelta> modifications = new HashSet<>();

        // Remove two non-existent emails
        AttributeDeltaBuilder emailsDelta = new AttributeDeltaBuilder();
        emailsDelta.setName("emails");
        emailsDelta.addValueToRemove("{\"address\":\"nonexistent1-email@example.com\",\"type\":\"work\",\"primary\":false}");
        emailsDelta.addValueToRemove("{\"address\":\"nonexistent2-email@example.com\",\"type\":\"home\",\"primary\":false}");
        modifications.add(emailsDelta.build());

        // Remove two non-existent phones
        AttributeDeltaBuilder phonesDelta = new AttributeDeltaBuilder();
        phonesDelta.setName("phones");
        phonesDelta.addValueToRemove("{\"value\":\"+1-555-888-0001\",\"type\":\"work\",\"primary\":true}");
        phonesDelta.addValueToRemove("{\"value\":\"+1-555-888-0002\",\"type\":\"home\",\"primary\":false}");
        modifications.add(phonesDelta.build());

        // Remove two non-existent addresses
        AttributeDeltaBuilder addressesDelta = new AttributeDeltaBuilder();
        addressesDelta.setName("addresses");
        addressesDelta.addValueToRemove("{\"type\":\"work\",\"formatted\":\"888 Nonexistent Work St\",\"primary\":true}");
        addressesDelta.addValueToRemove("{\"type\":\"home\",\"formatted\":\"889 Nonexistent Home St\",\"primary\":false}");
        modifications.add(addressesDelta.build());

        // Remove two non-existent organizations
        AttributeDeltaBuilder organizationsDelta = new AttributeDeltaBuilder();
        organizationsDelta.setName("organizations");
        organizationsDelta.addValueToRemove("{\"name\":\"Nonexistent Company A\",\"title\":\"Developer\",\"primary\":true,\"type\":\"work\"}");
        organizationsDelta.addValueToRemove("{\"name\":\"Nonexistent Company B\",\"title\":\"Consultant\",\"primary\":false,\"type\":\"work\"}");
        modifications.add(organizationsDelta.build());

        // Remove two non-existent ims
        AttributeDeltaBuilder imsDelta = new AttributeDeltaBuilder();
        imsDelta.setName("ims");
        imsDelta.addValueToRemove("{\"im\":\"nonexistent1@example.org\",\"protocol\":\"jabber\",\"type\":\"work\",\"primary\":true}");
        imsDelta.addValueToRemove("{\"im\":\"nonexistent2@example.org\",\"protocol\":\"jabber\",\"type\":\"home\",\"primary\":false}");
        modifications.add(imsDelta.build());

        // Remove two non-existent externalIds
        AttributeDeltaBuilder externalIdsDelta = new AttributeDeltaBuilder();
        externalIdsDelta.setName("externalIds");
        externalIdsDelta.addValueToRemove("{\"value\":\"NONEXISTENT1-EMP001\",\"type\":\"organization\"}");
        externalIdsDelta.addValueToRemove("{\"value\":\"NONEXISTENT2-EMP002\",\"type\":\"organization\"}");
        modifications.add(externalIdsDelta.build());

        // Remove two non-existent relations
        AttributeDeltaBuilder relationsDelta = new AttributeDeltaBuilder();
        relationsDelta.setName("relations");
        relationsDelta.addValueToRemove("{\"value\":\"nonexistent1-manager@example.com\",\"type\":\"manager\"}");
        relationsDelta.addValueToRemove("{\"value\":\"nonexistent2-manager@example.com\",\"type\":\"manager\"}");
        modifications.add(relationsDelta.build());

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("removetest004"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify that current user data was fetched first
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathMatching("/admin/directory/v1/users/removetest004")));

        // Verify request contains empty arrays for all attributes (no change, since all originals were empty)
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathMatching("/admin/directory/v1/users/removetest004"))
                .withHeader("Content-Type", matching("application/json.*"))
                .withRequestBody(containing("\"emails\":[]"))
                .withRequestBody(containing("\"phones\":[]"))
                .withRequestBody(containing("\"addresses\":[]"))
                .withRequestBody(containing("\"organizations\":[]"))
                .withRequestBody(containing("\"ims\":[]"))
                .withRequestBody(containing("\"externalIds\":[]"))
                .withRequestBody(containing("\"relations\":[]")));
    }

    @Test
    @DisplayName("Remove two values from single-valued attributes (1→0, over-removal)")
    public void testRemoveTwoValuesFromSingle() {
        // Setup mock response for getting current user data (1 value for each multi-valued attribute)
        stubFor(get(urlPathMatching("/admin/directory/v1/users/removetest005"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"removetest005\",\n" +
                                "  \"etag\": \"\\\"removetest005-single-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"removetest005@example.com\",\n" +
                                "  \"emails\": [\n" +
                                "    {\"address\": \"single-email@example.com\", \"type\": \"work\", \"primary\": false}\n" +
                                "  ]\n" +
                                "}")));

        // Setup mock response for user update
        stubFor(put(urlPathMatching("/admin/directory/v1/users/removetest005"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"removetest005\",\n" +
                                "  \"etag\": \"\\\"removetest005-updated-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"removetest005@example.com\"\n" +
                                "}")));

        // Prepare modifications using remove operations (1→0, trying to remove 2 values)
        Set<AttributeDelta> modifications = new HashSet<>();

        // Try to remove two emails (one existing, one non-existent)
        AttributeDeltaBuilder emailsDelta = new AttributeDeltaBuilder();
        emailsDelta.setName("emails");
        emailsDelta.addValueToRemove("{\"address\":\"single-email@example.com\",\"type\":\"work\",\"primary\":false}");
        emailsDelta.addValueToRemove("{\"address\":\"nonexistent-email@example.com\",\"type\":\"home\",\"primary\":false}");
        modifications.add(emailsDelta.build());

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("removetest005"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify that current user data was fetched first
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathMatching("/admin/directory/v1/users/removetest005")));

        // Verify request contains empty emails array (the one existing value was removed)
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathMatching("/admin/directory/v1/users/removetest005"))
                .withHeader("Content-Type", matching("application/json.*"))
                .withRequestBody(containing("\"emails\":[]")));
    }

    @Test
    @DisplayName("Remove two values from dual-valued attributes (2→0)")
    public void testRemoveTwoValuesFromDual() {
        // Setup mock response for getting current user data (2 values for each multi-valued attribute)
        stubFor(get(urlPathMatching("/admin/directory/v1/users/removetest006"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"removetest006\",\n" +
                                "  \"etag\": \"\\\"removetest006-dual-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"removetest006@example.com\",\n" +
                                "  \"emails\": [\n" +
                                "    {\"address\": \"first-email@example.com\", \"type\": \"work\", \"primary\": false},\n" +
                                "    {\"address\": \"second-email@example.com\", \"type\": \"home\", \"primary\": false}\n" +
                                "  ],\n" +
                                "  \"phones\": [\n" +
                                "    {\"value\": \"+1-555-301-0001\", \"type\": \"work\", \"primary\": true},\n" +
                                "    {\"value\": \"+1-555-301-0002\", \"type\": \"home\", \"primary\": false}\n" +
                                "  ],\n" +
                                "  \"addresses\": [\n" +
                                "    {\"type\": \"work\", \"formatted\": \"301 First Work St\", \"primary\": true},\n" +
                                "    {\"type\": \"home\", \"formatted\": \"302 Second Home St\", \"primary\": false}\n" +
                                "  ],\n" +
                                "  \"organizations\": [\n" +
                                "    {\"name\": \"First Company\", \"title\": \"Developer\", \"primary\": true, \"type\": \"work\"},\n" +
                                "    {\"name\": \"Second Company\", \"title\": \"Consultant\", \"primary\": false, \"type\": \"work\"}\n" +
                                "  ],\n" +
                                "  \"ims\": [\n" +
                                "    {\"im\": \"first@example.org\", \"protocol\": \"jabber\", \"type\": \"work\", \"primary\": true},\n" +
                                "    {\"im\": \"second@example.org\", \"protocol\": \"jabber\", \"type\": \"home\", \"primary\": false}\n" +
                                "  ],\n" +
                                "  \"externalIds\": [\n" +
                                "    {\"value\": \"FIRST-EMP001\", \"type\": \"organization\"},\n" +
                                "    {\"value\": \"SECOND-EMP002\", \"type\": \"organization\"}\n" +
                                "  ],\n" +
                                "  \"relations\": [\n" +
                                "    {\"value\": \"first-manager@example.com\", \"type\": \"manager\"},\n" +
                                "    {\"value\": \"second-manager@example.com\", \"type\": \"manager\"}\n" +
                                "  ]\n" +
                                "}")));

        // Setup mock response for user update
        stubFor(put(urlPathMatching("/admin/directory/v1/users/removetest006"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"removetest006\",\n" +
                                "  \"etag\": \"\\\"removetest006-updated-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"removetest006@example.com\"\n" +
                                "}")));

        // Prepare modifications using remove operations (2→0 for all multi-valued attributes)
        Set<AttributeDelta> modifications = new HashSet<>();

        // Remove both emails
        AttributeDeltaBuilder emailsDelta = new AttributeDeltaBuilder();
        emailsDelta.setName("emails");
        emailsDelta.addValueToRemove("{\"address\":\"first-email@example.com\",\"type\":\"work\",\"primary\":false}");
        emailsDelta.addValueToRemove("{\"address\":\"second-email@example.com\",\"type\":\"home\",\"primary\":false}");
        modifications.add(emailsDelta.build());

        // Remove both phones
        AttributeDeltaBuilder phonesDelta = new AttributeDeltaBuilder();
        phonesDelta.setName("phones");
        phonesDelta.addValueToRemove("{\"value\":\"+1-555-301-0001\",\"type\":\"work\",\"primary\":true}");
        phonesDelta.addValueToRemove("{\"value\":\"+1-555-301-0002\",\"type\":\"home\",\"primary\":false}");
        modifications.add(phonesDelta.build());

        // Remove both addresses
        AttributeDeltaBuilder addressesDelta = new AttributeDeltaBuilder();
        addressesDelta.setName("addresses");
        addressesDelta.addValueToRemove("{\"type\":\"work\",\"formatted\":\"301 First Work St\",\"primary\":true}");
        addressesDelta.addValueToRemove("{\"type\":\"home\",\"formatted\":\"302 Second Home St\",\"primary\":false}");
        modifications.add(addressesDelta.build());

        // Remove both organizations
        AttributeDeltaBuilder organizationsDelta = new AttributeDeltaBuilder();
        organizationsDelta.setName("organizations");
        organizationsDelta.addValueToRemove("{\"name\":\"First Company\",\"title\":\"Developer\",\"primary\":true,\"type\":\"work\"}");
        organizationsDelta.addValueToRemove("{\"name\":\"Second Company\",\"title\":\"Consultant\",\"primary\":false,\"type\":\"work\"}");
        modifications.add(organizationsDelta.build());

        // Remove both ims
        AttributeDeltaBuilder imsDelta = new AttributeDeltaBuilder();
        imsDelta.setName("ims");
        imsDelta.addValueToRemove("{\"im\":\"first@example.org\",\"protocol\":\"jabber\",\"type\":\"work\",\"primary\":true}");
        imsDelta.addValueToRemove("{\"im\":\"second@example.org\",\"protocol\":\"jabber\",\"type\":\"home\",\"primary\":false}");
        modifications.add(imsDelta.build());

        // Remove both externalIds
        AttributeDeltaBuilder externalIdsDelta = new AttributeDeltaBuilder();
        externalIdsDelta.setName("externalIds");
        externalIdsDelta.addValueToRemove("{\"value\":\"FIRST-EMP001\",\"type\":\"organization\"}");
        externalIdsDelta.addValueToRemove("{\"value\":\"SECOND-EMP002\",\"type\":\"organization\"}");
        modifications.add(externalIdsDelta.build());

        // Remove both relations
        AttributeDeltaBuilder relationsDelta = new AttributeDeltaBuilder();
        relationsDelta.setName("relations");
        relationsDelta.addValueToRemove("{\"value\":\"first-manager@example.com\",\"type\":\"manager\"}");
        relationsDelta.addValueToRemove("{\"value\":\"second-manager@example.com\",\"type\":\"manager\"}");
        modifications.add(relationsDelta.build());

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("removetest006"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify that current user data was fetched first
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathMatching("/admin/directory/v1/users/removetest006")));

        // Verify request contains empty arrays for all attributes (all values removed)
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathMatching("/admin/directory/v1/users/removetest006"))
                .withHeader("Content-Type", matching("application/json.*"))
                .withRequestBody(containing("\"emails\":[]"))
                .withRequestBody(containing("\"phones\":[]"))
                .withRequestBody(containing("\"addresses\":[]"))
                .withRequestBody(containing("\"organizations\":[]"))
                .withRequestBody(containing("\"ims\":[]"))
                .withRequestBody(containing("\"externalIds\":[]"))
                .withRequestBody(containing("\"relations\":[]")));
    }

    @Test
    @DisplayName("Remove two values from triple-valued attributes (3→1)")
    public void testRemoveTwoValuesFromTriple() {
        // Setup mock response for getting current user data (3 values for each multi-valued attribute)
        stubFor(get(urlPathMatching("/admin/directory/v1/users/removetest007"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"removetest007\",\n" +
                                "  \"etag\": \"\\\"removetest007-triple-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"removetest007@example.com\",\n" +
                                "  \"emails\": [\n" +
                                "    {\"address\": \"keep-email@example.com\", \"type\": \"work\", \"primary\": false},\n" +
                                "    {\"address\": \"remove1-email@example.com\", \"type\": \"home\", \"primary\": false},\n" +
                                "    {\"address\": \"remove2-email@example.com\", \"type\": \"other\", \"primary\": false}\n" +
                                "  ],\n" +
                                "  \"phones\": [\n" +
                                "    {\"value\": \"+1-555-401-0001\", \"type\": \"work\", \"primary\": true},\n" +
                                "    {\"value\": \"+1-555-401-0002\", \"type\": \"home\", \"primary\": false},\n" +
                                "    {\"value\": \"+1-555-401-0003\", \"type\": \"mobile\", \"primary\": false}\n" +
                                "  ],\n" +
                                "  \"addresses\": [\n" +
                                "    {\"type\": \"work\", \"formatted\": \"401 Keep Work St\", \"primary\": true},\n" +
                                "    {\"type\": \"home\", \"formatted\": \"402 Remove Home St\", \"primary\": false},\n" +
                                "    {\"type\": \"other\", \"formatted\": \"403 Remove Other St\", \"primary\": false}\n" +
                                "  ],\n" +
                                "  \"organizations\": [\n" +
                                "    {\"name\": \"Keep Company\", \"title\": \"Developer\", \"primary\": true, \"type\": \"work\"},\n" +
                                "    {\"name\": \"Remove Company A\", \"title\": \"Consultant\", \"primary\": false, \"type\": \"work\"},\n" +
                                "    {\"name\": \"Remove Company B\", \"title\": \"Advisor\", \"primary\": false, \"type\": \"work\"}\n" +
                                "  ],\n" +
                                "  \"ims\": [\n" +
                                "    {\"im\": \"keep@example.org\", \"protocol\": \"jabber\", \"type\": \"work\", \"primary\": true},\n" +
                                "    {\"im\": \"remove1@example.org\", \"protocol\": \"jabber\", \"type\": \"home\", \"primary\": false},\n" +
                                "    {\"im\": \"remove2@example.org\", \"protocol\": \"jabber\", \"type\": \"other\", \"primary\": false}\n" +
                                "  ],\n" +
                                "  \"externalIds\": [\n" +
                                "    {\"value\": \"KEEP-EMP001\", \"type\": \"organization\"},\n" +
                                "    {\"value\": \"REMOVE1-EMP002\", \"type\": \"organization\"},\n" +
                                "    {\"value\": \"REMOVE2-EMP003\", \"type\": \"organization\"}\n" +
                                "  ],\n" +
                                "  \"relations\": [\n" +
                                "    {\"value\": \"keep-manager@example.com\", \"type\": \"manager\"},\n" +
                                "    {\"value\": \"remove1-manager@example.com\", \"type\": \"manager\"},\n" +
                                "    {\"value\": \"remove2-manager@example.com\", \"type\": \"manager\"}\n" +
                                "  ]\n" +
                                "}")));

        // Setup mock response for user update
        stubFor(put(urlPathMatching("/admin/directory/v1/users/removetest007"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"removetest007\",\n" +
                                "  \"etag\": \"\\\"removetest007-updated-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"removetest007@example.com\"\n" +
                                "}")));

        // Prepare modifications using remove operations (3→1 for all multi-valued attributes)
        Set<AttributeDelta> modifications = new HashSet<>();

        // Remove second and third emails (keep first)
        AttributeDeltaBuilder emailsDelta = new AttributeDeltaBuilder();
        emailsDelta.setName("emails");
        emailsDelta.addValueToRemove("{\"address\":\"remove1-email@example.com\",\"type\":\"home\",\"primary\":false}");
        emailsDelta.addValueToRemove("{\"address\":\"remove2-email@example.com\",\"type\":\"other\",\"primary\":false}");
        modifications.add(emailsDelta.build());

        // Remove second and third phones (keep first)
        AttributeDeltaBuilder phonesDelta = new AttributeDeltaBuilder();
        phonesDelta.setName("phones");
        phonesDelta.addValueToRemove("{\"value\":\"+1-555-401-0002\",\"type\":\"home\",\"primary\":false}");
        phonesDelta.addValueToRemove("{\"value\":\"+1-555-401-0003\",\"type\":\"mobile\",\"primary\":false}");
        modifications.add(phonesDelta.build());

        // Remove second and third addresses (keep first)
        AttributeDeltaBuilder addressesDelta = new AttributeDeltaBuilder();
        addressesDelta.setName("addresses");
        addressesDelta.addValueToRemove("{\"type\":\"home\",\"formatted\":\"402 Remove Home St\",\"primary\":false}");
        addressesDelta.addValueToRemove("{\"type\":\"other\",\"formatted\":\"403 Remove Other St\",\"primary\":false}");
        modifications.add(addressesDelta.build());

        // Remove second and third organizations (keep first)
        AttributeDeltaBuilder organizationsDelta = new AttributeDeltaBuilder();
        organizationsDelta.setName("organizations");
        organizationsDelta.addValueToRemove("{\"name\":\"Remove Company A\",\"title\":\"Consultant\",\"primary\":false,\"type\":\"work\"}");
        organizationsDelta.addValueToRemove("{\"name\":\"Remove Company B\",\"title\":\"Advisor\",\"primary\":false,\"type\":\"work\"}");
        modifications.add(organizationsDelta.build());

        // Remove second and third ims (keep first)
        AttributeDeltaBuilder imsDelta = new AttributeDeltaBuilder();
        imsDelta.setName("ims");
        imsDelta.addValueToRemove("{\"im\":\"remove1@example.org\",\"protocol\":\"jabber\",\"type\":\"home\",\"primary\":false}");
        imsDelta.addValueToRemove("{\"im\":\"remove2@example.org\",\"protocol\":\"jabber\",\"type\":\"other\",\"primary\":false}");
        modifications.add(imsDelta.build());

        // Remove second and third externalIds (keep first)
        AttributeDeltaBuilder externalIdsDelta = new AttributeDeltaBuilder();
        externalIdsDelta.setName("externalIds");
        externalIdsDelta.addValueToRemove("{\"value\":\"REMOVE1-EMP002\",\"type\":\"organization\"}");
        externalIdsDelta.addValueToRemove("{\"value\":\"REMOVE2-EMP003\",\"type\":\"organization\"}");
        modifications.add(externalIdsDelta.build());

        // Remove second and third relations (keep first)
        AttributeDeltaBuilder relationsDelta = new AttributeDeltaBuilder();
        relationsDelta.setName("relations");
        relationsDelta.addValueToRemove("{\"value\":\"remove1-manager@example.com\",\"type\":\"manager\"}");
        relationsDelta.addValueToRemove("{\"value\":\"remove2-manager@example.com\",\"type\":\"manager\"}");
        modifications.add(relationsDelta.build());

        // Execute update
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("removetest007"), modifications, null);

        // Verify results
        assertThat(result).isNotNull();

        // Verify that current user data was fetched first
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathMatching("/admin/directory/v1/users/removetest007")));

        // Verify request contains only the first (kept) values for all attributes
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathMatching("/admin/directory/v1/users/removetest007"))
                .withHeader("Content-Type", matching("application/json.*"))
                // Verify only kept email remains
                .withRequestBody(containing("{\"address\":\"keep-email@example.com\",\"type\":\"work\",\"primary\":\"false\"}"))
                .withRequestBody(not(containing("remove1-email@example.com")))
                .withRequestBody(not(containing("remove2-email@example.com")))
                // Verify only kept phone remains
                .withRequestBody(containing("{\"value\":\"+1-555-401-0001\",\"type\":\"work\",\"primary\":\"true\"}"))
                .withRequestBody(not(containing("+1-555-401-0002")))
                .withRequestBody(not(containing("+1-555-401-0003")))
                // Verify only kept address remains
                .withRequestBody(containing("{\"type\":\"work\",\"formatted\":\"401 Keep Work St\",\"primary\":\"true\"}"))
                .withRequestBody(not(containing("402 Remove Home St")))
                .withRequestBody(not(containing("403 Remove Other St")))
                // Verify only kept organization remains
                .withRequestBody(containing("{\"name\":\"Keep Company\",\"title\":\"Developer\",\"primary\":\"true\",\"type\":\"work\"}"))
                .withRequestBody(not(containing("Remove Company A")))
                .withRequestBody(not(containing("Remove Company B")))
                // Verify only kept im remains
                .withRequestBody(containing("{\"im\":\"keep@example.org\",\"protocol\":\"jabber\",\"type\":\"work\",\"primary\":\"true\"}"))
                .withRequestBody(not(containing("remove1@example.org")))
                .withRequestBody(not(containing("remove2@example.org")))
                // Verify only kept externalId remains
                .withRequestBody(containing("{\"value\":\"KEEP-EMP001\",\"type\":\"organization\"}"))
                .withRequestBody(not(containing("REMOVE1-EMP002")))
                .withRequestBody(not(containing("REMOVE2-EMP003")))
                // Verify only kept relation remains
                .withRequestBody(containing("{\"value\":\"keep-manager@example.com\",\"type\":\"manager\"}"))
                .withRequestBody(not(containing("remove1-manager@example.com")))
                .withRequestBody(not(containing("remove2-manager@example.com"))));
    }
}