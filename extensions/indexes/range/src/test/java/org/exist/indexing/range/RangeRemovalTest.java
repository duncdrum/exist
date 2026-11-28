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
package org.exist.indexing.range;

import org.apache.lucene.document.IntField;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.indexing.lucene.LuceneFieldNames;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.dom.persistent.LockedDocument;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the range index finds after a document or a collection was removed, each removal committed on its own. The terms
 * are read with a scan (util:index-keys), and so are the terms of every document that is left, with the number of nodes
 * that hold them.
 */
public class RangeRemovalTest {

    private static final String COLLECTION_NAME = "removal-range";
    private static final XmldbURI ROOT = XmldbURI.create("/db/" + COLLECTION_NAME);
    private static final XmldbURI CONFIG_COLLECTION = XmldbURI.CONFIG_COLLECTION_URI.append("db").append(COLLECTION_NAME);

    private static final String CONFIGURATION = """
            <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <index>
                <range><create qname="p" type="xs:string"/></range>
              </index>
            </collection>
            """;

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    /** the collection a holds two documents, the collection b one */
    @BeforeEach
    void store() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transactionManager = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transactionManager.beginTransaction()) {
            final Collection root = broker.getOrCreateCollection(transaction, ROOT);
            broker.saveCollection(transaction, root);
            pool.getConfigurationManager().addConfiguration(transaction, broker, root, CONFIGURATION);
            final Collection a = broker.getOrCreateCollection(transaction, ROOT.append("a"));
            final Collection b = broker.getOrCreateCollection(transaction, ROOT.append("b"));
            broker.storeDocument(transaction, XmldbURI.create("d1.xml"), new StringInputSource("<t><p>cat</p><p>dog</p></t>"), MimeType.XML_TYPE, a);
            broker.storeDocument(transaction, XmldbURI.create("d2.xml"), new StringInputSource("<t><p>cat</p></t>"), MimeType.XML_TYPE, a);
            broker.storeDocument(transaction, XmldbURI.create("d3.xml"), new StringInputSource("<t><p>cat</p><p>emu</p></t>"), MimeType.XML_TYPE, b);
            transactionManager.commit(transaction);
        }
    }

    @AfterEach
    void cleanup() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transactionManager = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transactionManager.beginTransaction()) {
            for (final XmldbURI uri : List.of(ROOT, CONFIG_COLLECTION)) {
                try (final Collection collection = broker.openCollection(uri, LockMode.WRITE_LOCK)) {
                    if (collection != null) {
                        broker.removeCollection(transaction, collection);
                    }
                }
            }
            transactionManager.commit(transaction);
        }
    }

    @Test
    void removalOfADocument() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        assertEquals("cat:3 dog:1 emu:1", scan(pool), "before");
        final int d1 = docId(pool, "a/d1.xml");
        final int d2 = docId(pool, "a/d2.xml");
        final int d3 = docId(pool, "b/d3.xml");
        assertEquals(List.of(2, 1, 2), List.of(entries(pool, d1), entries(pool, d2), entries(pool, d3)), "the entries of the three documents in the index");

        remove(pool, "a", "d1.xml");
        assertEquals("cat:2 emu:1", scan(pool), "after the removal of the document with dog");
        assertEquals(List.of(0, 1, 2), List.of(entries(pool, d1), entries(pool, d2), entries(pool, d3)), "the entries after the removal of d1");

        remove(pool, "a", "d2.xml");
        assertEquals("cat:1 emu:1", scan(pool), "after the removal of a second document");
        assertEquals(List.of(0, 0, 2), List.of(entries(pool, d1), entries(pool, d2), entries(pool, d3)), "the entries after the removal of d2");

        remove(pool, "b", "d3.xml");
        assertEquals("", scan(pool), "after the removal of the last document");
        assertEquals(List.of(0, 0, 0), List.of(entries(pool, d1), entries(pool, d2), entries(pool, d3)), "the entries after the removal of d3");
    }

    @Test
    void removalOfACollection() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final int d1 = docId(pool, "a/d1.xml");
        final int d3 = docId(pool, "b/d3.xml");

        removeCollection(pool, "b");
        assertEquals("cat:2 dog:1", scan(pool), "after the removal of the collection b");
        assertEquals(List.of(2, 0), List.of(entries(pool, d1), entries(pool, d3)), "the entries after the removal of the collection b");

        removeCollection(pool, "a");
        assertEquals("", scan(pool), "after the removal of the collection a");
        assertEquals(List.of(0, 0), List.of(entries(pool, d1), entries(pool, d3)), "the entries after the removal of the collection a");
    }

    private static int docId(final BrokerPool pool, final String path) throws Exception {
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final LockedDocument document = broker.getXMLResource(ROOT.append(path), LockMode.READ_LOCK)) {
            return document.getDocument().getDocId();
        }
    }

    /** the Lucene documents that the index holds for an eXist document, not hidden by the document set of a query */
    private static int entries(final BrokerPool pool, final int docId) throws Exception {
        final LuceneIndex index = (RangeIndex) pool.getIndexManager().getIndexById(RangeIndex.ID);
        return index.withSearcher(searcher -> searcher.searcher().count(IntField.newExactQuery(LuceneFieldNames.FIELD_DOC_ID, docId)));
    }

    /** the terms of the index below the root, as term:count */
    private static String scan(final BrokerPool pool) throws Exception {
        final XQuery xquery = pool.getXQueryService();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            return xquery.execute(broker, """
                    string-join(collection('%s')/util:index-keys-by-qname(xs:QName('p'), '',
                        function($key, $counts) { $key || ':' || $counts[1] }, 100, 'range-index'), ' ')
                    """.formatted(ROOT), null).getStringValue();
        }
    }

    private static void remove(final BrokerPool pool, final String collection, final String document) throws Exception {
        final TransactionManager transactionManager = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transactionManager.beginTransaction();
             final Collection c = broker.openCollection(ROOT.append(collection), LockMode.WRITE_LOCK)) {
            c.removeXMLResource(transaction, broker, XmldbURI.create(document));
            transactionManager.commit(transaction);
        }
    }

    private static void removeCollection(final BrokerPool pool, final String collection) throws Exception {
        final TransactionManager transactionManager = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transactionManager.beginTransaction();
             final Collection c = broker.openCollection(ROOT.append(collection), LockMode.WRITE_LOCK)) {
            broker.removeCollection(transaction, c);
            transactionManager.commit(transaction);
        }
    }
}
