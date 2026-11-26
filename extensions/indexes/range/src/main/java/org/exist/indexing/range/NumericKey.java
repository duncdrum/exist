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
package org.exist.indexing.range;

import org.apache.lucene.document.DoubleField;
import org.apache.lucene.document.FloatField;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.LongField;
import org.apache.lucene.search.Query;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.DateValue;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.TimeValue;
import org.exist.xquery.value.Type;

import javax.annotation.Nullable;

/**
 * A key as the range index holds a number, a date or a time: a Lucene point of one of four kinds, whatever the XQuery
 * type of the value was. The kind decides which queries ask for it, so the queries are written once for the four kinds
 * and not once for each XQuery type.
 */
final class NumericKey {

    private final Number value;

    private NumericKey(final Number value) {
        this.value = value;
    }

    /**
     * The point that the index holds for an XQuery value.
     *
     * @param content the key
     * @return the point, or null if the index holds values of this type in another form
     * @throws XPathException if the value cannot be read as a number
     */
    static @Nullable NumericKey of(final AtomicValue content) throws XPathException {
        return switch (content.getType()) {
            case Type.INTEGER, Type.LONG, Type.UNSIGNED_LONG -> new NumericKey(((NumericValue) content).getLong());
            case Type.INT, Type.UNSIGNED_INT, Type.SHORT, Type.UNSIGNED_SHORT -> new NumericKey(((NumericValue) content).getInt());
            case Type.DECIMAL, Type.DOUBLE -> new NumericKey(((NumericValue) content).getDouble());
            case Type.FLOAT -> new NumericKey((float) ((NumericValue) content).getDouble());
            case Type.DATE -> new NumericKey(RangeIndexConfigElement.dateToLong((DateValue) content));
            case Type.TIME -> new NumericKey(RangeIndexConfigElement.timeToLong((TimeValue) content));
            default -> null;
        };
    }

    /**
     * The query for the nodes that hold exactly this value.
     *
     * @param field the Lucene field
     * @return the query
     */
    Query exact(final String field) {
        return switch (value) {
            case final Long l -> LongField.newExactQuery(field, l);
            case final Integer i -> IntField.newExactQuery(field, i);
            case final Double d -> DoubleField.newExactQuery(field, d);
            case final Float f -> FloatField.newExactQuery(field, f);
            default -> throw new IllegalStateException("not a point: " + value.getClass());
        };
    }

    /**
     * The query for the nodes that hold a value below, above or from this one. An operator that is none of the four
     * comparisons asks as {@code ge} does.
     *
     * @param field the Lucene field
     * @param operator the operator
     * @return the query
     */
    Query range(final String field, final RangeIndex.Operator operator) {
        return switch (value) {
            case final Long l -> switch (operator) {
                case LT -> LongField.newRangeQuery(field, Long.MIN_VALUE, Math.addExact(l, -1));
                case LE -> LongField.newRangeQuery(field, Long.MIN_VALUE, l);
                case GT -> LongField.newRangeQuery(field, Math.addExact(l, 1), Long.MAX_VALUE);
                default -> LongField.newRangeQuery(field, l, Long.MAX_VALUE);
            };
            case final Integer i -> switch (operator) {
                case LT -> IntField.newRangeQuery(field, Integer.MIN_VALUE, Math.addExact(i, -1));
                case LE -> IntField.newRangeQuery(field, Integer.MIN_VALUE, i);
                case GT -> IntField.newRangeQuery(field, Math.addExact(i, 1), Integer.MAX_VALUE);
                default -> IntField.newRangeQuery(field, i, Integer.MAX_VALUE);
            };
            case final Double d -> switch (operator) {
                case LT -> DoubleField.newRangeQuery(field, Double.NEGATIVE_INFINITY, Math.nextDown(d));
                case LE -> DoubleField.newRangeQuery(field, Double.NEGATIVE_INFINITY, d);
                case GT -> DoubleField.newRangeQuery(field, Math.nextUp(d), Double.POSITIVE_INFINITY);
                default -> DoubleField.newRangeQuery(field, d, Double.POSITIVE_INFINITY);
            };
            case final Float f -> switch (operator) {
                case LT -> FloatField.newRangeQuery(field, Float.NEGATIVE_INFINITY, Math.nextDown(f));
                case LE -> FloatField.newRangeQuery(field, Float.NEGATIVE_INFINITY, f);
                case GT -> FloatField.newRangeQuery(field, Math.nextUp(f), Float.POSITIVE_INFINITY);
                default -> FloatField.newRangeQuery(field, f, Float.POSITIVE_INFINITY);
            };
            default -> throw new IllegalStateException("not a point: " + value.getClass());
        };
    }
}
