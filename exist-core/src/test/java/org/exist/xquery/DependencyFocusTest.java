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

import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the key of a comparison reports about the focus. A key reads the focus when its value depends on the context item,
 * on the position or on the size of the context: such a key cannot be evaluated once for all the items of a step, and the
 * comparison and the rewriters that look a key up once have to ask. {@link Dependency#CONTEXT_SET} cannot answer: a step or
 * {@code .} reports only that, and a builtin function reports the context item for every call.
 * {@link Dependency#CONTEXT_FOCUS} can.
 *
 * <p>The table is the completeness test: one row per kind of expression a key can be. A new kind of expression that holds
 * other expressions and does not pass the bit on shows up as a row that fails.</p>
 */
public class DependencyFocusTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final List<Key> KEYS = List.of(
        // no focus
        neither("'x'"),
        neither("$v"),
        neither("$v/b"),
        noFocus("$v/b[@x]"),
        noFocus("($v, 'y')[1]"),
        noFocus("($v, 'y')[last()]"),
        // the focus functions
        position("last()"),
        position("position()"),
        // a relative path, ".", an absolute path
        focus("@n"),
        focus("../@n"),
        focus("."),
        focus("/wrap"),
        // operators and sequences that hold them
        position("1 + last()"),
        position("(last(), 'x')"),
        position("-last()"),
        position("('a' || last())"),
        position("(1 to last())"),
        focus("(@n | @m)"),
        position("(last() and true())"),
        focus("(@n or false())"),
        focus("(@m = 'x')"),
        neither("($v = 'x')"),
        position("(last() = 1)"),
        // functions: through their arguments, and the forms that default to the context item
        focus("string(@n)"),
        position("string(last())"),
        neither("string('x')"),
        neither("lower-case('X')"),
        neither("lower-case($v)"),
        focus("concat('a', @n)"),
        neither("concat('a', 'b')"),
        focus("data(@n)"),
        neither("substring($v, 1, 2)"),
        focus("count(@n)"),
        focus("exists(@n)"),
        focus("not(@n)"),
        neither("sum((1, 2))"),
        position("round(last() div 2)"),
        neither("true()"),
        focus("string()"),
        focus("name()"),
        focus("normalize-space()"),
        focus("string-length()"),
        focus("number()"),
        focus("root()"),
        focus("id('x')"),
        neither("current-date()"),
        neither("current-dateTime()"),
        neither("default-collation()"),
        // casts and type tests
        focus("xs:string(@n)"),
        neither("xs:string('x')"),
        position("xs:string(last())"),
        position("(last() castable as xs:string)"),
        position("(last() instance of xs:integer)"),
        position("(last() treat as xs:integer)"),
        // user functions: the arguments, not the body
        focus("local:f(@n)"),
        neither("local:f($v)"),
        position("local:f(last())"),
        // conditionals, FLWOR, quantifiers
        position("(if (last() = 1) then 'a' else 'b')"),
        focus("(if (@n = '1') then 'a' else 'b')"),
        neither("(if ($v = 'x') then 'a' else 'b')"),
        position("(if (true()) then last() else 0)"),
        position("(if (true()) then 0 else position())"),
        position("(let $l := last() return string($l))"),
        neither("(let $l := 1 return $l)"),
        neither("(for $i in (1, 2) return $i)"),
        position("(for $i in (1, 2) return $i + last())"),
        position("(some $i in (1, 2) satisfies $i = last())"),
        position("(try { last() } catch * { 0 })"),
        position("(switch (last()) case 1 return 'a' default return 'b')"),
        // simple map and arrow: the right operand has the focus of the left one
        focus("(@n ! string(.))"),
        neither("($v ! string(.))"),
        position("(last() ! string(.))"),
        position("(last() => string())"),
        neither("($v => string())"),
        // constructors
        position("[last()]"),
        position("map { 'k': last() }"),
        position("array { last() }"),
        position("<e>{last()}</e>"),
        neither("<e>{$v}</e>")
    );

    /** What a key is expected to report about the position and the size of the context. */
    private enum Position {
        READS, DOES_NOT_READ, NOT_ASSERTED
    }

    /** A key, and whether it is expected to read the focus and the position or the size of the context. */
    private record Key(String form, boolean focus, Position position) {
    }

    /** Reads neither the focus nor the position. */
    private static Key neither(final String form) {
        return new Key(form, false, Position.DOES_NOT_READ);
    }

    /** Reads the focus, not the position or the size. */
    private static Key focus(final String form) {
        return new Key(form, true, Position.DOES_NOT_READ);
    }

    /** Reads the focus and the position or the size. */
    private static Key position(final String form) {
        return new Key(form, true, Position.READS);
    }

    /** Does not read the focus; what it reports about the position is not part of the contract (a nested predicate). */
    private static Key noFocus(final String form) {
        return new Key(form, false, Position.NOT_ASSERTED);
    }

    @Test
    void keysReportTheFocus() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final List<String> wrong = new ArrayList<>();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            for (final Key key : KEYS) {
                final String query = """
                        xquery version '3.1';
                        declare variable $v := 'x';
                        declare function local:f($x) { $x };
                        collection('/db/none')/wrap/item[@a = %s]
                        """.formatted(key.form());
                final Expression compiled;
                try {
                    compiled = (Expression) xquery.compile(new XQueryContext(broker.getBrokerPool()), query);
                } catch (final Exception e) {
                    wrong.add(key.form() + ": does not compile: " + String.valueOf(e.getMessage()).replace('\n', ' '));
                    continue;
                }
                final GeneralComparison[] found = new GeneralComparison[1];
                compiled.accept(new DefaultExpressionVisitor() {
                    @Override
                    public void visitGeneralComparison(final GeneralComparison comparison) {
                        if (found[0] == null) {
                            found[0] = comparison;
                        }
                        super.visitGeneralComparison(comparison);
                    }
                });
                if (found[0] == null) {
                    wrong.add(key.form() + ": no comparison in the compiled query");
                    continue;
                }
                final int deps = found[0].getRight().getDependencies();
                final boolean readsFocus = Dependency.dependsOn(deps, Dependency.CONTEXT_FOCUS);
                final boolean readsPosition = Dependency.dependsOn(deps, Dependency.CONTEXT_POSITION);
                if (readsFocus != key.focus()) {
                    wrong.add(key.form() + ": reads the focus is " + readsFocus + ", expected " + key.focus()
                            + " (" + Dependency.getDependenciesName(deps) + ")");
                }
                final boolean expectedPosition = key.position() == Position.READS;
                if (key.position() != Position.NOT_ASSERTED && readsPosition != expectedPosition) {
                    wrong.add(key.form() + ": reads the position or the size is " + readsPosition + ", expected "
                            + expectedPosition + " (" + Dependency.getDependenciesName(deps) + ")");
                }
            }
        }
        assertTrue(wrong.isEmpty(), "keys that report the focus wrongly:\n" + String.join("\n", wrong));
    }
}
