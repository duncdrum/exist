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

import org.exist.storage.dom.DocumentLookups;
import org.exist.storage.dom.LookupStatistics;

/**
 * JMX MXBean for examining how nodes are found by their id in the DOM index: how many were found by an entry of their own,
 * by reading forward from the nearest entry before them, or by climbing to an ancestor, and how many records the second kind
 * read, in buckets relative to the scan limit.
 */
public interface NodeLookupsMXBean extends PerInstanceMBean {

    /**
     * Get the {@code index-depth}: the levels of elements, counted from the root, that have an entry in the DOM index.
     *
     * @return the index depth
     */
    int getIndexDepth();

    /**
     * Get the {@code index-scan-limit}: the most records a lookup of a node by its id reads from the nearest entry before it,
     * for documents stored or rebuilt now. 0 means that no anchor entries are stored.
     *
     * @return the scan limit
     */
    int getIndexScanLimit();

    /**
     * Get a snapshot of the counters since the database started or the statistics were last reset.
     *
     * @return the counters
     */
    LookupStatistics getStatistics();

    /**
     * Whether the lookups are also counted per document.
     *
     * @return true if they are
     */
    boolean isDocumentStatistics();

    /**
     * Switch the counting of lookups per document on or off. It is off by default.
     *
     * @param enabled true to count per document
     */
    void setDocumentStatistics(boolean enabled);

    /**
     * Get the documents whose lookups read the most records from entries, at most ten.
     *
     * @return the documents; empty unless the statistics per document are on
     */
    List<DocumentLookups> getTopDocuments();

    /**
     * Set all counters, and those per document, back to zero.
     */
    void resetStatistics();
}
