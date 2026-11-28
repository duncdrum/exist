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

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.QName;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.indexing.IndexWorker;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeType;
import org.exist.util.Occurrences;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import static org.exist.indexing.QNamedKeysIndex.QNAMES_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A scan of the range index with the name {@code *} (any local name, no namespace given) lists the terms of every
 * indexed name.
 */
public class RangeWildcardNameScanTest {

    private static final String COLLECTION_NAME = "wildcard-name-range";
    private static final XmldbURI COLLECTION = XmldbURI.create("/db/" + COLLECTION_NAME);
    private static final XmldbURI CONFIG_COLLECTION = XmldbURI.CONFIG_COLLECTION_URI.append("db").append(COLLECTION_NAME);

    private static final String CONFIGURATION = """
            <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <index>
                <range><create qname="a" type="xs:string"/><create qname="b" type="xs:string"/></range>
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

    @Disabled("The range index reads the name * as a local name, so the scan lists nothing; the full-text index lists every name. See https://github.com/eXist-db/exist/issues/6990")
    @Test
    void aWildcardNameListsTheTermsOfEveryName() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transactionManager = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            try (final Txn transaction = transactionManager.beginTransaction()) {
                final Collection collection = broker.getOrCreateCollection(transaction, COLLECTION);
                broker.saveCollection(transaction, collection);
                pool.getConfigurationManager().addConfiguration(transaction, broker, collection, CONFIGURATION);
                broker.storeDocument(transaction, XmldbURI.create("d.xml"), new StringInputSource("<t><a>one</a><b>two</b></t>"), MimeType.XML_TYPE, collection);
                transactionManager.commit(transaction);
            }
            final MutableDocumentSet docs = new DefaultDocumentSet();
            try (final Collection collection = broker.openCollection(COLLECTION, LockMode.READ_LOCK)) {
                collection.allDocs(broker, docs, true);
            }
            final IndexWorker worker = broker.getIndexController().getWorkerByIndexId(RangeIndex.ID);

            final Occurrences[] named = worker.scanIndex(null, docs, null, Map.of(QNAMES_KEY, List.of(new QName("a", ""), new QName("b", ""))));
            final Occurrences[] wild = worker.scanIndex(null, docs, null, Map.of(QNAMES_KEY, List.of(new QName(QName.WILDCARD, null))));

            assertEquals(new TreeSet<>(List.of("one", "two")), terms(named), "the two names given");
            assertEquals(terms(named), terms(wild), "the name *");
        }
    }

    private static TreeSet<String> terms(final Occurrences[] occurrences) {
        final TreeSet<String> terms = new TreeSet<>();
        if (occurrences != null) {
            Arrays.stream(occurrences).forEach(o -> terms.add(o.getTerm().toString()));
        }
        return terms;
    }
}
