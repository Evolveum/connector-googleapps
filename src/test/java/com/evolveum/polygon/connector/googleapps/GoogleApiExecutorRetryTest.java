package com.evolveum.polygon.connector.googleapps;

import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.directory.Directory;
import com.google.api.services.directory.model.User;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.RetryableException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class GoogleApiExecutorRetryTest {

    @DataProvider
    public Object[][] missingResponses() {
        return new Object[][] {
                {""},
                {"{\"error\":{\"code\":404,\"message\":\"missing\"}}"},
                {"{\"error\":{\"code\":404,\"errors\":[]}}"},
                {"{\"error\":{\"code\":404,\"errors\":[{\"reason\":\"notFound\"}]}}"}
        };
    }

    @Test(dataProvider = "missingResponses")
    public void missingObjectUsesNotFoundHandlerRegardlessOfErrorDetails(String body) throws Exception {
        Fixture fixture = new Fixture(body, 404);
        assertThatThrownBy(() -> fixture.executor.execute(fixture.request(), new RequestResultHandler.NoOp<>()))
                .isInstanceOf(UnknownUidException.class);
        assertThat(fixture.transport.attempts).isEqualTo(1);
        assertThat(fixture.delays).isEmpty();
    }

    @Test(dataProvider = "missingResponses")
    public void missingAliasDeletionRemainsIdempotent(String body) throws Exception {
        Fixture fixture = new Fixture(body, 404);
        Object result = fixture.executor.execute(fixture.directory.users().aliases().delete("user-id", "alias@example.com"),
                new RequestResultHandler.IgnoreMissing<Directory.Users.Aliases.Delete, Void>());
        assertThat(result).isNull();
        assertThat(fixture.transport.attempts).isEqualTo(1);
        assertThat(fixture.delays).isEmpty();
    }

    @DataProvider
    public Object[][] serverErrors() {
        return new Object[][] {{500}, {502}, {503}, {504}};
    }

    @Test(dataProvider = "serverErrors")
    public void transientServerFailuresStopAfterFourRetries(int status) throws Exception {
        Fixture fixture = new Fixture("{}", status);
        assertThatThrownBy(() -> fixture.executor.execute(fixture.request(), new RequestResultHandler.NoOp<>()))
                .isInstanceOf(RetryableException.class);
        assertThat(fixture.transport.attempts).isEqualTo(5);
        assertBackoff(fixture.delays);
    }

    @Test
    public void serverFailureCanRecover() throws Exception {
        Fixture fixture = new Fixture("{}", 503, 200);
        User result = fixture.executor.execute(fixture.request(), returningUser());
        assertThat(result.getId()).isEqualTo("user-id");
        assertThat(fixture.transport.attempts).isEqualTo(2);
        assertThat(fixture.delays).hasSize(1);
    }

    @Test
    public void ioFailureCanRecover() throws Exception {
        Fixture fixture = new Fixture("", -1, 200);
        User result = fixture.executor.execute(fixture.request(), returningUser());
        assertThat(result.getId()).isEqualTo("user-id");
        assertThat(fixture.transport.attempts).isEqualTo(2);
        assertThat(fixture.delays).hasSize(1);
    }

    @Test
    public void exhaustedIoFailuresReachTheErrorHandler() throws Exception {
        Fixture fixture = new Fixture("", -1);
        assertThatThrownBy(() -> fixture.executor.execute(fixture.request(), new RequestResultHandler.NoOp<>()))
                .isInstanceOf(ConnectorException.class)
                .hasCauseInstanceOf(IOException.class);
        assertThat(fixture.transport.attempts).isEqualTo(5);
        assertBackoff(fixture.delays);
    }

    @DataProvider
    public Object[][] permanentErrors() {
        return new Object[][] {{400}, {403}, {409}};
    }

    @Test(dataProvider = "permanentErrors")
    public void permanentFailuresAreNotRetried(int status) throws Exception {
        Fixture fixture = new Fixture("{}", status);
        assertThatThrownBy(() -> fixture.executor.execute(fixture.request(), new RequestResultHandler.NoOp<>()))
                .isInstanceOf(ConnectorException.class)
                .isNotInstanceOf(RetryableException.class);
        assertThat(fixture.transport.attempts).isEqualTo(1);
        assertThat(fixture.delays).isEmpty();
    }

    @DataProvider
    public Object[][] rateLimitResponses() {
        return new Object[][] {
                {429, "{}"},
                {403, "{\"error\":{\"code\":403,\"errors\":[null,{\"reason\":\"other\"},{\"reason\":\"rateLimitExceeded\"}]}}"},
                {403, "{\"error\":{\"code\":403,\"errors\":[{\"reason\":\"userRateLimitExceeded\"}]}}"},
                {403, "{\"error\":{\"code\":403,\"errors\":[{\"reason\":\"quotaExceeded\"}]}}"}
        };
    }

    @Test(dataProvider = "rateLimitResponses")
    public void rateLimitsAreReturnedToTheCallerWithoutLocalRetries(int status, String body) throws Exception {
        Fixture fixture = new Fixture(body, status);
        assertThatThrownBy(() -> fixture.executor.execute(fixture.request(), new RequestResultHandler.NoOp<>()))
                .isInstanceOf(RetryableException.class);
        assertThat(fixture.transport.attempts).isEqualTo(1);
        assertThat(fixture.delays).isEmpty();
    }

    @Test
    public void duplicateHandlerResultIsReturnedImmediately() throws Exception {
        Fixture fixture = new Fixture("{\"error\":{\"code\":409,\"errors\":[{\"reason\":\"duplicate\"}]}}", 409);
        Object result = fixture.executor.execute(fixture.request(), new RequestResultHandler.IgnoreDuplicate<>());
        assertThat(result).isNull();
        assertThat(fixture.transport.attempts).isEqualTo(1);
        assertThat(fixture.delays).isEmpty();
    }

    @Test
    public void resultHandlerFailureDoesNotRepeatTheRequest() throws Exception {
        Fixture fixture = new Fixture("", 200);
        IllegalStateException failure = new IllegalStateException("conversion failed");
        assertThatThrownBy(() -> fixture.executor.execute(fixture.request(),
                new RequestResultHandler<Directory.Users.Get, User, User>() {
                    @Override public User handleResult(Directory.Users.Get request, User value) {
                        throw failure;
                    }
                })).isSameAs(failure);
        assertThat(fixture.transport.attempts).isEqualTo(1);
        assertThat(fixture.delays).isEmpty();
    }

    @Test
    public void interruptedBackoffStopsRetryingAndPreservesInterruptStatus() throws Exception {
        Fixture fixture = new Fixture("{}", 503);
        GoogleApiExecutor executor = new GoogleApiExecutor(new GoogleAppsConfiguration(), delay -> {
            throw new InterruptedException("cancelled");
        });
        try {
            assertThatThrownBy(() -> executor.execute(fixture.request(), new RequestResultHandler.NoOp<>()))
                    .isInstanceOf(ConnectorException.class)
                    .hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(fixture.transport.attempts).isEqualTo(1);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void invalidRetryIndexFailsBeforeSendingOrSleeping() throws Exception {
        Fixture fixture = new Fixture("", 200);
        for (int retry : new int[] {-2, 4}) {
            assertThatThrownBy(() -> fixture.executor.execute(fixture.request(), new RequestResultHandler.NoOp<>(), retry))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(fixture.transport.attempts).isZero();
        assertThat(fixture.delays).isEmpty();
    }

    private static RequestResultHandler<Directory.Users.Get, User, User> returningUser() {
        return new RequestResultHandler<Directory.Users.Get, User, User>() {
            @Override public User handleResult(Directory.Users.Get request, User value) {
                return value;
            }
        };
    }

    private static void assertBackoff(List<Long> delays) {
        assertThat(delays).hasSize(4);
        for (int index = 0; index < delays.size(); index++) {
            long base = 1000L << index;
            assertThat(delays.get(index)).isBetween(base, base + 999);
        }
    }

    private static final class Fixture {
        private final ScriptedTransport transport;
        private final Directory directory;
        private final List<Long> delays = new ArrayList<>();
        private final GoogleApiExecutor executor = new GoogleApiExecutor(new GoogleAppsConfiguration(), delays::add);

        private Fixture(String errorBody, int... statuses) {
            transport = new ScriptedTransport(errorBody, statuses);
            directory = new Directory.Builder(transport, GsonFactory.getDefaultInstance(), request -> request.setNumberOfRetries(0))
                    .setApplicationName("executor-regression-test").build();
        }

        private Directory.Users.Get request() throws IOException {
            return directory.users().get("user-id");
        }
    }

    private static final class ScriptedTransport extends MockHttpTransport {
        private final String errorBody;
        private final int[] statuses;
        private int attempts;

        private ScriptedTransport(String errorBody, int[] statuses) {
            this.errorBody = errorBody;
            this.statuses = statuses;
        }

        @Override public MockLowLevelHttpRequest buildRequest(String method, String url) {
            return new MockLowLevelHttpRequest(url) {
                @Override public LowLevelHttpResponse execute() throws IOException {
                    int status = statuses[Math.min(attempts++, statuses.length - 1)];
                    if (status == -1) {
                        throw new IOException("connection lost");
                    }
                    return new MockLowLevelHttpResponse().setStatusCode(status).setContentType("application/json")
                            .setContent(status == 200 ? "{\"id\":\"user-id\"}" : errorBody);
                }
            };
        }
    }
}
