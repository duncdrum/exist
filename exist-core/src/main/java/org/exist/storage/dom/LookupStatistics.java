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
 * A snapshot of the counters of the by-id lookups of nodes in the DOM B-tree.
 *
 * <p>Every lookup ends in exactly one of three ways, so {@code exactHits + lookupsFromEntry + ancestorFallbacks} is the
 * number of lookups: the node has an entry of its own, or it is answered from the nearest entry before it, or, when there is
 * none that can be used, it climbs to the nearest ancestor with an entry. {@code rejectedEntries} counts the entries that
 * were ignored on the way, each time one is, on either path; a lookup can ignore none or several.</p>
 *
 * @param lookupsFromEntry the lookups of nodes without an entry that were answered by reading forward from the nearest entry
 *     before the node, whether they found the node or that it is not in the document
 * @param recordsScanned the records read from entries, also by reads that ended in an ignored entry
 * @param rejectedEntries the entries that were ignored because they lead to no record, to another node, or could not
 *     be read
 * @param ancestorFallbacks the lookups that climbed to the nearest ancestor with an entry, because there was no entry
 *     before the node in its document or the nearest one was ignored
 * @param exactHits the lookups of nodes that have an entry of their own, which need no reading forward
 * @param forwardLinkHops the entries whose record was reached through a forward link, because a page split moved it
 * @param buckets the records read by the lookups that were answered from an entry, relative to the scan limit
 */
public record LookupStatistics(long lookupsFromEntry, long recordsScanned, long rejectedEntries, long ancestorFallbacks,
        long exactHits, long forwardLinkHops, LookupBuckets buckets) {

    /**
     * Returns the number of lookups, however they were answered.
     *
     * @return the lookups of nodes with an entry, from an entry and by climbing
     */
    public long lookups() {
        return exactHits + lookupsFromEntry + ancestorFallbacks;
    }

    /**
     * Returns the counters of this snapshot minus those of an earlier one.
     *
     * @param earlier the earlier snapshot
     * @return what was counted in between
     */
    public LookupStatistics since(final LookupStatistics earlier) {
        return new LookupStatistics(lookupsFromEntry - earlier.lookupsFromEntry, recordsScanned - earlier.recordsScanned,
                rejectedEntries - earlier.rejectedEntries, ancestorFallbacks - earlier.ancestorFallbacks,
                exactHits - earlier.exactHits, forwardLinkHops - earlier.forwardLinkHops, buckets.since(earlier.buckets));
    }
}
