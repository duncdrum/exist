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
package xquery.lucene;

import org.exist.test.ExistEmbeddedServer;
import org.exist.test.pathshapes.IndexUseGolden;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.file.Path;

/**
 * Which index answered each test of the generated path-shapes file of this module, see {@link IndexUseGolden}.
 */
public class IndexUseGoldenTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    void pathShapes() throws Exception {
        IndexUseGolden.check(IndexUseGolden.record(existEmbeddedServer.getBrokerPool(), "lucene", Path.of("src/test/xquery/lucene/path-shapes.xqm")),
                Path.of("src/test/xquery/lucene/path-shapes.index-use.golden"));
    }
}
