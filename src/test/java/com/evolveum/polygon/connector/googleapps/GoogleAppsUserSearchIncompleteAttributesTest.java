package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GoogleAppsConnector User search with incomplete attributes handling.
 * <p>
 * This test class focuses on verifying the behavior when non-default attributes
 * (photo, groups) are requested with AllowPartialAttributeValues=true. These attributes
 * have setReturnedByDefault(false) and require separate API calls, but return empty
 * lists when AllowPartialAttributeValues=true.
 *
 * @author Hiroyuki Wada
 */
@DisplayName("GoogleApps User Search - Incomplete Attributes Tests")
class GoogleAppsUserSearchIncompleteAttributesTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test search with incomplete attributes handling")
    public void testSearchWithIncompleteAttributes() {
        // This test verifies proper handling when non-default attributes (photo, groups) are requested
        // with AllowPartialAttributeValues=true. These attributes have setReturnedByDefault(false)
        // and require separate API calls, but return empty lists when AllowPartialAttributeValues=true.

        // Setup mock response specifically for incomplete attributes test
        // When AllowPartialAttributeValues=true, only mandatory fields are requested
        stubFor(get(urlPathMatching("/admin/directory/v1/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"users\": [\n" +
                                "    {\n" +
                                "      \"id\": \"incomplete001\",\n" +
                                "      \"etag\": \"\\\"incomplete001-etag\\\"\",\n" +
                                "      \"primaryEmail\": \"incomplete@example.com\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        List<ConnectorObject> results = new ArrayList<>();

        // Execute search requesting non-default attributes with partial attribute values allowed
        OperationOptions options = new OperationOptionsBuilder()
                .setAttributesToGet("__PHOTO__", "__GROUPS__")
                .setAllowPartialAttributeValues(true)
                .build();

        connectorFacade.search(ObjectClass.ACCOUNT, null, results::add, options);

        // Verify results handle missing non-default attributes gracefully
        assertThat(results).hasSize(1);
        ConnectorObject user = results.get(0);
        assertThat(user.getUid().getUidValue()).isEqualTo("incomplete001");

        // Verify non-default attributes return empty list when AllowPartialAttributeValues=true
        Attribute photo = user.getAttributeByName("__PHOTO__");
        assertThat(photo).isNotNull();
        assertThat(photo.getValue()).isEmpty(); // Empty list when partial values allowed and separate API not called
        assertThat(photo.getAttributeValueCompleteness()).isEqualTo(AttributeValueCompleteness.INCOMPLETE); // Should be marked as incomplete

        Attribute groups = user.getAttributeByName("__GROUPS__");
        assertThat(groups).isNotNull();
        assertThat(groups.getValue()).isEmpty(); // Empty list when partial values allowed and separate API not called
        assertThat(groups.getAttributeValueCompleteness()).isEqualTo(AttributeValueCompleteness.INCOMPLETE); // Should be marked as incomplete

        // Verify request was sent with only mandatory fields (photos and groups use separate APIs)
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/users"))
                .withQueryParam("fields", matching(".*etag.*"))
                .withQueryParam("fields", matching(".*primaryEmail.*")));

        // Verify that __PHOTO__ and __GROUPS__ separate APIs were NOT called due to AllowPartialAttributeValues=true
        GoogleApiMockServer.verify(0, getRequestedFor(urlPathMatching("/admin/directory/v1/users/.*/photos/thumbnail")));
        GoogleApiMockServer.verify(0, getRequestedFor(urlPathMatching("/admin/directory/v1/groups")).withQueryParam("userKey", matching(".*")));
    }
}