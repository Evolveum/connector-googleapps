/*
 * Copyright (c) 2026 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.polygon.connector.googleapps;

import com.google.api.client.googleapis.services.json.AbstractGoogleJsonClientRequest;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.expectThrows;

public class TestRequestResultHandler {

    private final RequestResultHandler<AbstractGoogleJsonClientRequest<?>, Object, String> handler =
            new RequestResultHandler<>() {
                @Override
                public String handleResult(AbstractGoogleJsonClientRequest<?> request, Object value) {
                    return String.valueOf(value);
                }
            };

    @Test
    public void resultIsDelegatedToHandlerImplementation() {
        assertEquals(handler.handleResult(null, "done"), "done");
    }

    @Test
    public void defaultsMapApiErrorsToConnectorExceptions() {
        IOException notFound = new IOException("missing");
        IOException duplicate = new IOException("duplicate");
        RuntimeException failure = new RuntimeException("failure");

        assertEquals(expectThrows(UnknownUidException.class, () -> handler.handleNotFound(notFound)).getCause(), notFound);
        assertEquals(expectThrows(AlreadyExistsException.class, () -> handler.handleDuplicate(duplicate)).getCause(), duplicate);
        RuntimeException propagated = expectThrows(RuntimeException.class, () -> handler.handleError(failure));
        assertSame(propagated, failure);
    }
}
