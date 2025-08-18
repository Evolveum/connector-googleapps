package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GoogleAppsConnector User GET operation with structured attributes JSON format.
 * <p>
 * This test class verifies that structured attributes (emails, phones, externalIds, etc.)
 * are correctly formatted as JSON strings with:
 * - Colon (:) as key-value separator (not equals =)
 * - Keys sorted alphabetically for consistent output
 *
 * @author Hiroyuki Wada
 */
@DisplayName("GoogleApps User Get - Structured Attributes JSON Format Tests")
class GoogleAppsUserGetStructuredAttributesTest extends GoogleAppsConnectorTestBase {

    @Test
    @DisplayName("Test structured attributes return proper JSON format with colons and sorted keys")
    public void testStructuredAttributesJsonFormat() {
        // Setup mock response with various structured attributes
        // Keys are intentionally in non-alphabetical order in the response
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users/struct001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"struct001\",\n" +
                                "  \"etag\": \"\\\"struct001-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"struct001@example.com\",\n" +
                                "  \"emails\": [\n" +
                                "    {\n" +
                                "      \"type\": \"work\",\n" +
                                "      \"primary\": true,\n" +
                                "      \"address\": \"struct001@example.com\"\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"type\": \"home\",\n" +
                                "      \"primary\": false,\n" +
                                "      \"customType\": \"personal\",\n" +
                                "      \"address\": \"struct.home@example.org\"\n" +
                                "    }\n" +
                                "  ],\n" +
                                "  \"phones\": [\n" +
                                "    {\n" +
                                "      \"value\": \"+1-555-1234\",\n" +
                                "      \"type\": \"work\",\n" +
                                "      \"primary\": true\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"value\": \"+1-555-5678\",\n" +
                                "      \"type\": \"mobile\",\n" +
                                "      \"primary\": false,\n" +
                                "      \"customType\": \"iPhone\"\n" +
                                "    }\n" +
                                "  ],\n" +
                                "  \"externalIds\": [\n" +
                                "    {\n" +
                                "      \"value\": \"EMP001\",\n" +
                                "      \"type\": \"organization\",\n" +
                                "      \"customType\": \"employeeId\"\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"type\": \"custom\",\n" +
                                "      \"customType\": \"badge\",\n" +
                                "      \"value\": \"BADGE123\"\n" +
                                "    }\n" +
                                "  ],\n" +
                                "  \"relations\": [\n" +
                                "    {\n" +
                                "      \"value\": \"manager@example.com\",\n" +
                                "      \"type\": \"manager\"\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"type\": \"assistant\",\n" +
                                "      \"customType\": \"admin\",\n" +
                                "      \"value\": \"assistant@example.com\"\n" +
                                "    }\n" +
                                "  ],\n" +
                                "  \"organizations\": [\n" +
                                "    {\n" +
                                "      \"title\": \"Software Engineer\",\n" +
                                "      \"primary\": true,\n" +
                                "      \"name\": \"Example Corp\",\n" +
                                "      \"location\": \"Building A\",\n" +
                                "      \"department\": \"Engineering\",\n" +
                                "      \"type\": \"work\",\n" +
                                "      \"costCenter\": \"CC001\"\n" +
                                "    }\n" +
                                "  ],\n" +
                                "  \"addresses\": [\n" +
                                "    {\n" +
                                "      \"type\": \"work\",\n" +
                                "      \"streetAddress\": \"123 Main St\",\n" +
                                "      \"region\": \"CA\",\n" +
                                "      \"primary\": true,\n" +
                                "      \"postalCode\": \"94043\",\n" +
                                "      \"locality\": \"Mountain View\",\n" +
                                "      \"formatted\": \"123 Main St, Mountain View, CA 94043\",\n" +
                                "      \"country\": \"USA\"\n" +
                                "    }\n" +
                                "  ],\n" +
                                "  \"ims\": [\n" +
                                "    {\n" +
                                "      \"protocol\": \"gtalk\",\n" +
                                "      \"primary\": true,\n" +
                                "      \"im\": \"struct001@gmail.com\",\n" +
                                "      \"type\": \"work\"\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"type\": \"home\",\n" +
                                "      \"protocol\": \"skype\",\n" +
                                "      \"im\": \"struct.home\",\n" +
                                "      \"customProtocol\": \"skype-personal\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Get user by UID to fetch structured attributes
        ConnectorObject user = connectorFacade.getObject(ObjectClass.ACCOUNT,
                new Uid("struct001"), null);

        assertThat(user).isNotNull();
        assertThat(user.getUid().getUidValue()).isEqualTo("struct001");

        // Verify emails attribute - should be JSON with colon and alphabetically sorted keys
        Attribute emails = user.getAttributeByName("emails");
        assertThat(emails).isNotNull();
        List<Object> emailValues = emails.getValue();
        assertThat(emailValues).hasSize(2);

        // First email: primary work email with 3 keys
        String email1 = (String) emailValues.get(0);
        assertThat(email1).isEqualTo("{\"address\":\"struct001@example.com\",\"primary\":\"true\",\"type\":\"work\"}");

        // Second email: home email with 4 keys including customType
        String email2 = (String) emailValues.get(1);
        assertThat(email2).isEqualTo("{\"address\":\"struct.home@example.org\",\"customType\":\"personal\",\"primary\":\"false\",\"type\":\"home\"}");

        // Verify phones attribute
        Attribute phones = user.getAttributeByName("phones");
        assertThat(phones).isNotNull();
        List<Object> phoneValues = phones.getValue();
        assertThat(phoneValues).hasSize(2);

        String phone1 = (String) phoneValues.get(0);
        assertThat(phone1).isEqualTo("{\"primary\":\"true\",\"type\":\"work\",\"value\":\"+1-555-1234\"}");

        String phone2 = (String) phoneValues.get(1);
        assertThat(phone2).isEqualTo("{\"customType\":\"iPhone\",\"primary\":\"false\",\"type\":\"mobile\",\"value\":\"+1-555-5678\"}");

        // Verify externalIds attribute
        Attribute externalIds = user.getAttributeByName("externalIds");
        assertThat(externalIds).isNotNull();
        List<Object> externalIdValues = externalIds.getValue();
        assertThat(externalIdValues).hasSize(2);

        String extId1 = (String) externalIdValues.get(0);
        assertThat(extId1).isEqualTo("{\"customType\":\"employeeId\",\"type\":\"organization\",\"value\":\"EMP001\"}");

        String extId2 = (String) externalIdValues.get(1);
        assertThat(extId2).isEqualTo("{\"customType\":\"badge\",\"type\":\"custom\",\"value\":\"BADGE123\"}");

        // Verify relations attribute
        Attribute relations = user.getAttributeByName("relations");
        assertThat(relations).isNotNull();
        List<Object> relationValues = relations.getValue();
        assertThat(relationValues).hasSize(2);

        String relation1 = (String) relationValues.get(0);
        assertThat(relation1).isEqualTo("{\"type\":\"manager\",\"value\":\"manager@example.com\"}");

        String relation2 = (String) relationValues.get(1);
        assertThat(relation2).isEqualTo("{\"customType\":\"admin\",\"type\":\"assistant\",\"value\":\"assistant@example.com\"}");

        // Verify organizations attribute - most complex with many fields
        Attribute organizations = user.getAttributeByName("organizations");
        assertThat(organizations).isNotNull();
        List<Object> orgValues = organizations.getValue();
        assertThat(orgValues).hasSize(1);

        String org1 = (String) orgValues.get(0);
        // Keys should be alphabetically sorted: costCenter, department, location, name, primary, title, type
        assertThat(org1).isEqualTo("{\"costCenter\":\"CC001\",\"department\":\"Engineering\",\"location\":\"Building A\",\"name\":\"Example Corp\",\"primary\":\"true\",\"title\":\"Software Engineer\",\"type\":\"work\"}");

        // Verify addresses attribute
        Attribute addresses = user.getAttributeByName("addresses");
        assertThat(addresses).isNotNull();
        List<Object> addressValues = addresses.getValue();
        assertThat(addressValues).hasSize(1);

        String address1 = (String) addressValues.get(0);
        // Keys should be alphabetically sorted: country, formatted, locality, postalCode, primary, region, streetAddress, type
        assertThat(address1).isEqualTo("{\"country\":\"USA\",\"formatted\":\"123 Main St, Mountain View, CA 94043\",\"locality\":\"Mountain View\",\"postalCode\":\"94043\",\"primary\":\"true\",\"region\":\"CA\",\"streetAddress\":\"123 Main St\",\"type\":\"work\"}");

        // Verify ims attribute
        Attribute ims = user.getAttributeByName("ims");
        assertThat(ims).isNotNull();
        List<Object> imValues = ims.getValue();
        assertThat(imValues).hasSize(2);

        String im1 = (String) imValues.get(0);
        assertThat(im1).isEqualTo("{\"im\":\"struct001@gmail.com\",\"primary\":\"true\",\"protocol\":\"gtalk\",\"type\":\"work\"}");

        String im2 = (String) imValues.get(1);
        assertThat(im2).isEqualTo("{\"customProtocol\":\"skype-personal\",\"im\":\"struct.home\",\"protocol\":\"skype\",\"type\":\"home\"}");
    }

    @Test
    @DisplayName("Test empty values are included in JSON")
    public void testEmptyValuesIncluded() {
        // Setup mock response with some empty/null values
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users/empty001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"id\": \"empty001\",\n" +
                                "  \"etag\": \"\\\"empty001-etag\\\"\",\n" +
                                "  \"primaryEmail\": \"empty001@example.com\",\n" +
                                "  \"phones\": [\n" +
                                "    {\n" +
                                "      \"value\": \"+1-555-0000\",\n" +
                                "      \"type\": \"work\"\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"value\": \"\",\n" +
                                "      \"type\": \"mobile\"\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"value\": null,\n" +
                                "      \"type\": \"home\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        ConnectorObject user = connectorFacade.getObject(ObjectClass.ACCOUNT,
                new Uid("empty001"), null);

        assertThat(user).isNotNull();

        // Verify phones attribute - all entries including empty values are included for midPoint sync
        // Entries with null values have the null field omitted from JSON
        Attribute phones = user.getAttributeByName("phones");
        assertThat(phones).isNotNull();
        List<Object> phoneValues = phones.getValue();

        // All 3 entries should be present (including empty string value for midPoint to detect and clean)
        assertThat(phoneValues).hasSize(3);

        String phone1 = (String) phoneValues.get(0);
        assertThat(phone1).isEqualTo("{\"type\":\"work\",\"value\":\"+1-555-0000\"}");

        // The second entry has empty string value - now included for midPoint sync
        String phone2 = (String) phoneValues.get(1);
        assertThat(phone2).isEqualTo("{\"type\":\"mobile\",\"value\":\"\"}");

        // The third entry had null value, so the value field is omitted
        String phone3 = (String) phoneValues.get(2);
        assertThat(phone3).isEqualTo("{\"type\":\"home\"}");  // null value field is omitted from JSON
    }
}