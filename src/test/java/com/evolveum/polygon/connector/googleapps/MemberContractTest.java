package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.MEMBER;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

public class MemberContractTest extends GoogleAppsConnectorTestBase {
    @Test public void memberSearchMustConsumeAllPages() {
        String path = "/admin/directory/v1/groups/g-paged/members";
        stubFor(get(urlPathEqualTo(path)).withQueryParam("pageToken", absent())
            .willReturn(okJson("{\"members\":[{\"id\":\"m1\",\"email\":\"m1@example.com\"}],\"nextPageToken\":\"page2\"}")));
        stubFor(get(urlPathEqualTo(path)).withQueryParam("pageToken", equalTo("page2"))
            .willReturn(okJson("{\"members\":[{\"id\":\"m2\",\"email\":\"m2@example.com\"}]}")));
        List<ConnectorObject> results = new ArrayList<>();
        connectorFacade.search(MEMBER, FilterBuilder.equalTo(AttributeBuilder.build("groupKey", "g-paged")),
            o -> { results.add(o); return true; }, null);
        assertThat(results).hasSize(2);
    }

    @Test public void memberRoleUpdateMustNotPutImmutableIdInEmail() {
        String path = "/admin/directory/v1/groups/g-role/members/123456789";
        stubFor(patch(urlPathEqualTo(path)).willReturn(okJson("{\"id\":\"123456789\",\"email\":\"role@example.com\",\"role\":\"MANAGER\"}")));
        connectorFacade.updateDelta(MEMBER, new Uid("g-role/123456789"), Set.of(AttributeDeltaBuilder.build("role", "MANAGER")), null);
        GoogleApiMockServer.verifyRequest(patchRequestedFor(urlPathEqualTo(path)).withRequestBody(equalToJson("{\"role\":\"MANAGER\"}")));
    }

    @Test public void oldMemberUidMustRemainStableOrRequireAnExplicitMigration() {
        stubFor(get(urlPathEqualTo("/admin/directory/v1/groups/g-legacy/members/member@example.com"))
            .willReturn(okJson("{\"id\":\"123456\",\"email\":\"member@example.com\"}")));
        Uid oldUid = new Uid("g-legacy/member@example.com");
        assertThat(connectorFacade.getObject(MEMBER, oldUid, null).getUid().getUidValue()).isEqualTo(oldUid.getUidValue());
    }
}
