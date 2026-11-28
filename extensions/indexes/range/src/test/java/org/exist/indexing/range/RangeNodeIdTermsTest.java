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

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.numbering.NodeId;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.ByteConversion;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.indexing.lucene.LuceneFieldNames;
import org.exist.indexing.lucene.LuceneUtil;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The terms that the range index writes for a node: the doc id and the id of the node, without units, are indexed under
 * {@code exist:id}, and the id with the units in front is kept as a doc value under {@code exist:nodeId}. A node that is
 * removed takes its term with it. The test builds the bytes of the id on its own and reads them back from the Lucene
 * index, so that the way the index builds them can be changed without changing what it writes.
 */
public class RangeNodeIdTermsTest {

    private static final String COLLECTION_NAME = "range-node-id-terms";
    private static final XmldbURI COLLECTION = XmldbURI.create("/db/" + COLLECTION_NAME);
    private static final XmldbURI CONFIG_COLLECTION = XmldbURI.CONFIG_COLLECTION_URI.append("db").append(COLLECTION_NAME);
    private static final String DOCUMENT = "items.xml";

    private static final String CONFIGURATION = """
            <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <index>
                <range>
                  <create qname="item" type="xs:string"/>
                </range>
              </index>
            </collection>
            """;

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @AfterEach
    void cleanup() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transactionManager = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transactionManager.beginTransaction()) {
            for (final XmldbURI uri : List.of(COLLECTION, CONFIG_COLLECTION)) {
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
    void everyNodeHasItsIdAsATermAndAsADocValue() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final RangeIndex index = (RangeIndex) pool.getIndexManager().getIndexById(RangeIndex.ID);
            store(pool, broker);
            final List<NodeProxy> items = items(pool, broker);
            assertEquals(3, items.size());
            final int docId = items.get(0).getOwnerDocument().getDocId();

            index.withSearcher(searcher -> {
                for (final NodeProxy item : items) {
                    final Query query = idQuery(docId, item.getNodeId());
                    assertEquals(1, searcher.searcher().count(query), "the term of node " + item.getNodeId());

                    final ScoreDoc hit = searcher.searcher().search(query, 1).scoreDocs[0];
                    final List<LeafReaderContext> leaves = searcher.searcher().getIndexReader().leaves();
                    final LeafReaderContext leaf = leaves.get(ReaderUtil.subIndex(hit.doc, leaves));
                    final NodeId decoded = LuceneUtil.readNodeId(hit.doc - leaf.docBase,
                            leaf.reader().getBinaryDocValues(LuceneFieldNames.FIELD_NODE_ID), pool);
                    assertEquals(item.getNodeId(), decoded, "the doc value of node " + item.getNodeId());
                }
                return null;
            });
        }
    }

    @Test
    void aRemovedNodeTakesItsTermWithIt() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transactionManager = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final RangeIndex index = (RangeIndex) pool.getIndexManager().getIndexById(RangeIndex.ID);
            store(pool, broker);
            final List<NodeProxy> items = items(pool, broker);
            final int docId = items.get(0).getOwnerDocument().getDocId();

            try (final Txn transaction = transactionManager.beginTransaction();
                 final Collection collection = broker.openCollection(COLLECTION, LockMode.READ_LOCK)) {
                final MutableDocumentSet docs = new DefaultDocumentSet();
                collection.allDocs(broker, docs, false);
                final XUpdateProcessor processor = new XUpdateProcessor(broker, docs);
                processor.setBroker(broker);
                processor.setDocumentSet(docs);
                final String xupdate = "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">"
                        + "<xu:remove select=\"//item[2]\"/></xu:modifications>";
                final Modification[] modifications = processor.parse(new InputSource(new StringReader(xupdate)));
                modifications[0].process(transaction);
                processor.reset();
                transactionManager.commit(transaction);
            }

            final int[] counts = index.withSearcher(searcher -> new int[] {
                    searcher.searcher().count(idQuery(docId, items.get(0).getNodeId())),
                    searcher.searcher().count(idQuery(docId, items.get(1).getNodeId())),
                    searcher.searcher().count(idQuery(docId, items.get(2).getNodeId()))});
            assertEquals(1, counts[0], "the first item stays");
            assertEquals(0, counts[1], "the removed item");
            assertEquals(1, counts[2], "the third item stays");
        }
    }

    private static Query idQuery(final int docId, final NodeId nodeId) {
        return new TermQuery(new Term(LuceneFieldNames.FIELD_ID, new BytesRef(docIdAndId(docId, nodeId))));
    }

    /** the term of a node as the index writes it: the doc id, then the id, without units, built here and not by the index */
    private static byte[] docIdAndId(final int docId, final NodeId nodeId) {
        final byte[] data = new byte[nodeId.size() + 4];
        ByteConversion.intToByteH(docId, data, 0);
        nodeId.serialize(data, 4);
        return data;
    }

    private static void store(final BrokerPool pool, final DBBroker broker) throws Exception {
        final TransactionManager transactionManager = pool.getTransactionManager();
        try (final Txn transaction = transactionManager.beginTransaction()) {
            final Collection collection = broker.getOrCreateCollection(transaction, COLLECTION);
            broker.saveCollection(transaction, collection);
            pool.getConfigurationManager().addConfiguration(transaction, broker, collection, CONFIGURATION);
            broker.storeDocument(transaction, XmldbURI.create(DOCUMENT),
                    new StringInputSource("<div><item>one</item><item>two</item><item>three</item></div>"), MimeType.XML_TYPE, collection);
            transactionManager.commit(transaction);
        }
    }

    /** the items of the document, found without Lucene, so that nothing is written to the index */
    private static List<NodeProxy> items(final BrokerPool pool, final DBBroker broker) throws Exception {
        final XQuery xquery = pool.getXQueryService();
        final Sequence sequence = xquery.execute(broker, "doc('" + COLLECTION + "/" + DOCUMENT + "')//item", null);
        final List<NodeProxy> items = new ArrayList<>();
        for (final SequenceIterator it = sequence.iterate(); it.hasNext(); ) {
            items.add((NodeProxy) it.nextItem());
        }
        return items;
    }
}
