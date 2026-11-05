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

/**
 * Distributes a path over a parenthesised union: {@code outer//(A | B [| C ...])} becomes
 * {@code outer//A | outer//B [| outer//C ...]}. The parser wraps the parenthesised union in a PathExpr that holds a
 * single Union (a tree of Unions for n-ary cases). The engine evaluates that wrapper as a generic expression and walks
 * every descendant; after distribution, each branch is looked up by qname through the structural index. This is the
 * rewrite applications otherwise have to do by hand.
 *
 * <p>Safety guards, in addition to the caller's (see {@link Optimizer#visitPathExpr(PathExpr)}):
 * <ul>
 *   <li><em>{@link #hasOnlyLocationStepSuffix}</em>: distribution moves
 *       suffix steps INTO each branch. A non-node-returning suffix like
 *       {@code /string()} would fail the surrounding Union's
 *       operand-must-be-a-node-sequence invariant.</li>
 *   <li><em>{@link #isDistributableUnion}</em>: every leaf branch must
 *       consist only of step-like node expressions (see
 *       {@link #isStepLikeNodeExpr}).</li>
 * </ul>
 *
 * <p>Branch recognition accepts both raw LocationSteps and LocationSteps
 * that {@link Optimizer#visitLocationStep(LocationStep)} has wrapped in an
 * {@code ExtensionExpression(#exist:optimize#)} pragma; the wrapper
 * preserves node-yielding semantics so distribution remains safe.
 */
final class UnionStepDistribution {

    private UnionStepDistribution() {
        // static helpers only
    }

    /**
     * Rewrites every parenthesised union step of {@code pathExpr} that can be distributed.
     *
     * @param pathExpr the path; the caller ensures it is not inside a predicate
     * @return true if the path was rewritten
     */
    static boolean distribute(final PathExpr pathExpr) {
        boolean rewritten = false;
        boolean rewroteAny = true;
        while (rewroteAny) {
            rewroteAny = false;
            for (int i = 0; i < pathExpr.getLength(); i++) {
                final Expression step = pathExpr.getExpression(i);
                if (step.getClass() != PathExpr.class || !(step instanceof final PathExpr inner)
                        || inner.getLength() != 1) {
                    continue;
                }
                final Expression innerStep = inner.getExpression(0);
                if (innerStep instanceof final Union innerUnion
                        && hasOnlyLocationStepSuffix(pathExpr, i)
                        && isDistributableUnion(innerUnion)) {
                    final Union distributed = distributeOverUnion(pathExpr, i, innerUnion);
                    distributed.setLocation(inner.getLine(), inner.getColumn());
                    pathExpr.replaceAllSteps(distributed);
                    rewritten = true;
                    rewroteAny = true;
                    break;
                }
            }
        }
        return rewritten;
    }

    /**
     * Check that every step after {@code unionStepIndex} in {@code outer} is
     * a LocationStep. After distribution those suffix steps land at the end
     * of each new branch, and the surrounding Union requires each branch to
     * yield a node sequence. LocationSteps preserve nodes; function-call or
     * filter steps generally don't.
     */
    private static boolean hasOnlyLocationStepSuffix(final PathExpr outer, final int unionStepIndex) {
        for (int j = unionStepIndex + 1; j < outer.getLength(); j++) {
            if (!isStepLikeNodeExpr(outer.getExpression(j))) {
                return false;
            }
        }
        return true;
    }

    /**
     * True if {@code e} returns nodes from a step-like position: either a
     * raw LocationStep, or an ExtensionExpression introduced by
     * {@link Optimizer#visitLocationStep(LocationStep)} to wrap an optimizable
     * LocationStep in a {@code #exist:optimize#} pragma. The pragma wrapper
     * preserves the wrapped step's node-yielding semantics, so distribution
     * over a union remains valid.
     */
    private static boolean isStepLikeNodeExpr(final Expression e) {
        return e instanceof LocationStep
                || e instanceof final ExtensionExpression ext && ext.getExpression() instanceof LocationStep;
    }

    /**
     * A Union is distributable over an outer path if every leaf branch is a
     * non-empty PathExpr consisting only of step-like node expressions
     * (raw LocationSteps, or LocationSteps wrapped in the optimizer's
     * {@code #exist:optimize#} ExtensionExpression -- see
     * {@link #isStepLikeNodeExpr(Expression)}). Nested Unions on either side
     * recurse: an n-ary union {@code A | B | C} parses as
     * {@code Union(Union(A, B), C)} where one branch wraps a Union.
     *
     * <p>This conservative check rules out branches that depend on the outer
     * context in a way distribution would break (e.g. branches containing
     * {@code position()} or {@code last()} as a top-level step expression,
     * which would parse as a FunctionCall step rather than a LocationStep).
     * Predicates within a LocationStep are fine -- they refer to the step's
     * own context, not the outer.
     */
    private static boolean isDistributableUnion(final Union union) {
        return isDistributableBranch(union.getLeft())
                && isDistributableBranch(union.getRight());
    }

    /**
     * True if a union branch is either a nested union that is itself distributable, or a non-empty path of step-like
     * node expressions.
     */
    private static boolean isDistributableBranch(final PathExpr branch) {
        if (branch == null || branch.getLength() == 0) {
            return false;
        }
        // Single-step branch holding another Union: recurse for n-ary.
        if (branch.getLength() == 1 && branch.getExpression(0) instanceof final Union nested) {
            return isDistributableUnion(nested);
        }
        for (int i = 0; i < branch.getLength(); i++) {
            if (!isStepLikeNodeExpr(branch.getExpression(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Build a new Union whose branches are full distributed paths:
     * {@code outer.steps[0..i-1]} + branch.steps + {@code outer.steps[i+1..n]}
     * for each branch of {@code innerUnion}. Recurses into nested Unions so
     * n-ary cases get fully distributed.
     */
    private static Union distributeOverUnion(final PathExpr outer, final int unionStepIndex,
                                       final Union innerUnion) {
        final PathExpr distributedLeft = distributeBranch(outer, unionStepIndex, innerUnion.getLeft());
        final PathExpr distributedRight = distributeBranch(outer, unionStepIndex, innerUnion.getRight());
        // Use the original outer PathExpr's static context, not the Optimizer's
        // own context field. When the Optimizer is invoked over an imported
        // module's AST, the two share the same module context — but when the
        // outer PathExpr was constructed in a different module from the one
        // currently driving the optimizer pass (e.g. a path inside a user
        // function called by a higher-level query), `this.context` is the
        // caller's context and lacks the namespace declarations needed to
        // resolve the prefixed names baked into the inner steps. Re-analysis
        // after the rewrite then raises a spurious XPST0081. See #6385.
        final Union result = new Union(outer.getContext(), distributedLeft, distributedRight);
        result.setLocation(innerUnion.getLine(), innerUnion.getColumn());
        return result;
    }

    /**
     * Distribute the outer's surrounding steps over a single Union branch.
     * If the branch itself wraps a nested Union (the n-ary case), recurse to
     * produce a distributed Union and wrap it in a single-step PathExpr to
     * preserve the AST shape Union expects on its branches. Otherwise build
     * a fresh PathExpr containing the prefix + branch's steps + suffix.
     *
     * <p>Prefix/suffix LocationSteps are <em>cloned</em> per-branch via
     * {@link #cloneLocationStepIfPossible(Expression)} rather than shared.
     * Sharing causes a real runtime regression: {@link LocationStep#analyze}
     * mutates per-instance state (notably rewriting {@code //}'s axis from
     * descendant-or-self to descendant, and storing {@code parent},
     * {@code unordered}, {@code staticReturnType}, etc.). With one shared
     * step instance reachable from multiple branches, only the last branch
     * to analyze "wins" and downstream eval (index dispatch in particular)
     * loses the per-branch context, leaving the rewritten path no faster
     * than the original parens form. Cloning gives each branch its own
     * mutable state and matches the manual rewrite's runtime profile.
     * Non-LocationStep prefix expressions (VariableReference, FunctionCall,
     * etc.) are still shared -- their analyze paths don't carry destructive
     * per-branch state.
     */
    private static PathExpr distributeBranch(final PathExpr outer, final int unionStepIndex,
                                       final PathExpr branch) {
        // See note in distributeOverUnion: take the static context from the
        // outer path being rewritten, not the Optimizer's own context.
        final XQueryContext outerContext = outer.getContext();
        if (branch.getLength() == 1 && branch.getExpression(0) instanceof final Union nested) {
            final Union distributedNested = distributeOverUnion(outer, unionStepIndex, nested);
            final PathExpr wrap = new PathExpr(outerContext);
            wrap.setLocation(branch.getLine(), branch.getColumn());
            wrap.add(distributedNested);
            return wrap;
        }
        final PathExpr distributed = new PathExpr(outerContext);
        distributed.setLocation(branch.getLine(), branch.getColumn());
        for (int j = 0; j < unionStepIndex; j++) {
            addStepWithParent(distributed, cloneLocationStepIfPossible(outer.getExpression(j)));
        }
        for (int j = 0; j < branch.getLength(); j++) {
            addStepWithParent(distributed, branch.getExpression(j));
        }
        for (int j = unionStepIndex + 1; j < outer.getLength(); j++) {
            addStepWithParent(distributed, cloneLocationStepIfPossible(outer.getExpression(j)));
        }
        return distributed;
    }

    /**
     * Add {@code step} to {@code branch} and update its {@code parent}
     * pointer. Setting the parent matters BEFORE the post-distribution
     * super-descent: visitLocationStep reads {@code locationStep.getParentExpression()}
     * to find the {@code RewritableExpression} it should call replace() on
     * when wrapping a predicated step in {@code (#exist:optimize#)}. Without
     * this, the LocationStep's parent still points at the original
     * pre-distribution branch PathExpr (or is null for cloned prefix steps),
     * so the wrap is inserted into a dead branch and the new branches end
     * up unwrapped -- a 5x perf miss observed empirically against the
     * manually-rewritten form.
     */
    private static void addStepWithParent(final PathExpr branch, final Expression step) {
        branch.add(step);
        if (step instanceof final LocationStep ls) {
            ls.setParent(branch);
        }
    }

    /**
     * Return a per-branch-private copy of {@code e} if it is a simple
     * LocationStep (no predicates); otherwise return {@code e} unchanged.
     * The simple-step case covers {@code //}, {@code /}, {@code ..}, named
     * axis steps without filters -- the common shapes that appear as
     * outer-path prefixes in the rewrites this optimizer performs. Steps
     * carrying predicates are not cloned because re-creating Predicate
     * subtrees would require deep AST cloning machinery the engine doesn't
     * provide; in those cases we accept residual sharing rather than block
     * the rewrite. ExtensionExpression-wrapped steps fall through (the
     * wrapper is the optimizer's own {@code #exist:optimize#} pragma and
     * its inner LocationStep typically carries the predicates we'd need to
     * deep-clone anyway).
     */
    private static Expression cloneLocationStepIfPossible(final Expression e) {
        if (e instanceof final LocationStep ls
                && (ls.getPredicates() == null || ls.getPredicates().length == 0)) {
            // Preserve the step's own static context — see note in distributeOverUnion.
            final LocationStep clone = new LocationStep(ls.getContext(), ls.getAxis(), ls.getTest());
            clone.setAbbreviated(ls.isAbbreviated());
            clone.setLocation(ls.getLine(), ls.getColumn());
            return clone;
        }
        return e;
    }
}
