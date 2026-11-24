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

import org.exist.storage.journal.Journal;
import org.exist.test.ExistEmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.exist.storage.IdIndexTestSupport.DTD;
import static org.exist.storage.IdIndexTestSupport.idCounts;
import static org.exist.storage.IdIndexTestSupport.run;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What {@code fn:id} finds after the operations that move, replace, copy or remove documents and collections: document
 * replace, collection copy and move, resource removal, reindex, restart, and removal and re-creation of a collection
 * under the same name. The IDs are {@code xml:id} values (one with surrounding whitespace) and DTD-declared IDs.
 */
public class IdIndexMatrixTest {

    private static final Path DATA_DIR;

    private static final Properties PROPERTIES = new Properties();

    static {
        try {
            DATA_DIR = Files.createTempDirectory("id-matrix");
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        PROPERTIES.put(BrokerPool.PROPERTY_DATA_DIR, DATA_DIR);
        PROPERTIES.put(Journal.PROPERTY_RECOVERY_JOURNAL_DIR, DATA_DIR);
    }

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(PROPERTIES, true, false);

    private static final String PLAIN = "<r xml:id=\"a\"><c xml:id=\" b \"/><c xml:id=\"c\"/></r>";

    private static final String REPLACED = "<r xml:id=\"a2\"><c xml:id=\"b\"/><c xml:id=\"c2\"/></r>";

    private static String ids(final String collection) throws Exception {
        return idCounts(existEmbeddedServer, collection, "a", "b", "c", "a2", "c2", "x", "y");
    }

    @Test
    void matrix() throws Exception {
        run(existEmbeddedServer, "xmldb:create-collection('/db', 'm')");
        run(existEmbeddedServer, "xmldb:store('/db/m', 'd1.xml', '" + PLAIN + "', 'application/xml')");
        run(existEmbeddedServer, "xmldb:store('/db/m', 'd2.xml', '" + DTD.replace("'", "''") + "', 'application/xml')");
        assertEquals("a=1 b=1 c=1 a2=0 c2=0 x=1 y=1", ids("/db/m"), "stored; the whitespace of ' b ' is not part of the ID");

        run(existEmbeddedServer, "xmldb:store('/db/m', 'd1.xml', '" + REPLACED + "', 'application/xml')");
        assertEquals("a=0 b=1 c=0 a2=1 c2=1 x=1 y=1", ids("/db/m"), "document replaced: the old IDs are gone, the new ones found");

        run(existEmbeddedServer, "xmldb:create-collection('/db', 'm2')");
        run(existEmbeddedServer, "xmldb:copy-collection('/db/m', '/db/m2')");
        assertEquals("a=0 b=1 c=0 a2=1 c2=1 x=1 y=1", ids("/db/m"), "collection copied: the source is unchanged");
        assertEquals("a=0 b=1 c=0 a2=1 c2=1 x=1 y=1", ids("/db/m2"), "collection copied: the copy has the IDs");

        run(existEmbeddedServer, "xmldb:rename('/db/m2', 'm3')");
        assertEquals("a=0 b=1 c=0 a2=1 c2=1 x=1 y=1", ids("/db/m3"), "collection moved");

        run(existEmbeddedServer, "xmldb:remove('/db/m', 'd2.xml')");
        assertEquals("a=0 b=1 c=0 a2=1 c2=1 x=0 y=0", ids("/db/m"), "resource removed: its IDs are gone");

        run(existEmbeddedServer, "xmldb:reindex('/db/m')");
        assertEquals("a=0 b=1 c=0 a2=1 c2=1 x=0 y=0", ids("/db/m"), "reindexed");

        existEmbeddedServer.restart(false);
        assertEquals("a=0 b=1 c=0 a2=1 c2=1 x=0 y=0", ids("/db/m"), "after a restart");
        assertEquals("a=0 b=1 c=0 a2=1 c2=1 x=1 y=1", ids("/db/m3"), "after a restart, the moved collection");

        run(existEmbeddedServer, "xmldb:remove('/db/m3')");
        run(existEmbeddedServer, "xmldb:create-collection('/db', 'm3')");
        run(existEmbeddedServer, "xmldb:store('/db/m3', 'e.xml', '<r xml:id=\"z\"/>', 'application/xml')");
        assertEquals("a=0 b=0 c=0 a2=0 c2=0 x=0 y=0", ids("/db/m3"), "collection removed and created again: no ID of the old one");
        assertEquals("z=1 a2=0", idCounts(existEmbeddedServer, "/db/m3", "z", "a2"), "the new collection has its own ID only");
    }
}
