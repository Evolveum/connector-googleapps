package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;
import java.util.*;
import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

public class SearchContractTest extends GoogleAppsConnectorTestBase {
    @Test public void groupContainsMustNotBeNarrowedToPrefix() {
        org.identityconnectors.framework.api.APIConfiguration api =
                org.identityconnectors.test.common.TestHelpers.createTestConfiguration(GoogleAppsConnector.class, configuration);
        api.getResultsHandlerConfiguration().setEnableFilteredResultsHandler(true);
        connectorFacade = org.identityconnectors.framework.api.ConnectorFacadeFactory.getInstance().newInstance(api);
        stubFor(get(urlPathEqualTo("/admin/directory/v1/groups"))
            .willReturn(okJson("{\"groups\":[{\"id\":\"g-containing\",\"email\":\"team-sales@example.com\"},"
                    + "{\"id\":\"g-other\",\"email\":\"engineering@example.com\"}]}")));
        stubFor(get(urlPathEqualTo("/admin/directory/v1/groups")).withQueryParam("query", equalTo("email:sales*"))
            .willReturn(okJson("{\"groups\":[]}")));
        List<ConnectorObject> found = new ArrayList<>();
        connectorFacade.search(ObjectClass.GROUP, FilterBuilder.contains(new Name("sales")),
            o -> { found.add(o); return true; }, null);
        assertThat(found).hasSize(1);
    }

    @Test public void noDefaultAttributesMustStillReturnIdentity() {
        stubFor(get(urlPathEqualTo("/admin/directory/v1/users/u-empty"))
            .willReturn(okJson("{\"id\":\"u-empty\",\"primaryEmail\":\"empty@example.com\"}")));
        OperationOptions options = new OperationOptionsBuilder().setReturnDefaultAttributes(false).build();
        assertThat(connectorFacade.getObject(ObjectClass.ACCOUNT, new Uid("u-empty"), options)).isNotNull();
    }
}
