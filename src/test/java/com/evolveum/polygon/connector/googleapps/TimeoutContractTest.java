package com.evolveum.polygon.connector.googleapps;

import org.identityconnectors.framework.common.objects.*;
import org.testng.annotations.Test;
import java.util.*;
import static com.evolveum.polygon.connector.googleapps.GoogleApiMockServer.stubFor;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

public class TimeoutContractTest extends GoogleAppsConnectorTestBase {
    @Test public void configuredTimeoutsMustReachGoogleHttpRequest() throws Exception {
        configuration.setHttpConnectTimeout(1234);
        configuration.setHttpSocketTimeout(2345);
        com.google.api.client.http.HttpRequest request = configuration.getDirectory().users().get("u-timeout").buildHttpRequest();
        assertThat(request.getConnectTimeout()).isEqualTo(1234);
        assertThat(request.getReadTimeout()).isEqualTo(2345);
    }
}
