/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.util.ExpressionDumper;

import static org.exist.xquery.ExpressionUnwrap.simplifyPath;

/**
 * Rewrites {@code step[a and b]} into {@code step[a][b]}, so that the optimizer can handle each predicate on its own.
 * Logs under {@link Optimizer}'s logger name, so existing logging configuration still applies.
 */
final class AndPredicateSplitter {

    // Optimizer's logger name, so existing logging configuration still applies
    private static final Logger LOG = LogManager.getLogger(Optimizer.class);

    private AndPredicateSplitter() {
        // static helpers only
    }

    /**
     * Splits {@code and} if it is the top-level expression of a predicate on a location step.
     *
     * @param context the context in which to create the new predicate
     * @param and the {@code and} expression, inside a predicate
     * @return true if the expression was rewritten
     */
    static boolean split(final XQueryContext context, final OpAnd and) {
        // inside a filter expression, we can often replace a logical and with
        // a chain of filters, which can then be further optimized
        Expression parent = and.getParent();
        if (!(parent instanceof PathExpr)) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Parent expression of boolean operator is not a PathExpr: {}", parent);
            }
            return false;
        }

        final PathExpr path;
        final Predicate predicate;
        if (parent instanceof Predicate predicate1) {
            predicate = predicate1;
            path = predicate;
        } else {
            path = (PathExpr) parent;
            parent = path.getParent();
            if (!(parent instanceof Predicate) || path.getLength() > 1) {
                LOG.debug("Boolean operator is not a top-level expression in the predicate: {}", parent == null ? "?" : parent.getClass().getName());
                return false;
            }
            predicate = (Predicate) parent;
        }

        // The predicate splitting optimization only applies when the
        // predicate belongs to a LocationStep — not a FilteredExpression
        // or other expression type that also carries predicates.
        if (!(predicate.getParent() instanceof LocationStep)) {
            return false;
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Rewriting boolean expression: {}", ExpressionDumper.dump(and));
        }
        final LocationStep step = (LocationStep) predicate.getParent();
        final Predicate newPred = new Predicate(context);
        newPred.add(simplifyPath(and.getRight()));
        step.insertPredicate(predicate, newPred);
        path.replace(and, simplifyPath(and.getLeft()));
        return true;
    }
}
