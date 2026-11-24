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
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;

import java.util.Optional;

/**
 * Helpers of the ID index characterisation tests: run a query as the system user and ask {@code fn:id} and
 * {@code fn:idref} over the documents of a collection.
 */
final class IdIndexTestSupport {

    static final String DTD = """
            <!DOCTYPE r [<!ELEMENT r (c*)><!ELEMENT c EMPTY><!ATTLIST c id ID #IMPLIED ref IDREF #IMPLIED refs IDREFS #IMPLIED>]>
            <r><c id="x"/><c id="y" ref="x" refs="x y"/></r>""";

    private IdIndexTestSupport() {
    }

    /** Runs the query (the xmldb module is imported) and joins the items with a space. */
    static String run(final ExistEmbeddedServer server, final String query) throws Exception {
        final BrokerPool pool = server.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final String prolog = "import module namespace xmldb = 'http://exist-db.org/xquery/xmldb'; ";
            final Sequence seq = xquery.execute(broker, xquery.compile(new XQueryContext(pool), prolog + query), null);
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < seq.getItemCount(); i++) {
                sb.append(i == 0 ? "" : " ").append(seq.itemAt(i).getStringValue());
            }
            return sb.toString();
        }
    }

    /** The number of elements {@code fn:id} finds for each value in the documents of the collection: {@code a=1 b=0}. */
    static String idCounts(final ExistEmbeddedServer server, final String collection, final String... values) throws Exception {
        return counts(server, "id", collection, values);
    }

    /** The number of nodes {@code fn:idref} finds for each value in the documents of the collection: {@code x=2}. */
    static String idrefCounts(final ExistEmbeddedServer server, final String collection, final String... values) throws Exception {
        return counts(server, "idref", collection, values);
    }

    private static String counts(final ExistEmbeddedServer server, final String function, final String collection,
            final String... values) throws Exception {
        final StringBuilder query = new StringBuilder("string-join((");
        for (int i = 0; i < values.length; i++) {
            query.append(i == 0 ? "" : ", ").append("'").append(values[i]).append("=' || count(for $d in collection('")
                    .append(collection).append("') return ").append(function).append("('").append(values[i]).append("', $d))");
        }
        return run(server, query.append("), ' ')").toString());
    }
}
