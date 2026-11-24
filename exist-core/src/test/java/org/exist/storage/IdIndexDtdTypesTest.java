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
 * IDs and references declared by a DTD ({@code ID}, {@code IDREF}, {@code IDREFS}): found when the document is stored
 * and again after the nodes were read back from storage, which a collection copy and a reindex do. The type of an
 * attribute is part of the stored node, so it must survive that round trip.
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
    void idrefIsFoundAfterACollectionCopy() throws Exception {
        store("dtd-copy-source");
        run(existEmbeddedServer, "xmldb:create-collection('/db', 'dtd-copy-target')");
        run(existEmbeddedServer, "xmldb:copy-collection('/db/dtd-copy-source', '/db/dtd-copy-target')");
        assertEquals("x=2 y=1", idrefCounts(existEmbeddedServer, "/db/dtd-copy-source", "x", "y"), "the source keeps its references");
        assertEquals("x=1 y=1", idCounts(existEmbeddedServer, "/db/dtd-copy-target", "x", "y"), "the copy has the IDs");
        assertEquals("x=2 y=1", idrefCounts(existEmbeddedServer, "/db/dtd-copy-target", "x", "y"), "the copy has the references");
    }

    @Test
    void idrefIsFoundAfterAReindex() throws Exception {
        store("dtd-reindex");
        assertEquals("x=2 y=1", idrefCounts(existEmbeddedServer, "/db/dtd-reindex", "x", "y"));
        run(existEmbeddedServer, "xmldb:reindex('/db/dtd-reindex')");
        assertEquals("x=1 y=1", idCounts(existEmbeddedServer, "/db/dtd-reindex", "x", "y"), "the IDs are found");
        assertEquals("x=2 y=1", idrefCounts(existEmbeddedServer, "/db/dtd-reindex", "x", "y"), "and the references");
    }
}
