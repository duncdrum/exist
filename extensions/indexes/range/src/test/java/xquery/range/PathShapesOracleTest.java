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
package xquery.range;

import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmMap;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The expected answers of the path-shapes matrix, checked against two other engines. The matrix answers were signed off by hand,
 * and its tests compare eXist with them; this test compares the same queries, rewritten to run over variables, on in-memory
 * copies of the documents in eXist and in Saxon-HE, with the same expected answers. A wrong answer in the matrix then shows as a
 * disagreement, not as a test that quietly passes.
 *
 * <p>Only tests that use no index function are checked: the comparisons, {@code fn:matches}, {@code fn:contains} and the tests that
 * run them again over other collections. {@code range:}, {@code ft:} and {@code ngram:} functions mean nothing in another engine;
 * their answers are pinned against the equivalent comparison instead.</p>
 */
public class PathShapesOracleTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String SEP1 = "¶";
    private static final String SEP2 = "¦";

    private static String run(final String query) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final Sequence seq = xquery.execute(broker, xquery.compile(new XQueryContext(pool), query), null);
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < seq.getItemCount(); i++) {
                sb.append(i == 0 ? "" : SEP2).append(seq.itemAt(i).getStringValue());
            }
            return sb.toString();
        }
    }

    private static String quote(final String s) {
        return "'" + s.replace("'", "''") + "'";
    }

    @Test
    void noIndex() throws Exception {
        check("noindex", Path.of("src/test/xquery/range/path-shapes-noindex.xqm"));
    }

    @Test
    void range() throws Exception {
        check("range", Path.of("src/test/xquery/range/path-shapes.xqm"));
    }

    private void check(final String name, final Path xqm) throws Exception {
        final String ns = "http://exist-db.org/xquery/test/path-shapes/" + name;
        final String coll = "/db/test-path-shapes-" + name;
        final String module = Files.readString(xqm);
        final String uri = xqm.toAbsolutePath().toUri().toString();
        final String imports = "xquery version '3.1'; import module namespace ps = '" + ns + "' at '" + uri + "'; ";
        run(imports + "ps:setUp()");
        try {
            // every collection of the module, with the documents under it
            final String entries = run("xquery version '3.1'; import module namespace xmldb = 'http://exist-db.org/xquery/xmldb'; "
                    + "declare function local:all($c) { $c, for $s in xmldb:get-child-collections($c) return local:all($c || '/' || $s) }; "
                    + "for $root in (" + quote(coll) + ", " + quote(coll + "-bare") + ", " + quote(coll + "-partial") + ") "
                    + "where xmldb:collection-available($root) "
                    + "for $c in local:all($root) "
                    + "for $r in xmldb:get-child-resources($c) "
                    + "let $d := doc($c || '/' || $r) "
                    + "return $c || '" + SEP1 + "' || $r || '" + SEP1 + "' || serialize($d, map {'omit-xml-declaration': true()})");
            final Processor saxon = new Processor(false);
            final DocumentBuilder builder = saxon.newDocumentBuilder();
            final Map<String, List<XdmNode>> collsSaxon = new LinkedHashMap<>();
            final Map<String, XdmNode> docsSaxon = new LinkedHashMap<>();
            final Map<String, String> memDocs = new LinkedHashMap<>();
            for (final String entry : entries.split(SEP2)) {
                final String[] parts = entry.split(SEP1, 3);
                final StreamSource source = new StreamSource(new StringReader(parts[2]));
                source.setSystemId("file://" + parts[0] + "/" + parts[1]);
                final XdmNode node = builder.build(source);
                // a collection holds the documents under it: every ancestor collection of this one too
                String c = parts[0];
                while (c.length() > "/db".length()) {
                    collsSaxon.computeIfAbsent(c, k -> new ArrayList<>()).add(node);
                    c = c.substring(0, c.lastIndexOf('/'));
                }
                docsSaxon.put(parts[0] + "/" + parts[1], node);
                memDocs.put(parts[0] + "/" + parts[1], parts[0]);
            }

            final Matcher helpers = Pattern.compile("declare function ps:\\w+\\(.*?\\n\\};", Pattern.DOTALL).matcher(module);
            final StringBuilder helperText = new StringBuilder();
            while (helpers.find()) {
                helperText.append(helpers.group()).append("\n");
            }
            final String prolog = "xquery version '3.1'; declare namespace ps = '" + ns + "'; declare namespace h = 'http://www.w3.org/1999/xhtml'; "
                    + "declare variable $ps:COLLECTION := " + quote(coll) + "; "
                    + "declare function local:collection($u as xs:string) as document-node()* { $colls($u) }; "
                    + "declare function local:doc($u as xs:string) as document-node()? { $docs($u) }; ";

            final StringBuilder memVars = new StringBuilder("declare variable $colls := map:merge((");
            boolean first = true;
            for (final String c : collsSaxon.keySet()) {
                memVars.append(first ? "" : ", ").append("map { ").append(quote(c)).append(" : (for $d in collection(").append(quote(c))
                        .append(") return document { $d/node() }) }");
                first = false;
            }
            memVars.append(")); declare variable $docs := map:merge((");
            first = true;
            for (final String d : memDocs.keySet()) {
                memVars.append(first ? "" : ", ").append("map { ").append(quote(d)).append(" : document { doc(").append(quote(d)).append(")/node() } }");
                first = false;
            }
            memVars.append(")); ");
            final String saxonVars = "declare variable $colls external; declare variable $docs external; ";

            XdmMap collsMap = new XdmMap();
            for (final Map.Entry<String, List<XdmNode>> e : collsSaxon.entrySet()) {
                collsMap = collsMap.put(new XdmAtomicValue(e.getKey()), new XdmValue(e.getValue()));
            }
            XdmMap docsMap = new XdmMap();
            for (final Map.Entry<String, XdmNode> e : docsSaxon.entrySet()) {
                docsMap = docsMap.put(new XdmAtomicValue(e.getKey()), e.getValue());
            }

            final Pattern cell = Pattern.compile("^declare %test:assertEquals\\(\"(.*?)\"\\) function ps:(\\S+?)\\(\\) \\{ (.*) \\};$");
            final Pattern extension = Pattern.compile("\\b(range|ft|ngram|util|system|xmldb|inspect|vec)\\s*:");
            final List<String> out = new ArrayList<>();
            int total = 0, skipped = 0, agree = 0, saxonDiffers = 0, memDiffers = 0, bothDiffer = 0;
            long saxonNanos = 0, memNanos = 0;
            for (final String line : module.split("\n")) {
                final Matcher m = cell.matcher(line);
                if (!m.matches()) {
                    continue;
                }
                final String expected = m.group(1);
                final String cellName = m.group(2);
                String b = m.group(3);
                if (extension.matcher(b).find()) {
                    skipped++;
                    continue;
                }
                total++;
                b = b.replaceAll("(?<![\\w:.-])collection\\(", "local:collection(").replaceAll("(?<![\\w:.-])doc\\(", "local:doc(");

                String mem;
                long t0 = System.nanoTime();
                try {
                    mem = run(prolog + memVars + helperText + b);
                } catch (final Exception e) {
                    mem = "ERROR " + String.valueOf(e.getMessage()).replace('\n', ' ');
                }
                memNanos += System.nanoTime() - t0;

                String sx;
                t0 = System.nanoTime();
                try {
                    final XQueryCompiler compiler = saxon.newXQueryCompiler();
                    final XQueryEvaluator ev = compiler.compile(prolog + saxonVars + helperText + b).load();
                    ev.setExternalVariable(new QName("colls"), collsMap);
                    ev.setExternalVariable(new QName("docs"), docsMap);
                    final XdmValue value = ev.evaluate();
                    sx = value.size() == 0 ? "" : value.itemAt(0).getStringValue();
                } catch (final Exception e) {
                    sx = "ERROR " + String.valueOf(e.getMessage()).replace('\n', ' ');
                }
                saxonNanos += System.nanoTime() - t0;

                final boolean memOk = expected.equals(mem);
                final boolean sxOk = expected.equals(sx);
                if (memOk && sxOk) {
                    agree++;
                } else if (!memOk && !sxOk) {
                    bothDiffer++;
                } else if (!sxOk) {
                    saxonDiffers++;
                } else {
                    memDiffers++;
                }
                out.add((memOk && sxOk ? "AGREE " : "DIFF  ") + cellName + " | expected=" + expected + " | memory=" + mem + " | saxon=" + sx);
            }
            out.add("SUMMARY " + name + " total=" + total + " skipped(extension functions)=" + skipped + " agree=" + agree + " saxon-only-differs="
                    + saxonDiffers + " memory-only-differs=" + memDiffers + " both-differ=" + bothDiffer
                    + " memory-ms=" + memNanos / 1_000_000 + " saxon-ms=" + saxonNanos / 1_000_000);
            final List<String> disagreements = out.stream().filter(l -> l.startsWith("DIFF")).toList();
            assertTrue(total > 0 && disagreements.isEmpty(), name + ": " + disagreements.size() + " of " + total
                    + " tests disagree (expected answer, eXist in memory, Saxon):\n" + String.join("\n", disagreements));
        } finally {
            run(imports + "ps:tearDown()");
        }
    }
}
