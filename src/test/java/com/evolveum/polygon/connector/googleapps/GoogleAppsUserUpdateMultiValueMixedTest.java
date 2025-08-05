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
 * Tests for GoogleAppsConnector User multi-valued attribute Mixed (Add+Remove) operations
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsUserUpdateMultiValueMixedTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Add+1 & Remove-1 → same count mixed operations (1→1)")
    public void testMixedAddOneRemoveOneFromSingle() {
        // Setup mock response for getting current user data (single values for each multi-valued attribute)
        stubFor(get(urlPathMatching("/admin/directory/v1/users/mixedtest001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"mixedtest001\",\n" +
                                "  \"etag\": \"\\\"mixedtest001-single-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"mixedtest001@example.com\",\n" +
                                "  \"emails\": [{\"address\": \"old.email@example.com\", \"type\": \"work\", \"primary\": false}],\n" +
                                "  \"phones\": [{\"value\": \"+1-555-0001\", \"type\": \"work\"}],\n" +
                                "  \"addresses\": [{\"type\": \"work\", \"formatted\": \"123 Old St, City, ST 12345\"}],\n" +
                                "  \"organizations\": [{\"name\": \"Old Corp\", \"type\": \"work\"}],\n" +
                                "  \"ims\": [{\"im\": \"old.user\", \"protocol\": \"gtalk\"}],\n" +
                                "  \"externalIds\": [{\"value\": \"OLD001\", \"type\": \"organization\"}],\n" +
                                "  \"relations\": [{\"value\": \"Old Manager\", \"type\": \"manager\"}]\n" +
                                "}")));

        // Setup mock response for user update
        stubFor(put(urlPathMatching("/admin/directory/v1/users/mixedtest001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"mixedtest001\",\n" +
                                "  \"etag\": \"\\\"mixedtest001-updated-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"mixedtest001@example.com\"\n" +
                                "}")));

        // Prepare modifications using mixed add/remove operations (1→1 for all multi-valued attributes)
        Set<AttributeDelta> modifications = new HashSet<>();

        // emails: Remove old + Add new
        modifications.add(AttributeDeltaBuilder.build("emails",
                Set.of("{\"address\": \"new.email@example.com\", \"type\": \"work\", \"primary\": false}"),
                Set.of("{\"address\": \"old.email@example.com\", \"type\": \"work\", \"primary\": false}")));

        // phones: Remove old + Add new
        modifications.add(AttributeDeltaBuilder.build("phones",
                Set.of("{\"value\": \"+1-555-9999\", \"type\": \"work\"}"),
                Set.of("{\"value\": \"+1-555-0001\", \"type\": \"work\"}")));

        // addresses: Remove old + Add new
        modifications.add(AttributeDeltaBuilder.build("addresses",
                Set.of("{\"type\": \"work\", \"formatted\": \"999 New Ave, City, ST 99999\"}"),
                Set.of("{\"type\": \"work\", \"formatted\": \"123 Old St, City, ST 12345\"}")));

        // organizations: Remove old + Add new
        modifications.add(AttributeDeltaBuilder.build("organizations",
                Set.of("{\"name\": \"New Corp\", \"type\": \"work\"}"),
                Set.of("{\"name\": \"Old Corp\", \"type\": \"work\"}")));

        // ims: Remove old + Add new
        modifications.add(AttributeDeltaBuilder.build("ims",
                Set.of("{\"im\": \"new.user\", \"protocol\": \"gtalk\"}"),
                Set.of("{\"im\": \"old.user\", \"protocol\": \"gtalk\"}")));

        // externalIds: Remove old + Add new  
        modifications.add(AttributeDeltaBuilder.build("externalIds",
                Set.of("{\"value\": \"NEW999\", \"type\": \"organization\"}"),
                Set.of("{\"value\": \"OLD001\", \"type\": \"organization\"}")));

        // relations: Remove old + Add new
        modifications.add(AttributeDeltaBuilder.build("relations",
                Set.of("{\"value\": \"New Manager\", \"type\": \"manager\"}"),
                Set.of("{\"value\": \"Old Manager\", \"type\": \"manager\"}")));

        // Execute updateDelta operation
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("mixedtest001"), modifications, null);

        // Verify result
        assertThat(result).isNotNull();

        // Verify that the PUT request contains expected new values and excludes old values (bug fix test)
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathMatching("/admin/directory/v1/users/mixedtest001"))
                .withHeader("Content-Type", matching("application/json.*"))
                // Verify new email added, old email removed
                .withRequestBody(containing("new.email@example.com"))
                .withRequestBody(not(containing("old.email@example.com")))
                // Verify new phone added, old phone removed
                .withRequestBody(containing("+1-555-9999"))
                .withRequestBody(not(containing("+1-555-0001")))
                // Verify new address added, old address removed
                .withRequestBody(containing("999 New Ave, City, ST 99999"))
                .withRequestBody(not(containing("123 Old St, City, ST 12345")))
                // Verify new organization added, old organization removed
                .withRequestBody(containing("New Corp"))
                .withRequestBody(not(containing("Old Corp")))
                // Verify new im added, old im removed
                .withRequestBody(containing("new.user"))
                .withRequestBody(not(containing("old.user")))
                // Verify new externalId added, old externalId removed
                .withRequestBody(containing("NEW999"))
                .withRequestBody(not(containing("OLD001")))
                // Verify new relation added, old relation removed
                .withRequestBody(containing("New Manager"))
                .withRequestBody(not(containing("Old Manager"))));
    }

    @Test
    @DisplayName("Add+2 & Remove-1 → increase operations (1→2)")
    public void testMixedAddTwoRemoveOneFromSingle() {
        // Setup mock response for getting current user data (single values)
        stubFor(get(urlPathMatching("/admin/directory/v1/users/mixedtest002"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"mixedtest002\",\n" +
                                "  \"etag\": \"\\\"mixedtest002-single-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"mixedtest002@example.com\",\n" +
                                "  \"emails\": [{\"address\": \"old.email@example.com\", \"type\": \"work\", \"primary\": false}],\n" +
                                "  \"phones\": [{\"value\": \"+1-555-0001\", \"type\": \"work\"}],\n" +
                                "  \"addresses\": [{\"type\": \"work\", \"formatted\": \"123 Old St, City, ST 12345\"}],\n" +
                                "  \"organizations\": [{\"name\": \"Old Corp\", \"type\": \"work\"}],\n" +
                                "  \"ims\": [{\"im\": \"old.user\", \"protocol\": \"gtalk\"}],\n" +
                                "  \"externalIds\": [{\"value\": \"OLD001\", \"type\": \"organization\"}],\n" +
                                "  \"relations\": [{\"value\": \"Old Manager\", \"type\": \"manager\"}]\n" +
                                "}")));

        // Setup mock response for user update
        stubFor(put(urlPathMatching("/admin/directory/v1/users/mixedtest002"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"mixedtest002\",\n" +
                                "  \"etag\": \"\\\"mixedtest002-updated-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"mixedtest002@example.com\"\n" +
                                "}")));

        // Prepare modifications using mixed add/remove operations (1→2 for all multi-valued attributes)
        Set<AttributeDelta> modifications = new HashSet<>();

        // emails: Remove 1 + Add 2 → net +1
        modifications.add(AttributeDeltaBuilder.build("emails",
                Set.of(
                        "{\"address\": \"new1.email@example.com\", \"type\": \"work\", \"primary\": false}",
                        "{\"address\": \"new2.email@example.com\", \"type\": \"home\", \"primary\": false}"
                ),
                Set.of("{\"address\": \"old.email@example.com\", \"type\": \"work\", \"primary\": false}")));

        // phones: Remove 1 + Add 2 → net +1
        modifications.add(AttributeDeltaBuilder.build("phones",
                Set.of(
                        "{\"value\": \"+1-555-1111\", \"type\": \"work\"}",
                        "{\"value\": \"+1-555-2222\", \"type\": \"home\"}"
                ),
                Set.of("{\"value\": \"+1-555-0001\", \"type\": \"work\"}")));

        // addresses: Remove 1 + Add 2 → net +1
        modifications.add(AttributeDeltaBuilder.build("addresses",
                Set.of(
                        "{\"type\": \"work\", \"formatted\": \"111 New Work Ave, City, ST 11111\"}",
                        "{\"type\": \"home\", \"formatted\": \"222 New Home St, City, ST 22222\"}"
                ),
                Set.of("{\"type\": \"work\", \"formatted\": \"123 Old St, City, ST 12345\"}")));

        // organizations: Remove 1 + Add 2 → net +1
        modifications.add(AttributeDeltaBuilder.build("organizations",
                Set.of(
                        "{\"name\": \"New Corp 1\", \"type\": \"work\"}",
                        "{\"name\": \"New Corp 2\", \"type\": \"other\"}"
                ),
                Set.of("{\"name\": \"Old Corp\", \"type\": \"work\"}")));

        // ims: Remove 1 + Add 2 → net +1
        modifications.add(AttributeDeltaBuilder.build("ims",
                Set.of(
                        "{\"im\": \"new1.user\", \"protocol\": \"gtalk\"}",
                        "{\"im\": \"new2.user\", \"protocol\": \"skype\"}"
                ),
                Set.of("{\"im\": \"old.user\", \"protocol\": \"gtalk\"}")));

        // externalIds: Remove 1 + Add 2 → net +1
        modifications.add(AttributeDeltaBuilder.build("externalIds",
                Set.of(
                        "{\"value\": \"NEW111\", \"type\": \"organization\"}",
                        "{\"value\": \"NEW222\", \"type\": \"custom\"}"
                ),
                Set.of("{\"value\": \"OLD001\", \"type\": \"organization\"}")));

        // relations: Remove 1 + Add 2 → net +1
        modifications.add(AttributeDeltaBuilder.build("relations",
                Set.of(
                        "{\"value\": \"New Manager 1\", \"type\": \"manager\"}",
                        "{\"value\": \"New Assistant\", \"type\": \"assistant\"}"
                ),
                Set.of("{\"value\": \"Old Manager\", \"type\": \"manager\"}")));

        // Execute updateDelta operation
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("mixedtest002"), modifications, null);

        // Verify result
        assertThat(result).isNotNull();

        // Verify that the PUT request contains 2 new values and excludes old values (1→2 operation)
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathMatching("/admin/directory/v1/users/mixedtest002"))
                .withHeader("Content-Type", matching("application/json.*"))
                // Verify 2 new emails added, old email removed
                .withRequestBody(containing("new1.email@example.com"))
                .withRequestBody(containing("new2.email@example.com"))
                .withRequestBody(not(containing("old.email@example.com")))
                // Verify 2 new phones added, old phone removed
                .withRequestBody(containing("+1-555-1111"))
                .withRequestBody(containing("+1-555-2222"))
                .withRequestBody(not(containing("+1-555-0001")))
                // Verify 2 new addresses added, old address removed
                .withRequestBody(containing("111 New Work Ave, City, ST 11111"))
                .withRequestBody(containing("222 New Home St, City, ST 22222"))
                .withRequestBody(not(containing("123 Old St, City, ST 12345")))
                // Verify 2 new organizations added, old organization removed
                .withRequestBody(containing("New Corp 1"))
                .withRequestBody(containing("New Corp 2"))
                .withRequestBody(not(containing("Old Corp")))
                // Verify 2 new ims added, old im removed
                .withRequestBody(containing("new1.user"))
                .withRequestBody(containing("new2.user"))
                .withRequestBody(not(containing("old.user")))
                // Verify 2 new externalIds added, old externalId removed
                .withRequestBody(containing("NEW111"))
                .withRequestBody(containing("NEW222"))
                .withRequestBody(not(containing("OLD001")))
                // Verify 2 new relations added, old relation removed
                .withRequestBody(containing("New Manager 1"))
                .withRequestBody(containing("New Assistant"))
                .withRequestBody(not(containing("Old Manager"))));
    }

    @Test
    @DisplayName("Add+1 & Remove-2 → decrease operations (2→1)")
    public void testMixedAddOneRemoveTwoFromDual() {
        // Setup mock response for getting current user data (dual values)
        stubFor(get(urlPathMatching("/admin/directory/v1/users/mixedtest003"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"mixedtest003\",\n" +
                                "  \"etag\": \"\\\"mixedtest003-dual-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"mixedtest003@example.com\",\n" +
                                "  \"emails\": [\n" +
                                "    {\"address\": \"old1.email@example.com\", \"type\": \"work\", \"primary\": false},\n" +
                                "    {\"address\": \"old2.email@example.com\", \"type\": \"home\", \"primary\": false}\n" +
                                "  ],\n" +
                                "  \"phones\": [\n" +
                                "    {\"value\": \"+1-555-0001\", \"type\": \"work\"},\n" +
                                "    {\"value\": \"+1-555-0002\", \"type\": \"home\"}\n" +
                                "  ],\n" +
                                "  \"addresses\": [\n" +
                                "    {\"type\": \"work\", \"formatted\": \"123 Old Work St, City, ST 12345\"},\n" +
                                "    {\"type\": \"home\", \"formatted\": \"456 Old Home Ave, City, ST 45678\"}\n" +
                                "  ],\n" +
                                "  \"organizations\": [\n" +
                                "    {\"name\": \"Old Corp 1\", \"type\": \"work\"},\n" +
                                "    {\"name\": \"Old Corp 2\", \"type\": \"other\"}\n" +
                                "  ],\n" +
                                "  \"ims\": [\n" +
                                "    {\"im\": \"old1.user\", \"protocol\": \"gtalk\"},\n" +
                                "    {\"im\": \"old2.user\", \"protocol\": \"skype\"}\n" +
                                "  ],\n" +
                                "  \"externalIds\": [\n" +
                                "    {\"value\": \"OLD001\", \"type\": \"organization\"},\n" +
                                "    {\"value\": \"OLD002\", \"type\": \"custom\"}\n" +
                                "  ],\n" +
                                "  \"relations\": [\n" +
                                "    {\"value\": \"Old Manager\", \"type\": \"manager\"},\n" +
                                "    {\"value\": \"Old Assistant\", \"type\": \"assistant\"}\n" +
                                "  ]\n" +
                                "}")));

        // Setup mock response for user update
        stubFor(put(urlPathMatching("/admin/directory/v1/users/mixedtest003"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"mixedtest003\",\n" +
                                "  \"etag\": \"\\\"mixedtest003-updated-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"mixedtest003@example.com\"\n" +
                                "}")));

        // Prepare modifications using mixed add/remove operations (2→1 for all multi-valued attributes)
        Set<AttributeDelta> modifications = new HashSet<>();

        // emails: Remove 2 + Add 1 → net -1
        modifications.add(AttributeDeltaBuilder.build("emails",
                Set.of("{\"address\": \"new.email@example.com\", \"type\": \"work\", \"primary\": false}"),
                Set.of(
                        "{\"address\": \"old1.email@example.com\", \"type\": \"work\", \"primary\": false}",
                        "{\"address\": \"old2.email@example.com\", \"type\": \"home\", \"primary\": false}"
                )));

        // phones: Remove 2 + Add 1 → net -1
        modifications.add(AttributeDeltaBuilder.build("phones",
                Set.of("{\"value\": \"+1-555-9999\", \"type\": \"work\"}"),
                Set.of(
                        "{\"value\": \"+1-555-0001\", \"type\": \"work\"}",
                        "{\"value\": \"+1-555-0002\", \"type\": \"home\"}"
                )));

        // addresses: Remove 2 + Add 1 → net -1
        modifications.add(AttributeDeltaBuilder.build("addresses",
                Set.of("{\"type\": \"work\", \"formatted\": \"999 New St, City, ST 99999\"}"),
                Set.of(
                        "{\"type\": \"work\", \"formatted\": \"123 Old Work St, City, ST 12345\"}",
                        "{\"type\": \"home\", \"formatted\": \"456 Old Home Ave, City, ST 45678\"}"
                )));

        // organizations: Remove 2 + Add 1 → net -1
        modifications.add(AttributeDeltaBuilder.build("organizations",
                Set.of("{\"name\": \"New Corp\", \"type\": \"work\"}"),
                Set.of(
                        "{\"name\": \"Old Corp 1\", \"type\": \"work\"}",
                        "{\"name\": \"Old Corp 2\", \"type\": \"other\"}"
                )));

        // ims: Remove 2 + Add 1 → net -1
        modifications.add(AttributeDeltaBuilder.build("ims",
                Set.of("{\"im\": \"new.user\", \"protocol\": \"gtalk\"}"),
                Set.of(
                        "{\"im\": \"old1.user\", \"protocol\": \"gtalk\"}",
                        "{\"im\": \"old2.user\", \"protocol\": \"skype\"}"
                )));

        // externalIds: Remove 2 + Add 1 → net -1
        modifications.add(AttributeDeltaBuilder.build("externalIds",
                Set.of("{\"value\": \"NEW999\", \"type\": \"organization\"}"),
                Set.of(
                        "{\"value\": \"OLD001\", \"type\": \"organization\"}",
                        "{\"value\": \"OLD002\", \"type\": \"custom\"}"
                )));

        // relations: Remove 2 + Add 1 → net -1
        modifications.add(AttributeDeltaBuilder.build("relations",
                Set.of("{\"value\": \"New Manager\", \"type\": \"manager\"}"),
                Set.of(
                        "{\"value\": \"Old Manager\", \"type\": \"manager\"}",
                        "{\"value\": \"Old Assistant\", \"type\": \"assistant\"}"
                )));

        // Execute updateDelta operation
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("mixedtest003"), modifications, null);

        // Verify result
        assertThat(result).isNotNull();

        // Verify that the PUT request contains 1 new value and excludes 2 old values (2→1 operation)
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathMatching("/admin/directory/v1/users/mixedtest003"))
                .withHeader("Content-Type", matching("application/json.*"))
                // Verify 1 new email added, 2 old emails removed
                .withRequestBody(containing("new.email@example.com"))
                .withRequestBody(not(containing("old1.email@example.com")))
                .withRequestBody(not(containing("old2.email@example.com")))
                // Verify 1 new phone added, 2 old phones removed
                .withRequestBody(containing("+1-555-9999"))
                .withRequestBody(not(containing("+1-555-0001")))
                .withRequestBody(not(containing("+1-555-0002")))
                // Verify 1 new address added, 2 old addresses removed
                .withRequestBody(containing("999 New St, City, ST 99999"))
                .withRequestBody(not(containing("123 Old Work St, City, ST 12345")))
                .withRequestBody(not(containing("456 Old Home Ave, City, ST 45678")))
                // Verify 1 new organization added, 2 old organizations removed
                .withRequestBody(containing("New Corp"))
                .withRequestBody(not(containing("Old Corp 1")))
                .withRequestBody(not(containing("Old Corp 2")))
                // Verify 1 new im added, 2 old ims removed
                .withRequestBody(containing("new.user"))
                .withRequestBody(not(containing("old1.user")))
                .withRequestBody(not(containing("old2.user")))
                // Verify 1 new externalId added, 2 old externalIds removed
                .withRequestBody(containing("NEW999"))
                .withRequestBody(not(containing("OLD001")))
                .withRequestBody(not(containing("OLD002")))
                // Verify 1 new relation added, 2 old relations removed
                .withRequestBody(containing("New Manager"))
                .withRequestBody(not(containing("Old Manager")))
                .withRequestBody(not(containing("Old Assistant"))));
    }

    @Test
    @DisplayName("Add+2 & Remove-2 → same count complex operations (2→2)")
    public void testMixedAddTwoRemoveTwoFromDual() {
        // Setup mock response for getting current user data (dual values)
        stubFor(get(urlPathMatching("/admin/directory/v1/users/mixedtest004"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"mixedtest004\",\n" +
                                "  \"etag\": \"\\\"mixedtest004-dual-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"mixedtest004@example.com\",\n" +
                                "  \"emails\": [\n" +
                                "    {\"address\": \"old1.email@example.com\", \"type\": \"work\", \"primary\": false},\n" +
                                "    {\"address\": \"old2.email@example.com\", \"type\": \"home\", \"primary\": false}\n" +
                                "  ],\n" +
                                "  \"phones\": [\n" +
                                "    {\"value\": \"+1-555-0001\", \"type\": \"work\"},\n" +
                                "    {\"value\": \"+1-555-0002\", \"type\": \"home\"}\n" +
                                "  ],\n" +
                                "  \"addresses\": [\n" +
                                "    {\"type\": \"work\", \"formatted\": \"123 Old Work St, City, ST 12345\"},\n" +
                                "    {\"type\": \"home\", \"formatted\": \"456 Old Home Ave, City, ST 45678\"}\n" +
                                "  ],\n" +
                                "  \"organizations\": [\n" +
                                "    {\"name\": \"Old Corp 1\", \"type\": \"work\"},\n" +
                                "    {\"name\": \"Old Corp 2\", \"type\": \"other\"}\n" +
                                "  ],\n" +
                                "  \"ims\": [\n" +
                                "    {\"im\": \"old1.user\", \"protocol\": \"gtalk\"},\n" +
                                "    {\"im\": \"old2.user\", \"protocol\": \"skype\"}\n" +
                                "  ],\n" +
                                "  \"externalIds\": [\n" +
                                "    {\"value\": \"OLD001\", \"type\": \"organization\"},\n" +
                                "    {\"value\": \"OLD002\", \"type\": \"custom\"}\n" +
                                "  ],\n" +
                                "  \"relations\": [\n" +
                                "    {\"value\": \"Old Manager\", \"type\": \"manager\"},\n" +
                                "    {\"value\": \"Old Assistant\", \"type\": \"assistant\"}\n" +
                                "  ]\n" +
                                "}")));

        // Setup mock response for user update
        stubFor(put(urlPathMatching("/admin/directory/v1/users/mixedtest004"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"mixedtest004\",\n" +
                                "  \"etag\": \"\\\"mixedtest004-updated-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"mixedtest004@example.com\"\n" +
                                "}")));

        // Prepare modifications using mixed add/remove operations (2→2 for all multi-valued attributes)
        Set<AttributeDelta> modifications = new HashSet<>();

        // emails: Remove 2 + Add 2 → net 0, complete replacement
        modifications.add(AttributeDeltaBuilder.build("emails",
                Set.of(
                        "{\"address\": \"new1.email@example.com\", \"type\": \"work\", \"primary\": false}",
                        "{\"address\": \"new2.email@example.com\", \"type\": \"home\", \"primary\": false}"
                ),
                Set.of(
                        "{\"address\": \"old1.email@example.com\", \"type\": \"work\", \"primary\": false}",
                        "{\"address\": \"old2.email@example.com\", \"type\": \"home\", \"primary\": false}"
                )));

        // phones: Remove 2 + Add 2 → net 0, complete replacement
        modifications.add(AttributeDeltaBuilder.build("phones",
                Set.of(
                        "{\"value\": \"+1-555-1111\", \"type\": \"work\"}",
                        "{\"value\": \"+1-555-2222\", \"type\": \"home\"}"
                ),
                Set.of(
                        "{\"value\": \"+1-555-0001\", \"type\": \"work\"}",
                        "{\"value\": \"+1-555-0002\", \"type\": \"home\"}"
                )));

        // addresses: Remove 2 + Add 2 → net 0, complete replacement
        modifications.add(AttributeDeltaBuilder.build("addresses",
                Set.of(
                        "{\"type\": \"work\", \"formatted\": \"111 New Work St, City, ST 11111\"}",
                        "{\"type\": \"home\", \"formatted\": \"222 New Home Ave, City, ST 22222\"}"
                ),
                Set.of(
                        "{\"type\": \"work\", \"formatted\": \"123 Old Work St, City, ST 12345\"}",
                        "{\"type\": \"home\", \"formatted\": \"456 Old Home Ave, City, ST 45678\"}"
                )));

        // organizations: Remove 2 + Add 2 → net 0, complete replacement
        modifications.add(AttributeDeltaBuilder.build("organizations",
                Set.of(
                        "{\"name\": \"New Corp 1\", \"type\": \"work\"}",
                        "{\"name\": \"New Corp 2\", \"type\": \"other\"}"
                ),
                Set.of(
                        "{\"name\": \"Old Corp 1\", \"type\": \"work\"}",
                        "{\"name\": \"Old Corp 2\", \"type\": \"other\"}"
                )));

        // ims: Remove 2 + Add 2 → net 0, complete replacement
        modifications.add(AttributeDeltaBuilder.build("ims",
                Set.of(
                        "{\"im\": \"new1.user\", \"protocol\": \"gtalk\"}",
                        "{\"im\": \"new2.user\", \"protocol\": \"skype\"}"
                ),
                Set.of(
                        "{\"im\": \"old1.user\", \"protocol\": \"gtalk\"}",
                        "{\"im\": \"old2.user\", \"protocol\": \"skype\"}"
                )));

        // externalIds: Remove 2 + Add 2 → net 0, complete replacement
        modifications.add(AttributeDeltaBuilder.build("externalIds",
                Set.of(
                        "{\"value\": \"NEW111\", \"type\": \"organization\"}",
                        "{\"value\": \"NEW222\", \"type\": \"custom\"}"
                ),
                Set.of(
                        "{\"value\": \"OLD001\", \"type\": \"organization\"}",
                        "{\"value\": \"OLD002\", \"type\": \"custom\"}"
                )));

        // relations: Remove 2 + Add 2 → net 0, complete replacement
        modifications.add(AttributeDeltaBuilder.build("relations",
                Set.of(
                        "{\"value\": \"New Manager\", \"type\": \"manager\"}",
                        "{\"value\": \"New Assistant\", \"type\": \"assistant\"}"
                ),
                Set.of(
                        "{\"value\": \"Old Manager\", \"type\": \"manager\"}",
                        "{\"value\": \"Old Assistant\", \"type\": \"assistant\"}"
                )));

        // Execute updateDelta operation
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("mixedtest004"), modifications, null);

        // Verify result
        assertThat(result).isNotNull();

        // Verify that the PUT request contains 2 new values and excludes 2 old values (2→2 complete replacement)
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathMatching("/admin/directory/v1/users/mixedtest004"))
                .withHeader("Content-Type", matching("application/json.*"))
                // Verify 2 new emails added, 2 old emails removed
                .withRequestBody(containing("new1.email@example.com"))
                .withRequestBody(containing("new2.email@example.com"))
                .withRequestBody(not(containing("old1.email@example.com")))
                .withRequestBody(not(containing("old2.email@example.com")))
                // Verify 2 new phones added, 2 old phones removed
                .withRequestBody(containing("+1-555-1111"))
                .withRequestBody(containing("+1-555-2222"))
                .withRequestBody(not(containing("+1-555-0001")))
                .withRequestBody(not(containing("+1-555-0002")))
                // Verify 2 new addresses added, 2 old addresses removed
                .withRequestBody(containing("111 New Work St, City, ST 11111"))
                .withRequestBody(containing("222 New Home Ave, City, ST 22222"))
                .withRequestBody(not(containing("123 Old Work St, City, ST 12345")))
                .withRequestBody(not(containing("456 Old Home Ave, City, ST 45678")))
                // Verify 2 new organizations added, 2 old organizations removed
                .withRequestBody(containing("New Corp 1"))
                .withRequestBody(containing("New Corp 2"))
                .withRequestBody(not(containing("Old Corp 1")))
                .withRequestBody(not(containing("Old Corp 2")))
                // Verify 2 new ims added, 2 old ims removed
                .withRequestBody(containing("new1.user"))
                .withRequestBody(containing("new2.user"))
                .withRequestBody(not(containing("old1.user")))
                .withRequestBody(not(containing("old2.user")))
                // Verify 2 new externalIds added, 2 old externalIds removed
                .withRequestBody(containing("NEW111"))
                .withRequestBody(containing("NEW222"))
                .withRequestBody(not(containing("OLD001")))
                .withRequestBody(not(containing("OLD002")))
                // Verify 2 new relations added, 2 old relations removed
                .withRequestBody(containing("New Manager"))
                .withRequestBody(containing("New Assistant"))
                .withRequestBody(not(containing("Old Manager")))
                .withRequestBody(not(containing("Old Assistant"))));
    }

    @Test
    @DisplayName("Add+2 & Remove-3 → complex decrease operations (3→2)")
    public void testMixedAddTwoRemoveThreeFromTriple() {
        // Setup mock response for getting current user data (triple values)
        stubFor(get(urlPathMatching("/admin/directory/v1/users/mixedtest005"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"mixedtest005\",\n" +
                                "  \"etag\": \"\\\"mixedtest005-triple-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"mixedtest005@example.com\",\n" +
                                "  \"emails\": [\n" +
                                "    {\"address\": \"old1.email@example.com\", \"type\": \"work\", \"primary\": false},\n" +
                                "    {\"address\": \"old2.email@example.com\", \"type\": \"home\", \"primary\": false},\n" +
                                "    {\"address\": \"old3.email@example.com\", \"type\": \"other\", \"primary\": false}\n" +
                                "  ],\n" +
                                "  \"phones\": [\n" +
                                "    {\"value\": \"+1-555-0001\", \"type\": \"work\"},\n" +
                                "    {\"value\": \"+1-555-0002\", \"type\": \"home\"},\n" +
                                "    {\"value\": \"+1-555-0003\", \"type\": \"mobile\"}\n" +
                                "  ],\n" +
                                "  \"addresses\": [\n" +
                                "    {\"type\": \"work\", \"formatted\": \"123 Old Work St, City, ST 12345\"},\n" +
                                "    {\"type\": \"home\", \"formatted\": \"456 Old Home Ave, City, ST 45678\"},\n" +
                                "    {\"type\": \"other\", \"formatted\": \"789 Old Other Rd, City, ST 78901\"}\n" +
                                "  ],\n" +
                                "  \"organizations\": [\n" +
                                "    {\"name\": \"Old Corp 1\", \"type\": \"work\"},\n" +
                                "    {\"name\": \"Old Corp 2\", \"type\": \"other\"},\n" +
                                "    {\"name\": \"Old Corp 3\", \"type\": \"school\"}\n" +
                                "  ],\n" +
                                "  \"ims\": [\n" +
                                "    {\"im\": \"old1.user\", \"protocol\": \"gtalk\"},\n" +
                                "    {\"im\": \"old2.user\", \"protocol\": \"skype\"},\n" +
                                "    {\"im\": \"old3.user\", \"protocol\": \"yahoo\"}\n" +
                                "  ],\n" +
                                "  \"externalIds\": [\n" +
                                "    {\"value\": \"OLD001\", \"type\": \"organization\"},\n" +
                                "    {\"value\": \"OLD002\", \"type\": \"custom\"},\n" +
                                "    {\"value\": \"OLD003\", \"type\": \"account\"}\n" +
                                "  ],\n" +
                                "  \"relations\": [\n" +
                                "    {\"value\": \"Old Manager\", \"type\": \"manager\"},\n" +
                                "    {\"value\": \"Old Assistant\", \"type\": \"assistant\"},\n" +
                                "    {\"value\": \"Old Spouse\", \"type\": \"spouse\"}\n" +
                                "  ]\n" +
                                "}")));

        // Setup mock response for user update
        stubFor(put(urlPathMatching("/admin/directory/v1/users/mixedtest005"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"mixedtest005\",\n" +
                                "  \"etag\": \"\\\"mixedtest005-updated-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"mixedtest005@example.com\"\n" +
                                "}")));

        // Prepare modifications using mixed add/remove operations (3→2 for all multi-valued attributes)
        Set<AttributeDelta> modifications = new HashSet<>();

        // emails: Remove 3 + Add 2 → net -1
        modifications.add(AttributeDeltaBuilder.build("emails",
                Set.of(
                        "{\"address\": \"new1.email@example.com\", \"type\": \"work\", \"primary\": false}",
                        "{\"address\": \"new2.email@example.com\", \"type\": \"home\", \"primary\": false}"
                ),
                Set.of(
                        "{\"address\": \"old1.email@example.com\", \"type\": \"work\", \"primary\": false}",
                        "{\"address\": \"old2.email@example.com\", \"type\": \"home\", \"primary\": false}",
                        "{\"address\": \"old3.email@example.com\", \"type\": \"other\", \"primary\": false}"
                )));

        // phones: Remove 3 + Add 2 → net -1
        modifications.add(AttributeDeltaBuilder.build("phones",
                Set.of(
                        "{\"value\": \"+1-555-1111\", \"type\": \"work\"}",
                        "{\"value\": \"+1-555-2222\", \"type\": \"home\"}"
                ),
                Set.of(
                        "{\"value\": \"+1-555-0001\", \"type\": \"work\"}",
                        "{\"value\": \"+1-555-0002\", \"type\": \"home\"}",
                        "{\"value\": \"+1-555-0003\", \"type\": \"mobile\"}"
                )));

        // addresses: Remove 3 + Add 2 → net -1
        modifications.add(AttributeDeltaBuilder.build("addresses",
                Set.of(
                        "{\"type\": \"work\", \"formatted\": \"111 New Work St, City, ST 11111\"}",
                        "{\"type\": \"home\", \"formatted\": \"222 New Home Ave, City, ST 22222\"}"
                ),
                Set.of(
                        "{\"type\": \"work\", \"formatted\": \"123 Old Work St, City, ST 12345\"}",
                        "{\"type\": \"home\", \"formatted\": \"456 Old Home Ave, City, ST 45678\"}",
                        "{\"type\": \"other\", \"formatted\": \"789 Old Other Rd, City, ST 78901\"}"
                )));

        // organizations: Remove 3 + Add 2 → net -1
        modifications.add(AttributeDeltaBuilder.build("organizations",
                Set.of(
                        "{\"name\": \"New Corp 1\", \"type\": \"work\"}",
                        "{\"name\": \"New Corp 2\", \"type\": \"other\"}"
                ),
                Set.of(
                        "{\"name\": \"Old Corp 1\", \"type\": \"work\"}",
                        "{\"name\": \"Old Corp 2\", \"type\": \"other\"}",
                        "{\"name\": \"Old Corp 3\", \"type\": \"school\"}"
                )));

        // ims: Remove 3 + Add 2 → net -1
        modifications.add(AttributeDeltaBuilder.build("ims",
                Set.of(
                        "{\"im\": \"new1.user\", \"protocol\": \"gtalk\"}",
                        "{\"im\": \"new2.user\", \"protocol\": \"skype\"}"
                ),
                Set.of(
                        "{\"im\": \"old1.user\", \"protocol\": \"gtalk\"}",
                        "{\"im\": \"old2.user\", \"protocol\": \"skype\"}",
                        "{\"im\": \"old3.user\", \"protocol\": \"yahoo\"}"
                )));

        // externalIds: Remove 3 + Add 2 → net -1
        modifications.add(AttributeDeltaBuilder.build("externalIds",
                Set.of(
                        "{\"value\": \"NEW111\", \"type\": \"organization\"}",
                        "{\"value\": \"NEW222\", \"type\": \"custom\"}"
                ),
                Set.of(
                        "{\"value\": \"OLD001\", \"type\": \"organization\"}",
                        "{\"value\": \"OLD002\", \"type\": \"custom\"}",
                        "{\"value\": \"OLD003\", \"type\": \"account\"}"
                )));

        // relations: Remove 3 + Add 2 → net -1
        modifications.add(AttributeDeltaBuilder.build("relations",
                Set.of(
                        "{\"value\": \"New Manager\", \"type\": \"manager\"}",
                        "{\"value\": \"New Assistant\", \"type\": \"assistant\"}"
                ),
                Set.of(
                        "{\"value\": \"Old Manager\", \"type\": \"manager\"}",
                        "{\"value\": \"Old Assistant\", \"type\": \"assistant\"}",
                        "{\"value\": \"Old Spouse\", \"type\": \"spouse\"}"
                )));

        // Execute updateDelta operation
        Set<AttributeDelta> result = connectorFacade.updateDelta(ObjectClass.ACCOUNT,
                new Uid("mixedtest005"), modifications, null);

        // Verify result
        assertThat(result).isNotNull();

        // Verify that the PUT request contains 2 new values and excludes 3 old values (3→2 complex decrease)
        GoogleApiMockServer.verifyRequest(putRequestedFor(urlPathMatching("/admin/directory/v1/users/mixedtest005"))
                .withHeader("Content-Type", matching("application/json.*"))
                // Verify 2 new emails added, 3 old emails removed
                .withRequestBody(containing("new1.email@example.com"))
                .withRequestBody(containing("new2.email@example.com"))
                .withRequestBody(not(containing("old1.email@example.com")))
                .withRequestBody(not(containing("old2.email@example.com")))
                .withRequestBody(not(containing("old3.email@example.com")))
                // Verify 2 new phones added, 3 old phones removed
                .withRequestBody(containing("+1-555-1111"))
                .withRequestBody(containing("+1-555-2222"))
                .withRequestBody(not(containing("+1-555-0001")))
                .withRequestBody(not(containing("+1-555-0002")))
                .withRequestBody(not(containing("+1-555-0003")))
                // Verify 2 new addresses added, 3 old addresses removed
                .withRequestBody(containing("111 New Work St, City, ST 11111"))
                .withRequestBody(containing("222 New Home Ave, City, ST 22222"))
                .withRequestBody(not(containing("123 Old Work St, City, ST 12345")))
                .withRequestBody(not(containing("456 Old Home Ave, City, ST 45678")))
                .withRequestBody(not(containing("789 Old Other Rd, City, ST 78901")))
                // Verify 2 new organizations added, 3 old organizations removed
                .withRequestBody(containing("New Corp 1"))
                .withRequestBody(containing("New Corp 2"))
                .withRequestBody(not(containing("Old Corp 1")))
                .withRequestBody(not(containing("Old Corp 2")))
                .withRequestBody(not(containing("Old Corp 3")))
                // Verify 2 new ims added, 3 old ims removed
                .withRequestBody(containing("new1.user"))
                .withRequestBody(containing("new2.user"))
                .withRequestBody(not(containing("old1.user")))
                .withRequestBody(not(containing("old2.user")))
                .withRequestBody(not(containing("old3.user")))
                // Verify 2 new externalIds added, 3 old externalIds removed
                .withRequestBody(containing("NEW111"))
                .withRequestBody(containing("NEW222"))
                .withRequestBody(not(containing("OLD001")))
                .withRequestBody(not(containing("OLD002")))
                .withRequestBody(not(containing("OLD003")))
                // Verify 2 new relations added, 3 old relations removed
                .withRequestBody(containing("New Manager"))
                .withRequestBody(containing("New Assistant"))
                .withRequestBody(not(containing("Old Manager")))
                .withRequestBody(not(containing("Old Assistant")))
                .withRequestBody(not(containing("Old Spouse"))));
    }
}