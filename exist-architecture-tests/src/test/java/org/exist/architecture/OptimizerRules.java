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
package org.exist.architecture;

import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.exist.xquery.Expression;
import org.exist.xquery.RewritableExpression;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * Rules for the XQuery optimizer and the expression tree it rewrites. Included by {@link ArchitectureTest}.
 */
public final class OptimizerRules {

    private OptimizerRules() {
        // rules only
    }

    /**
     * An expression must not keep state between evaluations: compiled queries are reused, so a value cached on an
     * expression during one evaluation is seen by the next. Writes are followed through calls into the expression's
     * own class and its supertypes. Writes to other expressions created during evaluation are not counted, because
     * ArchUnit cannot tell which instance a call targets.
     */
    @ArchTest
    static final ArchRule EVAL_WRITES_NO_EXPRESSION_FIELD = FreezingArchRule.freeze(
            methods().that().haveName("eval")
                    .and().areDeclaredInClassesThat().areAssignableTo(Expression.class)
                    .should(notWriteAnExpressionField())
                    .allowEmptyShould(true)
                    .as("eval does not write a field of an expression"));

    /**
     * New rewrites must not use {@link RewritableExpression#replace}: if the parent cannot replace the child, the
     * rewrite silently does nothing.
     */
    @ArchTest
    static final ArchRule NO_NEW_REPLACE_CALL_SITES = FreezingArchRule.freeze(
            methods().that().areDeclaredInClassesThat().resideInAPackage("org.exist..")
                    .should(notCallRewritableExpressionReplace())
                    .allowEmptyShould(true)
                    .as("no method calls RewritableExpression.replace"));

    private static ArchCondition<JavaMethod> notWriteAnExpressionField() {
        return new ArchCondition<>("not write a field of an expression") {
            @Override
            public void check(final JavaMethod eval, final ConditionEvents events) {
                final Set<String> reported = new HashSet<>();
                for (final JavaFieldAccess write : writesReachableOnItself(eval)) {
                    final String message = eval.getFullName() + " writes " + write.getTargetOwner().getName() + "."
                            + write.getName() + " in " + write.getOrigin().getFullName();
                    if (reported.add(message)) {
                        events.add(SimpleConditionEvent.violated(eval, message));
                    }
                }
            }
        };
    }

    /**
     * Collects the writes to fields of expression classes made by {@code eval} and, transitively, by the methods it
     * calls on its own class and supertypes. Calls to another {@code eval} are not followed.
     */
    private static Set<JavaFieldAccess> writesReachableOnItself(final JavaMethod eval) {
        final Set<JavaFieldAccess> writes = new HashSet<>();
        final Set<JavaCodeUnit> visited = new HashSet<>();
        final Deque<JavaCodeUnit> pending = new ArrayDeque<>();
        pending.push(eval);
        while (!pending.isEmpty()) {
            final JavaCodeUnit unit = pending.pop();
            if (!visited.add(unit)) {
                continue;
            }
            for (final JavaFieldAccess access : unit.getFieldAccesses()) {
                if (access.getAccessType() == JavaFieldAccess.AccessType.SET
                        && access.getTargetOwner().isAssignableTo(Expression.class)) {
                    writes.add(access);
                }
            }
            for (final JavaMethodCall call : unit.getMethodCallsFromSelf()) {
                call.getTarget().resolveMember().ifPresent(target -> {
                    if (!"eval".equals(target.getName()) && eval.getOwner().isAssignableTo(target.getOwner().getName())) {
                        pending.push(target);
                    }
                });
            }
        }
        return writes;
    }

    private static ArchCondition<JavaMethod> notCallRewritableExpressionReplace() {
        return new ArchCondition<>("not call RewritableExpression.replace") {
            @Override
            public void check(final JavaMethod method, final ConditionEvents events) {
                for (final JavaMethodCall call : method.getMethodCallsFromSelf()) {
                    if ("replace".equals(call.getName()) && call.getTargetOwner().isAssignableTo(RewritableExpression.class)) {
                        events.add(SimpleConditionEvent.violated(method, method.getFullName() + " calls "
                                + call.getTargetOwner().getName() + ".replace"));
                    }
                }
            }
        };
    }
}
