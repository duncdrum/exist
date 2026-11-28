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
package org.exist.indexing.lucene;

import java.util.Optional;
import java.util.Properties;

import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.btree.IndexQuery;
import org.exist.storage.dom.DOMFile;
import org.exist.storage.dom.LookupStatistics;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lucene field expressions that need the indexed node itself, on nodes far below the index depth, in a document that has
 * anchor entries in its DOM index. The worker keeps the storage address of each pending node, so evaluating a field
 * expression reads the node directly; only when that address leads to no record, or to another node, is the node found by
 * its id, which is where the anchors bound the cost. Both must give the same values as for any other node, as stored, after
 * an update that splits pages, and after a defragmentation and a reindex.
 *
 * <p>The lookups by id that remain are those of the parent of an attribute ({@code local-name(..)} on {@code @n}); the
 * statistics of the DOM file show that there is about one per category and that each reads at most the scan limit of records,
 * with no climb to an ancestor and no entry that was ignored. Without the carried address there would be one lookup per field
 * and category as well, without anchors each would read the records up to the nearest head entry.</p>
 */
public class FieldExpressionAnchorsTest {

    private static final int SCAN_LIMIT = 8;
    private static final int INDEX_DEPTH = 3;
    private static final int CATEGORIES = 60;
    private static final int INSERTED = 40;

    private static final String COLLECTION = "/db/test-field-anchors";
    private static final String CONFIG = "/db/system/config/db/test-field-anchors";

    private static final String XCONF = "<collection xmlns='http://exist-db.org/collection-config/1.0'><index><lucene>"
            + "<text qname='category'>"
            + "<field name='kind' expression='local-name(.)'/>"
            + "<field name='nchildren' expression='count(*)'/>"
            + "<field name='parentname' expression='local-name(..)'/>"
            + "</text>"
            + "<text qname='@n'><field name='attrparent' expression='local-name(..)'/></text>"
            + "</lucene></index></collection>";

    @RegisterExtension
    static final ExistEmbeddedServer SERVER = new ExistEmbeddedServer(configuration(), true, true);

    private static Properties configuration() {
        final Properties configuration = new Properties();
        configuration.put("indexer.index-depth", INDEX_DEPTH);
        configuration.put(NativeBroker.PROPERTY_INDEX_SCAN_LIMIT, SCAN_LIMIT);
        return configuration;
    }

    private static DBBroker broker() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        return pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
    }

    private static String run(final String query) throws Exception {
        try (final DBBroker broker = broker()) {
            final var result = SERVER.getBrokerPool().getXQueryService().execute(broker, query,null);
            final StringBuilder out = new StringBuilder();
            for (int i = 0; i < result.getItemCount(); i++) {
                out.append(i == 0 ? "" : ",").append(result.itemAt(i).getStringValue());
            }
            return out.toString();
        }
    }

    private static String ft(final String body) {
        return "import module namespace ft = 'http://exist-db.org/xquery/lucene'; " + body;
    }

    private static String count(final String field, final String value) throws Exception {
        return run(ft("count(collection('" + COLLECTION + "')//category[ft:query(., '" + field + ":" + value + "')])"));
    }

    private static String attributeCount() throws Exception {
        return run(ft("count(collection('" + COLLECTION + "')//category/@n[ft:query(., 'attrparent:category')])"));
    }

    private static String document(final int categories) {
        final StringBuilder xml = new StringBuilder("<TEI><teiHeader><taxonomy>");
        for (int i = 0; i < categories; i++) {
            xml.append("<category xml:id=\"c").append(i).append("\" n=\"n").append(i).append("\"><catDesc>d").append(i)
                    .append("</catDesc></category>");
        }
        return xml.append("</taxonomy></teiHeader></TEI>").toString();
    }

    @BeforeAll
    static void setUp() throws Exception {
        run("(xmldb:create-collection('/db/system', 'config'), xmldb:create-collection('/db/system/config', 'db'))");
        run("xmldb:create-collection('/db/system/config/db', 'test-field-anchors')");
        run("xmldb:store('" + CONFIG + "', 'collection.xconf', " + XCONF.replace("'", "''").transform(s -> "parse-xml('" + s + "')") + ")");
        run("xmldb:create-collection('/db', 'test-field-anchors')");
    }

    @AfterAll
    static void tearDown() throws Exception {
        run("(xmldb:remove('" + COLLECTION + "'), xmldb:remove('" + CONFIG + "'))");
    }

    /** The number of entries of the document in the DOM index, and how many records of its nodes are below the head. */
    private static int domEntries() throws Exception {
        try (final DBBroker broker = broker();
                final LockedDocument locked = broker.getXMLResource(XmldbURI.create(COLLECTION + "/d.xml"), LockMode.READ_LOCK)) {
            final DocumentImpl doc = locked.getDocument();
            return ((NativeBroker) broker).getDOMFile().findKeys(new IndexQuery(IndexQuery.TRUNC_RIGHT, new NativeBroker.NodeRef(doc.getDocId()))).size();
        }
    }

    private static LookupStatistics lookups() throws Exception {
        try (final DBBroker broker = broker()) {
            return ((NativeBroker) broker).getDOMFile().getLookupStatistics();
        }
    }

    /**
     * Checks the lookups by node id that happened since {@code before}: some did (the evaluation of the fields ran), no more
     * than expected, none climbed to an ancestor or ignored an entry, and each read at most {@code perLookup} records on average.
     */
    private static void assertBoundedLookups(final String phase, final LookupStatistics before, final long maxLookups, final int perLookup) throws Exception {
        final LookupStatistics counted = lookups().since(before);
        assertTrue(counted.lookupsFromEntry() > 0, phase + ": nodes were found by their id from an entry: " + counted);
        assertTrue(counted.lookupsFromEntry() <= maxLookups, phase + ": no more than " + maxLookups + " lookups by id, the nodes are read by their address: " + counted);
        assertEquals(0, counted.ancestorFallbacks(), phase + ": no lookup climbed to an ancestor: " + counted);
        assertEquals(0, counted.rejectedEntries(), phase + ": no entry was ignored: " + counted);
        assertTrue(counted.recordsScanned() <= (long) perLookup * counted.lookupsFromEntry(),
                phase + ": a lookup reads at most " + perLookup + " records on average: " + counted);
    }

    @Test
    void fieldExpressionsAgreeWithAnchorsAsStoredAfterSplitsAndAfterRestructuring() throws Exception {
        final LookupStatistics beforeStore = lookups();
        run("xmldb:store('" + COLLECTION + "', 'd.xml', parse-xml('" + document(CATEGORIES) + "'))");
        assertBoundedLookups("store", beforeStore, CATEGORIES, SCAN_LIMIT);

        // as stored: every category has its values, and the document has anchors
        assertEquals(String.valueOf(CATEGORIES), count("kind", "category"));
        assertEquals(String.valueOf(CATEGORIES), count("nchildren", "1"));
        assertEquals(String.valueOf(CATEGORIES), count("parentname", "taxonomy"));
        assertEquals(String.valueOf(CATEGORIES), attributeCount());
        // the three levels of the head have an entry each; the rest are anchors, about one per scan limit of records
        assertTrue(domEntries() > 3 + CATEGORIES * 4 / SCAN_LIMIT / 2, "the document has anchors: " + domEntries() + " entries");

        // one statement inserts big nodes before the fifth category, so pages are split while their nodes are pending
        final LookupStatistics beforeUpdate = lookups();
        run("update insert (for $i in 1 to " + INSERTED + " return <category xml:id=\"x{$i}\" n=\"y{$i}\"><catDesc>{string-join((1 to 70) ! 'abcdefghi', '')}</catDesc></category>)"
                + " preceding doc('" + COLLECTION + "/d.xml')//category[5]");
        assertBoundedLookups("update", beforeUpdate, 2L * INSERTED, 2 * SCAN_LIMIT);
        final int total = CATEGORIES + INSERTED;
        assertEquals(String.valueOf(total), count("kind", "category"));
        assertEquals(String.valueOf(total), count("nchildren", "1"));
        assertEquals(String.valueOf(total), count("parentname", "taxonomy"));
        assertEquals(String.valueOf(total), attributeCount());

        // a defragmentation renumbers, and a reindex evaluates every field expression again
        final LookupStatistics beforeRestructure = lookups();
        run("xmldb:defragment(doc('" + COLLECTION + "/d.xml')/TEI, 0)");
        run("xmldb:reindex('" + COLLECTION + "')");
        assertBoundedLookups("defragment and reindex", beforeRestructure, 2L * (CATEGORIES + INSERTED), 2 * SCAN_LIMIT);
        assertEquals(String.valueOf(total), count("kind", "category"));
        assertEquals(String.valueOf(total), count("nchildren", "1"));
        assertEquals(String.valueOf(total), count("parentname", "taxonomy"));
        assertEquals(String.valueOf(total), attributeCount());
    }
}
