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
 * How many records the lookups that were answered by reading forward from an entry had to read, in buckets that are
 * relative to the scan limit (K, {@code index-scan-limit}): a lookup reads at most K records after a store, so most of them
 * should be in the first buckets, and a count in the last bucket says that a run of records without an entry is longer than K.
 *
 * <p>Without a scan limit (0) there is no bound to measure against, and every lookup is counted in {@code beyondLimit}.</p>
 *
 * @param upToQuarter lookups that read up to K/4 records
 * @param upToHalf lookups that read more than K/4 and up to K/2 records
 * @param upToLimit lookups that read more than K/2 and up to K records
 * @param beyondLimit lookups that read more than K records, or all of them if there is no scan limit
 */
public record LookupBuckets(long upToQuarter, long upToHalf, long upToLimit, long beyondLimit) {

    /** The number of buckets. */
    static final int COUNT = 4;

    /**
     * Returns the bucket of a lookup.
     *
     * @param records the number of records the lookup read
     * @param scanLimit the scan limit, 0 if there is none
     * @return 0 for up to K/4 records, 1 for up to K/2, 2 for up to K, 3 for more, or for any number if there is no scan limit
     */
    static int bucketOf(final long records, final int scanLimit) {
        if (scanLimit <= 0) {
            return 3;
        }
        if (records <= scanLimit / 4) {
            return 0;
        }
        if (records <= scanLimit / 2) {
            return 1;
        }
        return records <= scanLimit ? 2 : 3;
    }

    /**
     * Returns the lookups in all buckets.
     *
     * @return the number of lookups that were counted
     */
    public long total() {
        return upToQuarter + upToHalf + upToLimit + beyondLimit;
    }

    /**
     * Returns the counts of this snapshot minus those of an earlier one.
     *
     * @param earlier the earlier snapshot
     * @return what was counted in between
     */
    public LookupBuckets since(final LookupBuckets earlier) {
        return new LookupBuckets(upToQuarter - earlier.upToQuarter, upToHalf - earlier.upToHalf,
                upToLimit - earlier.upToLimit, beyondLimit - earlier.beyondLimit);
    }
}
