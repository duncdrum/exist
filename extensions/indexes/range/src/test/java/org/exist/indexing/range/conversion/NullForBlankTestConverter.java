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
package org.exist.indexing.range.conversion;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;

/**
 * A converter for the tests of an index that does not hold what it cannot read: a text that is blank is not indexed,
 * any other text is indexed as it is.
 */
public class NullForBlankTestConverter implements TypeConverter {

    @Override
    public Field toField(final String fieldName, final String content) {
        return content.isBlank() ? null : new TextField(fieldName, content, Field.Store.NO);
    }
}
