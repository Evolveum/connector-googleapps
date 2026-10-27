package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.*;
import org.testng.annotations.Test;
import java.util.*;
import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;
import static com.evolveum.polygon.connector.googleapps.GoogleAppsConstants.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

public class IdentityContractTest extends GoogleAppsConnectorTestBase {
    @Test public void changingLicenseSkuMustReturnNewUidDelta() {
        String oldUid = "Google-Drive-storage/sku/Google-Drive-storage-20GB/user/user@example.com";
        String newUid = "Google-Drive-storage/sku/Google-Drive-storage-50GB/user/user@example.com";
        stubFor(patch(urlPathEqualTo("/apps/licensing/v1/product/" + oldUid))
            .willReturn(okJson("{\"productId\":\"Google-Drive-storage\","
                    + "\"skuId\":\"Google-Drive-storage-50GB\",\"userId\":\"user@example.com\"}")));
        Set<AttributeDelta> result = connectorFacade.updateDelta(LICENSE_ASSIGNMENT, new Uid(oldUid),
            Set.of(AttributeDeltaBuilder.build(SKU_ID_ATTR, "Google-Drive-storage-50GB")), null);
        assertThat(result).anySatisfy(d -> {
            assertThat(d.getName()).isEqualTo(Uid.NAME);
            assertThat(d.getValuesToReplace()).containsExactly(newUid);
        });
    }

    @Test public void renamedOrgUnitMustReturnNewUidDelta() {
        stubFor(patch(urlPathEqualTo("/admin/directory/v1/customer/my_customer/orgunits/OldReview"))
            .willReturn(okJson("{\"orgUnitPath\":\"/NewReview\",\"etag\":\"e2\"}")));
        Set<AttributeDelta> result = connectorFacade.updateDelta(ORG_UNIT, new Uid("OldReview"),
            Set.of(AttributeDeltaBuilder.build(Name.NAME, "NewReview")), null);
        assertThat(result).anySatisfy(d -> {
            assertThat(d.getName()).isEqualTo(Uid.NAME);
            assertThat(d.getValuesToReplace()).containsExactly("NewReview");
        });
    }
}
