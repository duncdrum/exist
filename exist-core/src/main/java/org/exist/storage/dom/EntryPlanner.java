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
package org.exist.storage.dom;

import org.w3c.dom.Node;

/**
 * Decides, record by record, which records of a document that is being written get an entry in the DOM B-tree: those
 * that {@link EntryPolicy#hasEntry(short, int)} gives one, and an anchor for the record that completes a run of
 * {@link EntryPolicy#getScanLimit()} records without an entry. Attributes count towards a run but never get an anchor.
 *
 * <p>The count starts again with every document, so a document is cut into runs of at most the scan limit when it is
 * stored in one go. Records inserted by an update are counted from the first of them, so the runs next to them can be
 * up to twice as long. Removing nodes removes their entries and places no new ones, so a run can grow when anchors
 * are removed and the records between them are not; storing the document again, a reindex that stores, or a defragmentation
 * cuts it into runs of the scan limit again.</p>
 *
 * <p>Not thread safe: there is one for each broker, and a broker writes one document at a time.</p>
 */
public final class EntryPlanner {

    private final EntryPolicy policy;
    private int documentId = -1;
    private int sinceEntry = 0;

    /**
     * Creates the planner for a policy.
     *
     * @param policy the policy
     */
    public EntryPlanner(final EntryPolicy policy) {
        this.policy = policy;
    }

    /**
     * Takes the next record of a document into account, and tells whether it gets an entry.
     *
     * @param documentId the id of the document that is being written
     * @param nodeType the DOM node type of the record
     * @param level the level of the node in the document, 1 for the root element
     * @return true if the record is stored with an entry
     */
    public boolean nextRecord(final int documentId, final short nodeType, final int level) {
        if (documentId != this.documentId) {
            this.documentId = documentId;
            sinceEntry = 0;
        }
        if (policy.hasEntry(nodeType, level)) {
            sinceEntry = 0;
            return true;
        }
        sinceEntry++;
        final int scanLimit = policy.getScanLimit();
        if (scanLimit > 0 && sinceEntry >= scanLimit && nodeType != Node.ATTRIBUTE_NODE) {
            sinceEntry = 0;
            return true;
        }
        return false;
    }
}
