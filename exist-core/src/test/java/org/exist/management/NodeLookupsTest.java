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
package org.exist.management;

import java.util.Optional;
import java.util.Properties;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

import org.exist.management.impl.NodeLookups;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.test.ExistEmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The MXBean for the by-id lookups of nodes: it reports the configured values, its records map to JMX types, and its
 * switch and its reset reach the counters. It is registered with a server of its own, as the platform server is shared.
 */
public class NodeLookupsTest {

    private static final int SCAN_LIMIT = 12;
    private static final int INDEX_DEPTH = 4;

    @RegisterExtension
    static final ExistEmbeddedServer SERVER = new ExistEmbeddedServer(configuration(), true, true);

    private static MBeanServer server;
    private static ObjectName name;

    private static Properties configuration() {
        final Properties configuration = new Properties();
        configuration.put(NativeBroker.PROPERTY_INDEX_DEPTH, INDEX_DEPTH);
        configuration.put(NativeBroker.PROPERTY_INDEX_SCAN_LIMIT, SCAN_LIMIT);
        return configuration;
    }

    @BeforeAll
    static void register() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        server = MBeanServerFactory.newMBeanServer();
        final NodeLookups mbean = new NodeLookups(pool);
        name = mbean.getName();
        server.registerMBean(new javax.management.StandardMBean(mbean, org.exist.management.impl.NodeLookupsMXBean.class, true), name);
    }

    @AfterAll
    static void unregister() throws Exception {
        server.unregisterMBean(name);
    }

    @Test
    void itReportsTheConfiguredIndexDepthAndScanLimit() throws Exception {
        assertEquals(INDEX_DEPTH, server.getAttribute(name, "IndexDepth"));
        assertEquals(SCAN_LIMIT, server.getAttribute(name, "IndexScanLimit"));
        assertEquals(SERVER.getBrokerPool().getId(), server.getAttribute(name, "InstanceId"));
    }

    @Test
    void theStatisticsMapToJmxTypesWithTheBuckets() throws Exception {
        final CompositeData statistics = (CompositeData) server.getAttribute(name, "Statistics");
        for (final String item : new String[]{"lookupsFromEntry", "recordsScanned", "rejectedEntries", "ancestorFallbacks", "exactHits", "forwardLinkHops"}) {
            assertTrue(statistics.containsKey(item), "the statistics have " + item + ": " + statistics.getCompositeType().keySet());
            assertTrue(statistics.get(item) instanceof Long, item + " is a number");
        }
        final CompositeData buckets = (CompositeData) statistics.get("buckets");
        assertEquals(java.util.Set.of("upToQuarter", "upToHalf", "upToLimit", "beyondLimit"), buckets.getCompositeType().keySet());
    }

    @Test
    void theSwitchPerDocumentAndTheResetReachTheCounters() throws Exception {
        assertEquals(false, server.getAttribute(name, "DocumentStatistics"), "off by default");
        server.setAttribute(name, new javax.management.Attribute("DocumentStatistics", true));
        try {
            assertEquals(true, server.getAttribute(name, "DocumentStatistics"));
            assertNotNull(server.getAttribute(name, "TopDocuments"));
        } finally {
            server.setAttribute(name, new javax.management.Attribute("DocumentStatistics", false));
        }
        assertEquals(false, server.getAttribute(name, "DocumentStatistics"));
        server.invoke(name, "resetStatistics", new Object[0], new String[0]);
        final CompositeData statistics = (CompositeData) server.getAttribute(name, "Statistics");
        assertEquals(0L, statistics.get("lookupsFromEntry"));
        assertEquals(0L, statistics.get("exactHits"));
    }

    @Test
    void theTopDocumentsNameTheirDocuments() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        server.invoke(name, "resetStatistics", new Object[0], new String[0]);
        server.setAttribute(name, new javax.management.Attribute("DocumentStatistics", true));
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            // nested deeper than the index depth, so that the nodes have no entry of their own and are read forward from one
            pool.getXQueryService().execute(broker, "xmldb:store('/db', 'node-lookups.xml', document { <root>"
                    + "<w><w><w><w><w><w>{ (1 to 40) ! <x k=\"{ . }\"><a>{ . }</a></x> }</w></w></w></w></w></w></root> })", null);
            pool.getXQueryService().execute(broker,
                    "count(for $x in doc('/db/node-lookups.xml')//x return util:node-by-id(root($x), util:node-id($x)))", null);

            final CompositeData[] top = (CompositeData[]) server.getAttribute(name, "TopDocuments");
            assertTrue(top.length > 0, "the lookups were counted per document");
            boolean named = false;
            for (final CompositeData document : top) {
                named |= "/db/node-lookups.xml".equals(document.get("uri"));
                assertTrue((Long) document.get("lookups") > 0, "each document in the list had lookups: " + document);
            }
            assertTrue(named, "the document is named by its path, not only by its id");
        } finally {
            server.setAttribute(name, new javax.management.Attribute("DocumentStatistics", false));
            server.invoke(name, "resetStatistics", new Object[0], new String[0]);
            try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
                pool.getXQueryService().execute(broker, "xmldb:remove('/db', 'node-lookups.xml')", null);
            }
        }
    }
}
