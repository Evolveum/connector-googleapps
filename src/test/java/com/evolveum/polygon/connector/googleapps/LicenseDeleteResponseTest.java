package com.evolveum.polygon.connector.googleapps;

import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.licensing.Licensing;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LicenseDeleteResponseTest {
    private static final Uid UID = new Uid("Google-Drive-storage/sku/Google-Drive-storage-20GB/user/user@example.com");

    @DataProvider
    public Object[][] successfulResponses() {
        return new Object[][] {{200, "{}"}, {204, ""}};
    }

    @Test(dataProvider = "successfulResponses")
    public void deleteAcceptsEmptyModelOrNoContent(int status, String body) {
        LicenseAssignmentsHandler.executeLicenseAssignmentDelete(executor(status, body), UID);
    }

    @Test
    public void missingLicenseUsesTheNotFoundContractWithoutErrorDetails() {
        assertThatThrownBy(() -> LicenseAssignmentsHandler.executeLicenseAssignmentDelete(
                executor(404, "{\"error\":{\"code\":404,\"message\":\"missing\"}}"), UID))
                .isInstanceOf(UnknownUidException.class);
    }

    private GoogleApiExecutor executor(int status, String body) {
        MockHttpTransport transport = new MockHttpTransport.Builder().setLowLevelHttpResponse(
                new MockLowLevelHttpResponse().setStatusCode(status).setContentType("application/json").setContent(body))
                .build();
        Licensing licensing = new Licensing.Builder(transport, GsonFactory.getDefaultInstance(), request -> request.setNumberOfRetries(0))
                .setApplicationName("license-delete-regression-test").build();
        return new GoogleApiExecutor(new GoogleAppsConfiguration() {
            @Override public Licensing getLicensing() {
                return licensing;
            }
        });
    }
}
