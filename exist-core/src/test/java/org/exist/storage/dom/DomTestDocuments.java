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

import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.IStoredNode;
import org.exist.dom.persistent.LockedDocument;
import org.exist.dom.persistent.NodeProxy;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.btree.BTree;
import org.exist.storage.btree.IndexQuery;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.Txn;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A document that nests elements three levels below the index depth and holds a comment, a processing instruction,
 * text, an attribute and a CDATA section at each of them, and a comment and a processing instruction before and after
 * the root element, for the tests of the DOM B-tree entries.
 */
final class DomTestDocuments {

    /** An index depth above the code default of 1, so that the rules for a stored and a rebuilt entry differ for the nodes in the document. */
    static final int INDEX_DEPTH = 3;

    private DomTestDocuments() {
    }

    /**
     * Returns the configuration for an embedded server that uses {@link #INDEX_DEPTH} and no anchors, so that only the
     * entries of the head rule are in the tests of that rule.
     *
     * @return the configuration properties
     */
    static Properties configuration() {
        return configuration(0);
    }

    /**
     * Returns the configuration for an embedded server that uses {@link #INDEX_DEPTH} and a scan limit.
     *
     * @param scanLimit the value of {@code index-scan-limit}
     * @return the configuration properties
     */
    static Properties configuration(final int scanLimit) {
        final Properties configuration = new Properties();
        configuration.put("indexer.index-depth", INDEX_DEPTH);
        configuration.put(NativeBroker.PROPERTY_INDEX_SCAN_LIMIT, scanLimit);
        return configuration;
    }

    /**
     * A document whose third level has many children below the index depth, each an element with an attribute, a text
     * and a comment: four records.
     *
     * @param children the number of children
     * @return the document
     */
    static String wideDocument(final int children) {
        final StringBuilder xml = new StringBuilder("<r a=\"1\"><b><c>");
        for (int i = 0; i < children; i++) {
            xml.append("<x p=\"").append(i).append("\">t").append(i).append("<!--k").append(i).append("--></x>");
        }
        return xml.append("</c></b></r>").toString();
    }

    static String document(final int levels) {
        final StringBuilder xml = new StringBuilder("<!--before--><?before x?>");
        for (int l = 1; l <= levels; l++) {
            xml.append("<l").append(l).append(" a=\"").append(l).append("\"><!--c").append(l).append("-->t").append(l)
                    .append("<?pi").append(l).append(" x?><![CDATA[cdata").append(l).append("]]>");
        }
        for (int l = levels; l >= 1; l--) {
            xml.append("u").append(l).append("</l").append(l).append(">");
        }
        return xml.append("<!--after--><?after x?>").toString();
    }

    /**
     * Acquires a broker for the system user. The caller closes it.
     *
     * @param pool the broker pool
     * @return the broker
     * @throws Exception if no broker can be acquired
     */
    static DBBroker systemBroker(final BrokerPool pool) throws Exception {
        return pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
    }

    /**
     * Opens {@code d.xml} of a collection with a read lock. The caller closes it.
     *
     * @param broker the broker
     * @param collection the path of the collection
     * @return the locked document
     * @throws Exception if the document cannot be opened
     */
    static LockedDocument open(final DBBroker broker, final String collection) throws Exception {
        return broker.getXMLResource(XmldbURI.create(collection + "/d.xml"), LockMode.READ_LOCK);
    }

    /**
     * Runs an update expression.
     *
     * @param pool the broker pool
     * @param broker the broker
     * @param query the expression
     * @throws Exception if it fails
     */
    static void update(final BrokerPool pool, final DBBroker broker, final String query) throws Exception {
        pool.getXQueryService().execute(broker, query, null);
    }

    /**
     * Stores the document as {@code d.xml} in a new collection.
     *
     * @param pool the broker pool
     * @param broker the broker
     * @param collection the path of the collection
     * @throws Exception if the document cannot be stored
     */
    static void store(final BrokerPool pool, final DBBroker broker, final String collection) throws Exception {
        store(pool, broker, collection, null);
    }

    /**
     * Stores a document as {@code d.xml} in a new collection.
     *
     * @param pool the broker pool
     * @param broker the broker
     * @param collection the path of the collection
     * @param xml the document, or null for {@link #document(int)} three levels below the index depth
     * @throws Exception if the document cannot be stored
     */
    static void store(final BrokerPool pool, final DBBroker broker, final String collection, final String xml) throws Exception {
        final int depth = ((NativeBroker) broker).getEntryPolicy().getIndexDepth();
        try (final Txn txn = pool.getTransactionManager().beginTransaction()) {
            final Collection col = broker.getOrCreateCollection(txn, XmldbURI.create(collection));
            broker.saveCollection(txn, col);
            broker.storeDocument(txn, XmldbURI.create("d.xml"), new StringInputSource(xml == null ? document(depth + 3) : xml), MimeType.XML_TYPE, col);
            txn.commit();
        }
    }

    /**
     * Lists the nodes of a document in document order, each element followed by its attributes.
     *
     * @param doc the document
     * @return the stored nodes
     */
    static List<IStoredNode<?>> nodesOf(final DocumentImpl doc) {
        final List<IStoredNode<?>> nodes = new ArrayList<>();
        for (Node node = doc.getFirstChild(); node != null; node = node.getNextSibling()) {
            collect(node, nodes);
        }
        return nodes;
    }

    private static void collect(final Node node, final List<IStoredNode<?>> out) {
        out.add((IStoredNode<?>) node);
        final NamedNodeMap attributes = node.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                out.add((IStoredNode<?>) attributes.item(i));
            }
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            collect(child, out);
        }
    }

    static boolean hasEntry(final DBBroker broker, final DocumentImpl doc, final IStoredNode<?> node) throws Exception {
        final DOMFile domDb = ((NativeBroker) broker).getDOMFile();
        return domDb.findValue(new NativeBroker.NodeRef(doc.getDocId(), node.getNodeId())) != BTree.KEY_NOT_FOUND;
    }

    static String describe(final IStoredNode<?> node) {
        return "node " + node.getNodeId() + " of type " + node.getNodeType() + " at level " + node.getNodeId().getTreeLevel();
    }

    /** The longest run of records, in storage order, from one entry up to the next. */
    static int longestRun(final DBBroker broker, final DocumentImpl doc, final List<IStoredNode<?>> nodes) throws Exception {
        int longest = 0;
        int run = 0;
        for (final IStoredNode<?> node : nodes) {
            if (DomTestDocuments.hasEntry(broker, doc, node)) {
                run = 0;
            } else {
                longest = Math.max(longest, ++run);
            }
        }
        return longest;
    }

    /** The nodes with an entry that the entry rule alone does not give them. */
    static List<IStoredNode<?>> anchorsOf(final DBBroker broker, final DocumentImpl doc, final List<IStoredNode<?>> nodes) throws Exception {
        final EntryPolicy policy = ((NativeBroker) broker).getEntryPolicy();
        final List<IStoredNode<?>> anchors = new ArrayList<>();
        for (final IStoredNode<?> node : nodes) {
            if (!policy.hasEntry((short) node.getNodeType(), node.getNodeId().getTreeLevel()) && DomTestDocuments.hasEntry(broker, doc, node)) {
                assertTrue(node.getNodeType() != Node.ATTRIBUTE_NODE, "an attribute has an entry: " + DomTestDocuments.describe(node));
                anchors.add(node);
            }
        }
        return anchors;
    }

    static List<String> anchorIds(final DBBroker broker, final DocumentImpl doc) throws Exception {
        return anchorsOf(broker, doc, DomTestDocuments.nodesOf(doc)).stream().map(node -> node.getNodeId().toString()).toList();
    }

    /** Finds every node without an entry by its id and returns the records read for each, on average. */
    static double recordsReadPerLookup(final DBBroker broker, final DocumentImpl doc, final List<IStoredNode<?>> nodes) throws Exception {
        final DOMFile domDb = ((NativeBroker) broker).getDOMFile();
        final LookupStatistics before = domDb.getLookupStatistics();
        int lookups = 0;
        for (final IStoredNode<?> node : nodes) {
            if (!DomTestDocuments.hasEntry(broker, doc, node)) {
                assertNotNull(broker.objectWith(new NodeProxy(null, doc, node.getNodeId(), node.getNodeType())), DomTestDocuments.describe(node));
                lookups++;
            }
        }
        final LookupStatistics counted = domDb.getLookupStatistics().since(before);
        assertTrue(lookups > 0, "there are nodes without an entry");
        assertEquals(lookups, counted.lookupsFromEntry(), "each was read from an entry");
        assertEquals(0, counted.ancestorFallbacks(), "none climbed to an ancestor");
        assertEquals(0, counted.rejectedEntries(), "no entry was ignored");
        return (double) counted.recordsScanned() / lookups;
    }

    /** The address of the entry of each node that has one, by node id. */
    static Map<String, Long> entriesOf(final DBBroker broker, final DocumentImpl doc) throws Exception {
        final DOMFile domDb = ((NativeBroker) broker).getDOMFile();
        final Map<String, Long> entries = new HashMap<>();
        for (final IStoredNode<?> node : DomTestDocuments.nodesOf(doc)) {
            final long address = domDb.findValue(new NativeBroker.NodeRef(doc.getDocId(), node.getNodeId()));
            if (address != BTree.KEY_NOT_FOUND) {
                entries.put(node.getNodeId().toString(), address);
            }
        }
        return entries;
    }

    /** Inserts elements before the fifth child, each too large for the room left on its page, so that pages split. */
    static void insertBeforeTheFifthChild(final BrokerPool pool, final DBBroker broker, final String collection, final int count) throws Exception {
        for (int i = 0; i < count; i++) {
            DomTestDocuments.update(pool, broker, "update insert <y n=\"" + i + "\">{string-join((1 to 70) ! 'abcdefghi', '')}</y> preceding doc('"
                    + collection + "/d.xml')/r/b/c/x[5]");
        }
    }

    /** The number of entries of a document, found by its id. */
    static int entriesOfDocument(final DBBroker broker, final int docId) throws Exception {
        return ((NativeBroker) broker).getDOMFile().findKeys(new IndexQuery(IndexQuery.TRUNC_RIGHT, new NativeBroker.NodeRef(docId))).size();
    }
}
