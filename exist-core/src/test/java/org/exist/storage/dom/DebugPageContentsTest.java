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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.exist.collections.Collection;
import org.exist.dom.persistent.IStoredNode;
import org.exist.dom.persistent.LockedDocument;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.StorageAddress;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.ManagedLock;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.exist.storage.btree.Paged.Page.NO_PAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code DOMFile.debugPageContents} describes the records of a page for the log and for error messages: where each record
 * starts, its tuple id, whether it is a link or was relocated, and what it holds. The output is pinned as it is, quirks
 * included, so that cleaning up the method cannot change what an error message says.
 *
 * <p>A document starts on a page of its own, so the offsets, tuple ids and lengths of a small document do not depend on
 * what else is in the database; only the number of the page does, and it is cut off before the comparison.</p>
 */
public class DebugPageContentsTest {

    @RegisterExtension
    static final ExistEmbeddedServer SERVER = new ExistEmbeddedServer(true, true);

    private static DBBroker broker() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        return pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
    }

    private static void store(final DBBroker broker, final String collection, final String xml) throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final Txn txn = pool.getTransactionManager().beginTransaction()) {
            final Collection col = broker.getOrCreateCollection(txn, XmldbURI.create(collection));
            broker.saveCollection(txn, col);
            broker.storeDocument(txn, XmldbURI.create("d.xml"), new StringInputSource(xml), MimeType.XML_TYPE, col);
            txn.commit();
        }
    }

    /** The description of each page of a document, from the page of its root element along the chain of pages. */
    private static List<String> describePages(final DBBroker broker, final String collection) throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        final DOMFile domFile = ((NativeBroker) broker).getDOMFile();
        final List<String> pages = new ArrayList<>();
        try (final LockedDocument locked = broker.getXMLResource(XmldbURI.create(collection + "/d.xml"), LockMode.READ_LOCK);
                final ManagedLock<?> lock = pool.getLockManager().acquireBtreeReadLock(domFile.getLockName())) {
            long pageNum = StorageAddress.pageFromPointer(((IStoredNode<?>) locked.getDocument().getDocumentElement()).getInternalAddress());
            while (pageNum != NO_PAGE && pages.size() < 50) {
                final DOMFile.DOMPage page = domFile.getDOMPage(pageNum);
                pages.add(domFile.debugPageContents(page));
                pageNum = page.getPageHeader().getNextDataPage();
            }
        }
        return pages;
    }

    private static String withoutPageNumber(final String description) {
        final Matcher matcher = Pattern.compile("^Page \\d+: ").matcher(description);
        assertTrue(matcher.find(), "the description starts with the page number: " + description);
        return description.substring(matcher.end());
    }

    @Test
    void everyKindOfRecordIsDescribedAsItIs() throws Exception {
        try (final DBBroker broker = broker()) {
            store(broker, "/db/dbg-kinds",
                    "<r a=\"1\" xmlns:p=\"urn:p\" p:b=\"two\"><!--c--><?pi d?>short<e>abcdefghijklmnopqrst</e><![CDATA[cdata value that is long]]><f/></r>");
            final List<String> pages = describePages(broker, "/db/dbg-kinds");
            assertEquals(1, pages.size(), "the document is on one page");
            // the element with its two attributes and its children; an attribute that has a namespace takes a branch that cannot
            // read the node id (and says so); a comment and a processing instruction are not described; a text of more than 15
            // characters is cut to its first and last 8, a CDATA section too
            assertEquals("0/0[element (1) children: 8 attributes: 2] 22/1[[attribute (1.1):'1'] ] "
                    + "33/2[[attribute (1.2)(unable to read the node ID at : 48] ] 51/3[Unknown node type !] 61/4[Unknown node type !] "
                    + "77/5[text (1.5):'short'] 91/6[element (1.6) children: 1 attributes: 0] 107/7[text (1.6.1):'abcdefgh...mnopqrst'] "
                    + "136/8[CDATA (1.7):'cdata va... is long'] 169/9[element (1.8) children: 0 attributes: 0] "
                    + "; records in page: 10 (header says: 10); currentTupleID: 9; data length: 185 (last non-zero byte: 185)",
                    withoutPageNumber(pages.get(0)));
        }
    }

    @Test
    void aValueThatDoesNotFitOnItsPageIsNotDecoded() throws Exception {
        try (final DBBroker broker = broker()) {
            store(broker, "/db/dbg-overflow", "<r><x>" + "y".repeat(5000) + "</x><z>after</z></r>");
            final String description = withoutPageNumber(describePages(broker, "/db/dbg-overflow").get(0));
            // the record holds the address of an overflow page, not the text, so what follows it is read as records that are not
            assertTrue(description.contains("0/0[element (1) children: 2 attributes: 0] 15/1[element (1.1) children: 1 attributes: 0] "), description);
            assertTrue(description.contains("(unable to read the node ID at : "), description);
            assertTrue(description.contains("; records in page: 5 (header says: 5); currentTupleID: 4; "), description);
        }
    }

    @Test
    void relocatedRecordsAndLinksAfterPageSplitsAreMarked() throws Exception {
        try (final DBBroker broker = broker()) {
            final StringBuilder xml = new StringBuilder("<r><b><c>");
            for (int i = 0; i < 60; i++) {
                xml.append("<x p=\"").append(i).append("\">t").append(i).append("</x>");
            }
            store(broker, "/db/dbg-split", xml.append("</c></b></r>").toString());
            for (int i = 0; i < 6; i++) {
                SERVER.getBrokerPool().getXQueryService().execute(broker,
                        "update insert <y>{string-join((1 to 70) ! 'abcdefghi', '')}</y> preceding doc('/db/dbg-split/d.xml')/r/b/c/x[5]", null);
            }
            final List<String> pages = describePages(broker, "/db/dbg-split");
            assertTrue(pages.size() >= 3, "the document is on several pages: " + pages.size());
            final Pattern relocated = Pattern.compile("\\d+/\\d+R");
            final Pattern link = Pattern.compile("\\d+/\\d+L:\\d+ ");
            final Pattern footer = Pattern.compile("; records in page: (\\d+) \\(header says: (\\d+)\\); currentTupleID: \\d+; data length: \\d+");
            boolean anyRelocated = false;
            boolean anyLink = false;
            for (final String page : pages) {
                anyRelocated |= relocated.matcher(page).find();
                anyLink |= link.matcher(page).find();
                final Matcher matcher = footer.matcher(page);
                assertTrue(matcher.find(), "each page ends with its counts: " + page);
                assertEquals(matcher.group(1), matcher.group(2), "the records counted in the page are the records the header says: " + page);
                assertTrue(page.contains("[element (") || page.contains("[text (") || relocated.matcher(page).find() || link.matcher(page).find(),
                        "a page describes its records: " + page);
            }
            assertTrue(anyRelocated, "a record that was moved is marked R on the page it was moved from");
            assertTrue(anyLink, "a forward link is marked L with the address it leads to");
        }
    }
}
