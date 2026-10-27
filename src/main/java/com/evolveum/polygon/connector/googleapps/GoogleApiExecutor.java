/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 ForgeRock AS. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Portions Copyrighted 2025 Nomura Research Institute, Ltd.
 */

package com.evolveum.polygon.connector.googleapps;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.services.json.AbstractGoogleJsonClientRequest;
import com.google.api.client.util.Sleeper;
import com.google.api.services.directory.Directory;
import com.google.api.services.licensing.Licensing;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.RetryableException;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Executor for Google API requests with error handling and retry logic.
 * <p>
 * This class encapsulates the common logic for executing Google API requests,
 * including rate limit handling, error processing, and retry mechanisms.
 * <p>
 * Instances of this class can cache authentication information and HTTP clients
 * for better performance when used with PoolableConnector.
 *
 * @author Hiroyuki Wada
 */
public class GoogleApiExecutor {

    private static final Log logger = Log.getLog(GoogleApiExecutor.class);

    private static final int MAX_RETRY_INDEX = 3;
    private static final long BASE_DELAY_MS = 1_000L;
    private static final long JITTER_BOUND_MS = 1_000L;

    private final GoogleAppsConfiguration configuration;
    private final Sleeper sleeper;

    /**
     * Create a new GoogleApiExecutor instance for the given configuration.
     *
     * @param configuration the Google Apps configuration containing authentication and API settings
     */
    public GoogleApiExecutor(GoogleAppsConfiguration configuration) {
        this(configuration, Sleeper.DEFAULT);
    }

    // Allow retry tests to observe backoff without waiting in real time.
    GoogleApiExecutor(GoogleAppsConfiguration configuration, Sleeper sleeper) {
        this.configuration = configuration;
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    public GoogleAppsConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Get the Directory API client from configuration.
     *
     * @return the Directory API client
     */
    public Directory getDirectory() {
        return configuration.getDirectory();
    }

    /**
     * Get the Licensing API client from configuration.
     *
     * @return the Licensing API client
     */
    public Licensing getLicensing() {
        return configuration.getLicensing();
    }

    public <T, G extends AbstractGoogleJsonClientRequest<T>, R> R execute(
            G request,
            RequestResultHandler<G, T, R> handler) {

        return execute(request, handler, -1);
    }

    /**
     * Executes a request with bounded retries (one initial attempt and at most four retries).
     *
     * @param retry -1 for the initial attempt; 0..3 for a retry attempt
     */
    public <T, G extends AbstractGoogleJsonClientRequest<T>, R> R execute(
            G request,
            RequestResultHandler<G, T, R> handler,
            int retry) {

        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(handler, "handler");

        if (retry < -1 || retry > MAX_RETRY_INDEX) {
            throw new IllegalArgumentException(
                    "retry must be between -1 and " + MAX_RETRY_INDEX);
        }

        for (int retryIndex = retry; ; retryIndex++) {
            waitBeforeRetry(retryIndex);

            final T result;
            try {
                result = request.execute();
            } catch (GoogleJsonResponseException e) {
                int status = e.getStatusCode();

                switch (status) {
                    case 403:
                        if (hasReason(e,
                                "userRateLimitExceeded",
                                "rateLimitExceeded",
                                "quotaExceeded")) {
                            throw RetryableException.wrap(e.getMessage(), e);
                        }
                        return handler.handleError(e);

                    case 404:
                        // Error details are optional; the HTTP status defines the not-found contract.
                        return handler.handleNotFound(e);

                    case 409:
                        if (hasReason(e, "duplicate")) {
                            return handler.handleDuplicate(e);
                        }
                        return handler.handleError(e);

                    case 429:
                        throw RetryableException.wrap(e.getMessage(), e);

                    case 500:
                    case 502:
                    case 503:
                    case 504:
                        if (retryIndex >= MAX_RETRY_INDEX) {
                            throw RetryableException.wrap(e.getMessage(), e);
                        }
                        logger.warn(
                                "Retrying Google API request after HTTP "
                                        + status + "; retry index "
                                        + (retryIndex + 1));
                        break;

                    default:
                        return handler.handleError(e);
                }

                continue;
            } catch (IOException e) {
                if (retryIndex >= MAX_RETRY_INDEX) {
                    return handler.handleError(e);
                }

                logger.warn(
                        "Retrying Google API request after IOException"
                                + "; retry index " + (retryIndex + 1));
                continue;
            }

            // Result handling is outside the request retry boundary.
            return handler.handleResult(request, result);
        }
    }

    private void waitBeforeRetry(int retryIndex) {
        if (retryIndex < 0) {
            return;
        }

        // retryIndex is validated as 0..3 before reaching this method.
        long delayMillis = (BASE_DELAY_MS << retryIndex)
                + ThreadLocalRandom.current().nextLong(JITTER_BOUND_MS);

        try {
            sleeper.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ConnectorException.wrap(e);
        }
    }

    private static boolean hasReason(
            GoogleJsonResponseException exception,
            String... expectedReasons) {

        GoogleJsonError details = exception.getDetails();
        if (details == null || details.getErrors() == null) {
            return false;
        }

        for (GoogleJsonError.ErrorInfo error : details.getErrors()) {
            if (error == null) {
                continue;
            }

            for (String expected : expectedReasons) {
                if (expected.equalsIgnoreCase(error.getReason())) {
                    return true;
                }
            }
        }

        return false;
    }
}
