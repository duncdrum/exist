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

import javax.annotation.Nullable;

/**
 * Removes the wrapper expressions the parser puts around an expression. Used by the optimizer and the
 * {@link org.exist.xquery.pragmas.Optimize} pragma.
 */
public final class ExpressionUnwrap {

    private ExpressionUnwrap() {
        // static helpers only
    }

    /**
     * Removes one single-step {@link PathExpr}, such as the parser creates around a parenthesised expression.
     *
     * @param expression the expression
     * @return the only step of {@code expression} if it is a path of length one, otherwise {@code expression}
     */
    static Expression simplifyPath(final Expression expression) {
        if (!(expression instanceof final PathExpr path)) {
            return expression;
        }

        if (path.getLength() != 1) {
            return path;
        }

        return path.getExpression(0);
    }

    /**
     * Strips the wrappers the parser puts around an operand: any number of single-step
     * {@link PathExpr}s (see {@link #simplifyPath}), and the {@link InternalFunctionCall} that every
     * built-in function call is wrapped in.
     *
     * @param expression the operand
     * @return the expression underneath the wrappers, or null if {@code expression} is null
     */
    public static @Nullable Expression unwrapOperand(@Nullable final Expression expression) {
        if (expression == null) {
            return null;
        }
        final Expression simplified = simplifyPath(expression);
        if (simplified != expression) {
            return unwrapOperand(simplified);
        }
        if (expression instanceof final InternalFunctionCall call) {
            return unwrapOperand(call.getFunction());
        }
        return expression;
    }
}
