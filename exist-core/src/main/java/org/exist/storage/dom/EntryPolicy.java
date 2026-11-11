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
 * Decides which nodes of a document get an entry in the DOM B-tree ({@code dom.dbx}), which maps the id of a node to the
 * address of its record.
 *
 * <p>A node without an entry is found by walking the document from its nearest ancestor that has one, so what has an
 * entry sets the cost of finding a node by its id. Every node at level 1 has an entry, see
 * https://github.com/eXist-db/exist/issues/1691, and below it every element up to {@code index-depth} levels. The same
 * rule applies when a node is stored, when the entries of a document are rebuilt and when an update inserts nodes.</p>
 *
 * <p>Documents stored before the rule was made one may hold entries for comments and processing instructions below the
 * root level. They are harmless: an entry is only a hint, and a node found through one is read and checked.</p>
 */
public final class EntryPolicy {

    private final int indexDepth;

    /**
     * Creates the policy for an index depth.
     *
     * @param indexDepth the number of levels, counted from the root element at level 1, whose elements have an entry
     */
    public EntryPolicy(final int indexDepth) {
        this.indexDepth = indexDepth;
    }

    /**
     * Returns the index depth.
     *
     * @return the number of levels whose elements have an entry
     */
    public int getIndexDepth() {
        return indexDepth;
    }

    /**
     * Tells whether a node gets an entry.
     *
     * @param nodeType the DOM node type of the node
     * @param level the level of the node in the document, 1 for the root element
     * @return true if the record of the node is stored with an entry
     */
    public boolean hasEntry(final short nodeType, final int level) {
        return level == 1 || (nodeType == Node.ELEMENT_NODE && level <= indexDepth);
    }
}
