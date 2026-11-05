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

import org.exist.xquery.value.Sequence;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

/**
 * Detects predicates that evaluate to {@code false} at compile time. The optimizer does not wrap a step with such a
 * predicate in the optimize pragma, because the step's result is empty anyway. See GH-3918.
 */
final class ConstantFalsePredicate {

    private ConstantFalsePredicate() {
        // static helpers only
    }

    /**
     * Returns true if {@code preds} contains a predicate that is structurally
     * independent of the context node and folds to effective-boolean
     * {@code false} at compile time. When this happens the surrounding step
     * yields the empty sequence regardless of any other predicates, so
     * wrapping the step in {@code (#exist:optimize#)} just adds a per-node
     * index pre-select that the result will discard. See GH-3918.
     */
    static boolean hasConstantFalsePredicate(@Nullable final Predicate[] preds) {
        return preds != null && hasConstantFalsePredicate(Arrays.asList(preds));
    }

    /**
     * Same as {@link #hasConstantFalsePredicate(Predicate[])}, for the predicate list of a filtered expression.
     *
     * @param preds the predicates
     * @return true if one of them folds to {@code false} at compile time
     */
    static boolean hasConstantFalsePredicate(final List<Predicate> preds) {
        for (final Predicate pred : preds) {
            if (foldsToFalse(pred)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Evaluates {@code pred} at compile time if it does not depend on the context or on any binding.
     *
     * @param pred the predicate
     * @return true if it is context-independent and its effective boolean value is {@code false}
     */
    private static boolean foldsToFalse(final Predicate pred) {
        // Structural check first: the predicate's expression tree must not
        // touch the context node (LocationStep), bound variables, or
        // user-defined functions / FLWOR bindings. The conservative default
        // in Function.getDependencies() makes the dependency bitmask itself
        // unreliable for builtin functions on literal arguments
        // (see GH-3918), so we walk the tree explicitly.
        final ContextDependencyChecker checker = new ContextDependencyChecker();
        pred.accept(checker);
        if (checker.contextDependent) {
            return false;
        }
        // Compile-time-evaluate the predicate. Builtin functions are
        // assumed deterministic for the arguments we accept (literals,
        // operators, other builtins). Any failure means we cannot fold.
        try {
            final Sequence result = pred.preprocess();
            return result != null && !result.effectiveBooleanValue();
        } catch (final XPathException e) {
            return false;
        }
    }

    /**
     * Marks an expression tree as context-dependent if it references the
     * context node, a variable, a user-defined function, or a FLWOR binding.
     * Walks builtin function arguments and operator operands so that nested
     * occurrences are caught.
     */
    private static final class ContextDependencyChecker extends DefaultExpressionVisitor {
        private static final int CONTEXT_BITS = Dependency.CONTEXT_ITEM
                | Dependency.CONTEXT_SET
                | Dependency.CONTEXT_POSITION;

        private boolean contextDependent;

        @Override
        public void visitLocationStep(final LocationStep locationStep) {
            contextDependent = true;
        }

        @Override
        public void visitVariableReference(final VariableReference ref) {
            contextDependent = true;
        }

        @Override
        public void visitFunctionCall(final FunctionCall call) {
            contextDependent = true;
        }

        @Override
        public void visitUserFunction(final UserDefinedFunction function) {
            contextDependent = true;
        }

        @Override
        public void visitForExpression(final ForExpr forExpr) {
            contextDependent = true;
        }

        @Override
        public void visitLetExpression(final LetExpr letExpr) {
            contextDependent = true;
        }

        @Override
        public void visitWindowExpression(final WindowExpr windowExpr) {
            contextDependent = true;
        }

        @Override
        public void visitBuiltinFunction(final Function function) {
            if (contextDependent) {
                return;
            }
            // Optimizable builtins (range:eq, ft:query-field, lucene:query-field,
            // etc.) rely on the (#exist:optimize#) wrap to drive their index
            // pre-select. Their argument lists may look literal-only, but
            // dropping the wrap changes their evaluation path and results.
            // Treat them as context-dependent so the gate never strips a wrap
            // whose whole purpose is to feed an Optimizable predicate.
            if (function instanceof Optimizable) {
                contextDependent = true;
                return;
            }
            // No-arg builtins have no input besides the dynamic context, so
            // any context dependency they declare is real (not the conservative
            // Function default applied to literal arguments). Trust the
            // declared dependencies here. Builtins like fn:true(), fn:false(),
            // and fn:current-dateTime() declare NO_DEPENDENCY and remain
            // context-free; fn:position(), fn:last(), fn:name(), and
            // fn:local-name() declare CONTEXT_* and are correctly flagged.
            // The runtime safety net (preprocess() throws XPDY0002 on null
            // context) still backs this path for any future builtin that
            // does not declare its dependencies accurately.
            if (function.getArgumentCount() == 0
                    && (function.getDependencies() & CONTEXT_BITS) != 0) {
                contextDependent = true;
                return;
            }
            super.visitBuiltinFunction(function);
        }
    }
}
