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
 * <p>Every such lookup ends in exactly one of two ways, so {@code lookupsFromEntry + ancestorFallbacks} is the number
 * of lookups: it is answered from the nearest entry before the node, or, when there is none that can be used, it
 * climbs to the nearest ancestor with an entry. {@code rejectedEntries} counts the entries that were ignored on the
 * way, each time one is, on either path; a lookup can ignore none or several.</p>
 *
 * @param lookupsFromEntry the lookups that were answered by reading forward from the nearest entry before the node,
 *     whether they found the node or that it is not in the document
 * @param recordsScanned the records read from entries, also by reads that ended in an ignored entry
 * @param rejectedEntries the entries that were ignored because they lead to no record, to another node, or could not
 *     be read
 * @param ancestorFallbacks the lookups that climbed to the nearest ancestor with an entry, because there was no entry
 *     before the node in its document or the nearest one was ignored
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
