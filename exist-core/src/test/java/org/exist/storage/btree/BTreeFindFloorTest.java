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
package org.exist.storage.btree;

import org.exist.storage.BrokerPool;
import org.exist.test.ExistEmbeddedServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BTree#findFloor(Value)} against a sorted map, on trees with many leaf pages, so that the predecessor is
 * often the last key of the leaf to the left, also after keys were removed.
 */
public class BTreeFindFloorTest {

    private static final byte FILE_ID = 0x7E;
    private static final short FILE_VERSION = Short.MIN_VALUE;

    @RegisterExtension
    static final ExistEmbeddedServer SERVER = new ExistEmbeddedServer(true, true);

    @TempDir
    Path temporaryFolder;

    private Path file;

    @BeforeEach
    void createFile() throws Exception {
        file = Files.createFile(temporaryFolder.resolve("floor.dbx"));
    }

    private static Value key(final int n) {
        return new Value(String.format("K%08d", n));
    }

    private static void assertFloor(final BTree btree, final TreeMap<Integer, Long> expected, final int probe, final String context) throws Exception {
        final Map.Entry<Integer, Long> floor = expected.floorEntry(probe);
        final BTree.FloorEntry actual = btree.findFloor(key(probe));
        final String why = context + "probe " + probe;
        if (floor == null) {
            assertNull(actual, why + ": no key is <= it");
        } else {
            assertNotNull(actual, why + ": the floor is " + floor.getKey());
            assertEquals(key(floor.getKey()), actual.key(), why);
            assertEquals(floor.getValue(), actual.pointer(), why + ": pointer of the floor");
        }
    }

    private static void assertAllProbes(final BTree btree, final TreeMap<Integer, Long> expected, final int max) throws Exception {
        for (int probe = -1; probe <= max + 1; probe++) {
            assertFloor(btree, expected, probe, "");
        }
    }

    @Test
    void emptyTree() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final BTree btree = new BTree(pool, FILE_ID, FILE_VERSION, false, pool.getCacheManager(), file)) {
            btree.create((short) -1);
            assertNull(btree.findFloor(key(5)));
        }
    }

    @Test
    void floorOfEveryProbeInAGrowingTree() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final BTree btree = new BTree(pool, FILE_ID, FILE_VERSION, false, pool.getCacheManager(), file)) {
            btree.create((short) -1);
            final TreeMap<Integer, Long> expected = new TreeMap<>();
            // only every third number is a key, so most probes fall into a gap, some before the first and after the last
            for (int i = 3; i <= 9000; i += 3) {
                btree.addValue(key(i), i * 10L);
                expected.put(i, i * 10L);
            }
            assertAllProbes(btree, expected, 9000);
        }
    }

    @Test
    void randomInsertsAndRemovals() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        final long seed = System.nanoTime();
        final Random random = new Random(seed);
        final String why = "seed " + seed;
        try (final BTree btree = new BTree(pool, FILE_ID, FILE_VERSION, false, pool.getCacheManager(), file)) {
            btree.create((short) -1);
            final int range = 6000;
            final TreeMap<Integer, Long> expected = new TreeMap<>();
            for (int round = 0; round < 6; round++) {
                for (int i = 0; i < 3000; i++) {
                    final int n = random.nextInt(range);
                    if (expected.containsKey(n)) {
                        continue;
                    }
                    final long pointer = random.nextInt(Integer.MAX_VALUE);
                    btree.addValue(key(n), pointer);
                    expected.put(n, pointer);
                }
                // remove runs of neighbouring keys so that whole leaf pages are emptied
                final List<Integer> keys = new ArrayList<>(expected.keySet());
                final int runs = 5;
                for (int r = 0; r < runs && !keys.isEmpty(); r++) {
                    final int from = random.nextInt(keys.size());
                    final int to = Math.min(keys.size(), from + 100 + random.nextInt(900));
                    for (final int n : new ArrayList<>(keys.subList(from, to))) {
                        btree.removeValue(key(n));
                        expected.remove(n);
                    }
                    keys.clear();
                    keys.addAll(expected.keySet());
                }
                for (int probe = -1; probe <= range + 1; probe += 1 + random.nextInt(3)) {
                    assertFloor(btree, expected, probe, why + ", round " + round + ", ");
                }
            }
            assertTrue(expected.size() > 100, why + ": the tree still holds keys: " + expected.size());
        }
    }

    @Test
    void theKeyIsACopy() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final BTree btree = new BTree(pool, FILE_ID, FILE_VERSION, false, pool.getCacheManager(), file)) {
            btree.create((short) -1);
            btree.addValue(key(1), 11);
            final BTree.FloorEntry entry = btree.findFloor(key(2));
            assertNotNull(entry);
            btree.removeValue(key(1));
            btree.addValue(key(2), 22);
            assertEquals(key(1), entry.key());
            assertEquals(11, entry.pointer());
        }
    }
}
