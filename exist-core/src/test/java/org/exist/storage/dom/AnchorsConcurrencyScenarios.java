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
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scenarios shared by {@link AnchorsConcurrencyTest} and {@link AnchorsConcurrencyStressTest}: threads look nodes up
 * by their id while other threads insert with XUpdate, store documents and defragment, and the anchors must hold up.
 *
 * <p>Both classes run the same three scenarios. What they assert differs only in {@code strict}: the stress test also
 * asserts what the readers saw while the writers and the defragmentation ran, and the number of elements the inserts added.
 * Both class comments say why.</p>
 */
final class AnchorsConcurrencyScenarios {

    static final int SCAN_LIMIT = 8;
    static final int CHILDREN = 300;

    private final BrokerPool pool;
    private final boolean strict;

    /** Statements that failed in a writer or reader thread and were tolerated because the scenarios are not strict. */
    private final AtomicInteger tolerated = new AtomicInteger();

    /**
     * @param pool the pool of the embedded server
     * @param strict whether to assert what the readers saw during the run and the element counts after the inserts
     */
    AnchorsConcurrencyScenarios(final BrokerPool pool, final boolean strict) {
        this.pool = pool;
        this.strict = strict;
    }

    /**
     * Runs a statement of a writer thread. When strict, a failure fails the thread; otherwise it is counted and ignored,
     * because a statement can fail on a lookup that finds nothing for a node that exists while the document is updated
     * (eXist-db/exist#7013), which is not what the anchors are responsible for.
     */
    private void write(final DBBroker broker, final String query) throws Exception {
        try {
            DomTestDocuments.update(pool, broker, query);
        } catch (final Exception e) {
            if (strict) {
                throw e;
            }
            tolerated.incrementAndGet();
        }
    }

    /** Sweeps the lookups of a collection {@code sweeps} times and returns how many nodes the sweeps found wrong. */
    private Callable<Long> reader(final String collection, final int sweeps) {
        return () -> {
            long wrong = 0;
            try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
                for (int i = 0; i < sweeps; i++) {
                    try {
                        wrong += DomTestDocuments.sweepByNodeId(pool, broker, collection);
                    } catch (final Exception e) {
                        if (strict) {
                            throw e;
                        }
                        tolerated.incrementAndGet();
                    }
                }
            }
            return wrong;
        };
    }

    private Callable<Long> defragmenter(final List<String> documents, final int rounds, final AtomicInteger defragmentations) {
        return () -> {
            try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
                for (int round = 0; round < rounds; round++) {
                    for (final String document : documents) {
                        Thread.sleep(300);
                        try (final LockedDocument locked = broker.getXMLResource(XmldbURI.create(document), LockMode.WRITE_LOCK);
                             final Txn txn = pool.getTransactionManager().beginTransaction()) {
                            broker.defragXMLResource(txn, locked.getDocument());
                            txn.commit();
                        }
                        defragmentations.incrementAndGet();
                    }
                }
            }
            return 0L;
        };
    }

    /** What the readers found while the other threads ran: asserted only when strict, see the class comments. */
    private void assertReaders(final long wrong) {
        if (strict) {
            assertEquals(0, wrong, "the readers found nodes that were not the node asked for while the writers ran");
        }
    }

    /**
     * Once the threads are done: no stored element was lost, every node is found from an entry, and no entry is left without
     * its node. When strict, the document also has exactly the elements that were stored plus the ones the inserts added.
     */
    private void assertSettled(final String document, final int stored, final int inserted) throws Exception {
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool);
             final LockedDocument locked = broker.getXMLResource(XmldbURI.create(document), LockMode.READ_LOCK)) {
            final DocumentImpl doc = locked.getDocument();
            final List<IStoredNode<?>> nodes = DomTestDocuments.nodesOf(doc);
            final Set<String> present = new HashSet<>();
            long elements = 0;
            for (final IStoredNode<?> node : nodes) {
                if ("x".equals(node.getNodeName())) {
                    elements++;
                    if (((Element) node).hasAttribute("p")) {
                        present.add(((Element) node).getAttribute("p"));
                    }
                }
            }
            assertEquals(stored, present.size(), document + " lost one of the elements that were stored");
            if (strict) {
                assertEquals(stored + inserted, elements, document + " does not have the elements that were stored plus the ones inserted");
            }
            assertTrue(DomTestDocuments.anchorsOf(broker, doc, nodes).size() > 0, document + " has anchors");
            assertEquals(DomTestDocuments.entriesOf(broker, doc).size(), DomTestDocuments.entriesOfDocument(broker, doc.getDocId()),
                    document + " has entries without a node");
            for (final IStoredNode<?> node : nodes) {
                final IStoredNode<?> found = broker.objectWith(new NodeProxy(null, doc, node.getNodeId(), node.getNodeType()));
                assertNotNull(found, document + " does not find " + DomTestDocuments.describe(node));
                assertEquals(node.getNodeId(), found.getNodeId(), document + " returns another node for " + DomTestDocuments.describe(node));
            }
            assertTrue(DomTestDocuments.recordsReadPerLookup(broker, doc, nodes) > 0);
        }
    }

    /** The lookups the threads made while they ran: served from entries, and no entry was found wrong. */
    private static void assertLookups(final LookupStatistics counted) {
        assertTrue(counted.lookupsFromEntry() > 0, "lookups were served from entries");
        assertEquals(0, counted.rejectedEntries(), "no entry was found wrong");
    }

    /** Runs the tasks together and returns what the readers found wrong, with the lookup statistics of the run in {@code counted}. */
    private long run(final List<Callable<Long>> tasks, final LookupStatistics[] counted) throws Exception {
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
            final DOMFile domDb = ((NativeBroker) broker).getDOMFile();
            final LookupStatistics start = domDb.getLookupStatistics();
            final long wrong = DomTestDocuments.runConcurrently(tasks, strict ? 5 : 4);
            counted[0] = domDb.getLookupStatistics().since(start);
            return wrong;
        }
    }

    /** Three readers sweep one wide document while two writers insert into it and one thread defragments it. */
    void readersWritersAndDefragmentationOnOneDocument() throws Exception {
        final String document = "/db/anchors-conc/d.xml";
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
            DomTestDocuments.store(pool, broker, "/db/anchors-conc", DomTestDocuments.wideDocument(CHILDREN));
        }
        final AtomicInteger inserts = new AtomicInteger();
        final AtomicInteger defragmentations = new AtomicInteger();
        final List<Callable<Long>> tasks = new ArrayList<>();
        for (int r = 0; r < 3; r++) {
            tasks.add(reader("/db/anchors-conc", 10));
        }
        for (int w = 0; w < 2; w++) {
            final int writer = w;
            tasks.add(() -> {
                try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
                    for (int i = 0; i < 40; i++) {
                        final int position = 1 + ((i * 37 + writer * 11) % 250);
                        write(broker, "update insert <x k=\"w" + writer + "-" + i + "\"><a>v" + i + "</a></x> following (collection('/db/anchors-conc')//x)[" + position + "]");
                        inserts.incrementAndGet();
                    }
                }
                return 0L;
            });
        }
        tasks.add(defragmenter(List.of(document), 5, defragmentations));

        final LookupStatistics[] counted = new LookupStatistics[1];
        final long wrong = run(tasks, counted);
        assertEquals(80, inserts.get(), "every insert statement ran");
        assertEquals(5, defragmentations.get(), "every defragmentation ran");
        assertLookups(counted[0]);
        assertReaders(wrong);
        assertSettled(document, CHILDREN, 80);
    }

    /** Four writers store documents into one collection while two readers sweep it. */
    void storesIntoOneCollectionWhileReadersSweepIt() throws Exception {
        final String xml = DomTestDocuments.wideDocument(60);
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
            DomTestDocuments.update(pool, broker, "xmldb:create-collection('/db', 'anchors-stores')");
            // a document from the start, so that every sweep has lookups to make, however the threads are scheduled
            DomTestDocuments.update(pool, broker, "xmldb:store('/db/anchors-stores', 'seed.xml', parse-xml('" + xml + "'))");
        }
        final AtomicInteger stored = new AtomicInteger();
        final List<Callable<Long>> tasks = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            final int writer = t;
            tasks.add(() -> {
                try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
                    for (int i = 0; i < 25; i++) {
                        DomTestDocuments.update(pool, broker, "xmldb:store('/db/anchors-stores', 'w" + writer + "-" + i + ".xml', parse-xml('" + xml + "'))");
                        stored.incrementAndGet();
                    }
                }
                return 0L;
            });
        }
        for (int r = 0; r < 2; r++) {
            tasks.add(reader("/db/anchors-stores", 10));
        }

        final LookupStatistics[] counted = new LookupStatistics[1];
        final long wrong = run(tasks, counted);
        assertEquals(100, stored.get(), "every store ran");
        assertLookups(counted[0]);
        assertReaders(wrong);
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool);
             final Collection collection = broker.openCollection(XmldbURI.create("/db/anchors-stores"), LockMode.READ_LOCK)) {
            assertEquals(101, collection.getDocumentCount(broker), "every document is there");
            final Iterator<DocumentImpl> documents = collection.iterator(broker);
            while (documents.hasNext()) {
                final DocumentImpl doc = documents.next();
                final List<IStoredNode<?>> nodes = DomTestDocuments.nodesOf(doc);
                assertTrue(DomTestDocuments.anchorsOf(broker, doc, nodes).size() > 0, doc.getFileURI() + " has anchors");
                assertTrue(DomTestDocuments.recordsReadPerLookup(broker, doc, nodes) > 0);
            }
        }
    }

    /** Four writers each insert into their own document while one thread defragments all four and two readers sweep them. */
    void updatesAndDefragmentationOfDifferentDocuments() throws Exception {
        final List<String> documents = new ArrayList<>();
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
            DomTestDocuments.update(pool, broker, "xmldb:create-collection('/db', 'anchors-updates')");
            for (int t = 0; t < 4; t++) {
                DomTestDocuments.update(pool, broker, "xmldb:store('/db/anchors-updates', 'd" + t + ".xml', parse-xml('" + DomTestDocuments.wideDocument(CHILDREN) + "'))");
                documents.add("/db/anchors-updates/d" + t + ".xml");
            }
        }
        final AtomicInteger inserts = new AtomicInteger();
        final AtomicInteger defragmentations = new AtomicInteger();
        final List<Callable<Long>> tasks = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            final int writer = t;
            tasks.add(() -> {
                try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
                    for (int i = 0; i < 40; i++) {
                        write(broker, "update insert <x k=\"w" + writer + "-" + i + "\"><a>v</a></x> following (doc('/db/anchors-updates/d" + writer + ".xml')//x)["
                                + (20 + (i * 7) % 200) + "]");
                        inserts.incrementAndGet();
                    }
                }
                return 0L;
            });
        }
        tasks.add(defragmenter(documents, 3, defragmentations));
        for (int r = 0; r < 2; r++) {
            tasks.add(reader("/db/anchors-updates", 10));
        }

        final LookupStatistics[] counted = new LookupStatistics[1];
        final long wrong = run(tasks, counted);
        assertEquals(160, inserts.get(), "every insert statement ran");
        assertEquals(12, defragmentations.get(), "every defragmentation ran");
        assertLookups(counted[0]);
        assertReaders(wrong);
        for (final String document : documents) {
            assertSettled(document, CHILDREN, 40);
        }
    }
}
