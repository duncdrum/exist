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

/**
 * A snapshot of the counters of the by-id lookups of nodes that have no entry in the DOM B-tree.
 *
 * @param lookupsFromEntry the lookups that read forward from the nearest entry before the node
 * @param recordsScanned the records those lookups read
 * @param rejectedEntries the entries that were ignored because they did not lead to the node they are keyed by, or
 *     could not be read
 * @param ancestorFallbacks the lookups that fell back to the nearest ancestor with an entry
 */
public record LookupStatistics(long lookupsFromEntry, long recordsScanned, long rejectedEntries, long ancestorFallbacks) {

    /**
     * Returns the counters of this snapshot minus those of an earlier one.
     *
     * @param earlier the earlier snapshot
     * @return what was counted in between
     */
    public LookupStatistics since(final LookupStatistics earlier) {
        return new LookupStatistics(lookupsFromEntry - earlier.lookupsFromEntry, recordsScanned - earlier.recordsScanned,
                rejectedEntries - earlier.rejectedEntries, ancestorFallbacks - earlier.ancestorFallbacks);
    }
}
