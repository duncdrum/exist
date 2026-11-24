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

import org.exist.TestUtils;
import org.exist.backup.Backup;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xmldb.DatabaseImpl;
import org.exist.xmldb.EXistRestoreService;
import org.exist.xmldb.NullRestoreServiceTaskListener;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.xmldb.api.DatabaseManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.exist.storage.IdIndexTestSupport.DTD;
import static org.exist.storage.IdIndexTestSupport.idCounts;
import static org.exist.storage.IdIndexTestSupport.idrefCounts;
import static org.exist.storage.IdIndexTestSupport.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IDs of a collection that is backed up, removed and restored. {@code xml:id} values are found again. The IDs and
 * references declared by a DTD are not (a behaviour that looks like a defect and is pinned as it is: the DTD-derived
 * type of an attribute is not part of what is backed up, and a restored document is not typed again).
 */
public class IdIndexBackupRestoreTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @TempDir
    static Path temporaryFolder;

    private static String before;
    private static String afterRemoval;
    private static String afterRestoreXmlId;
    private static String afterRestoreDtdIds;
    private static String afterRestoreDtdRefs;
    private static String afterRestoreDocuments;

    @BeforeAll
    static void backupRemoveRestore() throws Exception {
        DatabaseManager.registerDatabase(new DatabaseImpl());
        run(existEmbeddedServer, "xmldb:create-collection('/db', 'bk')");
        run(existEmbeddedServer, "xmldb:store('/db/bk', 'p1.xml', '<r xml:id=\"b1\"><c xml:id=\"b2\"/></r>', 'application/xml')");
        run(existEmbeddedServer, "xmldb:store('/db/bk', 'p2.xml', '" + DTD.replace("'", "''") + "', 'application/xml')");
        before = idCounts(existEmbeddedServer, "/db/bk", "b1", "b2", "x", "y") + " refs " + idrefCounts(existEmbeddedServer, "/db/bk", "x");

        final Path backupDir = Files.createDirectories(temporaryFolder.resolve("backup"));
        new Backup(TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD, backupDir, XmldbURI.EMBEDDED_SERVER_URI.append("/db/bk"), new Properties(), false)
                .backup(false, null);
        final Path contents = backupDir.resolve("db").resolve("bk").resolve("__contents__.xml");
        assertTrue(Files.exists(contents), "the backup was written");

        run(existEmbeddedServer, "xmldb:remove('/db/bk')");
        run(existEmbeddedServer, "xmldb:create-collection('/db', 'bk')");
        afterRemoval = idCounts(existEmbeddedServer, "/db/bk", "b1", "b2", "x", "y");

        final org.xmldb.api.base.Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        root.getService(EXistRestoreService.class).restore(contents.toAbsolutePath().toString(), null, new NullRestoreServiceTaskListener(), false);
        afterRestoreXmlId = idCounts(existEmbeddedServer, "/db/bk", "b1", "b2");
        afterRestoreDtdIds = idCounts(existEmbeddedServer, "/db/bk", "x", "y");
        afterRestoreDtdRefs = idrefCounts(existEmbeddedServer, "/db/bk", "x");
        afterRestoreDocuments = run(existEmbeddedServer, "count(collection('/db/bk'))");
    }

    @Test
    void controlBeforeBackupAndAfterRemoval() {
        assertEquals("b1=1 b2=1 x=1 y=1 refs x=2", before, "all IDs are found before the backup");
        assertEquals("b1=0 b2=0 x=0 y=0", afterRemoval, "none after the collection was removed");
    }

    @Test
    void xmlIdsSurviveBackupAndRestore() {
        assertEquals("2", afterRestoreDocuments, "both documents are back");
        assertEquals("b1=1 b2=1", afterRestoreXmlId);
    }

    @Test
    void dtdDeclaredIdsAreLostOnBackupAndRestore() {
        assertEquals("x=0 y=0", afterRestoreDtdIds, "the IDs declared by the DTD are not found after a restore");
        assertEquals("x=0", afterRestoreDtdRefs, "nor the references");
    }
}
