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
package org.exist.storage;

import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Optional;

import static org.exist.storage.IdIndexTestSupport.DTD;
import static org.exist.storage.IdIndexTestSupport.run;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Crash recovery with IDs, in the pattern of {@link AbstractRecoverTest}: the transactions run on threads of their own,
 * the journal is flushed, the database is restarted with {@link BrokerPool#FORCE_CORRUPTION} set, so that recovery has
 * to replay the journal. Committed changes of the ID entries are replayed, uncommitted ones are undone.
 */
public class IdIndexRecoveryTest {

    @RegisterExtension
    public final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @AfterEach
    void resetCorruption() {
        BrokerPool.FORCE_CORRUPTION = false;
    }

    private interface Task {
        void run(DBBroker broker, Txn transaction) throws Exception;
    }

    private void inThread(final boolean commit, final Task task) throws Exception {
        final Throwable[] error = new Throwable[1];
        final Thread thread = new Thread(() -> {
            final BrokerPool pool = existEmbeddedServer.getBrokerPool();
            try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
                final Txn transaction = pool.getTransactionManager().beginTransaction();
                task.run(broker, transaction);
                if (commit) {
                    transaction.commit();
                    transaction.close();
                } else {
                    broker.removeCurrentTransaction(transaction);
                }
            } catch (final Throwable t) {
                error[0] = t;
            }
        });
        thread.start();
        thread.join();
        if (error[0] != null) {
            throw new RuntimeException(error[0]);
        }
    }

    private void store(final boolean commit, final String name, final String xml) throws Exception {
        inThread(commit, (broker, transaction) -> {
            final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            broker.saveCollection(transaction, root);
            broker.storeDocument(transaction, XmldbURI.create(name), new StringInputSource(xml), MimeType.XML_TYPE, root);
        });
    }

    private void delete(final boolean commit, final String name) throws Exception {
        inThread(commit, (broker, transaction) -> {
            final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            final DocumentImpl doc = root.getDocument(broker, XmldbURI.create(name));
            if (doc != null) {
                root.removeResource(transaction, broker, doc);
            }
        });
    }

    private String snapshot() throws Exception {
        return run(existEmbeddedServer, "string-join(for $i in ('c1','c2','u1','d1','e1','ins') return $i || ':' || "
                + "count(for $d in collection('/db/test') return id($i, $d)), ',')");
    }

    private void crashAndRecover() throws Exception {
        existEmbeddedServer.getBrokerPool().getJournalManager().get().flush(true, false);
        BrokerPool.FORCE_CORRUPTION = true;
        existEmbeddedServer.restart();
        BrokerPool.FORCE_CORRUPTION = false;
    }

    @Test
    void committedAndUncommittedChanges() throws Exception {
        BrokerPool.FORCE_CORRUPTION = true;
        store(true, "s1.xml", "<r xml:id=\"c1\"><c xml:id=\"c2\"/></r>");
        store(false, "s2.xml", "<r xml:id=\"u1\"/>");
        store(true, "s3.xml", "<r xml:id=\"d1\"/>");
        delete(true, "s3.xml");
        store(true, "s4.xml", "<r xml:id=\"e1\"/>");
        delete(false, "s4.xml");
        run(existEmbeddedServer, "update insert <n xml:id=\"ins\"/> into doc('/db/test/s1.xml')/r");
        assertEquals("c1:1,c2:1,u1:1,d1:0,e1:0,ins:1", snapshot(),
                "before the crash the uncommitted store is visible and the uncommitted delete has taken effect");

        crashAndRecover();
        assertEquals("c1:1,c2:1,u1:0,d1:0,e1:1,ins:1", snapshot(),
                "after recovery: committed store and update replayed, uncommitted store undone, committed delete kept, uncommitted delete undone");

        existEmbeddedServer.restart();
        assertEquals("c1:1,c2:1,u1:0,d1:0,e1:1,ins:1", snapshot(), "and the same after another restart");
    }

    @Test
    void dtdDocumentStoredBeforeTheOtherChanges() throws Exception {
        BrokerPool.FORCE_CORRUPTION = true;
        store(true, "s5.xml", DTD);
        store(true, "s1.xml", "<r xml:id=\"c1\"/>");
        store(false, "s2.xml", "<r xml:id=\"u1\"/>");
        final String ids = "string-join(for $i in ('x','y','c1','u1') return $i || ':' || count(for $d in collection('/db/test') return id($i, $d)), ',')";
        assertEquals("x:1,y:1,c1:1,u1:1", run(existEmbeddedServer, ids));

        crashAndRecover();
        assertEquals("x:1,y:1,c1:1,u1:0", run(existEmbeddedServer, ids), "after recovery the committed DTD document is found by its IDs");
    }
}
