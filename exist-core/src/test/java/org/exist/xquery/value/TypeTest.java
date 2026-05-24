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

package org.exist.xquery.value;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 Tests for {@link Type#getTypeName(int)}.

 @author eXist-db contributors
*/
public class TypeTest {

      /**
       * getTypeName returns non-null for all built-in type constants
       * that are registered in the typeNames map.
       */
      @Test
     public void getTypeName_knownTypesReturnNonNull() {
         assertTrue(Type.getTypeName(Type.ITEM) != null);
         assertTrue(Type.getTypeName(Type.ANY_TYPE) != null);
         assertTrue(Type.getTypeName(Type.ANY_SIMPLE_TYPE) != null);
         assertTrue(Type.getTypeName(Type.BOOLEAN) != null);
         assertTrue(Type.getTypeName(Type.INTEGER) != null);
         assertTrue(Type.getTypeName(Type.STRING) != null);
         assertTrue(Type.getTypeName(Type.NODE) != null);
         assertTrue(Type.getTypeName(Type.DATE_TIME) != null);
         assertTrue(Type.getTypeName(Type.FLOAT) != null);
         assertTrue(Type.getTypeName(Type.QNAME) != null);
         assertTrue(Type.getTypeName(Type.ANY_URI) != null);
         assertTrue(Type.getTypeName(Type.HEX_BINARY) != null);
         assertTrue(Type.getTypeName(Type.DOCUMENT) != null);
         assertTrue(Type.getTypeName(Type.ELEMENT) != null);
         assertTrue(Type.getTypeName(Type.ATTRIBUTE) != null);
         assertTrue(Type.getTypeName(Type.TEXT) != null);
      }

      /**
       * getTypeName uses typeNames map, so negative or out-of-range values
       * that are not registered should return null.
       */
      @Test
     public void getTypeName_unknownTypeReturnsNull() {
         assertNull(Type.getTypeName(-1));
         assertNull(Type.getTypeName(0));
         assertNull(Type.getTypeName(1000));
         assertNull(Type.getTypeName(Integer.MAX_VALUE));
         assertNull(Type.getTypeName(Integer.MIN_VALUE));
      }

      /**
       * Verify specific type name mappings match expected values.
       */
      @Test
     public void getTypeName_specificMappings() {
         assertEquals("item()", Type.getTypeName(Type.ITEM));
         assertEquals("xs:boolean", Type.getTypeName(Type.BOOLEAN));
         assertEquals("xs:integer", Type.getTypeName(Type.INTEGER));
         assertEquals("xs:string", Type.getTypeName(Type.STRING));
         assertEquals("xs:dateTime", Type.getTypeName(Type.DATE_TIME));
         assertEquals("xs:float", Type.getTypeName(Type.FLOAT));
         assertEquals("xs:decimal", Type.getTypeName(Type.DECIMAL));
         assertEquals("node()", Type.getTypeName(Type.NODE));
         assertEquals("empty-sequence()", Type.getTypeName(Type.EMPTY_SEQUENCE));
         assertEquals("xs:error", Type.getTypeName(Type.ERROR));
      }
}
