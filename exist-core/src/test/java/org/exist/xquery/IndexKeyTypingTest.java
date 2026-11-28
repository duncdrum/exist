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

import org.exist.xquery.IndexKeyTyping.Kind;
import org.exist.xquery.value.Type;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule by which a typed index may answer a comparison: it compares the stored text as the type the comparison
 * compares it as.
 */
public class IndexKeyTypingTest {

    private static boolean answers(final Kind kind, final int indexType, final int... keyTypes) {
        return IndexKeyTyping.answers(kind, indexType, keyTypes);
    }

    @Test
    void indexWithoutTypeConvertsNoKey() {
        assertTrue(answers(Kind.VALUE, Type.ITEM, Type.DATE_TIME));
        assertTrue(answers(Kind.GENERAL, Type.ITEM, Type.INTEGER));
        assertTrue(answers(Kind.TRUNCATION, Type.ITEM));
    }

    @Test
    void valueComparisonComparesTextAsString() {
        assertTrue(answers(Kind.VALUE, Type.STRING, Type.STRING));
        assertTrue(answers(Kind.VALUE, Type.STRING, Type.UNTYPED_ATOMIC));
        assertTrue(answers(Kind.VALUE, Type.STRING, Type.ANY_URI));
        assertTrue(answers(Kind.VALUE, Type.STRING, Type.NORMALIZED_STRING));
        assertTrue(answers(Kind.VALUE, Type.STRING), "no key, nothing to find");
    }

    @Test
    void valueComparisonWithTypedKeyIsATypeError() {
        assertFalse(answers(Kind.VALUE, Type.DATE_TIME, Type.DATE_TIME));
        assertFalse(answers(Kind.VALUE, Type.INTEGER, Type.INTEGER));
        assertFalse(answers(Kind.VALUE, Type.DOUBLE, Type.DOUBLE));
        assertFalse(answers(Kind.VALUE, Type.STRING, Type.INTEGER));
        assertFalse(answers(Kind.VALUE, Type.STRING, Type.DATE_TIME));
    }

    @Test
    void valueComparisonWithStringKeyOnTypedIndexComparesStrings() {
        assertFalse(answers(Kind.VALUE, Type.DATE_TIME, Type.STRING));
        assertFalse(answers(Kind.VALUE, Type.INTEGER, Type.STRING));
        assertFalse(answers(Kind.VALUE, Type.INTEGER, Type.UNTYPED_ATOMIC));
    }

    @Test
    void valueComparisonTakesOneItem() {
        assertFalse(answers(Kind.VALUE, Type.STRING, Type.STRING, Type.STRING));
        assertFalse(answers(Kind.VALUE, Type.STRING, Type.STRING, Type.UNTYPED_ATOMIC));
    }

    @Test
    void generalComparisonWithKeyOfTheIndexType() {
        assertTrue(answers(Kind.GENERAL, Type.STRING, Type.STRING));
        assertTrue(answers(Kind.GENERAL, Type.STRING, Type.UNTYPED_ATOMIC));
        assertTrue(answers(Kind.GENERAL, Type.DATE_TIME, Type.DATE_TIME));
        assertTrue(answers(Kind.GENERAL, Type.DATE, Type.DATE));
        assertTrue(answers(Kind.GENERAL, Type.TIME, Type.TIME));
        assertTrue(answers(Kind.GENERAL, Type.BOOLEAN, Type.BOOLEAN));
        assertTrue(answers(Kind.GENERAL, Type.DATE_TIME, Type.DATE_TIME_STAMP), "a subtype of the index type");
        assertTrue(answers(Kind.GENERAL, Type.STRING, Type.STRING, Type.STRING), "a general comparison takes a sequence");
    }

    @Test
    void generalComparisonWithStringKeyOnTypedIndexComparesStrings() {
        assertFalse(answers(Kind.GENERAL, Type.DATE_TIME, Type.STRING));
        assertFalse(answers(Kind.GENERAL, Type.INTEGER, Type.STRING));
        assertFalse(answers(Kind.GENERAL, Type.DATE, Type.UNTYPED_ATOMIC));
        assertFalse(answers(Kind.GENERAL, Type.DOUBLE, Type.ANY_URI));
    }

    @Test
    void generalComparisonWithTypedKeyOfAnotherType() {
        assertFalse(answers(Kind.GENERAL, Type.DATE_TIME, Type.DATE));
        assertFalse(answers(Kind.GENERAL, Type.DATE, Type.DATE_TIME));
        assertFalse(answers(Kind.GENERAL, Type.STRING, Type.DATE_TIME));
        assertFalse(answers(Kind.GENERAL, Type.STRING, Type.INTEGER));
        assertFalse(answers(Kind.GENERAL, Type.BOOLEAN, Type.INTEGER));
    }

    @Test
    void generalComparisonWithNumericKeyNeedsAnIndexItPromotesTo() {
        assertTrue(answers(Kind.GENERAL, Type.INTEGER, Type.INTEGER));
        assertTrue(answers(Kind.GENERAL, Type.INTEGER, Type.INT), "a derived integer type");
        assertTrue(answers(Kind.GENERAL, Type.DECIMAL, Type.INTEGER));
        assertTrue(answers(Kind.GENERAL, Type.DOUBLE, Type.INTEGER));
        assertTrue(answers(Kind.GENERAL, Type.DOUBLE, Type.DECIMAL));
        assertTrue(answers(Kind.GENERAL, Type.DOUBLE, Type.FLOAT));
        assertTrue(answers(Kind.GENERAL, Type.DOUBLE, Type.DOUBLE));
        assertFalse(answers(Kind.GENERAL, Type.INTEGER, Type.DECIMAL), "2.5 would be cut to 2");
        assertFalse(answers(Kind.GENERAL, Type.INTEGER, Type.DOUBLE));
        assertFalse(answers(Kind.GENERAL, Type.DECIMAL, Type.DOUBLE));
        assertFalse(answers(Kind.GENERAL, Type.FLOAT, Type.DOUBLE), "the double would be rounded");
    }

    @Test
    void generalComparisonWithSeveralKeysNeedsEveryKeyToAgree() {
        assertTrue(answers(Kind.GENERAL, Type.DOUBLE, Type.INTEGER, Type.DOUBLE));
        assertFalse(answers(Kind.GENERAL, Type.INTEGER, Type.INTEGER, Type.DOUBLE));
        assertFalse(answers(Kind.GENERAL, Type.STRING, Type.STRING, Type.INTEGER));
    }

    @Test
    void truncationComparesTextAsString() {
        assertTrue(answers(Kind.TRUNCATION, Type.STRING, Type.STRING));
        assertTrue(answers(Kind.TRUNCATION, Type.NORMALIZED_STRING, Type.STRING));
        assertFalse(answers(Kind.TRUNCATION, Type.DATE_TIME, Type.STRING));
        assertFalse(answers(Kind.TRUNCATION, Type.INTEGER, Type.STRING));
    }
}
