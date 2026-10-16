package com.evolveum.polygon.connector.googleapps;
import com.google.api.client.testing.http.*;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.directory.Directory;
import org.identityconnectors.framework.common.exceptions.*;
import org.testng.annotations.Test;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
public class RequestExecutionTest {
    @Test public void missingObjectIsReportedWithoutErrorDetails() throws Exception {
        Directory d=new Directory.Builder(new MockHttpTransport.Builder().setLowLevelHttpResponse(
            new MockLowLevelHttpResponse().setStatusCode(404).setContentType("application/json").setContent("{\"error\":{\"code\":404,\"message\":\"missing\"}}"))
            .build(),new GsonFactory(),null).setApplicationName("test").build();
        GoogleApiExecutor executor=new GoogleApiExecutor(new GoogleAppsConfiguration());
        assertThatThrownBy(()->executor.execute(d.users().get("missing"),new RequestResultHandler.NoOp<>())).isInstanceOf(UnknownUidException.class);
    }
    @Test public void noOpSuccessHandlerMustPropagateErrors() {
        assertThatThrownBy(()->new RequestResultHandler.NoOp<>().handleError(new IOException("denied"))).isInstanceOf(ConnectorException.class);
    }
}
