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
package org.exist.storage.dom;

import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.test.ExistEmbeddedServer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Finding a stored node again by its id, which every parent, ancestor and {@code util:node-by-id} step does for a node
 * below the {@code index-depth}, with the anchor entries of the DOM index off (scan limit 0) and on (64), over documents of
 * five shapes: a flat run of elements, a deep chain, a binary tree, mixed content, and elements with many attributes.
 *
 * <p>With anchors on, a lookup reads at most the scan limit of records, whatever the shape, except for an attribute of an
 * element with more attributes than that: attributes never get an entry. The {@code attributes} shape is there to keep that
 * limit visible. Before the run each trial checks that the document has anchors when they are on and that no lookup of a
 * node that is not an attribute read more than the limit, so that a trial cannot measure something else than it says.</p>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class NodeLookupBenchmark {

    private static final String DOCUMENT = "/db/node-lookup.xml";
    /** The content is nested this many levels deep, so that all of it is below the index depth and none of it has an entry of its own. */
    private static final int INDEX_DEPTH = 5;
    private static final int ATTRIBUTES_PER_ELEMENT = 200;

    private static final String PROLOG = """
            xquery version "3.1";
            declare function local:leaf($i as xs:integer) as element() { <x k="k{ $i }"><a>v{ $i }</a></x> };
            declare function local:spine($depth as xs:integer) as element()* {
                if ($depth le 0) then () else <c>{ local:leaf($depth), local:spine($depth - 1) }</c> };
            declare function local:binary($depth as xs:integer, $i as xs:integer) as element() {
                if ($depth = 0) then local:leaf($i)
                else <b>{ local:binary($depth - 1, $i * 2), local:binary($depth - 1, $i * 2 + 1) }</b> };
            declare function local:wrap($levels as xs:integer, $content as element()*) as element()* {
                if ($levels le 0) then $content else <w>{ local:wrap($levels - 1, $content) }</w> };
            declare function local:content($shape as xs:string, $n as xs:integer) as element()* {
                switch ($shape)
                    case "flat" return (1 to $n) ! local:leaf(.)
                    case "chain" return local:spine(xs:integer($n div 8))
                    case "binary" return local:binary(xs:integer(floor(math:log($n) div math:log(2))), 1)
                    case "mixed" return (1 to $n) ! <p>text <x k="k{ . }">bold { . }</x> more <i>it</i> end</p>
                    case "attributes" return (1 to xs:integer($n div @PER@)) !
                        element x { attribute k { "k" || . }, (1 to @PER@) ! attribute { "a" || . } { . } }
                    default return error(xs:QName("local:shape"), $shape) };
            """;

    @Param({"flat", "chain", "binary", "mixed", "attributes"})
    private String shape;

    /** 0 stores no anchors, 64 is the default of the 7.0 release. */
    @Param({"0", "64"})
    private int scanLimit;

    @Param({"800"})
    private int size;

    private ExistEmbeddedServer existServer;
    private long expectedElements;
    private long expectedAttributes;

    /**
     * Boots an embedded eXist server, stores the document for the current parameters, and checks that it is what the
     * parameters say.
     *
     * @throws Exception if the server cannot be started, the document cannot be stored, or the check fails
     */
    @Setup(Level.Trial)
    public void setUp() throws Exception {
        final Properties configuration = new Properties();
        configuration.put(NativeBroker.PROPERTY_INDEX_DEPTH, INDEX_DEPTH);
        configuration.put(NativeBroker.PROPERTY_INDEX_SCAN_LIMIT, scanLimit);
        existServer = new ExistEmbeddedServer(configuration, true, true);
        existServer.startDb();
        execute(PROLOG.replace("@PER@", String.valueOf(ATTRIBUTES_PER_ELEMENT)) + "xmldb:store('/db', 'node-lookup.xml', document { <root>{ local:wrap(" + INDEX_DEPTH + ", local:content('" + shape + "', " + size + ")) }</root> })");
        expectedElements = run("count(doc('" + DOCUMENT + "')//x)");
        expectedAttributes = run("count(doc('" + DOCUMENT + "')//x/@*)");
        check();
    }

    /**
     * Stops the embedded server.
     *
     * @throws Exception if the server cannot be stopped
     */
    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        existServer.stopDb(true);
    }

    private void check() throws Exception {
        final BrokerPool pool = existServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final DOMFile domFile = ((NativeBroker) broker).getDOMFile();
            final LookupStatistics before = domFile.getLookupStatistics();
            byNodeId();
            final LookupStatistics counted = domFile.getLookupStatistics().since(before);
            if (counted.lookupsFromEntry() == 0) {
                throw new IllegalStateException("no node was found from an entry, the document has none: " + counted);
            }
            if (scanLimit > 0 && !"attributes".equals(shape) && counted.buckets().beyondLimit() > 0) {
                throw new IllegalStateException("a lookup read more than the scan limit of " + scanLimit + " records: " + counted);
            }
            if (scanLimit == 0 && counted.buckets().total() != counted.buckets().beyondLimit()) {
                throw new IllegalStateException("without a scan limit every lookup is beyond it: " + counted);
            }
        }
    }

    private void execute(final String query) throws Exception {
        final BrokerPool pool = existServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            pool.getXQueryService().execute(broker, query, null);
        }
    }

    /** Runs a query that gives one number, and returns it. */
    private long run(final String query) throws Exception {
        final BrokerPool pool = existServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final var result = pool.getXQueryService().execute(broker, query, null);
            return result.isEmpty() ? 0 : Long.parseLong(result.itemAt(0).getStringValue());
        }
    }

    /**
     * Finds every {@code x} element again by its node id, so each lookup starts without a known address.
     *
     * @return the number of nodes found, so that JMH cannot optimise the call away
     * @throws Exception if the query fails or finds other than the elements of the document
     */
    @Benchmark
    public long byNodeId() throws Exception {
        final long found = run("count(for $x in doc('" + DOCUMENT + "')//x return util:node-by-id(root($x), util:node-id($x)))");
        if (found != expectedElements) {
            throw new IllegalStateException("found " + found + " of " + expectedElements + " elements");
        }
        return found;
    }

    /**
     * Navigates from every {@code x} element to its parent.
     *
     * @return the number of parents visited
     * @throws Exception if the query fails or visits other than the elements of the document
     */
    @Benchmark
    public long parent() throws Exception {
        final long visited = run("count(doc('" + DOCUMENT + "')//x ! local-name(..))");
        if (visited != expectedElements) {
            throw new IllegalStateException("visited " + visited + " of " + expectedElements + " elements");
        }
        return visited;
    }

    /**
     * Finds every attribute of the {@code x} elements again by its node id. Attributes never get an entry, so for an element
     * with more attributes than the scan limit this reads the attributes before it, with or without anchors.
     *
     * @return the number of attributes found
     * @throws Exception if the query fails or finds other than the attributes of the document
     */
    @Benchmark
    public long attributeByNodeId() throws Exception {
        final long found = run("count(for $a in doc('" + DOCUMENT + "')//x/@* return util:node-by-id(root($a), util:node-id($a)))");
        if (found != expectedAttributes) {
            throw new IllegalStateException("found " + found + " of " + expectedAttributes + " attributes");
        }
        return found;
    }
}
