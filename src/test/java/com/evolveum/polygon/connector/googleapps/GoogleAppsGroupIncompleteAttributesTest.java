package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Google Apps Group incomplete attributes functionality.
 * Tests the performance optimization for expensive __MEMBERS__ attribute.
 *
 * @author Hiroyuki Wada
 */
public class GoogleAppsGroupIncompleteAttributesTest extends GoogleAppsConnectorTestBase {

    @BeforeEach
    public void setUp() {
        super.setUp();

        // Setup basic group get endpoint stub
        setupGroupGetStub();
        // Setup members list endpoint stub for when members are actually requested
        setupGroupMembersStub();
    }

    private void setupGroupGetStub() {
        GoogleApiMockServer.stubFor(get(urlPathEqualTo("/admin/directory/v1/groups/group001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"group001\",\n" +
                                "  \"email\": \"test-group@example.com\",\n" +
                                "  \"name\": \"Test Group\",\n" +
                                "  \"description\": \"Test Description\",\n" +
                                "  \"etag\": \"\\\"etag001\\\"\"\n" +
                                "}")));
    }

    private void setupGroupMembersStub() {
        GoogleApiMockServer.stubFor(get(urlPathEqualTo("/admin/directory/v1/groups/group001/members"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"members\": [\n" +
                                "    {\n" +
                                "      \"id\": \"member001\",\n" +
                                "      \"email\": \"member001@example.com\",\n" +
                                "      \"role\": \"MEMBER\",\n" +
                                "      \"type\": \"USER\"\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"id\": \"member002\",\n" +
                                "      \"email\": \"member002@example.com\",\n" +
                                "      \"role\": \"MANAGER\",\n" +
                                "      \"type\": \"USER\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));
    }

    @Test
    public void testMembersIncompleteWhenPartialAttributeValuesAllowed() {
        // When __MEMBERS__ is requested with AllowPartialAttributeValues=true,
        // it should return as incomplete for performance optimization

        OperationOptions options = new OperationOptionsBuilder()
                .setAttributesToGet("__MEMBERS__")
                .setAllowPartialAttributeValues(true)
                .build();

        ConnectorObject group = connectorFacade.getObject(ObjectClass.GROUP, new Uid("group001"), options);

        // Verify __MEMBERS__ attribute exists and is marked as incomplete
        Attribute membersAttr = group.getAttributeByName("__MEMBERS__");
        assertThat(membersAttr).isNotNull();
        assertThat(membersAttr.getValue()).isEmpty();
        assertThat(membersAttr.getAttributeValueCompleteness()).isEqualTo(AttributeValueCompleteness.INCOMPLETE);

        // Verify members API was NOT called for performance optimization
        GoogleApiMockServer.verify(0, getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group001/members")));
    }

    @Test
    public void testMembersLoadedWhenExplicitlyRequested() {
        // When __MEMBERS__ is explicitly requested in attributesToGet,
        // actual member data should be fetched

        OperationOptions options = new OperationOptionsBuilder()
                .setAttributesToGet("__MEMBERS__")
                .build();

        ConnectorObject group = connectorFacade.getObject(ObjectClass.GROUP, new Uid("group001"), options);

        // Verify __MEMBERS__ attribute exists and contains actual data
        Attribute membersAttr = group.getAttributeByName("__MEMBERS__");
        assertThat(membersAttr).isNotNull();
        assertThat(membersAttr.getValue()).isNotEmpty();
        assertThat(membersAttr.getAttributeValueCompleteness()).isNotEqualTo(AttributeValueCompleteness.INCOMPLETE);

        // Verify actual member data is present
        assertThat(membersAttr.getValue()).hasSize(2);
        assertThat(membersAttr.getValue().toString()).contains("member001@example.com");
        assertThat(membersAttr.getValue().toString()).contains("member002@example.com");

        // Verify members API was called to fetch actual data
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group001/members")));
    }

    @Test
    public void testMembersIncompleteInSearchResults() {
        // During search operations with __MEMBERS__ requested and AllowPartialAttributeValues=true,
        // __MEMBERS__ should be incomplete for performance

        GoogleApiMockServer.stubFor(get(urlPathEqualTo("/admin/directory/v1/groups"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [\n" +
                                "    {\n" +
                                "      \"id\": \"group001\",\n" +
                                "      \"email\": \"test-group@example.com\",\n" +
                                "      \"name\": \"Test Group\",\n" +
                                "      \"description\": \"Test Description\",\n" +
                                "      \"etag\": \"\\\"etag001\\\"\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        OperationOptions options = new OperationOptionsBuilder()
                .setAttributesToGet("__MEMBERS__")
                .setAllowPartialAttributeValues(true)
                .build();

        final ConnectorObject[] foundGroups = new ConnectorObject[1];
        connectorFacade.search(ObjectClass.GROUP, null, obj -> {
            foundGroups[0] = obj;
            return true;
        }, options);

        assertThat(foundGroups[0]).isNotNull();

        // Verify __MEMBERS__ attribute is incomplete in search results
        Attribute membersAttr = foundGroups[0].getAttributeByName("__MEMBERS__");
        assertThat(membersAttr).isNotNull();
        assertThat(membersAttr.getValue()).isEmpty();
        assertThat(membersAttr.getAttributeValueCompleteness()).isEqualTo(AttributeValueCompleteness.INCOMPLETE);

        // Verify members API was NOT called during search for performance
        GoogleApiMockServer.verify(0, getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group001/members")));
    }

    @Test
    public void testMembersLoadedInSearchWhenExplicitlyRequested() {
        // When __MEMBERS__ is explicitly requested in search attributesToGet,
        // actual member data should be fetched

        GoogleApiMockServer.stubFor(get(urlPathEqualTo("/admin/directory/v1/groups"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"groups\": [\n" +
                                "    {\n" +
                                "      \"id\": \"group001\",\n" +
                                "      \"email\": \"test-group@example.com\",\n" +
                                "      \"name\": \"Test Group\",\n" +
                                "      \"description\": \"Test Description\",\n" +
                                "      \"etag\": \"\\\"etag001\\\"\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        OperationOptions options = new OperationOptionsBuilder()
                .setAttributesToGet("__MEMBERS__")
                .build();

        final ConnectorObject[] foundGroups = new ConnectorObject[1];
        connectorFacade.search(ObjectClass.GROUP, null, obj -> {
            foundGroups[0] = obj;
            return true;
        }, options);

        assertThat(foundGroups[0]).isNotNull();

        // Verify __MEMBERS__ attribute contains actual data
        Attribute membersAttr = foundGroups[0].getAttributeByName("__MEMBERS__");
        assertThat(membersAttr).isNotNull();
        assertThat(membersAttr.getValue()).isNotEmpty();
        assertThat(membersAttr.getAttributeValueCompleteness()).isNotEqualTo(AttributeValueCompleteness.INCOMPLETE);

        // Verify actual member data is present
        assertThat(membersAttr.getValue()).hasSize(2);

        // Verify members API was called to fetch actual data
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/groups/group001/members")));
    }

    @Test
    public void testOtherAttributesNotAffected() {
        // Verify that the incomplete attribute mechanism only affects __MEMBERS__
        // and not other attributes like name, email, description

        ConnectorObject group = connectorFacade.getObject(ObjectClass.GROUP, new Uid("group001"), null);

        // Verify other attributes are complete and have values
        Attribute nameAttr = group.getAttributeByName("name");
        assertThat(nameAttr).isNotNull();
        assertThat(nameAttr.getValue()).isNotEmpty();
        // Note: name attribute may not have AttributeValueCompleteness set, so we just check it's not INCOMPLETE
        if (nameAttr.getAttributeValueCompleteness() != null) {
            assertThat(nameAttr.getAttributeValueCompleteness()).isNotEqualTo(AttributeValueCompleteness.INCOMPLETE);
        }

        Attribute emailAttr = group.getAttributeByName("__NAME__"); // Group uses __NAME__ for email
        assertThat(emailAttr).isNotNull();
        assertThat(emailAttr.getValue()).isNotEmpty();
        if (emailAttr.getAttributeValueCompleteness() != null) {
            assertThat(emailAttr.getAttributeValueCompleteness()).isNotEqualTo(AttributeValueCompleteness.INCOMPLETE);
        }

        // Description might be null in our test data, so handle it carefully
        Attribute descAttr = group.getAttributeByName("__DESCRIPTION__");
        if (descAttr != null && descAttr.getAttributeValueCompleteness() != null) {
            assertThat(descAttr.getAttributeValueCompleteness()).isNotEqualTo(AttributeValueCompleteness.INCOMPLETE);
        }
    }
}