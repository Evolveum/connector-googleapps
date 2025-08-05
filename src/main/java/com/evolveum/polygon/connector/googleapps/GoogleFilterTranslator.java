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

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.AbstractFilterTranslator;
import org.identityconnectors.framework.common.objects.filter.ContainsFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.StartsWithFilter;

/**
 * FilterTranslator for Google connector that identifies read queries by Uid or Name.
 *
 * @author Hiroyuki Wada
 */
public class GoogleFilterTranslator extends AbstractFilterTranslator<GoogleFilter> {

    @Override
    protected GoogleFilter createEqualsExpression(EqualsFilter filter, boolean not) {
        if (not) {
            // Google API does not support NOT equals - return EMPTY to indicate no translation possible
            return GoogleFilter.EMPTY;
        }
        Attribute attr = filter.getAttribute();

        if (attr instanceof Uid) {
            return new GoogleFilter((Uid) attr);
        }
        if (attr instanceof Name) {
            return new GoogleFilter((Name) attr);
        }

        // For other attributes, return as generic search filter
        return new GoogleFilter(filter);
    }

    @Override
    protected GoogleFilter createContainsExpression(ContainsFilter filter, boolean not) {
        if (not) {
            // Google API does not support NOT contains - return EMPTY to indicate no translation possible
            return GoogleFilter.EMPTY;
        }
        // Return as search filter - GoogleFilter will handle the conversion
        return new GoogleFilter(filter);
    }

    @Override
    protected GoogleFilter createStartsWithExpression(StartsWithFilter filter, boolean not) {
        if (not) {
            // Google API does not support NOT starts-with - return EMPTY to indicate no translation possible
            return GoogleFilter.EMPTY;
        }
        // Return as search filter - GoogleFilter will handle the conversion
        return new GoogleFilter(filter);
    }

    @Override
    protected GoogleFilter createAndExpression(GoogleFilter leftExpression, GoogleFilter rightExpression) {
        // Priority: UID read operations (UID is unique, so other conditions are redundant)
        if (leftExpression != null && leftExpression.isReadByUid()) {
            return leftExpression;
        }
        if (rightExpression != null && rightExpression.isReadByUid()) {
            return rightExpression;
        }

        // Priority: Name read operations (Name is unique, so other conditions are redundant)
        if (leftExpression != null && leftExpression.isReadByName()) {
            return leftExpression;
        }
        if (rightExpression != null && rightExpression.isReadByName()) {
            return rightExpression;
        }

        // Support AND operations only if both expressions are search filters
        if (leftExpression != null && rightExpression != null &&
                !leftExpression.isReadByUid() && !leftExpression.isReadByName() &&
                !rightExpression.isReadByUid() && !rightExpression.isReadByName()) {

            // Create a new GoogleFilter that contains both filters for AND processing
            return new GoogleFilter(leftExpression, rightExpression);
        }

        // Not supported for other combinations
        return GoogleFilter.EMPTY;
    }

    @Override
    protected GoogleFilter createOrExpression(GoogleFilter leftExpression, GoogleFilter rightExpression) {
        // Not supported
        return GoogleFilter.EMPTY;
    }
}