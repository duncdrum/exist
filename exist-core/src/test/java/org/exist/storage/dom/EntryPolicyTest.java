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

    @ParameterizedTest(name = "depth {0}: a {1} at level {2} gets an entry: {3}")
    @CsvSource(textBlock = """
        # depth, type, level, entry
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
        1, comment,   2, false
        # the root level gets an entry whatever the depth
        0, element,   1, true
        0, element,   2, false
        0, comment,   1, true
        """)
    void hasEntry(final int depth, final String nodeType, final int level, final boolean expected) {
        assertEquals(expected, new EntryPolicy(depth).hasEntry(type(nodeType), level));
    }

    @Test
    void indexDepthIsKept() {
        assertEquals(7, new EntryPolicy(7).getIndexDepth());
    }
}
