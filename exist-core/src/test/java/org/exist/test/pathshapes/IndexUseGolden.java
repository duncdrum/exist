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
package org.exist.test.pathshapes;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which index answered each test of a generated path-shapes file, with the optimizer on and off, kept in a golden file next
 * to it. The answers of the path-shapes tests cannot show an index that stopped being used: the result is the same. The
 * golden file can, as a diff in review.
 *
 * <p>A line is {@code test | optimizer on | optimizer off}. Each side lists what the profiler traced for that test, as
 * {@code type:level} sorted, or {@code -} for nothing: the type is the index ({@code new-range}, {@code lucene},
 * {@code ngram}, ...; {@code range} is the comparison and {@code fn:matches} paths, which trace themselves too) and the
 * level is {@code OPTIMIZED} (pre-selected from the index), {@code BASIC} (evaluated as written by an index function) or
 * {@code NONE} (a comparison evaluated as written).</p>
 *
 * <p>Run the test of a module with {@code -Dpath-shapes.write=true} to write the file, review the diff, and commit it.
 * Two things the recording avoids: a traced query must not contain {@code starts-with}, which the parser rewrites into a
 * comparison that traces itself, so the names of the tests are read before tracing starts; and tracing is database wide,
 * so the tests of one module run one after the other.</p>
 */
public final class IndexUseGolden {

    private static final String NAMESPACE = "http://exist-db.org/xquery/test/path-shapes/";

    private IndexUseGolden() {
        // static helpers
    }

    /**
     * Runs each test function of a generated path-shapes module with tracing on, once with the optimizer on and once off.
     *
     * @param pool the database the module's tests run against
     * @param name the name of the module's matrix, which gives its namespace
     * @param xqm the generated file
     * @return a line per test, sorted by name: {@code test | optimizer on | optimizer off}
     */
    public static List<String> record(final BrokerPool pool, final String name, final Path xqm)
            throws EXistException, PermissionDeniedException, XPathException {
        final String module = xqm.toAbsolutePath().toUri().toString();
        final String namespace = NAMESPACE + name;
        final String imports = "import module namespace ps = '" + namespace + "' at '" + module + "'; "
                + "import module namespace system = 'http://exist-db.org/xquery/system'; "
                + "import module namespace inspect = 'http://exist-db.org/xquery/inspection'; "
                + "declare namespace stats = 'http://exist-db.org/xquery/profiling'; ";

        final String names = query(pool, imports, "yes", "for $f in inspect:module-functions(xs:anyURI('" + module + "')) "
                + "where exists(inspect:inspect-function($f)/annotation[starts-with(@name, 'test:assert')]) "
                + "return local-name-from-QName(function-name($f))");
        final String list = Arrays.stream(names.split("\n")).map(n -> "'" + n + "'").collect(Collectors.joining(", "));
        final String loop = "ps:setUp(), (for $name in (" + list + ") "
                + "let $f := function-lookup(QName('" + namespace + "', $name), 0) "
                + "let $cleared := system:clear-trace() "
                + "let $called := try { $f() ! () } catch * { 'error' } "
                + "let $trace := string-join(distinct-values(for $i in system:trace()//stats:index "
                + "return $i/@type || ':' || $i/@optimization-level), ',') "
                + "return $name || ' = ' || (if (empty($called)) then '' else 'ERROR,') || (if ($trace) then $trace else '-')), "
                + "ps:tearDown()";

        final Map<String, String[]> byTest = new TreeMap<>();
        for (final String mode : new String[] {"yes", "no"}) {
            query(pool, imports, mode, "system:clear-trace(), system:enable-tracing(true(), false())");
            try {
                for (final String line : query(pool, imports, mode, loop).split("\n")) {
                    final int split = line.indexOf(" = ");
                    if (split > 0) {
                        final String[] sides = byTest.computeIfAbsent(line.substring(0, split), k -> new String[2]);
                        sides["yes".equals(mode) ? 0 : 1] = normalized(line.substring(split + 3));
                    }
                }
            } finally {
                query(pool, imports, mode, "system:enable-tracing(false())");
            }
        }
        final List<String> lines = new ArrayList<>();
        byTest.forEach((test, sides) -> lines.add(test + " | " + sides[0] + " | " + sides[1]));
        return lines;
    }

    /**
     * Compares the recorded lines with the golden file, or writes it when {@code path-shapes.write} is {@code true}.
     *
     * @param recorded the lines of {@link #record}
     * @param golden the golden file
     */
    public static void check(final List<String> recorded, final Path golden) throws IOException {
        if (Boolean.getBoolean("path-shapes.write")) {
            final List<String> out = new ArrayList<>();
            out.add("# Which index answered each test of the generated file next to this one, optimizer on and off. Generated by");
            out.add("# org.exist.test.pathshapes.IndexUseGolden: run the module's IndexUseGoldenTest with -Dpath-shapes.write=true,");
            out.add("# review the diff, and commit it. A line is: test | optimizer on | optimizer off, each side type:level sorted, - for nothing.");
            out.addAll(recorded);
            Files.write(golden, out, StandardCharsets.UTF_8);
            return;
        }
        final List<String> expected = Files.readAllLines(golden, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.startsWith("#") && !line.isBlank())
                .toList();
        assertEquals(expected, recorded, golden + " is not what the tests trace: review the difference, then run this test with -Dpath-shapes.write=true");
    }

    private static String normalized(final String trace) {
        return Arrays.stream(trace.split(",")).distinct().sorted().collect(Collectors.joining(" "));
    }

    private static String query(final BrokerPool pool, final String imports, final String optimize, final String body)
            throws EXistException, PermissionDeniedException, XPathException {
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final String source = "xquery version '3.1'; " + imports + "declare option exist:optimize 'enable=" + optimize + "'; " + body;
            final Sequence seq = xquery.execute(broker, xquery.compile(new XQueryContext(pool), source), null);
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < seq.getItemCount(); i++) {
                sb.append(i == 0 ? "" : "\n").append(seq.itemAt(i).getStringValue());
            }
            return sb.toString();
        }
    }
}
