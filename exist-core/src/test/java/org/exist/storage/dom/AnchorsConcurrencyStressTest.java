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

import org.exist.test.ExistEmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.concurrent.TimeUnit;

/**
 * The scenarios of {@link AnchorsConcurrencyTest} with the two checks that class leaves out: the readers' own checks, made
 * while the writers and the defragmentation are running, found no node that was not the node asked for, and when the threads
 * are done each document has exactly the elements that were stored plus the ones the inserts added.
 *
 * <p>This class is excluded from the regular test run (see {@code exist-core/pom.xml}) because those two checks failed in
 * about two of five runs of the whole {@code org.exist.storage.dom} group on one machine, once with 838 wrong nodes and once
 * with one insert missing, and never when the class ran alone or repeated in one JVM, with the anchors on or off. The cause is
 * not known; it may be in the anchors, in the update path, or in how a query evaluates while its document is updated or
 * defragmented (eXist-db/exist#6829, #6969). Until it is, the test stays here as the reproducer, to be run on purpose:
 * {@code mvn test -pl exist-core -Dtest=AnchorsConcurrencyStressTest}, or together with the group it failed in,
 * {@code -Dtest='org.exist.storage.dom.*Test'} (the {@code test} parameter overrides the exclusion). A failure names the
 * assertion: wrong nodes seen by the readers, or an element count that is off.
 */
@Timeout(value = 3, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
public class AnchorsConcurrencyStressTest {

    @RegisterExtension
    static final ExistEmbeddedServer SERVER = new ExistEmbeddedServer(DomTestDocuments.configuration(AnchorsConcurrencyScenarios.SCAN_LIMIT), true, true);

    private static AnchorsConcurrencyScenarios scenarios() {
        return new AnchorsConcurrencyScenarios(SERVER.getBrokerPool(), true);
    }

    @Test
    void readersWritersAndDefragmentationOnOneDocument() throws Exception {
        scenarios().readersWritersAndDefragmentationOnOneDocument();
    }

    @Test
    void storesIntoOneCollectionWhileReadersSweepIt() throws Exception {
        scenarios().storesIntoOneCollectionWhileReadersSweepIt();
    }

    @Test
    void updatesAndDefragmentationOfDifferentDocuments() throws Exception {
        scenarios().updatesAndDefragmentationOfDifferentDocuments();
    }
}
