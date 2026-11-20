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

import org.exist.vector.VectorEmbeddingProvider;
import org.exist.vector.VectorEmbeddingService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The dimension of a vector field with a model is checked against what the model embeds into, so that 385 for 384
 * is not a space no query vector reaches. Run with:
 * <pre>
 *   mvn verify -pl extensions/indexes/vector-it -Ponnx-model
 * </pre>
 */
@SuppressWarnings("PMD.ClassNamingConventions") // Failsafe runs the classes named *IT
@RequiresOnnxModel
public class VectorModelDimensionIT {

    private static final String MODEL = "all-MiniLM-L6-v2";
    private static final Path MODEL_PATH = Path.of(OnnxModelCondition.MODEL_PATH);

    @Test
    void theModelsDimensionIsAccepted() {
        final VectorEmbeddingProvider provider = VectorEmbeddingService.getInstance().getProviderByPath(MODEL + "-384", MODEL_PATH, 384);
        assertNotNull(provider);
        assertEquals(384, provider.getDimension());
    }

    @Test
    void anotherDimensionIsRefused() {
        assertNull(VectorEmbeddingService.getInstance().getProviderByPath(MODEL + "-385", MODEL_PATH, 385));
    }
}
