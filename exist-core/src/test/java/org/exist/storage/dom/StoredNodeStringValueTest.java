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

import java.util.Optional;

import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.IStoredNode;
import org.exist.dom.persistent.LockedDocument;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The string value of a stored node, which {@code DOMFile.getNodeValue} writes from the records, is that of the same
 * document held in memory, for every kind of node, for values that fill a page and more (overflow pages), for documents
 * whose records were moved by page splits, and for the extra whitespace that is added between children on request.
 *
 * <p>The in-memory copy is the serialised stored document parsed again, so the two are made independently of each other.
 * CDATA sections are left out of that comparison, as the two models merge adjacent text differently; they are checked
 * against the string that is known.</p>
 */
public class StoredNodeStringValueTest {

    @RegisterExtension
    static final ExistEmbeddedServer SERVER = new ExistEmbeddedServer(true, true);

    /**
     * The nodes below the document node of the stored document and of its copy, each as its kind, its name and its string value,
     * sorted so that the order of the nodes in the two models does not matter: the number of nodes of each, the number of
     * positions at which the sorted lists differ, and the longest string value. The document node itself is left out; its
     * string value is worked out from the document, not from a record.
     */
    private static final String COMPARE = """
            let $p := doc('%s/d.xml')
            let $m := parse-xml(serialize($p, map { 'method': 'xml', 'indent': false(), 'omit-xml-declaration': true() }))
            let $key := function($n as node()) as xs:string {
                (if ($n instance of attribute()) then 'attribute:' || name($n)
                 else if ($n instance of element()) then 'element:' || name($n)
                 else if ($n instance of comment()) then 'comment'
                 else if ($n instance of processing-instruction()) then 'pi:' || name($n)
                 else 'text') || '=' || string($n) }
            let $a := sort(($p//node() | $p//@*) ! $key(.))
            let $b := sort(($m//node() | $m//@*) ! $key(.))
            return string-join((count($a), count($b), count(for $i in 1 to min((count($a), count($b))) where $a[$i] ne $b[$i] return $i),
                max(($p//node() | $p//@*) ! string-length(string(.)))), '|')
            """;

    private static DBBroker broker() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        return pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
    }

    private static String run(final DBBroker broker, final String query) throws Exception {
        final var result = SERVER.getBrokerPool().getXQueryService().execute(broker, query, null);
        return result.getItemCount() == 0 ? "" : result.itemAt(0).getStringValue();
    }

    /** Stores the document as {@code d.xml} in a new collection; the XML is parsed as it is, so CDATA sections stay CDATA sections. */
    private static void store(final DBBroker broker, final String collection, final String xml) throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final Txn txn = pool.getTransactionManager().beginTransaction()) {
            final Collection col = broker.getOrCreateCollection(txn, XmldbURI.create(collection));
            broker.saveCollection(txn, col);
            broker.storeDocument(txn, XmldbURI.create("d.xml"), new StringInputSource(xml), MimeType.XML_TYPE, col);
            txn.commit();
        }
    }

    private static String repeat(final String unit, final int length) {
        final StringBuilder out = new StringBuilder();
        while (out.length() < length) {
            out.append(unit);
        }
        return out.substring(0, length);
    }

    private static void assertCompare(final DBBroker broker, final String name, final String collection, final int atLeastNodes,
            final int atLeastLength) throws Exception {
        final String[] result = run(broker, COMPARE.formatted(collection)).split("\\|");
        final int stored = Integer.parseInt(result[0]);
        assertEquals(stored, Integer.parseInt(result[1]), name + ": the stored document and its copy have the same nodes");
        assertTrue(stored >= atLeastNodes, name + ": " + stored + " nodes were compared, at least " + atLeastNodes);
        assertEquals(0, Integer.parseInt(result[2]), name + ": nodes whose string value differs from the copy");
        assertTrue(Integer.parseInt(result[3]) >= atLeastLength, name + ": the longest string value has " + result[3] + " characters, at least " + atLeastLength);
    }

    private static void assertSameAsInMemory(final String name, final String xml, final int atLeastNodes, final int atLeastLength) throws Exception {
        try (final DBBroker broker = broker()) {
            final String collection = "/db/sv-" + name;
            store(broker, collection, xml);
            assertCompare(broker, name, collection, atLeastNodes, atLeastLength);
        }
    }

    /** A document with many children below one element, each an element with an attribute, a text and a comment. */
    private static String wideDocument(final int children) {
        final StringBuilder xml = new StringBuilder("<r a=\"1\"><b><c>");
        for (int i = 0; i < children; i++) {
            xml.append("<x p=\"").append(i).append("\">t").append(i).append("<!--k").append(i).append("--></x>");
        }
        return xml.append("</c></b></r>").toString();
    }

    /** Nested elements with an attribute, a comment, text and a processing instruction at each level, text after each. */
    private static String deepDocument(final int levels) {
        final StringBuilder xml = new StringBuilder("<!--before--><?before x?>");
        for (int l = 1; l <= levels; l++) {
            xml.append("<l").append(l).append(" a=\"").append(l).append("\"><!--c").append(l).append("-->t").append(l)
                    .append("<?pi").append(l).append(" x?>");
        }
        for (int l = levels; l >= 1; l--) {
            xml.append("u").append(l).append("</l").append(l).append(">");
        }
        return xml.append("<!--after--><?after x?>").toString();
    }

    @Test
    void everyKindOfNodeHasTheStringValueItHasInMemory() throws Exception {
        assertSameAsInMemory("kinds",
                "<!--before--><?before head?><r a=\"1\" xmlns:p=\"urn:p\" p:b=\"2\"><!--a comment--><?pi some data?>t1<e>t2<f>t3</f>t4</e>t5<g/> <h> padded </h></r><!--after-->",
                18, 10);
    }

    @Test
    void valuesThatFillAPageAndMoreAreTheSameAsInMemory() throws Exception {
        final String text = repeat("abcdefghij", 9_000);
        final String attribute = repeat("0123456789", 600);
        final String comment = repeat("comment ", 700);
        final String pi = repeat("data ", 1_100);
        assertSameAsInMemory("overflow",
                "<r a=\"" + attribute + "\"><!--" + comment + "--><?pi " + pi + "?><big>" + text + "</big><small>s</small>" + text + "</r>",
                8, 18_000);
    }

    @Test
    void aWideDocumentThatPageSplitsMovedIsTheSameAsInMemory() throws Exception {
        try (final DBBroker broker = broker()) {
            final String collection = "/db/sv-split";
            store(broker, collection, wideDocument(60));
            // each insert is too large for the room left on its page, so pages are split and records are moved
            for (int i = 0; i < 6; i++) {
                run(broker, "update insert <y n=\"" + i + "\">{string-join((1 to 70) ! 'abcdefghi', '')}</y> preceding doc('" + collection + "/d.xml')/r/b/c/x[5]");
            }
            try (final LockedDocument locked = broker.getXMLResource(XmldbURI.create(collection + "/d.xml"), LockMode.READ_LOCK)) {
                assertTrue(locked.getDocument().getSplitCount() > 0, "pages were split");
            }
            assertCompare(broker, "split", collection, 250, 600);
        }
    }

    @Test
    void aDeepDocumentIsTheSameAsInMemory() throws Exception {
        assertSameAsInMemory("deep", deepDocument(9), 40, 20);
    }

    @Test
    void aCdataSectionIsPartOfTheStringValueOfItsParents() throws Exception {
        try (final DBBroker broker = broker()) {
            store(broker, "/db/sv-cdata", "<r>a<e>b<![CDATA[<c&d>]]>e<f>f</f></e></r>");
            assertEquals("ab<c&d>ef", run(broker, "string(doc('/db/sv-cdata/d.xml'))"));
            assertEquals("ab<c&d>ef", run(broker, "string(doc('/db/sv-cdata/d.xml')/r)"));
            assertEquals("b<c&d>ef", run(broker, "string(doc('/db/sv-cdata/d.xml')/r/e)"));
        }
    }

    @Test
    void whitespaceIsAddedAfterEachChildOnlyWhenAsked() throws Exception {
        try (final DBBroker broker = broker()) {
            store(broker, "/db/sv-space", "<r z=\"9\"><a>1</a><b><c>2</c><d>3</d></b>t</r>");
            try (final LockedDocument locked = broker.getXMLResource(XmldbURI.create("/db/sv-space/d.xml"), LockMode.READ_LOCK)) {
                final DocumentImpl doc = locked.getDocument();
                final IStoredNode<?> root = (IStoredNode<?>) doc.getDocumentElement();
                final DOMFile domFile = ((NativeBroker) broker).getDOMFile();
                assertEquals("123t", domFile.getNodeValue(broker, root, false));
                // a space follows each child record, an attribute too, at every element with more than one child that is not an
                // attribute: the attribute z of r, then a, then b with its own two children, then the text
                assertEquals(" 1 2 3  t ", domFile.getNodeValue(broker, root, true));
            }
        }
    }
}
