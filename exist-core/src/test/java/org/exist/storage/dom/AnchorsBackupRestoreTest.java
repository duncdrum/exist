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

import org.exist.TestUtils;
import org.exist.backup.Backup;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.IStoredNode;
import org.exist.dom.persistent.LockedDocument;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xmldb.AbstractRestoreServiceTaskListener;
import org.exist.xmldb.DatabaseImpl;
import org.exist.xmldb.EXistRestoreService;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A restore stores the documents of a backup again, so the restored document is cut into runs by anchors as a stored one is.
 */
public class AnchorsBackupRestoreTest {

    private static final int SCAN_LIMIT = 8;
    private static final int CHILDREN = 60;
    private static final String COLLECTION = "/db/anchors-backup";

    @RegisterExtension
    static final ExistEmbeddedServer SERVER = new ExistEmbeddedServer(DomTestDocuments.configuration(SCAN_LIMIT), true, true);

    @TempDir
    Path backupDir;

    @BeforeAll
    static void registerXmldbDatabaseDriver() throws XMLDBException {
        DatabaseManager.registerDatabase(new DatabaseImpl());
    }

    private static List<String> idsOf(final List<IStoredNode<?>> nodes) {
        return nodes.stream().map(node -> node.getNodeId().toString()).toList();
    }

    @Test
    void aRestoredDocumentIsCutIntoRunsAgain() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        final List<String> ids;
        final int docId;
        try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
            DomTestDocuments.store(pool, broker, COLLECTION, DomTestDocuments.wideDocument(CHILDREN));
            try (final LockedDocument locked = DomTestDocuments.open(broker, COLLECTION)) {
                final List<IStoredNode<?>> nodes = DomTestDocuments.nodesOf(locked.getDocument());
                assertTrue(DomTestDocuments.anchorsOf(broker, locked.getDocument(), nodes).size() > 0, "the stored document has anchors");
                ids = idsOf(nodes);
                docId = locked.getDocument().getDocId();
            }
        }

        new Backup(TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD, backupDir, XmldbURI.EMBEDDED_SERVER_URI.append(COLLECTION), new Properties(), false)
                .backup(false, null);
        final Path contents;
        try (final Stream<Path> files = Files.walk(backupDir)) {
            contents = files.filter(file -> file.getFileName().toString().equals("__contents__.xml")).findFirst().orElseThrow();
        }

        try (final DBBroker broker = DomTestDocuments.systemBroker(pool)) {
            DomTestDocuments.update(pool, broker, "xmldb:remove('" + COLLECTION + "')");
            assertNull(broker.getXMLResource(XmldbURI.create(COLLECTION + "/d.xml"), LockMode.READ_LOCK), "the document was removed");
            assertEquals(0, DomTestDocuments.entriesOfDocument(broker, docId), "the removal took the entries, so those found later come from the restore");
        }

        final List<String> errors = new ArrayList<>();
        final Collection root = DatabaseManager.getCollection(XmldbURI.EMBEDDED_SERVER_URI.append(XmldbURI.ROOT_COLLECTION_URI).toString(), TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        root.getService(EXistRestoreService.class).restore(contents.normalize().toAbsolutePath().toString(), null, new AbstractRestoreServiceTaskListener() {
            @Override
            public void info(final String message) {
            }

            @Override
            public void warn(final String message) {
            }

            @Override
            public void error(final String message) {
                errors.add(message);
            }
        }, false);
        assertTrue(errors.isEmpty(), "the restore reported errors: " + errors);

        try (final DBBroker broker = DomTestDocuments.systemBroker(pool);
             final LockedDocument locked = DomTestDocuments.open(broker, COLLECTION)) {
            final DocumentImpl doc = locked.getDocument();
            final List<IStoredNode<?>> nodes = DomTestDocuments.nodesOf(doc);
            assertEquals(ids, idsOf(nodes), "the restored document has the same nodes");
            assertTrue(DomTestDocuments.anchorsOf(broker, doc, nodes).size() > 0, "the restored document has anchors");
            assertTrue(DomTestDocuments.longestRun(broker, doc, nodes) <= SCAN_LIMIT, "no run is longer than the scan limit");
            assertTrue(DomTestDocuments.recordsReadPerLookup(broker, doc, nodes) > 0);
        }
    }
}
