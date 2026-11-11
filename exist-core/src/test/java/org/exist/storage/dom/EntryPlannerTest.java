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

import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EntryPlannerTest {

    private static final short TEXT = Node.TEXT_NODE;
    private static final short ATTRIBUTE = Node.ATTRIBUTE_NODE;
    private static final short ELEMENT = Node.ELEMENT_NODE;

    /** Returns the positions, counted from 0, of the records of a run of deep text nodes that get an entry. */
    private static String anchors(final EntryPlanner planner, final int docId, final int records, final short nodeType) {
        final StringBuilder positions = new StringBuilder();
        for (int i = 0; i < records; i++) {
            if (planner.nextRecord(docId, nodeType, 9)) {
                positions.append(i).append(' ');
            }
        }
        return positions.toString().trim();
    }

    @Test
    void aRunOfRecordsWithoutAnEntryIsCutEveryScanLimitRecords() {
        final EntryPlanner planner = new EntryPlanner(new EntryPolicy(3, 4));
        assertEquals("3 7 11", anchors(planner, 1, 12, TEXT));
    }

    @Test
    void withoutAScanLimitThereAreNoAnchors() {
        final EntryPlanner planner = new EntryPlanner(new EntryPolicy(3, 0));
        assertEquals("", anchors(planner, 1, 1000, TEXT));
    }

    @Test
    void anEntryOfTheHeadStartsTheCountAgain() {
        final EntryPlanner planner = new EntryPlanner(new EntryPolicy(3, 4));
        assertEquals("3", anchors(planner, 1, 5, TEXT));
        assertTrue(planner.nextRecord(1, ELEMENT, 2), "an element within the index depth has an entry");
        assertEquals("3", anchors(planner, 1, 5, TEXT), "the run after it is counted from it");
    }

    @Test
    void attributesCountButNeverGetAnAnchor() {
        final EntryPlanner planner = new EntryPlanner(new EntryPolicy(3, 4));
        for (int i = 0; i < 10; i++) {
            assertFalse(planner.nextRecord(1, ATTRIBUTE, 9), "attribute " + i);
        }
        assertTrue(planner.nextRecord(1, TEXT, 9), "the next record that can has the anchor");
        assertFalse(planner.nextRecord(1, TEXT, 9));
    }

    @Test
    void aNewDocumentIsCountedFromItsFirstRecord() {
        final EntryPlanner planner = new EntryPlanner(new EntryPolicy(3, 4));
        assertEquals("", anchors(planner, 1, 3, TEXT));
        assertEquals("3 7", anchors(planner, 2, 8, TEXT), "the three records of the other document are not counted");
    }
}
