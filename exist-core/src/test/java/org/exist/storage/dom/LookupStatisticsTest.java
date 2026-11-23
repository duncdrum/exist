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

import java.util.List;
import java.util.stream.Collectors;

import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.IStoredNode;
import org.exist.dom.persistent.LockedDocument;
import org.exist.dom.persistent.NodeProxy;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.test.ExistEmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The counters of the by-id lookups: lookups of nodes with an entry of their own, lookups answered from an entry in buckets
 * relative to the scan limit, entries reached through a forward link after a page split, and the counters per document.
 */
public class LookupStatisticsTest {

    private static final int SCAN_LIMIT = 8;
    private static final int CHILDREN = 60;

    @RegisterExtension
    static final ExistEmbeddedServer SERVER = new ExistEmbeddedServer(DomTestDocuments.configuration(SCAN_LIMIT), true, true);

    private static DOMFile domFile(final DBBroker broker) {
        return ((NativeBroker) broker).getDOMFile();
    }

    @Test
    void aNodeWithAnEntryOfItsOwnIsAnExactHit() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
            DomTestDocuments.store(pool, broker, "/db/stats-exact", DomTestDocuments.wideDocument(CHILDREN));
            try (final LockedDocument locked = DomTestDocuments.open(broker, "/db/stats-exact")) {
                final DocumentImpl doc = locked.getDocument();
                final List<IStoredNode<?>> withEntry = DomTestDocuments.nodesOf(doc).stream()
                        .filter(node -> {
                            try {
                                return DomTestDocuments.hasEntry(broker, doc, node);
                            } catch (final Exception e) {
                                throw new IllegalStateException(e);
                            }
                        }).collect(Collectors.toList());
                assertTrue(withEntry.size() > 3, "the document has nodes with an entry: " + withEntry.size());
                final LookupStatistics before = domFile(broker).getLookupStatistics();
                for (final IStoredNode<?> node : withEntry) {
                    broker.objectWith(new NodeProxy(null, doc, node.getNodeId(), node.getNodeType()));
                }
                final LookupStatistics counted = domFile(broker).getLookupStatistics().since(before);
                assertEquals(withEntry.size(), counted.exactHits(), "each node with an entry was an exact hit: " + counted);
                assertEquals(0, counted.lookupsFromEntry() + counted.ancestorFallbacks(), "no node was read forward: " + counted);
                assertEquals(withEntry.size(), counted.lookups());
            }
        }
    }

    @Test
    void lookupsFromAnEntryAreCountedInBucketsRelativeToTheScanLimit() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
            final String collection = "/db/stats-buckets";
            DomTestDocuments.store(pool, broker, collection, DomTestDocuments.wideDocument(CHILDREN));
            final LookupStatistics beforeStored;
            try (final LockedDocument locked = DomTestDocuments.open(broker, collection)) {
                final DocumentImpl doc = locked.getDocument();
                beforeStored = domFile(broker).getLookupStatistics();
                DomTestDocuments.recordsReadPerLookup(broker, doc, DomTestDocuments.nodesOf(doc));
            }
            final LookupStatistics stored = domFile(broker).getLookupStatistics().since(beforeStored);
            final LookupBuckets buckets = stored.buckets();
            assertTrue(stored.lookupsFromEntry() > CHILDREN, "nodes were read forward from an entry: " + stored);
            assertEquals(stored.lookupsFromEntry(), buckets.total(), "every such lookup is in one bucket: " + stored);
            assertEquals(0, buckets.beyondLimit(), "after a store no lookup reads more than the scan limit: " + stored);
            assertTrue(buckets.upToQuarter() > 0 && buckets.upToHalf() + buckets.upToLimit() > 0,
                    "the lookups are spread over the buckets, not in one: " + stored);

            // without its anchors the same document has long runs again, and the last bucket counts them
            final String ids;
            try (final LockedDocument locked = DomTestDocuments.open(broker, collection)) {
                ids = DomTestDocuments.anchorIds(broker, locked.getDocument()).stream().map(id -> "'" + id + "'").collect(Collectors.joining(", "));
            }
            DomTestDocuments.update(pool, broker, "update delete (for $id in (" + ids + ") return util:node-by-id(doc('" + collection + "/d.xml'), $id))");
            final LookupStatistics beforeWidened = domFile(broker).getLookupStatistics();
            try (final LockedDocument locked = DomTestDocuments.open(broker, collection)) {
                final DocumentImpl doc = locked.getDocument();
                final List<IStoredNode<?>> nodes = DomTestDocuments.nodesOf(doc);
                for (final IStoredNode<?> node : nodes) {
                    broker.objectWith(new NodeProxy(null, doc, node.getNodeId(), node.getNodeType()));
                }
            }
            final LookupStatistics widened = domFile(broker).getLookupStatistics().since(beforeWidened);
            assertTrue(widened.buckets().beyondLimit() > 0, "lookups beyond the scan limit are counted: " + widened);
            assertEquals(widened.lookupsFromEntry(), widened.buckets().total());
        }
    }

    @Test
    void anEntryWhoseRecordWasMovedByAPageSplitCountsAForwardLinkHop() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
            final String collection = "/db/stats-hops";
            DomTestDocuments.store(pool, broker, collection, DomTestDocuments.wideDocument(CHILDREN));
            final LookupStatistics beforeSplit;
            try (final LockedDocument locked = DomTestDocuments.open(broker, collection)) {
                final DocumentImpl doc = locked.getDocument();
                beforeSplit = domFile(broker).getLookupStatistics();
                DomTestDocuments.recordsReadPerLookup(broker, doc, DomTestDocuments.nodesOf(doc));
            }
            assertEquals(0, domFile(broker).getLookupStatistics().since(beforeSplit).forwardLinkHops(), "no page was split: no forward link");

            DomTestDocuments.insertBeforeTheFifthChild(pool, broker, collection, 6);
            final LookupStatistics beforeSplitLookups;
            try (final LockedDocument locked = DomTestDocuments.open(broker, collection)) {
                final DocumentImpl doc = locked.getDocument();
                assertTrue(doc.getSplitCount() > 0, "pages were split");
                beforeSplitLookups = domFile(broker).getLookupStatistics();
                DomTestDocuments.recordsReadPerLookup(broker, doc, DomTestDocuments.nodesOf(doc));
            }
            final LookupStatistics afterSplit = domFile(broker).getLookupStatistics().since(beforeSplitLookups);
            assertTrue(afterSplit.forwardLinkHops() > 0, "an entry whose record moved was reached through its forward link: " + afterSplit);
            assertTrue(afterSplit.forwardLinkHops() <= afterSplit.lookupsFromEntry() + afterSplit.ancestorFallbacks(),
                    "a hop is counted for an entry of a lookup, once at most: " + afterSplit);
        }
    }

    @Test
    void theCountersCanBeResetAndAreCountedPerDocumentOnlyWhenSwitchedOn() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
            final DOMFile domFile = domFile(broker);
            DomTestDocuments.store(pool, broker, "/db/stats-doc-a", DomTestDocuments.wideDocument(CHILDREN));
            DomTestDocuments.store(pool, broker, "/db/stats-doc-b", DomTestDocuments.wideDocument(CHILDREN / 4));
            assertFalse(domFile.isDocumentStatistics(), "off by default");

            domFile.resetLookupStatistics();
            lookEverythingUp(broker, "/db/stats-doc-a");
            assertTrue(domFile.getDocumentStatistics(10).isEmpty(), "nothing is counted per document while it is off");
            assertTrue(domFile.getLookupStatistics().lookups() > 0);

            domFile.resetLookupStatistics();
            domFile.setDocumentStatistics(true);
            try {
                lookEverythingUp(broker, "/db/stats-doc-b");
                lookEverythingUp(broker, "/db/stats-doc-a");
                final List<DocumentLookups> top = domFile.getDocumentStatistics(10);
                assertEquals(2, top.size(), "both documents are followed: " + top);
                assertEquals("/db/stats-doc-a/d.xml", top.get(0).uri(), "a document is named by its path: " + top);
                assertEquals("/db/stats-doc-b/d.xml", top.get(1).uri(), "a document is named by its path: " + top);
                assertTrue(top.get(0).recordsScanned() > top.get(1).recordsScanned(), "the larger document read more: " + top);
                assertEquals(domFile.getLookupStatistics().lookupsFromEntry() + domFile.getLookupStatistics().ancestorFallbacks(),
                        top.stream().mapToLong(DocumentLookups::lookups).sum(), "the documents add up to the counters: " + top);
                assertEquals(1, domFile.getDocumentStatistics(1).size(), "the list is cut to the limit");
            } finally {
                domFile.setDocumentStatistics(false);
            }

            domFile.resetLookupStatistics();
            final LookupStatistics reset = domFile.getLookupStatistics();
            assertEquals(0, reset.lookups() + reset.recordsScanned() + reset.forwardLinkHops() + reset.buckets().total(), "all counters are zero: " + reset);
            assertTrue(domFile.getDocumentStatistics(10).isEmpty(), "the counters per document are cleared too");
        }
    }

    private static void lookEverythingUp(final DBBroker broker, final String collection) throws Exception {
        try (final LockedDocument locked = DomTestDocuments.open(broker, collection)) {
            final DocumentImpl doc = locked.getDocument();
            DomTestDocuments.recordsReadPerLookup(broker, doc, DomTestDocuments.nodesOf(doc));
        }
    }
}
