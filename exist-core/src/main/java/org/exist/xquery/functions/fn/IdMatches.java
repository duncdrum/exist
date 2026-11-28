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
package org.exist.xquery.functions.fn;

import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.w3c.dom.Node;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What {@code fn:id} and {@code fn:element-with-id} do with the attributes that the ID index finds for one ID value.
 *
 * <p>If several elements have the same ID value, the functions return the one that is first in document order, in
 * each document that is searched.</p>
 */
final class IdMatches {

    private IdMatches() {
    }

    /**
     * Adds the elements that own the first ID attribute of each document.
     *
     * @param result the elements found so far
     * @param attributes the ID attributes with one ID value, of one or more documents
     */
    static void addFirstElementOfEachDocument(final NodeSet result, final NodeSet attributes) {
        final Map<Integer, NodeProxy> firstOfDocument = new LinkedHashMap<>();
        for (final NodeProxy attribute : attributes) {
            firstOfDocument.merge(attribute.getOwnerDocument().getDocId(), attribute,
                    (first, other) -> other.getNodeId().compareTo(first.getNodeId()) < 0 ? other : first);
        }
        for (final NodeProxy attribute : firstOfDocument.values()) {
            result.add(new NodeProxy(attribute.getExpression(), attribute.getOwnerDocument(),
                    attribute.getNodeId().getParentId(), Node.ELEMENT_NODE));
        }
    }
}
