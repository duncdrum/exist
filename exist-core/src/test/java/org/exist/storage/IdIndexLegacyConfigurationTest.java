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
import static org.exist.storage.IdIndexTestSupport.run;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * IDs in a database whose collections also configure the legacy value indexes ({@code <create qname=...>} and
 * {@code <create path=...>}): the IDs share the value index with the configured entries, so they are found next to
 * them, and stay separate per collection when a collection is removed and created again under the same name.
 */
public class IdIndexLegacyConfigurationTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String CONFIG = "<collection xmlns='http://exist-db.org/collection-config/1.0'><index>"
            + "<create qname='item' type='xs:string'/><create path='//item/@k' type='xs:string'/></index></collection>";

    private static final String DTD_ITEMS = "<!DOCTYPE r [<!ELEMENT r (item*)><!ELEMENT item (#PCDATA)>"
            + "<!ATTLIST item id ID #IMPLIED ref IDREF #IMPLIED k CDATA #IMPLIED>]>"
            + "<r><item id='x' k='k1'>v1</item><item id='y' ref='x' k='k2'>v2</item></r>";

    private static String snapshot() throws Exception {
        return idCounts(existEmbeddedServer, "/db/lg", "a", "b", "c", "x", "y", "n7") + " "
                + run(existEmbeddedServer, "'q=' || count(collection('/db/lg')//item[. = 'v3']) || ' p=' || count(collection('/db/lg')//item[@k = 'k4'])"
                + " || ' lg2=' || count(collection('/db/lg2'))");
    }

    @Test
    void idsNextToConfiguredIndexes() throws Exception {
        for (final String name : new String[] {"lg", "lg2"}) {
            run(existEmbeddedServer, "xmldb:create-collection('/db/system/config/db', '" + name + "'), xmldb:store('/db/system/config/db/" + name
                    + "', 'collection.xconf', \"" + CONFIG + "\", 'application/xml')");
        }
        run(existEmbeddedServer, "xmldb:create-collection('/db', 'lg'), xmldb:create-collection('/db', 'lg2')");
        run(existEmbeddedServer, "xmldb:store('/db/lg', 'd1.xml', '<r xml:id=\"a\"><item xml:id=\"b\" k=\"k3\">v3</item><item xml:id=\"c\" k=\"k4\">v4</item></r>', 'application/xml')");
        run(existEmbeddedServer, "xmldb:store('/db/lg', 'd2.xml', '" + DTD_ITEMS.replace("'", "''") + "', 'application/xml')");
        run(existEmbeddedServer, "xmldb:store('/db/lg2', 'e1.xml', '<r xml:id=\"n7\"><item k=\"k9\">v9</item></r>', 'application/xml')");

        assertEquals("a=1 b=1 c=1 x=1 y=1 n7=0 q=1 p=1 lg2=1", snapshot(), "IDs and the configured comparisons answer");
        assertEquals("1", run(existEmbeddedServer, "count(for $d in collection('/db/lg2') return id('n7', $d))"), "the other collection has its own ID");

        run(existEmbeddedServer, "xmldb:reindex('/db/lg')");
        assertEquals("a=1 b=1 c=1 x=1 y=1 n7=0 q=1 p=1 lg2=1", snapshot(), "reindexed");

        run(existEmbeddedServer, "xmldb:remove('/db/lg2')");
        run(existEmbeddedServer, "xmldb:create-collection('/db', 'lg2')");
        run(existEmbeddedServer, "xmldb:store('/db/lg2', 'f1.xml', '<r xml:id=\"n8\"><item k=\"k8\">v8</item></r>', 'application/xml')");
        assertEquals("n8=1 n7=0", idCounts(existEmbeddedServer, "/db/lg2", "n8", "n7"),
                "removed and created again under the same name: the new ID only, none of the old one");
    }
}
