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
import org.exist.storage.lock.Lock.LockMode;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.w3c.dom.Node;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which nodes of a stored document have an entry in the DOM B-tree, after a store, after a reindex that stores and after
 * an update, as the entry rule says. The rules for a stored and a rebuilt entry differ for comments and processing
 * instructions below the root level, and the test shows it on a real document.
 */
public class DomEntriesTest {

    @RegisterExtension
    static final ExistEmbeddedServer SERVER = new ExistEmbeddedServer(DomTestDocuments.configuration(), true, true);

    private static void update(final BrokerPool pool, final DBBroker broker, final String query) throws Exception {
        pool.getXQueryService().execute(broker, query, null);
    }

    @Test
    void storingGivesEntriesByTheStoreRule() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final EntryPolicy policy = ((NativeBroker) broker).getEntryPolicy();
            assertEquals(DomTestDocuments.INDEX_DEPTH, policy.getIndexDepth(), "the configured index depth took effect");
            DomTestDocuments.store(pool, broker, "/db/entries-store");
            try (final LockedDocument locked = broker.getXMLResource(XmldbURI.create("/db/entries-store/d.xml"), LockMode.READ_LOCK)) {
                final DocumentImpl doc = locked.getDocument();
                final List<IStoredNode<?>> nodes = DomTestDocuments.nodesOf(doc);
                assertTrue(nodes.size() > 4 * (policy.getIndexDepth() + 3), "the document has nodes of every kind: " + nodes.size());
                int with = 0;
                int without = 0;
                boolean commentBelowRootWithEntry = false;
                for (final IStoredNode<?> node : nodes) {
                    final boolean entry = DomTestDocuments.hasEntry(broker, doc, node);
                    assertEquals(policy.entryOnStore(node.getNodeType(), node.getNodeId().getTreeLevel()), entry, DomTestDocuments.describe(node));
                    with += entry ? 1 : 0;
                    without += entry ? 0 : 1;
                    commentBelowRootWithEntry |= entry && node.getNodeType() == Node.COMMENT_NODE && node.getNodeId().getTreeLevel() > 1;
                }
                assertTrue(with > 0 && without > 0, "some nodes have an entry and some do not: " + with + " and " + without);
                assertTrue(commentBelowRootWithEntry, "storing gives a comment below the root level an entry");
            }
        }
    }

    @Test
    void aReindexThatStoresGivesEntriesByTheReindexRule() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final EntryPolicy policy = ((NativeBroker) broker).getEntryPolicy();
            assertEquals(DomTestDocuments.INDEX_DEPTH, policy.getIndexDepth(), "the configured index depth took effect");
            DomTestDocuments.store(pool, broker, "/db/entries-reindex");
            update(pool, broker, "xmldb:reindex('/db/entries-reindex')");
            try (final LockedDocument locked = broker.getXMLResource(XmldbURI.create("/db/entries-reindex/d.xml"), LockMode.READ_LOCK)) {
                final DocumentImpl doc = locked.getDocument();
                int with = 0;
                int without = 0;
                boolean commentBelowRootWithoutEntry = false;
                for (final IStoredNode<?> node : DomTestDocuments.nodesOf(doc)) {
                    final boolean entry = DomTestDocuments.hasEntry(broker, doc, node);
                    assertEquals(policy.entryOnReindex(node.getNodeType(), node.getNodeId().getTreeLevel()), entry, DomTestDocuments.describe(node));
                    with += entry ? 1 : 0;
                    without += entry ? 0 : 1;
                    commentBelowRootWithoutEntry |= !entry && node.getNodeType() == Node.COMMENT_NODE && node.getNodeId().getTreeLevel() > 1
                            && node.getNodeId().getTreeLevel() <= policy.getIndexDepth();
                }
                assertTrue(with > 0 && without > 0, "some nodes have an entry and some do not: " + with + " and " + without);
                assertTrue(commentBelowRootWithoutEntry, "a reindex leaves a comment below the root level, within the index depth, without an entry");
            }
        }
    }

    @Test
    void anUpdateGivesTheInsertedNodesEntriesByTheReindexRule() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final EntryPolicy policy = ((NativeBroker) broker).getEntryPolicy();
            DomTestDocuments.store(pool, broker, "/db/entries-update");
            update(pool, broker, "update insert <added a=\"x\"><!--c-->t<?p x?><inner><!--d-->u</inner></added> into doc('/db/entries-update/d.xml')/l1");
            try (final LockedDocument locked = broker.getXMLResource(XmldbURI.create("/db/entries-update/d.xml"), LockMode.READ_LOCK)) {
                final DocumentImpl doc = locked.getDocument();
                boolean inserted = false;
                int checked = 0;
                for (final IStoredNode<?> node : DomTestDocuments.nodesOf(doc)) {
                    if (node.getNodeType() == Node.ELEMENT_NODE && "added".equals(node.getNodeName())) {
                        inserted = true;
                    }
                    // the inserted element is the last child of the root; what follows at level 1 is the comment and the instruction after the root
                    if (inserted && node.getNodeId().getTreeLevel() >= 2) {
                        assertEquals(policy.entryOnReindex(node.getNodeType(), node.getNodeId().getTreeLevel()), DomTestDocuments.hasEntry(broker, doc, node), DomTestDocuments.describe(node));
                        checked++;
                    }
                }
                assertTrue(inserted, "the inserted element is in the document");
                assertTrue(checked >= 6, "the inserted element and its content were checked: " + checked);
            }
        }
    }
}
