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
 * {@code fn:id} sees an ID change made by an XUpdate expression inside the same query and in the queries after it.
 */
public class IdIndexUpdateVisibilityTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String XML = """
            <r xml:id="a">
                <c xml:id="b">x</c>
                <c xml:id="c"/>
            </r>""";

    private static void store(final String name) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction();
                final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI)) {
            broker.saveCollection(transaction, root);
            broker.storeDocument(transaction, XmldbURI.create(name), new StringInputSource(XML), MimeType.XML_TYPE, root);
            transact.commit(transaction);
        }
    }

    private static String doc(final String name) {
        return "doc('/db/test/" + name + "')";
    }

    private static String count(final String id, final String doc) {
        return "string(count(id('" + id + "', " + doc + ")))";
    }

    @Test
    void insert() throws Exception {
        store("iv1.xml");
        final String d = doc("iv1.xml");
        assertEquals("1", run(existEmbeddedServer, "let $u := update insert <n xml:id=\"z\"/> into " + d + "/r return " + count("z", d)),
                "the inserted ID is found in the same query");
        assertEquals("1", run(existEmbeddedServer, count("z", d)), "and in the next query");
        assertEquals("1 1 1", run(existEmbeddedServer, "(" + count("a", d) + ", " + count("b", d) + ", " + count("c", d) + ")"),
                "the IDs that were there stay");
    }

    @Test
    void changeValue() throws Exception {
        store("iv2.xml");
        final String d = doc("iv2.xml");
        assertEquals("0 1", run(existEmbeddedServer, "let $u := update value " + d + "/r/@xml:id with 'a2' return (" + count("a", d) + ", " + count("a2", d) + ")"),
                "the old value is gone and the new one found in the same query");
        assertEquals("0 1", run(existEmbeddedServer, "(" + count("a", d) + ", " + count("a2", d) + ")"), "the same in the next query");
    }

    @Test
    void delete() throws Exception {
        store("iv3.xml");
        final String d = doc("iv3.xml");
        assertEquals("0", run(existEmbeddedServer, "let $u := update delete " + d + "//c[@xml:id = 'b'] return " + count("b", d)),
                "the deleted ID is gone in the same query");
        assertEquals("0 1", run(existEmbeddedServer, "(" + count("b", d) + ", " + count("c", d) + ")"),
                "the deleted ID stays gone, its sibling is untouched");
    }

    @Test
    void replace() throws Exception {
        store("iv4.xml");
        final String d = doc("iv4.xml");
        assertEquals("0 1", run(existEmbeddedServer, "let $u := update replace " + d + "//c[@xml:id = 'b'] with <c xml:id=\"b9\"/> return ("
                + count("b", d) + ", " + count("b9", d) + ")"), "the replaced ID is gone and the new one found in the same query");
        assertEquals("0 1", run(existEmbeddedServer, "(" + count("b", d) + ", " + count("b9", d) + ")"), "the same in the next query");
    }
}
