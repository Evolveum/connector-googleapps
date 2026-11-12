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
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.services.directory.Directory;
import com.google.api.services.licensing.Licensing;
import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.RetryableException;

import java.util.Random;

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
    private static final Random random = new Random();

    private final GoogleAppsConfiguration configuration;

    /**
     * Create a new GoogleApiExecutor instance for the given configuration.
     *
     * @param configuration the Google Apps configuration containing authentication and API settings
     */
    public GoogleApiExecutor(GoogleAppsConfiguration configuration) {
        this.configuration = configuration;
    }

    private static long nextLong(long n) {
        return (long) (random.nextDouble() * n);
    }

    /**
     * Execute a Google API request with default retry behavior (instance method).
     */
    public <G extends AbstractGoogleJsonClientRequest, T, R> R execute(G request,
                                                                       RequestResultHandler<G, T, R> handler) {
        return execute(Assertions.nullChecked(request, "Google Json ClientRequest"),
                Assertions.nullChecked(handler, "handler"), -1);
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

    /**
     * Execute a Google API request with specified retry count (instance method).
     * This is the complete implementation copied from GoogleAppsConnector.
     */
    public <G extends AbstractGoogleJsonClientRequest, T, R> R execute(G request,
                                                                       RequestResultHandler<G, T, R> handler,
                                                                       int retry) {
        try {
            if (retry >= 0) {
                long sleep = (long) ((1000 * Math.pow(2, retry)) + nextLong(1000));
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException e) {
                    throw ConnectorException.wrap(e);
                }
            }
            return handler.handleResult(request, (T) request.execute());
        } catch (GoogleJsonResponseException e) {
            GoogleJsonError details = e.getDetails();
            if (null != details && null != details.getErrors()) {
                GoogleJsonError.ErrorInfo errorInfo = details.getErrors().get(0);
                // error: 403
                if (e.getStatusCode() == HttpStatusCodes.STATUS_CODE_FORBIDDEN) {
                    if ("userRateLimitExceeded".equalsIgnoreCase(errorInfo.getReason())
                            || "rateLimitExceeded".equalsIgnoreCase(errorInfo.getReason())) {
                        logger.info("System should retry");
                        throw RetryableException.wrap(e.getMessage(), e);
                    } else {
                        //if we are forbidden to do something we should not try again
                        return handler.handleError(e);
                    }
                } else if (e.getStatusCode() == HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
                    if ("notFound".equalsIgnoreCase(errorInfo.getReason())) {
                        return handler.handleNotFound(e);
                    }
                } else if (e.getStatusCode() == 409) {
                    if ("duplicate".equalsIgnoreCase(errorInfo.getReason())) {
                        // Already Exists
                        handler.handleDuplicate(e);
                    }
                } else if (e.getStatusCode() == 400) {
                    if ("invalid".equalsIgnoreCase(errorInfo.getReason())) {
                        // Already Exists "Invalid Ou Id"
                    }
                } else if (e.getStatusCode() == HttpStatusCodes.STATUS_CODE_SERVICE_UNAVAILABLE) {
                    if ("backendError".equalsIgnoreCase(errorInfo.getReason()) && retry < 3) {
                        logger.warn("retrying 503 backendError retry number " + retry);
                        return execute(request, handler, ++retry);
                    } else {
                        throw RetryableException.wrap(e.getMessage(), e);
                    }
                } else if (e.getStatusCode() == HttpStatusCodes.STATUS_CODE_SERVER_ERROR) {
                    if ("backendError".equalsIgnoreCase(errorInfo.getReason())
                            || "internalError".equalsIgnoreCase(errorInfo.getReason()) && retry < 3) {
                        logger.warn("retrying 500" + errorInfo.getReason() + "retry number " + retry);
                        return execute(request, handler, ++retry);
                    } else {
                        throw RetryableException.wrap(e.getMessage(), e);
                    }
                } else {
                    if (retry < 3) { //last resort retry. We must right all wrongs!
                        logger.warn("retrying " + e.getStatusCode() + " " + errorInfo.getReason() + " retry number " + retry);
                        return execute(request, handler, ++retry);
                    } else {
                        if (e.getStatusCode() == 409) {
                            if ("duplicate".equalsIgnoreCase(errorInfo.getReason())) {
                                // Already Exists
                                logger.warn("handling duplicate");
                                handler.handleDuplicate(e);
                            }
                        } else {
                            throw RetryableException.wrap(e.getMessage(), e);
                        }
                    }
                }
            }
            throw ConnectorException.wrap(e);
        } catch (java.io.IOException e) {
            // https://developers.google.com/admin-sdk/directory/v1/limits
            // rateLimitExceeded or userRateLimitExceeded
            if (retry < 3) {
                return execute(request, handler, ++retry);
            } else {
                return handler.handleError(e);
            }
        }
    }
}