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
package org.exist.management.impl;

import java.util.List;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.exist.storage.BrokerPool;
import org.exist.storage.NativeBroker;
import org.exist.storage.dom.DOMFile;
import org.exist.storage.dom.DocumentLookups;
import org.exist.storage.dom.LookupBuckets;
import org.exist.storage.dom.LookupStatistics;

/**
 * JMX MXBean for examining how nodes are found by their id in the DOM index.
 */
public class NodeLookups implements NodeLookupsMXBean {

    private static final int TOP_DOCUMENTS = 10;

    private final BrokerPool instance;

    public NodeLookups(final BrokerPool instance) {
        this.instance = instance;
    }

    public static String getAllInstancesQuery() {
        return getName("*");
    }

    private static String getName(final String instanceId) {
        return "org.exist.management." + instanceId + ":type=NodeLookups";
    }

    @Override
    public ObjectName getName() throws MalformedObjectNameException {
        return new ObjectName(getName(instance.getId()));
    }

    @Override
    public String getInstanceId() {
        return instance.getId();
    }

    /** The DOM file is shared by the brokers of the instance through its configuration; null before it was opened. */
    private DOMFile domFile() {
        return (DOMFile) instance.getConfiguration().getProperty(DOMFile.getConfigKeyForFile());
    }

    @Override
    public int getIndexDepth() {
        return NativeBroker.configuredIndexDepth(instance.getConfiguration());
    }

    @Override
    public int getIndexScanLimit() {
        return NativeBroker.configuredIndexScanLimit(instance.getConfiguration());
    }

    @Override
    public LookupStatistics getStatistics() {
        final DOMFile domFile = domFile();
        return domFile != null ? domFile.getLookupStatistics() : new LookupStatistics(0, 0, 0, 0, 0, 0, new LookupBuckets(0, 0, 0, 0));
    }

    @Override
    public boolean isDocumentStatistics() {
        final DOMFile domFile = domFile();
        return domFile != null && domFile.isDocumentStatistics();
    }

    @Override
    public void setDocumentStatistics(final boolean enabled) {
        final DOMFile domFile = domFile();
        if (domFile != null) {
            domFile.setDocumentStatistics(enabled);
        }
    }

    @Override
    public List<DocumentLookups> getTopDocuments() {
        final DOMFile domFile = domFile();
        return domFile != null ? domFile.getDocumentStatistics(TOP_DOCUMENTS) : List.of();
    }

    @Override
    public void resetStatistics() {
        final DOMFile domFile = domFile();
        if (domFile != null) {
            domFile.resetLookupStatistics();
        }
    }
}
