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

import org.exist.test.ExistEmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.exist.storage.IdIndexTestSupport.DTD;
import static org.exist.storage.IdIndexTestSupport.idCounts;
import static org.exist.storage.IdIndexTestSupport.idrefCounts;
import static org.exist.storage.IdIndexTestSupport.run;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * IDs and references declared by a DTD ({@code ID}, {@code IDREF}, {@code IDREFS}).
 * <p>
 * Two tests describe behaviour that looks like a defect and is pinned as it is, so that a change to the ID index stays
 * behaviour-preserving: an {@code IDREF} is not found after a collection copy or a reindex. The DTD-derived type of an
 * attribute is not part of what the database stores; the attribute is typed again only while its document is stored.
 */
public class IdIndexDtdTypesTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static void store(final String collection) throws Exception {
        run(existEmbeddedServer, "xmldb:create-collection('/db', '" + collection + "')");
        run(existEmbeddedServer, "xmldb:store('/db/" + collection + "', 'dtd.xml', '" + DTD.replace("'", "''") + "', 'application/xml')");
    }

    @Test
    void idAndIdrefOfAStoredDocument() throws Exception {
        store("dtd-stored");
        assertEquals("x=1 y=1", idCounts(existEmbeddedServer, "/db/dtd-stored", "x", "y"));
        assertEquals("x=2 y=1", idrefCounts(existEmbeddedServer, "/db/dtd-stored", "x", "y"),
                "x is referenced by the IDREF and by the IDREFS of c y, y by the IDREFS only");
    }

    @Test
    void idrefIsNotFoundAfterACollectionCopy() throws Exception {
        store("dtd-copy-source");
        run(existEmbeddedServer, "xmldb:create-collection('/db', 'dtd-copy-target')");
        run(existEmbeddedServer, "xmldb:copy-collection('/db/dtd-copy-source', '/db/dtd-copy-target')");
        assertEquals("x=2 y=1", idrefCounts(existEmbeddedServer, "/db/dtd-copy-source", "x", "y"), "the source keeps its references");
        assertEquals("x=1 y=1", idCounts(existEmbeddedServer, "/db/dtd-copy-target", "x", "y"), "the copy has the IDs");
        assertEquals("x=0 y=0", idrefCounts(existEmbeddedServer, "/db/dtd-copy-target", "x", "y"), "the copy has no references");
    }

    @Test
    void idrefIsNotFoundAfterAReindex() throws Exception {
        store("dtd-reindex");
        assertEquals("x=2 y=1", idrefCounts(existEmbeddedServer, "/db/dtd-reindex", "x", "y"));
        run(existEmbeddedServer, "xmldb:reindex('/db/dtd-reindex')");
        assertEquals("x=1 y=1", idCounts(existEmbeddedServer, "/db/dtd-reindex", "x", "y"), "the IDs are found again");
        assertEquals("x=0 y=0", idrefCounts(existEmbeddedServer, "/db/dtd-reindex", "x", "y"), "the references are not");
    }
}
