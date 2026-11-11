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
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.StoredNode;
import org.exist.numbering.NodeId;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.StorageAddress;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.ManagedLock;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.w3c.dom.Node;

import java.util.List;
import java.util.Optional;
import java.util.function.ToLongFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every node of a stored document is found by its id when its address is not known, and an id that does not exist is not
 * found. A node without an entry in the DOM B-tree is found by reading the document forward from the nearest entry before
 * it, and from its nearest ancestor that has one when that entry is wrong.
 */
public class DomLookupTest {

    @RegisterExtension
    static final ExistEmbeddedServer SERVER = new ExistEmbeddedServer(DomTestDocuments.configuration(), true, true);

    @Test
    void everyNodeIsFoundByItsIdWithoutAnAddress() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            DomTestDocuments.store(pool, broker, "/db/entries-find");
            try (final LockedDocument locked = broker.getXMLResource(XmldbURI.create("/db/entries-find/d.xml"), LockMode.READ_LOCK)) {
                final DocumentImpl doc = locked.getDocument();
                final List<IStoredNode<?>> nodes = DomTestDocuments.nodesOf(doc);
                assertTrue(nodes.size() > 4 * (DomTestDocuments.INDEX_DEPTH + 3), "the document has nodes of every kind: " + nodes.size());
                for (final IStoredNode<?> node : nodes) {
                    final IStoredNode<?> found = broker.objectWith(new NodeProxy(null, doc, node.getNodeId(), node.getNodeType()));
                    assertNotNull(found, DomTestDocuments.describe(node));
                    assertEquals(node.getNodeId(), found.getNodeId(), DomTestDocuments.describe(node));
                    assertEquals(node.getNodeType(), found.getNodeType(), DomTestDocuments.describe(node));
                    assertEquals(node.getNodeValue(), found.getNodeValue(), DomTestDocuments.describe(node));
                }
            }
        }
    }

    @Test
    void aMissingIdIsNotFound() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            DomTestDocuments.store(pool, broker, "/db/entries-missing");
            try (final LockedDocument locked = broker.getXMLResource(XmldbURI.create("/db/entries-missing/d.xml"), LockMode.READ_LOCK)) {
                final DocumentImpl doc = locked.getDocument();
                IStoredNode<?> deepest = null;
                for (final IStoredNode<?> node : DomTestDocuments.nodesOf(doc)) {
                    if (deepest == null || node.getNodeId().getTreeLevel() > deepest.getNodeId().getTreeLevel()) {
                        deepest = node;
                    }
                }
                assertNotNull(deepest, "the document has nodes");
                final NodeId missing = deepest.getNodeId().newChild();
                assertNull(broker.objectWith(new NodeProxy(null, doc, missing, Node.ELEMENT_NODE)), "a child of a leaf node does not exist");
            }
        }
    }

    private static IStoredNode<?> first(final List<IStoredNode<?>> nodes, final short nodeType, final int level) {
        return nodes.stream()
                .filter(n -> n.getNodeType() == nodeType && n.getNodeId().getTreeLevel() == level)
                .findFirst().orElseThrow();
    }

    @Test
    void nodesWithoutAnEntryAreFoundFromTheNearestEntry() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final DOMFile domDb = ((NativeBroker) broker).getDOMFile();
            DomTestDocuments.store(pool, broker, "/db/entries-from-entry");
            try (final LockedDocument locked = broker.getXMLResource(XmldbURI.create("/db/entries-from-entry/d.xml"), LockMode.READ_LOCK)) {
                final DocumentImpl doc = locked.getDocument();
                // walking the document resolves nodes by their id too, so list them before the counters are read
                final List<IStoredNode<?>> nodes = DomTestDocuments.nodesOf(doc);
                final LookupStatistics before = domDb.getLookupStatistics();
                int withoutEntry = 0;
                for (final IStoredNode<?> node : nodes) {
                    if (!DomTestDocuments.hasEntry(broker, doc, node)) {
                        withoutEntry++;
                    }
                    assertNotNull(broker.objectWith(new NodeProxy(null, doc, node.getNodeId(), node.getNodeType())), DomTestDocuments.describe(node));
                }
                assertTrue(withoutEntry > 10, "the document has nodes without an entry: " + withoutEntry);
                final LookupStatistics counted = domDb.getLookupStatistics().since(before);
                assertEquals(withoutEntry, counted.lookupsFromEntry(), "each of them was read from an entry, once");
                assertEquals(withoutEntry, counted.lookupsFromEntry() + counted.ancestorFallbacks(), "each lookup ended one way");
                assertTrue(counted.recordsScanned() > 0, "records were read");
                assertEquals(0, counted.rejectedEntries(), "no entry was wrong");
                assertEquals(0, counted.ancestorFallbacks(), "no lookup climbed to an ancestor");
            }
        }
    }

    @Test
    void aMissingIdIsDecidedFromTheNearestEntry() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final DOMFile domDb = ((NativeBroker) broker).getDOMFile();
            DomTestDocuments.store(pool, broker, "/db/entries-missing-entry");
            try (final LockedDocument locked = broker.getXMLResource(XmldbURI.create("/db/entries-missing-entry/d.xml"), LockMode.READ_LOCK)) {
                final DocumentImpl doc = locked.getDocument();
                final IStoredNode<?> deep = first(DomTestDocuments.nodesOf(doc), Node.ELEMENT_NODE, DomTestDocuments.INDEX_DEPTH + 2);
                final LookupStatistics before = domDb.getLookupStatistics();
                assertNull(broker.objectWith(new NodeProxy(null, doc, deep.getNodeId().newChild().newChild(), Node.ELEMENT_NODE)));
                final LookupStatistics counted = domDb.getLookupStatistics().since(before);
                assertEquals(1, counted.lookupsFromEntry(), "it was decided from the nearest entry");
                assertEquals(0, counted.ancestorFallbacks(), "a node that does not exist is not looked for twice");
            }
        }
    }

    private static IStoredNode<?> comment(final List<IStoredNode<?>> nodes, final int level) {
        return first(nodes, Node.COMMENT_NODE, level);
    }

    private void lookupWithWrongEntry(final String collection, final boolean ancestor, final long expectedRejections,
            final ToLongFunction<List<IStoredNode<?>>> wrongAddress) throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final DOMFile domDb = ((NativeBroker) broker).getDOMFile();
            DomTestDocuments.store(pool, broker, collection);
            try (final LockedDocument locked = broker.getXMLResource(XmldbURI.create(collection + "/d.xml"), LockMode.READ_LOCK)) {
                final DocumentImpl doc = locked.getDocument();
                final List<IStoredNode<?>> nodes = DomTestDocuments.nodesOf(doc);
                // the entry is the nearest one before the target: that of a node that precedes it without being its ancestor
                // (a comment), or that of an ancestor, which the climb to the nearest ancestor with an entry finds again
                final IStoredNode<?> keyed = ancestor
                        ? first(nodes, Node.ELEMENT_NODE, DomTestDocuments.INDEX_DEPTH + 1)
                        : comment(nodes, DomTestDocuments.INDEX_DEPTH + 2);
                final int below = keyed.getNodeId().getTreeLevel();
                final IStoredNode<?> target = nodes.stream()
                        .filter(n -> n.getNodeType() == Node.TEXT_NODE && n.getNodeId().getTreeLevel() > below + 1)
                        .findFirst().orElseThrow();
                assertFalse(DomTestDocuments.hasEntry(broker, doc, keyed), "the node has no entry of its own");
                final NativeBroker.NodeRef key = new NativeBroker.NodeRef(doc.getDocId(), keyed.getNodeId());
                final long address = wrongAddress.applyAsLong(nodes);
                try (final Txn txn = pool.getTransactionManager().beginTransaction();
                        final ManagedLock<?> lock = pool.getLockManager().acquireBtreeWriteLock(domDb.getLockName())) {
                    domDb.addValue(txn, key, address);
                    txn.commit();
                }
                try {
                    final LookupStatistics before = domDb.getLookupStatistics();
                    final IStoredNode<?> found = broker.objectWith(new NodeProxy(null, doc, target.getNodeId(), target.getNodeType()));
                    assertNotNull(found, "the node is still found");
                    assertEquals(target.getNodeId(), found.getNodeId());
                    assertEquals(target.getNodeValue(), found.getNodeValue());
                    final LookupStatistics counted = domDb.getLookupStatistics().since(before);
                    assertEquals(expectedRejections, counted.rejectedEntries(), "the wrong entry was rejected");
                    assertEquals(1, counted.ancestorFallbacks(), "the lookup climbed to an ancestor");
                    assertEquals(0, counted.lookupsFromEntry(), "it was not answered from an entry");
                } finally {
                    try (final Txn txn = pool.getTransactionManager().beginTransaction();
                            final ManagedLock<?> lock = pool.getLockManager().acquireBtreeWriteLock(domDb.getLockName())) {
                        domDb.removeValue(txn, key);
                        txn.commit();
                    }
                }
            }
        }
    }

    @Test
    void anEntryThatLeadsToAnotherNodeIsIgnored() throws Exception {
        lookupWithWrongEntry("/db/entries-wrong-node", false, 1, nodes -> first(nodes, Node.ELEMENT_NODE, 1).getInternalAddress());
    }

    @Test
    void anEntryThatLeadsToAPageThatDoesNotExistIsIgnored() throws Exception {
        lookupWithWrongEntry("/db/entries-wrong-page", false, 1, nodes -> StorageAddress.createPointer(9_999_999, (short) 1));
    }

    @Test
    void anEntryThatLeadsToNoRecordOnItsPageIsIgnored() throws Exception {
        lookupWithWrongEntry("/db/entries-wrong-slot", false, 1, nodes ->
                StorageAddress.createPointer(StorageAddress.pageFromPointer(first(nodes, Node.ELEMENT_NODE, 1).getInternalAddress()), (short) 30000));
    }

    @Test
    void anEntryWithoutAnAddressIsIgnored() throws Exception {
        lookupWithWrongEntry("/db/entries-wrong-unknown", false, 1, nodes -> StoredNode.UNKNOWN_NODE_IMPL_ADDRESS);
    }

    // the nearest entry and the nearest ancestor with an entry are the same entry here, so it is ignored by both

    @Test
    void anAncestorEntryThatLeadsToAPageThatDoesNotExistIsSkipped() throws Exception {
        lookupWithWrongEntry("/db/entries-anc-page", true, 2, nodes -> StorageAddress.createPointer(9_999_999, (short) 1));
    }

    @Test
    void anAncestorEntryThatLeadsToNoRecordOnItsPageIsSkipped() throws Exception {
        lookupWithWrongEntry("/db/entries-anc-slot", true, 2, nodes ->
                StorageAddress.createPointer(StorageAddress.pageFromPointer(first(nodes, Node.ELEMENT_NODE, 1).getInternalAddress()), (short) 30000));
    }

    @Test
    void anAncestorEntryWithoutAnAddressIsSkipped() throws Exception {
        // an address of -1 is what a missing key returns, so the climb passes it without counting it
        lookupWithWrongEntry("/db/entries-anc-unknown", true, 1, nodes -> StoredNode.UNKNOWN_NODE_IMPL_ADDRESS);
    }

    @Test
    void aNodeBeforeEveryEntryOfItsDocumentClimbsWithoutRejectingAnything() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final DOMFile domDb = ((NativeBroker) broker).getDOMFile();
            DomTestDocuments.store(pool, broker, "/db/entries-before-all");
            try (final LockedDocument locked = broker.getXMLResource(XmldbURI.create("/db/entries-before-all/d.xml"), LockMode.READ_LOCK)) {
                final DocumentImpl doc = locked.getDocument();
                final IStoredNode<?> firstChild = (IStoredNode<?>) doc.getFirstChild();
                final NodeId before = firstChild.getNodeId().insertBefore();
                final LookupStatistics start = domDb.getLookupStatistics();
                assertNull(broker.objectWith(new NodeProxy(null, doc, before, Node.COMMENT_NODE)), "there is no such node");
                final LookupStatistics counted = domDb.getLookupStatistics().since(start);
                assertEquals(1, counted.ancestorFallbacks(), "there is no entry of the document before it, so it climbed");
                assertEquals(0, counted.lookupsFromEntry(), "it was not answered from an entry");
                assertEquals(0, counted.rejectedEntries(), "nothing was ignored: there was nothing to ignore");
            }
        }
    }
}
