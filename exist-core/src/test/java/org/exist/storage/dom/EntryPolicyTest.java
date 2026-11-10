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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.w3c.dom.Node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule that decides which nodes get an entry in the DOM B-tree, as it was before it was moved into one class. The
 * two rules differ for comments and processing instructions below the root level; this test pins both.
 */
public class EntryPolicyTest {

    private static short type(final String name) {
        return switch (name) {
            case "element" -> Node.ELEMENT_NODE;
            case "attribute" -> Node.ATTRIBUTE_NODE;
            case "text" -> Node.TEXT_NODE;
            case "cdata" -> Node.CDATA_SECTION_NODE;
            case "comment" -> Node.COMMENT_NODE;
            case "pi" -> Node.PROCESSING_INSTRUCTION_NODE;
            default -> throw new IllegalArgumentException(name);
        };
    }

    @ParameterizedTest(name = "depth {0}: a {1} at level {2} gets an entry when stored: {3}")
    @CsvSource(textBlock = """
        # depth, type, level, entry
        5, element,   1, true
        5, element,   5, true
        5, element,   6, false
        5, element,  12, false
        5, comment,   1, true
        5, comment,   5, true
        5, comment,   6, false
        5, pi,        3, true
        5, pi,        6, false
        5, text,      1, false
        5, text,      3, false
        5, text,      8, false
        5, attribute, 2, false
        5, attribute, 4, false
        5, cdata,     2, false
        1, element,   1, true
        1, element,   2, false
        1, comment,   1, true
        1, comment,   2, false
        0, element,   1, false
        0, comment,   1, false
        """)
    void entryOnStore(final int depth, final String nodeType, final int level, final boolean expected) {
        assertEquals(expected, new EntryPolicy(depth).entryOnStore(type(nodeType), level));
    }

    @ParameterizedTest(name = "depth {0}: a {1} at level {2} gets an entry when the entries are rebuilt: {3}")
    @CsvSource(textBlock = """
        5, element,   1, true
        5, element,   5, true
        5, element,   6, false
        5, element,  12, false
        # everything at level 1 gets an entry (#1691), comments and processing instructions below it do not
        5, comment,   1, true
        5, comment,   2, false
        5, comment,   5, false
        5, comment,   6, false
        5, pi,        1, true
        5, pi,        3, false
        5, text,      3, false
        5, text,      8, false
        5, attribute, 2, false
        5, attribute, 4, false
        5, cdata,     2, false
        1, element,   1, true
        1, element,   2, false
        # with depth 0 the root level still gets an entry when rebuilding, but not when storing
        0, element,   1, true
        0, element,   2, false
        """)
    void entryOnReindex(final int depth, final String nodeType, final int level, final boolean expected) {
        assertEquals(expected, new EntryPolicy(depth).entryOnReindex(type(nodeType), level));
    }

    @Test
    void theTwoRulesDifferOnlyBelowTheRootLevelForCommentsAndProcessingInstructions() {
        final EntryPolicy policy = new EntryPolicy(5);
        for (final short nodeType : new short[] {Node.ELEMENT_NODE, Node.TEXT_NODE, Node.ATTRIBUTE_NODE, Node.CDATA_SECTION_NODE}) {
            for (int level = 2; level <= 8; level++) {
                assertEquals(policy.entryOnStore(nodeType, level), policy.entryOnReindex(nodeType, level),
                        "type " + nodeType + " at level " + level);
            }
        }
        assertTrue(policy.entryOnStore(Node.COMMENT_NODE, 3));
        assertFalse(policy.entryOnReindex(Node.COMMENT_NODE, 3));
    }

    @Test
    void indexDepthIsKept() {
        assertEquals(7, new EntryPolicy(7).getIndexDepth());
    }
}
