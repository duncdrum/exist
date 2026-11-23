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

import org.exist.dom.QName;
import org.exist.storage.DBBroker;
import org.exist.xquery.pragmas.Optimize;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.util.ExpressionDumper;

import javax.annotation.Nullable;
import java.util.*;

import static org.exist.xquery.ConstantFalsePredicate.hasConstantFalsePredicate;
import static org.exist.xquery.ExpressionUnwrap.simplifyPath;
import static org.exist.xquery.ExpressionUnwrap.unwrapOperand;

/**
 * Analyzes the query and marks optimizable expressions for the query engine.
 * This class just searches for potentially optimizable expressions in the query tree and
 * encloses those expressions with an (#exist:optimize#) pragma. The real optimization
 * work is not done by this class but by the pragma (see {@link org.exist.xquery.pragmas.Optimize}).
 * The pragma may also decide that the optimization is not applicable and just execute
 * the expression without any optimization.
 *
 * The optimizer is enabled by default ({@link XQueryContext#enableOptimizer} defaults to {@code true}
 * and the bundled conf.xml ships with {@code enable-query-rewriting="yes"}). To disable it globally,
 * change the attribute in conf.xml:
 *
 *  &lt;xquery enable-java-binding="no" enable-query-rewriting="no"&gt;...
 *
 * To enable/disable the optimizer for a single query, use an option:
 *
 * <pre>declare option exist:optimize "enable=yes|no";</pre>
 *
 */
public class Optimizer extends DefaultExpressionVisitor {

    private static final Logger LOG = LogManager.getLogger(Optimizer.class);

    private final XQueryContext context;
    private final List<QueryRewriter> rewriters;
    private final FindOptimizable findOptimizable = new FindOptimizable();

    private int predicates = 0;

    private boolean hasOptimized = false;

    public Optimizer(final XQueryContext context) {
        this.context = context;
        final DBBroker broker = context.getBroker();
        this.rewriters = broker != null ? broker.getIndexController().getQueryRewriters(context) : Collections.emptyList();
    }

    public boolean hasOptimized() {
        return hasOptimized;
    }

    @Override
    public void visitLocationStep(final LocationStep locationStep) {
        super.visitLocationStep(locationStep);

        // check query rewriters if they want to rewrite the location step
        Pragma optimizePragma = null;
        try {  // Keep try-catch out of loop
            for (final QueryRewriter rewriter : rewriters) {
                optimizePragma = rewriter.rewriteLocationStep(locationStep);
                if (optimizePragma != null) {
                    // expression was rewritten: return
                    hasOptimized = true;
                    break;
                }
            }
        } catch (final XPathException e) {
            LOG.warn("Exception called while rewriting location step: {}", e.getMessage(), e);
        }

        @Nullable final Predicate[] preds = locationStep.getPredicates();
        final boolean optimize = shouldWrapWithOptimizePragma(preds);

        final Expression parent = locationStep.getParentExpression();

        if (optimize) {
            // we found at least one Optimizable. Rewrite the whole expression and
            // enclose it in an (#exist:optimize#) pragma.
            if (!(parent instanceof final RewritableExpression path)) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Parent expression of step is not a PathExpr: {}", parent);
                }
                return;
            }

            hasOptimized = true;

            try {
                // Create the pragma
                final ExtensionExpression extension = new ExtensionExpression(context);
                if (optimizePragma != null) {
                    extension.addPragma(optimizePragma);
                }
                extension.addPragma(new Optimize(extension, context, Optimize.OPTIMIZE_PRAGMA, null, false));
                extension.setExpression(locationStep);

                // Replace the old expression with the pragma
                path.replace(locationStep, extension);

                if (LOG.isTraceEnabled()) {
                    LOG.trace("Rewritten expression: {}", ExpressionDumper.dump(parent));
                }
            } catch (final XPathException e) {
                LOG.warn("Failed to optimize expression: {}: {}", locationStep, e.getMessage(), e);
            }
        } else if (optimizePragma != null) {
            final ExtensionExpression extension = new ExtensionExpression(context);
            extension.addPragma(optimizePragma);
            extension.setExpression(locationStep);

            // Replace the old expression with the pragma
            final RewritableExpression path = (RewritableExpression) parent;
            path.replace(locationStep, extension);
        }
    }

    /**
     * Two related rewrites for parenthesised step expressions. The parser
     * produces a wrapping PathExpr for any parenthesised step expression
     * without outer predicates. At runtime the engine treats such wrapped
     * steps as generic expressions and materialises the descendant axis
     * instead of dispatching by qname through the structural index -- a
     * ~50x slowdown at scale, since the generic path walks every descendant
     * node while an indexed step looks the qname up directly.
     *
     * <p>Case A -- {@code //(name)} -> {@code //name}: the wrapping PathExpr
     * contains a single LocationStep; lift it out.
     *
     * <p>Case B -- {@code outer//(A | B [| C ...])} ->
     * {@code outer//A | outer//B [| outer//C ...]}: the wrapping PathExpr
     * contains a single Union. {@link UnionStepDistribution} describes the
     * rewrite and when a union is distributed.
     *
     * <p>The wrapping PathExpr by definition has no predicates of its own
     * (predicates would have produced a FilteredExpression, handled by
     * visitFilteredExpr) so both rewrites are semantics-preserving.
     *
     * <p>Case B is only tried outside predicates ({@code predicates == 0}):
     * a Union built inside a predicate does not pass the predicate's
     * contextId through its branches, so the per-step candidate-to-result
     * mapping is lost and the predicate engine throws "context is missing
     * for node" (see {@code Predicate.selectByNodeSet}).
     */
    @Override
    public void visitPathExpr(final PathExpr pathExpr) {
        // Case B runs BEFORE super.visitPathExpr descends. If we let super
        // run first, visitLocationStep would wrap each branch's predicated
        // LocationStep in an ExtensionExpression(#exist:optimize#) pragma at
        // its current parent (the parens-PathExpr) before distribution.
        // After distribution, the pragma stays on each branch's step but it
        // was set up against the parens-context, where the contextSequence
        // at eval time is huge (every descendant of the outer prefix). The
        // pragma's preSelect+findAncestors path then can't preselect
        // efficiently and falls through to a generic node-by-node filter --
        // the same ~9s slowdown the rewrite is meant to avoid. Distributing
        // first, then descending, lets visitLocationStep wrap each branch's
        // step at the BRANCH-PathExpr parent, matching the runtime profile
        // of the hand-written outer//A | outer//B | outer//C ...
        if (predicates == 0 && UnionStepDistribution.distribute(pathExpr)) {
            hasOptimized = true;
        }

        super.visitPathExpr(pathExpr);

        // Case A is safe to apply post-descent: the single-LocationStep
        // parens unwrap doesn't move pragma wrappers across parent boundaries.
        boolean rewroteAny = true;
        while (rewroteAny) {
            rewroteAny = false;
            for (int i = 0; i < pathExpr.getLength(); i++) {
                final Expression step = pathExpr.getExpression(i);
                // Exact-class check: many PathExpr subclasses (UnaryExpr,
                // BinaryOp, OpNumeric, EnclosedExpr, LogicalOp, RangeExpression, ...)
                // are semantically loaded and must never be unwrapped. Only
                // the parser's generic parens-wrapper has class == PathExpr.class.
                if (step.getClass() != PathExpr.class || !(step instanceof final PathExpr inner)
                        || inner.getLength() != 1) {
                    continue;
                }
                final Expression innerStep = inner.getExpression(0);
                if (innerStep instanceof final LocationStep innerLocationStep) {
                    pathExpr.replace(inner, innerLocationStep);
                    innerLocationStep.setParent(pathExpr);
                    hasOptimized = true;
                    visitLocationStep(innerLocationStep);
                    rewroteAny = true;
                    break;
                }
            }
        }
    }

    @Override
    public void visitFilteredExpr(final FilteredExpression filtered) {
        super.visitFilteredExpr(filtered);

        // check if filtered expression can be simplified:
        // handles expressions like //foo/(baz)[...]
        if (filtered.getExpression() instanceof final LocationStep step) {
            // single location step: simplify by directly attaching it to the parent path expression
            final Expression parent = filtered.getParent();
            if (parent instanceof final RewritableExpression rewritableParentExpression) {
                final List<Predicate> preds = filtered.getPredicates();
                final boolean optimizable = hasOptimizable(preds);
                if (optimizable) {
                    // copy predicates
                    for (Predicate pred : preds) {
                        step.addPredicate(pred);
                    }
                    rewritableParentExpression.replace(filtered, step);
                    step.setParent(parent);
                    visitLocationStep(step);
                    return;
                }
            }
        }

        // check if there are any predicates which could be optimized
        final List<Predicate> preds = filtered.getPredicates();
        final boolean optimize = hasOptimizable(preds) && !hasConstantFalsePredicate(preds);
        if (optimize) {
            // we found at least one Optimizable. Rewrite the whole expression and
            // enclose it in an (#exist:optimize#) pragma.
            final Expression parent = filtered.getParent();
            if (!(parent instanceof final RewritableExpression path)) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Parent expression: {} of step does not implement RewritableExpression", parent.getClass().getName());
                }
                return;
            }
            if (LOG.isTraceEnabled()) {
                LOG.trace("Rewriting expression: {}", ExpressionDumper.dump(filtered));
            }

            hasOptimized = true;

            try {
                // Create the pragma
                final ExtensionExpression extension = new ExtensionExpression(context);
                extension.addPragma(new Optimize(extension, context, Optimize.OPTIMIZE_PRAGMA, null, false));
                extension.setExpression(filtered);
                // Replace the old expression with the pragma
                path.replace(filtered, extension);
            } catch (final XPathException e) {
                LOG.warn("Failed to optimize expression: {}: {}", filtered, e.getMessage(), e);
            }
        }
    }

    private boolean hasOptimizable(final List<Predicate> preds) {
        // walk through the predicates attached to the current location step.
        // try to find a predicate containing an expression which is an instance
        // of Optimizable.
        boolean optimizable = false;
        for (int i = 0; i < preds.size(); i++) {
            preds.get(i).accept(findOptimizable);
            @Nullable final Optimizable[] list = findOptimizable.getOptimizables();
            findOptimizable.reset();
            if (list != null && (i > 0 || list.length > 1) && hasNonFilter(list)) {
                // pre-selection evaluates it before the predicates in front of it, or after the other index
                // expressions of its own predicate: only a filter gives the same result in either order, so the
                // step is evaluated as written
                return false;
            }
            optimizable |= canOptimize(list);
        }
        return optimizable;
    }

    private static boolean hasNonFilter(final Optimizable[] list) {
        for (final Optimizable optimizable : list) {
            if (!optimizable.isFilter()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Decide whether to wrap a LocationStep's predicates in
     * {@code (#exist:optimize#)}. Returns {@code true} only when at least one
     * predicate is index-eligible AND no predicate folds to compile-time
     * false (see GH-3918).
     */
    private boolean shouldWrapWithOptimizePragma(@Nullable final Predicate[] preds) {
        if (preds == null) {
            return false;
        }
        return hasOptimizable(Arrays.asList(preds)) && !hasConstantFalsePredicate(preds);
    }

    @Override
    public void visitAndExpr(final OpAnd and) {
        if (predicates > 0) {
            if (AndPredicateSplitter.split(context, and)) {
                hasOptimized = true;
            }
        } else if (and.isRewritable()) {
        	and.getLeft().accept(this);
			and.getRight().accept(this);
        }
    }

    @Override
    public void visitOrExpr(final OpOr or) {
    	if (or.isRewritable()) {
        	or.getLeft().accept(this);
			or.getRight().accept(this);
        }
	}

    @Override
    public void visitGeneralComparison(final GeneralComparison comparison) {
        comparison.getLeft().accept(this);
        comparison.getRight().accept(this);
    }

    @Override
    public void visitPredicate(final Predicate predicate) {
        ++predicates;
        super.visitPredicate(predicate);
        --predicates;
    }

    /**
     * Inlines a global variable if {@link GlobalVariableInliner} allows it.
     *
     * @param ref the variable reference
     */
    @Override
    public void visitVariableReference(final VariableReference ref) {
        GlobalVariableInliner.inline(context, ref, this);
    }

    private boolean canOptimize(@Nullable final Optimizable[] list) {
        if (list == null || list.length == 0) {
            return false;
        }

        for (final Optimizable optimizable : list) {
            final int axis = optimizable.getOptimizeAxis();
            if (!(axis == Constants.CHILD_AXIS || axis == Constants.DESCENDANT_AXIS ||
                    axis == Constants.DESCENDANT_SELF_AXIS || axis == Constants.ATTRIBUTE_AXIS ||
                    axis == Constants.DESCENDANT_ATTRIBUTE_AXIS || axis == Constants.SELF_AXIS
            )) {
                return false;
            }
        }
        return true;
    }

    /**
     * Try to find an expression object implementing interface Optimizable.
     */
    public static class FindOptimizable extends BasicExpressionVisitor {
        private @Nullable Optimizable[] optimizables = null;

        public @Nullable Optimizable[] getOptimizables() {
            return optimizables;
        }

        @Override
        public void visitPathExpr(final PathExpr expression) {
            for (int i = 0; i < expression.getLength(); i++) {
                final Expression next = expression.getExpression(i);
                next.accept(this);
            }
        }

        @Override
        public void visitGeneralComparison(final GeneralComparison comparison) {
            addOptimizable(comparison);
        }

        @Override
        public void visitPredicate(final Predicate predicate) {
            final Expression inner = predicate.getExpression(0);
            if (visitQuantifiedMatch(unwrapOperand(inner))) {
                return;
            }
            inner.accept(this);
        }

        /**
         * Recognizes {@code some $v in PATH satisfies f($v, ...)} where {@code f} is optimizable,
         * and points {@code f} at {@code PATH} so the range index can serve it.
         *
         * <p>This spelling is the specification-conformant way to ask "does any of these match?"
         * for a function whose signature takes a single item. Without this, such a predicate falls
         * back to evaluating the quantifier item by item, which is exactly the work the index
         * exists to avoid -- so the conformant spelling would be the slow one.</p>
         *
         * <p>Quantified expressions are invisible to {@link ExpressionVisitor}, which has no visit
         * method for them, so this is matched directly rather than through an {@code accept} call.</p>
         *
         * @param expression the predicate's expression
         * @return true if this was an optimizable quantified match, and has been registered
         */
        private boolean visitQuantifiedMatch(final Expression expression) {
            if (!(expression instanceof final QuantifiedExpression quantified)) {
                return false;
            }

            // "every" cannot use the index: it needs the non-matching nodes, which a
            // lookup by definition does not return.
            if (quantified.getType() != FLWORClause.ClauseType.SOME) {
                return false;
            }

            final QName boundVariable = quantified.getVariable();
            final Expression boundSequence = quantified.getInputSequence();
            if (boundVariable == null || boundSequence == null) {
                return false;
            }

            // The satisfies clause must be the optimizable call itself. Anything larger -- a
            // disjunction, say -- would make narrowing the candidate set unsound, because a node
            // could qualify through the other branch.
            if (!(unwrapOperand(quantified.getReturnExpression()) instanceof final Function function)
                    || !(function instanceof final BoundSequenceOptimizable optimizable)
                    || !isReferenceTo(boundVariable, function)) {
                return false;
            }

            // preSelect evaluates the remaining arguments once, outside the quantifier, where the
            // bound variable does not exist. A pattern such as matches($v, $v) is legal XQuery, so
            // decline rather than evaluate it unbound.
            if (!remainingArgumentsAreIndependentOf(boundVariable, function)) {
                return false;
            }

            optimizable.optimizeOverBoundSequence(boundSequence);
            addOptimizable(optimizable);
            return true;
        }

        /** The bound variable must be the argument the function would otherwise index on. */
        private boolean isReferenceTo(final QName boundVariable, final Function function) {
            if (function.getArgumentCount() == 0) {
                return false;
            }
            return unwrapOperand(function.getArgument(0)) instanceof final VariableReference ref
                    && boundVariable.equals(ref.getName());
        }

        /**
         * Strips the checks the compiler wraps around a function argument to enforce its declared
         * type, along with the wrappers {@link ExpressionUnwrap#unwrapOperand} removes.
         */
        private static @Nullable Expression unwrapArgumentChecks(final Expression argument) {
            Expression current = unwrapOperand(argument);
            while (current instanceof DynamicCardinalityCheck || current instanceof DynamicTypeCheck
                    || current instanceof Atomize || current instanceof UntypedValueCheck) {
                current = unwrapOperand(current.getSubExpression(0));
            }
            return current;
        }

        /**
         * True if every argument after the first is a literal or a reference to a variable other
         * than {@code boundVariable}, so that evaluating it once, outside the quantifier, gives the
         * value it has on every iteration.
         *
         * <p>This is an allowlist rather than a search for references to the bound variable. The
         * expression visitors do not see into every kind of expression -- a quantified expression,
         * for one, has no visit method -- so a search could miss a reference and optimize a query
         * it must not. Literal and variable patterns cover how the quantified form is written in
         * practice.</p>
         */
        private boolean remainingArgumentsAreIndependentOf(final QName boundVariable, final Function function) {
            for (int i = 1; i < function.getArgumentCount(); i++) {
                final Expression argument = unwrapArgumentChecks(function.getArgument(i));
                final boolean independent = argument instanceof LiteralValue
                        || (argument instanceof final VariableReference ref && !boundVariable.equals(ref.getName()));
                if (!independent) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void visitBuiltinFunction(final Function function) {
            if (function instanceof final Optimizable optimizable) {
                addOptimizable(optimizable);
            }
        }

        private void addOptimizable(final Optimizable optimizable) {
            if (optimizables == null) {
                optimizables = new Optimizable[1];
            } else {
                optimizables = Arrays.copyOf(optimizables, optimizables.length + 1);
            }
            optimizables[optimizables.length - 1] = optimizable;
        }

        /**
         * Reset this visitor for reuse.
         *
         * Clears the known {@link #optimizables}.
         */
        public void reset() {
            this.optimizables = null;
        }
    }

}
