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

import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.IStoredNode;
import org.exist.dom.persistent.LockedDocument;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.StorageAddress;
import org.exist.storage.btree.BTree;
import org.exist.storage.btree.Value;
import org.exist.test.ExistEmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With an {@code index-scan-limit}, a run of records without an entry is cut by anchors, however the document is
 * stored, and finding any node reads no more than that many records.
 */
public class DomAnchorsTest {

    private static final int SCAN_LIMIT = 8;
    private static final int CHILDREN = 60;

    @RegisterExtension
    static final ExistEmbeddedServer SERVER = new ExistEmbeddedServer(DomTestDocuments.configuration(SCAN_LIMIT), true, true);

    private void checkRuns(final DBBroker broker, final DocumentImpl doc, final int bound) throws Exception {
        final List<IStoredNode<?>> nodes = DomTestDocuments.nodesOf(doc);
        assertTrue(DomTestDocuments.anchorsOf(broker, doc, nodes).size() > CHILDREN * 4 / SCAN_LIMIT / 2, "the run below the index depth was cut by anchors");
        assertTrue(DomTestDocuments.longestRun(broker, doc, nodes) <= bound, "no run is longer than " + bound);
        assertTrue(DomTestDocuments.recordsReadPerLookup(broker, doc, nodes) <= SCAN_LIMIT, "a lookup reads no more than the scan limit on average");
    }

    @Test
    void aStoredDocumentIsCutIntoRunsOfAtMostTheScanLimit() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
            assertEquals(SCAN_LIMIT, ((NativeBroker) broker).getEntryPolicy().getScanLimit(), "the configured scan limit took effect");
            DomTestDocuments.store(pool, broker, "/db/anchors-store", DomTestDocuments.wideDocument(CHILDREN));
            try (final LockedDocument locked = DomTestDocuments.open(broker, "/db/anchors-store")) {
                checkRuns(broker, locked.getDocument(), SCAN_LIMIT);
            }
        }
    }

    @Test
    void aReindexThatStoresCutsTheRunsAgain() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
            DomTestDocuments.store(pool, broker, "/db/anchors-reindex", DomTestDocuments.wideDocument(CHILDREN));
            DomTestDocuments.update(pool, broker, "xmldb:reindex('/db/anchors-reindex')");
            try (final LockedDocument locked = DomTestDocuments.open(broker, "/db/anchors-reindex")) {
                checkRuns(broker, locked.getDocument(), SCAN_LIMIT);
            }
        }
    }

    @Test
    void anUpdateKeepsTheRunsWithinTwiceTheScanLimit() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
            DomTestDocuments.store(pool, broker, "/db/anchors-update", DomTestDocuments.wideDocument(CHILDREN));
            DomTestDocuments.update(pool, broker, "update insert (for $i in 1 to 40 return <y q=\"{$i}\">u{$i}<!--m--></y>) into doc('/db/anchors-update/d.xml')/r/b/c");
            try (final LockedDocument locked = DomTestDocuments.open(broker, "/db/anchors-update")) {
                checkRuns(broker, locked.getDocument(), 2 * SCAN_LIMIT);
            }
        }
    }

    @Test
    void removingTheAnchorsWidensTheRunsAndAReindexCutsThemAgain() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
            DomTestDocuments.store(pool, broker, "/db/anchors-delete", DomTestDocuments.wideDocument(CHILDREN));
            final List<String> anchorIds;
            try (final LockedDocument locked = DomTestDocuments.open(broker, "/db/anchors-delete")) {
                anchorIds = DomTestDocuments.anchorIds(broker, locked.getDocument());
            }
            assertTrue(anchorIds.size() > 10, "the document has anchors to remove: " + anchorIds.size());
            final String ids = anchorIds.stream().map(id -> "'" + id + "'").collect(Collectors.joining(", "));
            DomTestDocuments.update(pool, broker, "update delete (for $id in (" + ids + ") return util:node-by-id(doc('/db/anchors-delete/d.xml'), $id))");
            try (final LockedDocument locked = DomTestDocuments.open(broker, "/db/anchors-delete")) {
                final DocumentImpl doc = locked.getDocument();
                final List<IStoredNode<?>> nodes = DomTestDocuments.nodesOf(doc);
                assertTrue(DomTestDocuments.longestRun(broker, doc, nodes) > 2 * SCAN_LIMIT, "removing the anchors left runs longer than twice the scan limit");
                assertTrue(DomTestDocuments.recordsReadPerLookup(broker, doc, nodes) > SCAN_LIMIT, "lookups read more than the scan limit again, and still find every node");
            }
            DomTestDocuments.update(pool, broker, "xmldb:reindex('/db/anchors-delete')");
            try (final LockedDocument locked = DomTestDocuments.open(broker, "/db/anchors-delete")) {
                final DocumentImpl doc = locked.getDocument();
                final List<IStoredNode<?>> nodes = DomTestDocuments.nodesOf(doc);
                assertTrue(DomTestDocuments.longestRun(broker, doc, nodes) <= SCAN_LIMIT, "a reindex that stores cut the runs again");
            }
        }
    }

    @Test
    void anEntryStillLeadsToItsRecordAfterAPageSplitMovedIt() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
            final String collection = "/db/anchors-split";
            DomTestDocuments.store(pool, broker, collection, DomTestDocuments.wideDocument(CHILDREN));
            final DOMFile domDb = ((NativeBroker) broker).getDOMFile();
            final Map<String, Long> before;
            try (final LockedDocument locked = DomTestDocuments.open(broker, collection)) {
                before = DomTestDocuments.entriesOf(broker, locked.getDocument());
            }
            DomTestDocuments.insertBeforeTheFifthChild(pool, broker, collection, 6);
            try (final LockedDocument locked = DomTestDocuments.open(broker, collection)) {
                final DocumentImpl doc = locked.getDocument();
                assertTrue(doc.getSplitCount() > 0, "pages were split");
                final Map<String, Long> after = DomTestDocuments.entriesOf(broker, doc);
                int moved = 0;
                for (final IStoredNode<?> node : DomTestDocuments.nodesOf(doc)) {
                    final Long address = before.get(node.getNodeId().toString());
                    if (address != null) {
                        assertEquals(address, after.get(node.getNodeId().toString()), "a split leaves the entry as it was");
                        if (domDb.findRecord(address).getPage().getPageNum() != StorageAddress.pageFromPointer(address)) {
                            moved++;
                        }
                    }
                }
                assertTrue(moved > 0, "the record of at least one entry is on another page than the entry says");
                checkRuns(broker, doc, 2 * SCAN_LIMIT);
            }
        }
    }

    @Test
    void aDefragmentationCutsTheRunsAgain() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
            final String collection = "/db/anchors-defrag";
            DomTestDocuments.store(pool, broker, collection, DomTestDocuments.wideDocument(CHILDREN));
            DomTestDocuments.insertBeforeTheFifthChild(pool, broker, collection, 6);
            try (final LockedDocument locked = DomTestDocuments.open(broker, collection)) {
                assertTrue(locked.getDocument().getSplitCount() > 0, "pages were split");
            }
            DomTestDocuments.update(pool, broker, "xmldb:defragment(doc('" + collection + "/d.xml')/r, 0)");
            try (final LockedDocument locked = DomTestDocuments.open(broker, collection)) {
                final DocumentImpl doc = locked.getDocument();
                assertEquals(0, doc.getSplitCount(), "the document was defragmented");
                checkRuns(broker, doc, SCAN_LIMIT);
            }
        }
    }

    @Test
    void removingANodeWithAnAnchorRemovesTheAnchor() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
            final String collection = "/db/anchors-remove";
            DomTestDocuments.store(pool, broker, collection, DomTestDocuments.wideDocument(CHILDREN));
            final DOMFile domDb = ((NativeBroker) broker).getDOMFile();
            final String anchorId;
            try (final LockedDocument locked = DomTestDocuments.open(broker, collection)) {
                anchorId = DomTestDocuments.anchorIds(broker, locked.getDocument()).get(0);
            }
            DomTestDocuments.update(pool, broker, "update delete util:node-by-id(doc('" + collection + "/d.xml'), '" + anchorId + "')");
            try (final LockedDocument locked = DomTestDocuments.open(broker, collection)) {
                final DocumentImpl doc = locked.getDocument();
                final Value key = new NativeBroker.NodeRef(doc.getDocId(), pool.getNodeFactory().createFromString(anchorId));
                assertEquals(BTree.KEY_NOT_FOUND, domDb.findValue(key), "its entry is gone with the node");
                assertTrue(DomTestDocuments.recordsReadPerLookup(broker, doc, DomTestDocuments.nodesOf(doc)) > 0);
            }
        }
    }

    @Test
    void replacingTheElementOfAnAnchorLeavesEveryLookupCorrect() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
            final String collection = "/db/anchors-replace";
            DomTestDocuments.store(pool, broker, collection, DomTestDocuments.wideDocument(CHILDREN));
            final String anchorId;
            try (final LockedDocument locked = DomTestDocuments.open(broker, collection)) {
                anchorId = DomTestDocuments.anchorIds(broker, locked.getDocument()).get(0);
            }
            DomTestDocuments.update(pool, broker, "update replace util:node-by-id(doc('" + collection + "/d.xml'), '" + anchorId + "')/.. with <z><w/><w/><w/></z>");
            try (final LockedDocument locked = DomTestDocuments.open(broker, collection)) {
                final DocumentImpl doc = locked.getDocument();
                final List<IStoredNode<?>> nodes = DomTestDocuments.nodesOf(doc);
                assertTrue(nodes.stream().anyMatch(node -> "z".equals(node.getNodeName())), "the element was replaced");
                assertTrue(DomTestDocuments.recordsReadPerLookup(broker, doc, nodes) > 0);
            }
        }
    }
}
