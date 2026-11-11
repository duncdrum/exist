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
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Crash recovery with anchor entries: after a crash the journal is replayed, so a committed store or update leaves
 * the same entries it had, and a store or removal that was not committed leaves none of its changes.
 */
public class AnchorsRecoverTest {

    private static final int SCAN_LIMIT = 8;
    private static final int CHILDREN = 60;
    private static final String COLLECTION = "/db/anchors-recover";
    private static final String OTHER_COLLECTION = "/db/anchors-recover-other";
    private static final XmldbURI DOCUMENT = XmldbURI.create("d.xml");

    // started and stopped by the tests themselves, as they force corruption
    @AutoClose("stopDb")
    private final ExistEmbeddedServer server = new ExistEmbeddedServer(DomTestDocuments.configuration(SCAN_LIMIT), true, true);

    @AfterEach
    void resetCorruption() {
        BrokerPool.FORCE_CORRUPTION = false;
    }

    private BrokerPool startWithoutCheckpoints() throws Exception {
        BrokerPool.FORCE_CORRUPTION = true;
        server.startDb();
        return server.getBrokerPool();
    }

    /** Stops the database without a checkpoint and starts it again, which recovers from the journal. */
    private BrokerPool crashAndRecover() throws Exception {
        server.getBrokerPool().getJournalManager().get().flush(true, false);
        server.stopDb(false);
        BrokerPool.FORCE_CORRUPTION = false;
        server.startDb();
        return server.getBrokerPool();
    }

    private static Map<String, Long> storedEntries(final DBBroker broker, final String collection) throws Exception {
        try (final LockedDocument locked = DomTestDocuments.open(broker, collection)) {
            return DomTestDocuments.entriesOf(broker, locked.getDocument());
        }
    }

    /** The entries are those of before the crash, the document has anchors, and every node is found from an entry. */
    private static void assertRecovered(final DBBroker broker, final String collection, final Map<String, Long> entries, final int runBound) throws Exception {
        try (final LockedDocument locked = DomTestDocuments.open(broker, collection)) {
            final DocumentImpl doc = locked.getDocument();
            final List<IStoredNode<?>> nodes = DomTestDocuments.nodesOf(doc);
            assertEquals(entries, DomTestDocuments.entriesOf(broker, doc), "recovery restored the entries");
            assertTrue(DomTestDocuments.anchorsOf(broker, doc, nodes).size() > 0, "the document has anchors");
            assertTrue(DomTestDocuments.longestRun(broker, doc, nodes) <= runBound, "no run is longer than " + runBound);
            assertTrue(DomTestDocuments.recordsReadPerLookup(broker, doc, nodes) > 0);
        }
    }

    @Test
    void aCommittedStoreKeepsItsAnchors() throws Exception {
        BrokerPool pool = startWithoutCheckpoints();
        final Map<String, Long> entries;
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
            DomTestDocuments.store(pool, broker, COLLECTION, DomTestDocuments.wideDocument(CHILDREN));
            entries = storedEntries(broker, COLLECTION);
        }
        pool = crashAndRecover();
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
            assertRecovered(broker, COLLECTION, entries, SCAN_LIMIT);
        }
    }

    @Test
    void aStoreThatWasNotCommittedLeavesNoEntries() throws Exception {
        BrokerPool pool = startWithoutCheckpoints();
        final Map<String, Long> kept;
        final int docId;
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
            DomTestDocuments.store(pool, broker, OTHER_COLLECTION, DomTestDocuments.wideDocument(CHILDREN));
            kept = storedEntries(broker, OTHER_COLLECTION);

            // this transaction is not committed and is rolled back by recovery
            final Txn txn = pool.getTransactionManager().beginTransaction();
            final Collection collection = broker.getOrCreateCollection(txn, XmldbURI.create(COLLECTION));
            broker.saveCollection(txn, collection);
            broker.storeDocument(txn, DOCUMENT, new StringInputSource(DomTestDocuments.wideDocument(CHILDREN)), MimeType.XML_TYPE, collection);
            docId = collection.getDocument(broker, DOCUMENT).getDocId();
            assertTrue(DomTestDocuments.entriesOfDocument(broker, docId) > 0, "the store wrote entries");
        }
        pool = crashAndRecover();
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
            assertEquals(0, DomTestDocuments.entriesOfDocument(broker, docId), "no entry of the document is left");
            try (final Collection collection = broker.openCollection(XmldbURI.create(COLLECTION), LockMode.READ_LOCK)) {
                assertNull(collection, "the collection of the store is gone");
            }
            assertRecovered(broker, OTHER_COLLECTION, kept, SCAN_LIMIT);
        }
    }

    @Test
    void aRemovalThatWasNotCommittedIsUndoneWithItsAnchors() throws Exception {
        BrokerPool pool = startWithoutCheckpoints();
        final Map<String, Long> entries;
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
            DomTestDocuments.store(pool, broker, COLLECTION, DomTestDocuments.wideDocument(CHILDREN));
            final int docId;
            try (final LockedDocument locked = DomTestDocuments.open(broker, COLLECTION)) {
                docId = locked.getDocument().getDocId();
                entries = DomTestDocuments.entriesOf(broker, locked.getDocument());
            }
            final TransactionManager transactions = pool.getTransactionManager();
            try (final Collection collection = broker.openCollection(XmldbURI.create(COLLECTION), LockMode.WRITE_LOCK)) {
                // this transaction is not committed and is rolled back by recovery
                final Txn txn = transactions.beginTransaction();
                collection.removeXMLResource(txn, broker, DOCUMENT);
            }
            assertEquals(0, DomTestDocuments.entriesOfDocument(broker, docId), "the removal took the entries");
        }
        pool = crashAndRecover();
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
            assertRecovered(broker, COLLECTION, entries, SCAN_LIMIT);
        }
    }

    @Test
    void aCommittedUpdateThatSplitPagesKeepsItsAnchors() throws Exception {
        BrokerPool pool = startWithoutCheckpoints();
        final Map<String, Long> entries;
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
            DomTestDocuments.store(pool, broker, COLLECTION, DomTestDocuments.wideDocument(CHILDREN));
            DomTestDocuments.insertBeforeTheFifthChild(pool, broker, COLLECTION, 6);
            try (final LockedDocument locked = DomTestDocuments.open(broker, COLLECTION)) {
                assertTrue(locked.getDocument().getSplitCount() > 0, "pages were split");
                entries = DomTestDocuments.entriesOf(broker, locked.getDocument());
            }
        }
        pool = crashAndRecover();
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
            assertRecovered(broker, COLLECTION, entries, 2 * SCAN_LIMIT);
            try (final LockedDocument locked = DomTestDocuments.open(broker, COLLECTION)) {
                assertEquals(6, DomTestDocuments.nodesOf(locked.getDocument()).stream().filter(node -> "y".equals(node.getNodeName())).count(),
                        "the inserted elements are there");
            }
        }
    }
}
