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
package org.exist.indexing.range;

import org.exist.collections.Collection;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.QName;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.DateValue;
import org.exist.xquery.value.DecimalValue;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.FloatValue;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.TimeValue;
import org.exist.xquery.value.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the Lucene query that {@link RangeIndexWorker#toQuery} builds for every index type, key and operator, as a golden
 * file, so that the way a key becomes a query can be restructured without changing one of them. Regenerate with
 * {@code -Dkey-queries.write=true} and review the diff.
 */
class RangeKeyQueryCharacterizationTest {

    @RegisterExtension
    static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String COLLECTION_NAME = "range-key-queries";

    private static final Path GOLDEN = Path.of("src/test/resources/org/exist/indexing/range/key-queries.golden");

    private static final String COLLECTION_CONFIG = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
                <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <range>
                        <create qname="kstring" type="xs:string"/>
                        <create qname="kcase" type="xs:string" case="no"/>
                        <create qname="kinteger" type="xs:integer"/>
                        <create qname="klong" type="xs:long"/>
                        <create qname="kint" type="xs:int"/>
                        <create qname="kshort" type="xs:short"/>
                        <create qname="kdecimal" type="xs:decimal"/>
                        <create qname="kdouble" type="xs:double"/>
                        <create qname="kfloat" type="xs:float"/>
                        <create qname="kdate" type="xs:date"/>
                        <create qname="ktime" type="xs:time"/>
                        <create qname="kdatetime" type="xs:dateTime"/>
                        <create qname="kconvstring" type="xs:string" converter="org.exist.indexing.range.conversion.LowerCaseTestConverter"/>
                        <create qname="kconvdate" type="xs:date" converter="org.exist.indexing.range.conversion.DateConverter"/>
                    </range>
                </index>
            </collection>""";

    private static final String DATA = "<t><kstring>a</kstring></t>";

    private record Case(String name, List<AtomicValue> keys) {
    }

    @Test
    void theQueryOfEveryTypeKeyAndOperatorIsUnchanged() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        final List<String> lines = new ArrayList<>();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final DocumentSet docs;
            try (final Txn transaction = transact.beginTransaction()) {
                final Collection collection = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append(COLLECTION_NAME));
                broker.saveCollection(transaction, collection);
                pool.getConfigurationManager().addConfiguration(transaction, broker, collection, COLLECTION_CONFIG);
                broker.storeDocument(transaction, XmldbURI.create("test.xml"), new StringInputSource(DATA), MimeType.XML_TYPE, collection);
                transact.commit(transaction);
            }
            final Collection collection = broker.getCollection(XmldbURI.ROOT_COLLECTION_URI.append(COLLECTION_NAME));
            docs = collection.allDocs(broker, new DefaultDocumentSet(), false);
            final RangeIndexWorker worker = (RangeIndexWorker) broker.getIndexController().getWorkerByIndexId(RangeIndex.ID);

            for (final Case c : cases()) {
                final QName qname = new QName(c.name());
                for (final AtomicValue key : c.keys()) {
                    for (final RangeIndex.Operator operator : RangeIndex.Operator.values()) {
                        lines.add(c.name() + " | " + Type.getTypeName(key.getType()) + " " + key.getStringValue() + " | " + operator
                                + " | " + describe(worker, c.name(), qname, key, operator, docs));
                    }
                }
            }
        }
        final String actual = String.join("\n", lines) + "\n";
        if (Boolean.getBoolean("key-queries.write")) {
            Files.createDirectories(GOLDEN.getParent());
            Files.writeString(GOLDEN, actual, StandardCharsets.UTF_8);
        }
        assertEquals(Files.readString(GOLDEN, StandardCharsets.UTF_8), actual);
    }

    private static String describe(final RangeIndexWorker worker, final String field, final QName qname, final AtomicValue key,
            final RangeIndex.Operator operator, final DocumentSet docs) {
        try {
            return worker.toQuery(field, qname, key, operator, docs).toString().replace("\n", " ");
        } catch (final Exception e) {
            return "ERR " + e.getClass().getSimpleName();
        }
    }

    private static List<Case> cases() throws Exception {
        return List.of(
                new Case("kstring", List.of(new StringValue("Foo"), new StringValue("foo bar"), new StringValue(""), new StringValue("fo.*"),
                        new StringValue("a*b"), new StringValue("a?b"), new StringValue("a\\b"))),
                new Case("kcase", List.of(new StringValue("Foo"), new StringValue("FOO BAR"))),
                new Case("kinteger", List.of(new IntegerValue(5), new IntegerValue(-3))),
                new Case("klong", List.of(new IntegerValue(5, Type.LONG), new IntegerValue(-3, Type.LONG))),
                new Case("kint", List.of(new IntegerValue(5, Type.INT), new IntegerValue(-3, Type.INT))),
                new Case("kshort", List.of(new IntegerValue(5, Type.SHORT))),
                new Case("kdecimal", List.of(new DecimalValue(new BigDecimal("2.5")), new DecimalValue(new BigDecimal("-0.25")))),
                new Case("kdouble", List.of(new DoubleValue(2.5), new DoubleValue(-0.25))),
                new Case("kfloat", List.of(new FloatValue(2.5f))),
                new Case("kdate", List.of(new DateValue("2012-01-20"), new DateValue("-0800-02-11"))),
                new Case("ktime", List.of(new TimeValue("10:00:00"), new TimeValue("23:59:59+01:00"))),
                new Case("kconvstring", List.of(new StringValue("Foo"), new StringValue(" FOO "), new StringValue(""))),
                new Case("kconvdate", List.of(new DateValue("1918-01-01"), new DateValue("0800-12-01"))),
                new Case("kdatetime", List.of(new DateTimeValue("2012-01-20T10:00:00Z"), new DateTimeValue("2012-01-20T11:00:00+01:00"))));
    }
}
