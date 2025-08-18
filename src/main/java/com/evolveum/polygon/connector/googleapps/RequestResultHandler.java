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
 */

package com.evolveum.polygon.connector.googleapps;

import com.google.api.client.googleapis.services.json.AbstractGoogleJsonClientRequest;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.*;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A NAME does ...
 *
 * @author Laszlo Hordos
 */
public abstract class RequestResultHandler<G extends AbstractGoogleJsonClientRequest, T, R> {

    public abstract R handleResult(G request, T value);

    public R handleNotFound(IOException e) {
        throw new UnknownUidException(e.getMessage(), e);
    }

    public R handleDuplicate(IOException e) {
        throw new AlreadyExistsException(e.getMessage(), e);
    }

    public R handleError(Throwable e) {
        throw ConnectorException.wrap(e);
    }
    
    /**
     * No-operation request result handler that returns null for all operations.
     * Useful when the result is not needed.
     */
    public static class NoOp<G extends AbstractGoogleJsonClientRequest, T> extends RequestResultHandler<G, T, Object> {
        @Override
        public Object handleResult(G request, T value) {
            return null;
        }
        
        @Override
        public Object handleNotFound(IOException e) {
            return null;
        }
        
        @Override
        public Object handleDuplicate(IOException e) {
            return null;
        }
    }

    /**
     * Create operation request result handler.
     * Handles common create patterns with logging and UID generation.
     */
    public static class Create<G extends AbstractGoogleJsonClientRequest, T> extends RequestResultHandler<G, T, Uid> {
        private static final Log logger = Log.getLog(RequestResultHandler.class);
        private final ObjectClass objectClass;
        private final Function<T, Uid> uidGenerator;
        
        public Create(ObjectClass objectClass, Function<T, Uid> uidGenerator) {
            this.objectClass = objectClass;
            this.uidGenerator = uidGenerator;
        }
        
        @Override
        public Uid handleResult(G request, T value) {
            Uid uid = uidGenerator.apply(value);
            logger.ok("New {0} is created: {1}", objectClass.getObjectClassValue(), uid.getUidValue());
            return uid;
        }
    }

    /**
     * Delete operation request result handler.
     * Throws UnknownUidException when the object is not found.
     */
    public static class Delete extends RequestResultHandler<AbstractGoogleJsonClientRequest<Void>, Void, Void> {
        private final Uid uid;
        private final ObjectClass objectClass;
        
        public Delete(Uid uid, ObjectClass objectClass) {
            this.uid = uid;
            this.objectClass = objectClass;
        }
        
        @Override
        public Void handleResult(AbstractGoogleJsonClientRequest<Void> request, Void value) {
            return null;
        }
        
        @Override
        public Void handleNotFound(IOException e) {
            throw new UnknownUidException(uid, objectClass);
        }
    }
    
    /**
     * Common handler for read queries with configurable conversion logic.
     */
    public static class ReadQuery<G extends AbstractGoogleJsonClientRequest<T>, T> extends RequestResultHandler<G, T, Boolean> {
        private final Function<T, ConnectorObject> converter;
        private final Consumer<ConnectorObject> cacheUpdater;
        private final ResultsHandler handler;

        public ReadQuery(Function<T, ConnectorObject> converter, 
                        Consumer<ConnectorObject> cacheUpdater,
                        ResultsHandler handler) {
            this.converter = converter;
            this.cacheUpdater = cacheUpdater;
            this.handler = handler;
        }

        @Override
        public Boolean handleResult(G request, T value) {
            ConnectorObject connectorObject = converter.apply(value);
            if (cacheUpdater != null) {
                cacheUpdater.accept(connectorObject);
            }
            return handler.handle(connectorObject);
        }

        @Override
        public Boolean handleNotFound(IOException e) {
            // Do nothing if not found
            return true;
        }
    }
    
    // Utility methods for allowPartialAttributeValues support
    
    /**
     * Check if attribute should be returned as incomplete (partial) value for performance.
     * Common utility for all ObjectClass converters.
     */
    public static boolean shouldReturnPartialAttribute(String attributeName, Set<String> attributesToGet, 
                                                      OperationOptions options) {
        return attributesToGet.contains(attributeName) &&
               options != null && 
               options.getAllowPartialAttributeValues() != null && 
               options.getAllowPartialAttributeValues();
    }

    /**
     * Create an incomplete attribute for allowPartialAttributeValues support.
     * Common utility for all ObjectClass converters.
     */
    public static Attribute createIncompleteAttribute(String attr) {
        AttributeBuilder builder = new AttributeBuilder();
        builder.setName(attr).setAttributeValueCompleteness(AttributeValueCompleteness.INCOMPLETE);
        builder.addValue(Collections.EMPTY_LIST);
        return builder.build();
    }
    
    // Utility methods for AttributeDelta processing
    
    /**
     * Extract single value from AttributeDelta.
     * For single-valued attributes, only replace operations are supported.
     * Returns null if the attribute should be cleared (empty list), or the actual value to set.
     * Common utility for all ObjectClass handlers.
     */
    public static <T> T getSingleValue(AttributeDelta delta, Class<T> type) {
        List<Object> valuesToReplace = delta.getValuesToReplace();
        if (valuesToReplace == null) {
            // For single-valued attributes, add/remove operations don't make sense
            throw new InvalidAttributeValueException(
                "Single-valued attribute '" + delta.getName() + 
                "' requires replace operation (valuesToReplace), but none provided. " +
                "Add/remove operations are not supported for single-valued attributes.");
        }
        if (valuesToReplace.isEmpty()) {
            return null; // Clear the attribute (empty list means remove all values)
        }
        Object rawValue = valuesToReplace.get(0);
        return rawValue != null ? type.cast(rawValue) : null;
    }
}
