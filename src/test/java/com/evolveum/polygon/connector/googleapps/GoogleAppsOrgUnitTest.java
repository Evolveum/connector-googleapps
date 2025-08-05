package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Test class for GoogleApps Organization Unit operations
 *
 * @author Hiroyuki Wada
 */
@DisplayName("GoogleApps Organization Unit Tests")
public class GoogleAppsOrgUnitTest extends GoogleAppsConnectorTestBase {


    @Test
    @DisplayName("Test organization unit creation")
    public void testCreateOrgUnit() {
        // Setup stub for org unit creation
        GoogleApiMockServer.stubFor(post(urlPathEqualTo("/admin/directory/v1/customer/my_customer/orgunits"))
                .withRequestBody(containing("IT Department"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"kind\": \"admin#directory#orgUnit\",\n" +
                                "  \"etag\": \"\\\"orgunit001-etag\\\"\",\n" +
                                "  \"name\": \"IT Department\",\n" +
                                "  \"description\": \"Information Technology Department\",\n" +
                                "  \"orgUnitPath\": \"/IT Department\",\n" +
                                "  \"parentOrgUnitPath\": \"/\",\n" +
                                "  \"blockInheritance\": false\n" +
                                "}")));

        // Execute org unit creation
        Set<Attribute> attributes = new HashSet<>();
        attributes.add(AttributeBuilder.build(Name.NAME, "IT Department"));
        attributes.add(AttributeBuilder.build(PredefinedAttributes.DESCRIPTION, "Information Technology Department"));
        attributes.add(AttributeBuilder.build(PARENT_ORG_UNIT_PATH_ATTR, "/"));
        attributes.add(AttributeBuilder.build(BLOCK_INHERITANCE_ATTR, false));

        Uid createdUid = connectorFacade.create(ORG_UNIT, attributes, null);

        // Verify request was sent correctly
        GoogleApiMockServer.verifyRequest(postRequestedFor(urlPathEqualTo("/admin/directory/v1/customer/my_customer/orgunits"))
                .withRequestBody(equalToJson("{\n" +
                        "  \"name\": \"IT Department\",\n" +
                        "  \"description\": \"Information Technology Department\",\n" +
                        "  \"parentOrgUnitPath\": \"/\",\n" +
                        "  \"blockInheritance\": false\n" +
                        "}")));

        // Verify response
        assertThat(createdUid).isNotNull();
        assertThat(createdUid.getUidValue()).isEqualTo("IT Department");
    }

    @Test
    @DisplayName("Test organization unit deletion")
    public void testDeleteOrgUnit() {
        // Setup stub for org unit deletion
        GoogleApiMockServer.stubFor(delete(urlPathEqualTo("/admin/directory/v1/customer/my_customer/orgunits/IT%20Department"))
                .willReturn(aResponse()
                        .withStatus(204)));

        // Execute org unit deletion
        Uid uid = new Uid("IT Department");

        assertThatNoException().isThrownBy(() ->
                connectorFacade.delete(ORG_UNIT, uid, null));

        // Verify request was sent correctly
        GoogleApiMockServer.verifyRequest(deleteRequestedFor(urlPathEqualTo("/admin/directory/v1/customer/my_customer/orgunits/IT%20Department")));
    }

    @Test
    @DisplayName("Test organization unit name update")
    public void testUpdateOrgUnitName() {
        // Setup stub for org unit update
        GoogleApiMockServer.stubFor(patch(urlPathEqualTo("/admin/directory/v1/customer/my_customer/orgunits/IT%20Department"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"kind\": \"admin#directory#orgUnit\",\n" +
                                "  \"etag\": \"\\\"orgunit001-updated-etag\\\"\",\n" +
                                "  \"name\": \"Information Technology\",\n" +
                                "  \"description\": \"Information Technology Department\",\n" +
                                "  \"orgUnitPath\": \"/Information Technology\",\n" +
                                "  \"parentOrgUnitPath\": \"/\",\n" +
                                "  \"blockInheritance\": false\n" +
                                "}")));

        // Execute org unit name update
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build(Name.NAME, "Information Technology"));

        Uid uid = new Uid("IT Department");

        assertThatNoException().isThrownBy(() ->
                connectorFacade.updateDelta(ORG_UNIT, uid, modifications, null));

        // Verify PATCH request was sent correctly
        GoogleApiMockServer.verifyRequest(patchRequestedFor(urlPathEqualTo("/admin/directory/v1/customer/my_customer/orgunits/IT%20Department"))
                .withRequestBody(equalToJson("{\n" +
                        "  \"name\": \"Information Technology\"\n" +
                        "}")));
    }

    @Test
    @DisplayName("Test organization unit parent path update")
    public void testUpdateOrgUnitParentPath() {
        // Setup stub for org unit parent path update
        GoogleApiMockServer.stubFor(patch(urlPathEqualTo("/admin/directory/v1/customer/my_customer/orgunits/IT%20Department"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"kind\": \"admin#directory#orgUnit\",\n" +
                                "  \"etag\": \"\\\"orgunit001-updated-etag\\\"\",\n" +
                                "  \"name\": \"IT Department\",\n" +
                                "  \"description\": \"Information Technology Department\",\n" +
                                "  \"orgUnitPath\": \"/Engineering/IT Department\",\n" +
                                "  \"parentOrgUnitPath\": \"/Engineering\",\n" +
                                "  \"blockInheritance\": false\n" +
                                "}")));

        // Execute org unit parent path update
        Set<AttributeDelta> modifications = new HashSet<>();
        modifications.add(AttributeDeltaBuilder.build(PARENT_ORG_UNIT_PATH_ATTR, "/Engineering"));

        Uid uid = new Uid("IT Department");

        assertThatNoException().isThrownBy(() ->
                connectorFacade.updateDelta(ORG_UNIT, uid, modifications, null));

        // Verify PATCH request was sent correctly
        GoogleApiMockServer.verifyRequest(patchRequestedFor(urlPathEqualTo("/admin/directory/v1/customer/my_customer/orgunits/IT%20Department"))
                .withRequestBody(equalToJson("{\n" +
                        "  \"parentOrgUnitPath\": \"/Engineering\"\n" +
                        "}")));
    }

    @Test
    @DisplayName("Test get organization unit by UID")
    public void testGetOrgUnit() {
        // Setup stub for org unit get
        GoogleApiMockServer.stubFor(get(urlPathEqualTo("/admin/directory/v1/customer/my_customer/orgunits/IT%20Department"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"kind\": \"admin#directory#orgUnit\",\n" +
                                "  \"etag\": \"\\\"orgunit001-etag\\\"\",\n" +
                                "  \"name\": \"IT Department\",\n" +
                                "  \"description\": \"Information Technology Department\",\n" +
                                "  \"orgUnitPath\": \"/IT Department\",\n" +
                                "  \"parentOrgUnitPath\": \"/\",\n" +
                                "  \"blockInheritance\": false\n" +
                                "}")));

        // Execute org unit get
        Uid uid = new Uid("IT Department");
        final ConnectorObject[] result = new ConnectorObject[1];

        connectorFacade.search(ORG_UNIT, FilterBuilder.equalTo(uid), obj -> {
            result[0] = obj;
            return true;
        }, null);

        // Verify request was sent correctly
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/customer/my_customer/orgunits/IT%20Department")));

        // Verify response
        assertThat(result[0]).isNotNull();
        assertThat(result[0].getUid().getUidValue()).isEqualTo("IT Department");
        assertThat(result[0].getName().getNameValue()).isEqualTo("IT Department");
        assertThat(result[0].getAttributeByName(PredefinedAttributes.DESCRIPTION).getValue().get(0)).isEqualTo("Information Technology Department");
        assertThat(result[0].getAttributeByName(ORG_UNIT_PATH_ATTR).getValue().get(0)).isEqualTo("/IT Department");
        assertThat(result[0].getAttributeByName(PARENT_ORG_UNIT_PATH_ATTR).getValue().get(0)).isEqualTo("/");
        assertThat(result[0].getAttributeByName(BLOCK_INHERITANCE_ATTR).getValue().get(0)).isEqualTo(false);
    }

    @Test
    @DisplayName("Test search organization units")
    public void testSearchOrgUnits() {
        // Setup stub for org units search
        GoogleApiMockServer.stubFor(get(urlPathEqualTo("/admin/directory/v1/customer/my_customer/orgunits"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"kind\": \"admin#directory#orgUnits\",\n" +
                                "  \"etag\": \"\\\"orgunits-list-etag\\\"\",\n" +
                                "  \"organizationUnits\": [\n" +
                                "    {\n" +
                                "      \"kind\": \"admin#directory#orgUnit\",\n" +
                                "      \"etag\": \"\\\"orgunit001-etag\\\"\",\n" +
                                "      \"name\": \"IT Department\",\n" +
                                "      \"description\": \"Information Technology Department\",\n" +
                                "      \"orgUnitPath\": \"/IT Department\",\n" +
                                "      \"parentOrgUnitPath\": \"/\",\n" +
                                "      \"blockInheritance\": false\n" +
                                "    },\n" +
                                "    {\n" +
                                "      \"kind\": \"admin#directory#orgUnit\",\n" +
                                "      \"etag\": \"\\\"orgunit002-etag\\\"\",\n" +
                                "      \"name\": \"HR Department\",\n" +
                                "      \"description\": \"Human Resources Department\",\n" +
                                "      \"orgUnitPath\": \"/HR Department\",\n" +
                                "      \"parentOrgUnitPath\": \"/\",\n" +
                                "      \"blockInheritance\": true\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}")));

        // Execute org units search
        final java.util.List<ConnectorObject> results = new java.util.ArrayList<>();

        connectorFacade.search(ORG_UNIT, null, obj -> {
            results.add(obj);
            return true;
        }, null);

        // Verify request was sent correctly
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/customer/my_customer/orgunits")));

        // Verify response - should return 2 org units
        assertThat(results).hasSize(2);

        ConnectorObject orgUnit1 = results.get(0);
        assertThat(orgUnit1.getUid().getUidValue()).isEqualTo("IT Department");
        assertThat(orgUnit1.getName().getNameValue()).isEqualTo("IT Department");
        assertThat(orgUnit1.getAttributeByName(PredefinedAttributes.DESCRIPTION).getValue().get(0)).isEqualTo("Information Technology Department");

        ConnectorObject orgUnit2 = results.get(1);
        assertThat(orgUnit2.getUid().getUidValue()).isEqualTo("HR Department");
        assertThat(orgUnit2.getName().getNameValue()).isEqualTo("HR Department");
        assertThat(orgUnit2.getAttributeByName(BLOCK_INHERITANCE_ATTR).getValue().get(0)).isEqualTo(true);
    }

    @Test
    @DisplayName("Test organization unit not found error")
    public void testOrgUnitNotFound() {
        // Setup stub for org unit not found
        GoogleApiMockServer.stubFor(get(urlPathEqualTo("/admin/directory/v1/customer/my_customer/orgunits/NonExistent"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"error\": {\n" +
                                "    \"code\": 404,\n" +
                                "    \"message\": \"Org unit not found\",\n" +
                                "    \"errors\": [\n" +
                                "      {\n" +
                                "        \"domain\": \"global\",\n" +
                                "        \"reason\": \"notFound\",\n" +
                                "        \"message\": \"Org unit not found\"\n" +
                                "      }\n" +
                                "    ]\n" +
                                "  }\n" +
                                "}")));

        // Setup stub for org unit deletion not found
        GoogleApiMockServer.stubFor(delete(urlPathEqualTo("/admin/directory/v1/customer/my_customer/orgunits/NonExistent"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"error\": {\n" +
                                "    \"code\": 404,\n" +
                                "    \"message\": \"Org unit not found\",\n" +
                                "    \"errors\": [\n" +
                                "      {\n" +
                                "        \"domain\": \"global\",\n" +
                                "        \"reason\": \"notFound\",\n" +
                                "        \"message\": \"Org unit not found\"\n" +
                                "      }\n" +
                                "    ]\n" +
                                "  }\n" +
                                "}")));

        // Test get org unit not found
        Uid uid = new Uid("NonExistent");
        final ConnectorObject[] result = new ConnectorObject[1];

        connectorFacade.search(ORG_UNIT, FilterBuilder.equalTo(uid), obj -> {
            result[0] = obj;
            return true;
        }, null);

        // Should return null/empty result for not found
        assertThat(result[0]).isNull();

        // Test delete org unit not found - should throw UnknownUidException
        assertThatThrownBy(() ->
                connectorFacade.delete(ORG_UNIT, uid, null))
                .isInstanceOf(UnknownUidException.class);

        // Verify requests were sent
        GoogleApiMockServer.verifyRequest(getRequestedFor(urlPathEqualTo("/admin/directory/v1/customer/my_customer/orgunits/NonExistent")));
        GoogleApiMockServer.verifyRequest(deleteRequestedFor(urlPathEqualTo("/admin/directory/v1/customer/my_customer/orgunits/NonExistent")));
    }
}