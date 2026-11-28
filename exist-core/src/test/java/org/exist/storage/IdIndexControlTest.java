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
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Optional;

import static org.exist.storage.IdIndexTestSupport.run;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The ID entries of the value index are the only way {@code fn:id}, {@code fn:idref} and {@code fn:element-with-id}
 * find an element in a stored document: this is the control of the other ID index tests. Without the entries the
 * functions find nothing, while the attribute is still a node.
 */
public class IdIndexControlTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String DOC = "doc('/db/test/idctl.xml')";

    private static final String XML = """
            <r xml:id="a">
                <c xml:id="b">x</c>
                <c xml:id="c" ref="b"/>
            </r>""";

    private static final String BY_ID = "count(id('a', " + DOC + "))";
    private static final String BY_ELEMENT_WITH_ID = "count(element-with-id('b', " + DOC + "))";
    private static final String BY_ATTRIBUTE = "count(" + DOC + "//*[@xml:id = 'a'])";

    @Test
    void functionsHaveNoFallbackWithoutTheIdEntries() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction();
                final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI)) {
            broker.saveCollection(transaction, root);
            broker.storeDocument(transaction, XmldbURI.create("idctl.xml"), new StringInputSource(XML), MimeType.XML_TYPE, root);
            transact.commit(transaction);
        }

        assertEquals("1", run(existEmbeddedServer, BY_ID), "with the entries, fn:id finds the element");
        assertEquals("1", run(existEmbeddedServer, BY_ELEMENT_WITH_ID), "with the entries, fn:element-with-id finds it");
        assertEquals("1", run(existEmbeddedServer, BY_ATTRIBUTE), "the attribute is a node");

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Collection collection = broker.openCollection(TestConstants.TEST_COLLECTION_URI, LockMode.WRITE_LOCK)) {
            broker.getValueIndex().dropIndex(collection);
        }

        assertEquals("0", run(existEmbeddedServer, BY_ID), "without the entries, fn:id finds nothing");
        assertEquals("0", run(existEmbeddedServer, BY_ELEMENT_WITH_ID), "without the entries, fn:element-with-id finds nothing");
        assertEquals("1", run(existEmbeddedServer, BY_ATTRIBUTE), "the node is still there");
    }
}
