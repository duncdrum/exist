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

package org.exist.xquery;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 Tests for {@link FunctionSignature}.

 @author eXist-db contributors
*/
public class FunctionSignatureTest {

       private QName fnName(final String localPart) {
           return new QName(localPart, Namespaces.XPATH_FUNCTIONS_NS, localPart);
       }

       /**
        * getArgumentCount returns 0 when no arguments provided.
        */
       @Test
     public void getArgumentCount_noArgsReturnsZero() {
         final FunctionSignature sig = new FunctionSignature(fnName("empty"));
         assertEquals(0, sig.getArgumentCount());
     }

       /**
        * getArgumentCount returns correct count for variadic function.
        */
       @Test
     public void getArgumentCount_variadicReturnsNegativeOne() {
         final FunctionSignature sig = new FunctionSignature(
             fnName("concat"),
             null,
             new SequenceType[]{ new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE) },
             new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE),
             true   // variadic
         );
         assertEquals(-1, sig.getArgumentCount());
     }

       /**
        * getArgumentCount returns correct count for non-variadic function.
        */
       @Test
     public void getArgumentCount_threeArgsReturnsThree() {
         final SequenceType[] args = new SequenceType[]{
             new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
             new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
             new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
         };
         final FunctionSignature sig = new FunctionSignature(
             fnName("startsWith"),
             args,
             new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)
         );
         assertEquals(3, sig.getArgumentCount());
     }

       /**
        * getArgumentCount returns 0 when arguments array is explicitly null.
        */
       @Test
     public void getArgumentCount_nullArgsReturnsZero() {
         final FunctionSignature sig = new FunctionSignature(
             fnName("test"),
             null,
             null,
             null,
             false
         );
         assertEquals(0, sig.getArgumentCount());
     }

       /**
        * getName returns the QName set in constructor.
        */
       @Test
     public void getName_returnsSetQName() {
         final QName name = fnName("myFunction");
         final FunctionSignature sig = new FunctionSignature(name);
         assertEquals(name, sig.getName());
         assertEquals("myFunction", sig.getName().getLocalPart());
     }

       /**
        * getReturnType returns the return type set in constructor.
        */
       @Test
     public void getReturnType_returnsSetReturnType() {
         final SequenceType retType = new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE);
         final FunctionSignature sig = new FunctionSignature(
             fnName("floor"),
             new SequenceType[]{ new SequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE) },
             retType
         );
         assertEquals(retType, sig.getReturnType());
         assertEquals(Type.INTEGER, sig.getReturnType().getPrimaryType());
     }

       /**
        * getReturnType returns null by default.
        */
       @Test
     public void getReturnType_nullByDefault() {
         final FunctionSignature sig = new FunctionSignature(fnName("test"), new SequenceType[0], null);
         assertNull(sig.getReturnType());
     }

    /**
         * getReturnType returns null when null explicitly provided.
         */
        @Test
     public void getReturnType_nullWhenNullArg() {
         final FunctionSignature sig = new FunctionSignature(fnName("test"), null, null, null, false);
         assertNull(sig.getReturnType());
      }

        /**
         * setReturnType can change the return type.
         */
        @Test
     public void setReturnType_changesReturnType() {
         final SequenceType retType = new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE);
         final FunctionSignature sig = new FunctionSignature(fnName("test"), null, null, null, false);
         assertNull(sig.getReturnType());
         sig.setReturnType(retType);
         assertEquals(retType, sig.getReturnType());
      }

       /**
        * getArgumentTypes returns the arguments array.
        */
       @Test
     public void getArgumentTypes_returnsArgumentTypes() {
         final SequenceType[] args = new SequenceType[]{
             new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
         };
         final FunctionSignature sig = new FunctionSignature(
             fnName("doc"),
             args,
             new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
         );
         assertArrayEquals(args, sig.getArgumentTypes());
     }

       /**
        * getArgumentTypes returns null when no arguments set.
        */
       @Test
     public void getArgumentTypes_nullWhenNoArgs() {
         final FunctionSignature sig = new FunctionSignature(fnName("test"));
         assertNull(sig.getArgumentTypes());
     }

       /**
        * setArgumentTypes can change the argument types.
        */
       @Test
     public void setArgumentTypes_changesArgumentTypes() {
         final FunctionSignature sig = new FunctionSignature(fnName("test"));
         assertNull(sig.getArgumentTypes());

         final SequenceType[] args = new SequenceType[]{
             new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
         };
         sig.setArgumentTypes(args);
         assertArrayEquals(args, sig.getArgumentTypes());
     }

       /**
        * isVariadic returns false by default.
        */
       @Test
     public void isVariadic_defaultIsFalse() {
         final FunctionSignature sig = new FunctionSignature(fnName("test"));
         assertFalse(sig.isVariadic());
     }

       /**
        * isVariadic returns true for variadic functions.
        */
       @Test
     public void isVariadic_variadicIsTrue() {
         final FunctionSignature sig = new FunctionSignature(
             fnName("concat"),
             new SequenceType[]{ new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE) },
             new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE),
             true
         );
         assertTrue(sig.isVariadic());
     }

       /**
        * getDescription returns the description set in constructor.
        */
       @Test
     public void getDescription_returnsSetDescription() {
         final FunctionSignature sig = new FunctionSignature(
             fnName("doc"),
             "Returns the document at the given URI.",
             new SequenceType[]{ new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE) },
             new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
         );
         assertEquals("Returns the document at the given URI.", sig.getDescription());
     }

       /**
        * getDescription returns null when not provided.
        */
       @Test
     public void getDescription_nullWhenNotProvided() {
         final FunctionSignature sig = new FunctionSignature(fnName("test"));
         assertNull(sig.getDescription());
     }

       /**
        * setDescription can change the description.
        */
       @Test
     public void setDescription_changesDescription() {
         final FunctionSignature sig = new FunctionSignature(fnName("test"), "original", new SequenceType[0], null);
         assertEquals("original", sig.getDescription());
         sig.setDescription("updated");
         assertEquals("updated", sig.getDescription());
     }

       /**
        * getDeprecated returns null when not deprecated.
        */
       @Test
     public void getDeprecated_notDeprecatedReturnsNull() {
         final FunctionSignature sig = new FunctionSignature(fnName("test"));
         assertNull(sig.getDeprecated());
     }

       /**
        * getDeprecated returns null for empty string.
        */
       @Test
     public void getDeprecated_emptyStringReturnsNull() {
         final FunctionSignature sig = new FunctionSignature(fnName("test"));
         sig.setDeprecated("");
         assertNull(sig.getDeprecated());
     }

       /**
        * getDeprecated returns formatted message with deprecation suffix.
        */
       @Test
     public void getDeprecated_returnsFormattedMessage() {
         final FunctionSignature sig = new FunctionSignature(fnName("old-api"));
         sig.setDeprecated("Use new-method instead");
         String deprecated = sig.getDeprecated();
         assertNotNull(deprecated);
         assertTrue(deprecated.length() > "Use new-method instead".length());
     }

       /**
        * metadata is null before any key is added.
        */
       @Test
     public void getMetadata_nullWhenEmpty() {
         final FunctionSignature sig = new FunctionSignature(fnName("test"));
         assertNull(sig.getMetadata("no-such-key"));
     }

       /**
        * addMetadata stores key-value metadata, appending duplicates.
        */
       @Test
     public void addMetadata_appendsDuplicateKeys() {
         final FunctionSignature sig = new FunctionSignature(fnName("test"));
         sig.addMetadata("example", "value1");
         sig.addMetadata("example", "value2");
         assertEquals("value1, value2", sig.getMetadata("example"));
     }

       /**
        * getMetadata returns null for missing key.
        */
       @Test
     public void getMetadata_missingKeyReturnsNull() {
         final FunctionSignature sig = new FunctionSignature(fnName("test"));
         sig.addMetadata("key", "value");
         assertNull(sig.getMetadata("nonexistent"));
     }

       /**
        * Metadata map is not shared with copies (copy constructor).
        */
       @Test
     public void metadata_notSharedWithCopies() {
         final FunctionSignature sig1 = new FunctionSignature(fnName("test"), new SequenceType[0], null);
         sig1.addMetadata("key", "value1");

         final FunctionSignature sig2 = new FunctionSignature(sig1);
         sig2.addMetadata("key", "value2");

         assertEquals("value1", sig1.getMetadata("key"));
         assertEquals("value1, value2", sig2.getMetadata("key"));
     }

       /**
        * Copy constructor copies all fields.
        */
       @Test
     public void copyConstructor_copiesAllFields() {
         final QName name = fnName("original");
         final SequenceType[] args = new SequenceType[]{
             new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
         };
         final FunctionSignature original = new FunctionSignature(
             name, "original description", args,
             new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE), false
         );
         original.setDeprecated("old");
         original.addMetadata("author", "test");
         original.setAnnotations(new Annotation[0]);

         final FunctionSignature copy = new FunctionSignature(original);
         assertEquals(name, copy.getName());
         assertEquals("original description", copy.getDescription());
         assertArrayEquals(args, copy.getArgumentTypes());
         assertEquals(Type.INTEGER, copy.getReturnType().getPrimaryType());
         assertFalse(copy.isVariadic());
         assertNotNull(copy.getDeprecated());
         assertEquals("old\nThis function could be removed in the next major version release!", copy.getDeprecated());
         assertEquals("test", copy.getMetadata("author"));
          assertArrayEquals(original.getAnnotations(), copy.getAnnotations());
     }

       /**
        * Copy constructor handles null arguments array.
        */
       @Test
     public void copyConstructor_nullArguments() {
         final FunctionSignature original = new FunctionSignature(fnName("noargs"), new SequenceType[0], null);
         original.setAnnotations(null);
         final FunctionSignature copy = new FunctionSignature(original);
         assertArrayEquals(new SequenceType[0], copy.getArgumentTypes());
         assertNull(copy.getAnnotations());
     }

       /**
        * toString produces formatted signature string.
        */
       @Test
     public void toString_ProducesValidString() {
         final FunctionSignature sig = new FunctionSignature(
             fnName("fn:concat"),
             new SequenceType[]{ new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE) },
             new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE),
             true
         );
         String str = sig.toString();
         assertTrue(str.contains("fn:concat"));
         assertTrue(str.contains("as xs:string"));
         assertTrue(str.contains("..."));
     }

       /**
        * rename creates a new signature with a different name.
        */
       @Test
     public void rename_createsNewSignatureWithNewName() {
         final FunctionSignature sig = new FunctionSignature(
             fnName("old"),
             new SequenceType[]{ new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE) },
             new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE)
         );
         sig.setDeprecated("old-deprecated");
         sig.addMetadata("test", "value");
         sig.setAnnotations(new Annotation[0]);

         final FunctionSignature renamed = sig.rename(fnName("new"));

         assertEquals("new", renamed.getName().getLocalPart());
         assertArrayEquals(sig.getArgumentTypes(), renamed.getArgumentTypes());
         assertEquals(sig.getReturnType(), renamed.getReturnType());
         assertEquals(sig.getDeprecated(), renamed.getDeprecated());
         assertEquals(sig.getMetadata("test"), renamed.getMetadata("test"));
         assertNotNull(renamed.getAnnotations());
         assertArrayEquals(sig.getAnnotations(), renamed.getAnnotations());
     }

       /**
        * equals returns true for same name and argument count.
        */
       @Test
     public void equals_sameNameAndArityAreEqual() {
         final QName name = fnName("fn:sum");
         final FunctionSignature sig1 = new FunctionSignature(
             name,
             new SequenceType[]{ new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE) },
             new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
         );
         final FunctionSignature sig2 = new FunctionSignature(
             name,
             new SequenceType[]{ new SequenceType(Type.DECIMAL, Cardinality.EXACTLY_ONE) },
             new SequenceType(Type.DECIMAL, Cardinality.EXACTLY_ONE)
         );
         assertEquals(sig1, sig2);
     }

       /**
        * equals returns false for different names.
        */
       @Test
     public void equals_differentNamesAreNotEqual() {
         final FunctionSignature sig1 = new FunctionSignature(fnName("fn:sum"), new SequenceType[0], null);
         final FunctionSignature sig2 = new FunctionSignature(fnName("fn:avg"), new SequenceType[0], null);
         assertNotEquals(sig1, sig2);
     }

       /**
        * equals returns false when names are null (anonymous functions).
        */
       @Test
     public void equals_nullNamesAreNotEqual() {
         final FunctionSignature sig1 = new FunctionSignature(null, null, null);
         final FunctionSignature sig2 = new FunctionSignature(null, null, null);
         assertFalse(sig1.equals(sig2));
     }

       /**
        * equals returns false for different object types.
        */
       @Test
     public void equals_differentTypeIsNotEqualTo() {
         final FunctionSignature sig = new FunctionSignature(fnName("fn:sum"), null, null);
         assertFalse(sig.equals("string"));
     }

       /**
        * equals returns true for same object reference.
        */
       @Test
     public void equals_sameObjectReferenceIsEqual() {
         final FunctionSignature sig = new FunctionSignature(fnName("fn:sum"), null, null);
         assertTrue(sig.equals(sig));
     }

       /**
        * No args constant is an empty array.
        */
       @Test
     public void noArgsConstant() {
         assertSame(FunctionSignature.NO_ARGS, FunctionSignature.NO_ARGS);
         assertEquals(0, FunctionSignature.NO_ARGS.length);
     }

       /**
        * DEFAULT_TYPE is exactly one xs:item.
        */
       @Test
     public void defaultType_isItemZeroOrMore() {
         assertEquals(Type.ITEM, FunctionSignature.DEFAULT_TYPE.getPrimaryType());
         assertEquals(Cardinality.ZERO_OR_MORE, FunctionSignature.DEFAULT_TYPE.getCardinality());
     }
}
