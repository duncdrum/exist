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

import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.dom.QName;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A store whose transaction is aborted must leave the DOM and the Lucene index consistent with each other.
 *
 * <p>Today a runtime abort does not undo the DOM (undo only happens in crash recovery), so the aborted document
 * stays in the collection and in the index. That is not asserted here: if abort ever starts to undo the DOM,
 * the index has to follow, and if it does not, the index has to keep the document. What must never happen is
 * that one side keeps the document and the other does not, which is what a buffered index write that is
 * dropped on abort would produce while the DOM keeps the document.</p>
 *
 * <p>The index is checked at two levels, because they can disagree: what {@code ft:query} returns (it resolves
 * every hit to a node) and what the index physically holds (a term count on the shared searcher).</p>
 */
public class LuceneAbortedStoreTest {

    private static final XmldbURI COLLECTION_URI = XmldbURI.create("/db/abort-test");

    private static final String CONFIGURATION = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
                <index>
                    <lucene>
                        <text qname="p"/>
                    </lucene>
                </index>
            </collection>""";

    private static final String ABORTED_TERM = "zzabortedterm";
    private static final String COMMITTED_TERM = "zzcommittedterm";

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private Collection collection;

    @BeforeEach
    public void setUp() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {
            collection = broker.getOrCreateCollection(transaction, COLLECTION_URI);
            assertNotNull(collection);
            broker.saveCollection(transaction, collection);
            final CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(transaction, broker, collection, CONFIGURATION);
            transact.commit(transaction);
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {
            if (collection != null) {
                broker.removeCollection(transaction, collection);
            }
            final Collection config = broker.getCollection(XmldbURI.CONFIG_COLLECTION_URI.append("db").append("abort-test"));
            if (config != null) {
                broker.removeCollection(transaction, config);
            }
            transact.commit(transaction);
        }
    }

    @Test
    public void committedStoreIsFoundByQueryAndHeldByIndex() throws Exception {
        store("committed.xml", COMMITTED_TERM, true);

        assertEquals(1, documentCount(), "the committed document is in the collection");
        assertEquals(1, queryHits(COMMITTED_TERM), "the committed document is found by ft:query");
        assertEquals(1, indexedDocuments(COMMITTED_TERM), "the committed document is in the Lucene index");
    }

    @Test
    public void abortedStoreKeepsDomAndIndexConsistent() throws Exception {
        store("aborted.xml", ABORTED_TERM, false);

        final int documents = documentCount();
        assertEquals(documents, queryHits(ABORTED_TERM),
                "ft:query and the collection disagree about the aborted document");
        assertEquals(documents, indexedDocuments(ABORTED_TERM),
                "the Lucene index and the collection disagree about the aborted document");
    }

    @Test
    public void storeAfterAbortKeepsDomAndIndexConsistent() throws Exception {
        store("aborted.xml", ABORTED_TERM, false);
        final int abortedDocuments = documentCount();

        store("committed.xml", COMMITTED_TERM, true);

        assertEquals(abortedDocuments + 1, documentCount(), "the committed store adds exactly one document");
        assertEquals(1, queryHits(COMMITTED_TERM), "the committed document is found by ft:query");
        assertEquals(1, indexedDocuments(COMMITTED_TERM), "the committed document is in the Lucene index");
        assertEquals(abortedDocuments, queryHits(ABORTED_TERM),
                "a store after the abort changed what ft:query returns for the aborted term");
        assertEquals(abortedDocuments, indexedDocuments(ABORTED_TERM),
                "a store after the abort changed what the index holds for the aborted term");
    }

    private void store(final String name, final String term, final boolean commit) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {
            broker.storeDocument(transaction, XmldbURI.create(name),
                    new StringInputSource("<doc><p>alpha " + term + " omega</p></doc>"), MimeType.XML_TYPE, collection);
            // the store happened inside the transaction: without this, a store that did nothing would satisfy every assertion below
            assertNotNull(collection.getDocument(broker, XmldbURI.create(name)), "the document was stored before the transaction ended");
            if (commit) {
                transact.commit(transaction);
            } else {
                transact.abort(transaction);
            }
        }
    }

    private int queryHits(final String term) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final Sequence result = xquery.execute(broker,
                    "collection('" + COLLECTION_URI + "')//p[ft:query(., '" + term + "')]", null);
            return result.getItemCount();
        }
    }

    private int documentCount() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            return xquery.execute(broker, "collection('" + COLLECTION_URI + "')/doc", null).getItemCount();
        }
    }

    /** The number of Lucene documents in the shared index that hold the term in the {@code p} field. */
    private int indexedDocuments(final String term) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final LuceneIndexWorker worker = (LuceneIndexWorker) broker.getIndexController().getWorkerByIndexId(LuceneIndex.ID);
            final String field = LuceneFieldNames.encodeQName(new QName("p"), pool.getSymbols());
            return worker.index.withSearcher(searcher -> searcher.searcher().count(new TermQuery(new Term(field, term))));
        }
    }
}
