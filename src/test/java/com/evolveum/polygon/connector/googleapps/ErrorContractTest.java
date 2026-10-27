package com.evolveum.polygon.connector.googleapps;

import com.google.api.client.util.ArrayMap;
import com.google.api.services.directory.model.User;
import com.google.gson.JsonParser;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;
import java.util.*;
import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

public class ErrorContractTest extends GoogleAppsConnectorTestBase {
    private Set<Attribute> userAttributes() {
        return new HashSet<>(Arrays.asList(new Name("review@example.com"),
            AttributeBuilder.buildPassword("Review123!".toCharArray()),
            AttributeBuilder.build("givenName", "Review"), AttributeBuilder.build("familyName", "User")));
    }
    private String error(int code, String reason) {
        return "{\"error\":{\"code\":" + code + ",\"message\":\"review error\",\"errors\":[{\"reason\":\"" + reason + "\"}]}}";
    }
    @Test public void deniedMembershipRemovalMustFail() {
        stubFor(delete(urlPathEqualTo("/admin/directory/v1/groups/g-denied/members/u-denied"))
            .willReturn(aResponse().withStatus(403).withHeader("Content-Type", "application/json").withBody(error(403,"forbidden"))));
        assertThatThrownBy(() -> connectorFacade.updateDelta(ObjectClass.ACCOUNT, new Uid("u-denied"),
            Set.of(AttributeDeltaBuilder.build("__GROUPS__", null, List.of("g-denied"))), null))
            .isInstanceOf(ConnectorException.class);
    }

    @Test public void createMustReportFailedRequiredGroupAssignment() {
        stubFor(post(urlPathEqualTo("/admin/directory/v1/users"))
            .willReturn(okJson("{\"id\":\"u-create\",\"etag\":\"e1\",\"primaryEmail\":\"review@example.com\"}")));
        stubFor(post(urlPathEqualTo("/admin/directory/v1/groups/g-create/members"))
            .willReturn(aResponse().withStatus(403).withHeader("Content-Type", "application/json").withBody(error(403,"forbidden"))));
        Set<Attribute> attrs = userAttributes();
        attrs.add(AttributeBuilder.build("__GROUPS__", "g-create"));
        assertThatThrownBy(() -> connectorFacade.create(ObjectClass.ACCOUNT, attrs, null)).isInstanceOf(ConnectorException.class);
    }

    @Test public void updatingMissingGroupMustReportUnknownUid() {
        stubFor(patch(urlPathEqualTo("/admin/directory/v1/groups/missing-review"))
            .willReturn(aResponse().withStatus(404).withHeader("Content-Type", "application/json").withBody(error(404,"notFound"))));
        assertThatThrownBy(() -> connectorFacade.updateDelta(ObjectClass.GROUP, new Uid("missing-review"),
            Set.of(AttributeDeltaBuilder.build("name", "changed")), null)).isInstanceOf(UnknownUidException.class);
    }
}
