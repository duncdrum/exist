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
import org.exist.xquery.functions.array.ArrayConstructor;

import java.util.Collection;

import static org.apache.commons.lang3.ArrayUtils.isNotEmpty;
import static org.exist.xquery.ExpressionUnwrap.simplifyPath;

/**
 * Inlines a reference to a global variable of an imported module when the variable's expression is a literal value
 * or a simple construct over literals.
 *
 * <p>{@link #inline} runs the optimizer over the variable's expression before deciding, so the optimizer passes
 * itself in. Logs under {@link Optimizer}'s logger name, so existing logging configuration still applies.
 */
final class GlobalVariableInliner {

    // Optimizer's logger name, so existing logging configuration still applies
    private static final Logger LOG = LogManager.getLogger(Optimizer.class);

    private GlobalVariableInliner() {
        // static helpers only
    }

    /**
     * Check if a global variable can be inlined, usually if it
     * references a literal value or sequence thereof.
     *
     * @param context the context in which the reference is resolved
     * @param ref the variable reference
     * @param optimizer the optimizer; it runs over the variable's expression first and may rewrite it
     */
    static void inline(final XQueryContext context, final VariableReference ref, final ExpressionVisitor optimizer) {
        final String ns = ref.getName().getNamespaceURI();
        if (ns != null && !ns.isEmpty()) {

            final Module[] modules = context.getModules(ns);
            if (isNotEmpty(modules)) {
                for (final Module module : modules) {
                    if (module != null && !module.isInternalModule()) {
                        final Collection<VariableDeclaration> vars = ((ExternalModule) module).getVariableDeclarations();
                        for (final VariableDeclaration declaration : vars) {
                            if (declaration.getName().equals(ref.getName()) && declaration.getExpression().isPresent()) {
                                declaration.getExpression().get().accept(optimizer);
                                final Expression expression = simplifyPath(declaration.getExpression().get());
                                final InlineableVisitor visitor = new InlineableVisitor();
                                expression.accept(visitor);
                                if (visitor.isInlineable()) {
                                    final Expression parent = ref.getParent();
                                    if (parent instanceof final RewritableExpression parentRewritableExpression) {
                                        if (LOG.isDebugEnabled()) {
                                            LOG.debug("{} line {}: inlining variable {}", ref.getSource(), ref.getLine(), ref.getName());
                                        }
                                        parentRewritableExpression.replace(ref, expression);
                                    }
                                }

                                return;  // exit function!
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Traverses an expression subtree to check if it could be inlined.
     */
    static class InlineableVisitor extends DefaultExpressionVisitor {

        private boolean inlineable = true;

        public boolean isInlineable() {
            return inlineable;
        }

        @Override
        public void visit(final Expression expr) {
            if (expr instanceof LiteralValue) {
                return;
            }

            if (expr instanceof Atomize ||
                    expr instanceof DynamicCardinalityCheck ||
                    expr instanceof DynamicNameCheck ||
                    expr instanceof DynamicTypeCheck ||
                    expr instanceof UntypedValueCheck ||
                    expr instanceof ConcatExpr ||
                    expr instanceof ArrayConstructor) {
                expr.accept(this);
            } else {
                inlineable = false;
            }
        }

        @Override
        public void visitPathExpr(final PathExpr expr) {
            // continue to check for numeric operators and other simple constructs,
            // abort for all other path expressions with length > 1
            if (expr instanceof OpNumeric ||
                    expr instanceof SequenceConstructor ||
                    expr.getLength() == 1) {
                super.visitPathExpr(expr);
            } else {
                inlineable = false;
            }
        }

        @Override
        public void visitUserFunction(final UserDefinedFunction function) {
            inlineable = false;
        }

        @Override
        public void visitBuiltinFunction(final Function function) {
            inlineable = false;
        }

        @Override
        public void visitFunctionCall(final FunctionCall call) {
            inlineable = false;
        }

        @Override
        public void visitForExpression(final ForExpr forExpr) {
            inlineable = false;
        }

        @Override
        public void visitLetExpression(final LetExpr letExpr) {
            inlineable = false;
        }

        @Override
        public void visitOrderByClause(final OrderByClause orderBy) {
            inlineable = false;
        }

        @Override
        public void visitGroupByClause(final GroupByClause groupBy) {
            inlineable = false;
        }

        @Override
        public void visitWhereClause(final WhereClause where) {
            inlineable = false;
        }

        @Override
        public void visitConditional(final ConditionalExpression conditional) {
            inlineable = false;
        }

        @Override
        public void visitLocationStep(final LocationStep locationStep) {
            // unlike the default, does not visit the step's predicates: a step alone does not prevent inlining
        }

        @Override
        public void visitDocumentConstructor(final DocumentConstructor constructor) {
            inlineable = false;
        }

        @Override
        public void visitElementConstructor(final ElementConstructor constructor) {
            inlineable = false;
        }

        @Override
        public void visitTextConstructor(final DynamicTextConstructor constructor) {
            inlineable = false;
        }

        @Override
        public void visitAttribConstructor(final AttributeConstructor constructor) {
            inlineable = false;
        }

        @Override
        public void visitAttribConstructor(final DynamicAttributeConstructor constructor) {
            inlineable = false;
        }

        @Override
        public void visitUnionExpr(final Union union) {
            inlineable = false;
        }

        @Override
        public void visitIntersectionExpr(final Intersect intersect) {
            inlineable = false;
        }

        @Override
        public void visitVariableDeclaration(final VariableDeclaration decl) {
            inlineable = false;
        }

        @Override
        public void visitTryCatch(final TryCatchExpression tryCatch) {
            inlineable = false;
        }

        @Override
        public void visitCastExpr(final CastExpression expression) {
            inlineable = false;
        }

        @Override
        public void visitGeneralComparison(GeneralComparison comparison) {
            inlineable = false;
        }

        @Override
        public void visitAndExpr(final OpAnd and) {
            inlineable = false;
        }

        @Override
        public void visitOrExpr(final OpOr or) {
            inlineable = false;
        }

        @Override
        public void visitFilteredExpr(final FilteredExpression filtered) {
            inlineable = false;
        }

        @Override
        public void visitVariableReference(final VariableReference ref) {
            inlineable = false;
        }
    }
}
