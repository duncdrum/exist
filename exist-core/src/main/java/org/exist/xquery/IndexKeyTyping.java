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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * Decides whether a typed index may answer a comparison, from the type of the index and the types of the keys.
 *
 * <p>An index compares the stored text as the type it was configured with, and converts the key to that type. That
 * answers a comparison only if the comparison would compare the stored text as the same type. A comparison compares
 * untyped text as follows:</p>
 * <ul>
 *   <li>a value comparison ({@code eq}, {@code lt} and so on) as {@code xs:string}, so its key must be a string, an
 *       untyped atomic value or a URI, and it takes one item;</li>
 *   <li>a comparison with truncation ({@code starts-with}, {@code contains}, {@code ends-with}) as
 *       {@code xs:string};</li>
 *   <li>a general comparison ({@code =}, {@code <} and so on) as {@code xs:string} for a string or untyped key, as
 *       {@code xs:double} for a numeric key, and as the key's own type otherwise.</li>
 * </ul>
 * <p>If the index may not answer, the comparison is evaluated as written, which gives the answer or raises the error
 * the specification requires. An index never changes an answer.</p>
 */
public final class IndexKeyTyping {

    /** How a comparison compares untyped text. */
    public enum Kind {
        /** A value comparison: {@code eq}, {@code ne}, {@code lt}, {@code le}, {@code gt}, {@code ge}. */
        VALUE,
        /** A general comparison without truncation. */
        GENERAL,
        /** A comparison with truncation: {@code starts-with}, {@code contains}, {@code ends-with}. */
        TRUNCATION
    }

    private static final Logger LOG = LogManager.getLogger(IndexKeyTyping.class);

    private IndexKeyTyping() {
        // static helpers only
    }

    /**
     * Whether an index of the given type may answer a comparison with the given keys.
     *
     * @param kind how the comparison compares untyped text
     * @param indexType the type the index was configured with; {@link Type#ITEM} if it has none, which converts no key
     * @param keyTypes the types of the atomized keys, one per item of the key sequence
     * @return true if the index may answer the comparison, false if the comparison has to be evaluated as written
     */
    public static boolean answers(final Kind kind, final int indexType, final int[] keyTypes) {
        if (indexType == Type.ITEM) {
            return true;
        }
        return switch (kind) {
            case TRUNCATION -> isString(indexType);
            case VALUE -> keyTypes.length <= 1 && isString(indexType) && allStringLike(keyTypes);
            case GENERAL -> allAgree(indexType, keyTypes);
        };
    }

    /**
     * Whether an index of the given type may answer a comparison with the given keys.
     *
     * @param kind how the comparison compares untyped text
     * @param indexType the type the index was configured with; {@link Type#ITEM} if it has none, which converts no key
     * @param keys the atomized keys
     * @return true if the index may answer the comparison, false if the comparison has to be evaluated as written
     */
    public static boolean answers(final Kind kind, final int indexType, final Sequence keys) {
        final int[] keyTypes = new int[keys.getItemCount()];
        for (int i = 0; i < keyTypes.length; i++) {
            keyTypes[i] = keys.itemAt(i).getType();
        }
        return answers(kind, indexType, keyTypes);
    }

    /**
     * Reports that an index steps aside because of the type of the key, so that a slower query can be explained.
     *
     * @param expression the expression the message is about
     * @param what the index, as the message names it
     */
    public static void reportDeclined(final Expression expression, final String what) {
        final String message = what + " not used: the comparison does not compare in the type of the index";
        if (LOG.isTraceEnabled()) {
            LOG.trace("{}: {}", message, expression);
        }
        final Profiler profiler = expression.getContext().getProfiler();
        if (profiler.isEnabled()) {
            profiler.message(expression, Profiler.OPTIMIZATIONS, "OPTIMIZATION", message);
        }
    }

    private static boolean allStringLike(final int[] keyTypes) {
        for (final int keyType : keyTypes) {
            if (!isStringLike(keyType)) {
                return false;
            }
        }
        return true;
    }

    private static boolean allAgree(final int indexType, final int[] keyTypes) {
        for (final int keyType : keyTypes) {
            if (!agrees(indexType, keyType)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the index compares in the type a general comparison compares in for this key.
     *
     * @param indexType the type of the index
     * @param keyType the type of one key
     * @return true if the index and the comparison compare as the same type
     */
    private static boolean agrees(final int indexType, final int keyType) {
        if (isStringLike(keyType)) {
            return isString(indexType);
        }
        if (Type.subTypeOf(keyType, Type.NUMERIC)) {
            // the text is compared as xs:double; the key converts to the index's type without loss only if it promotes to it
            return Type.subTypeOf(indexType, Type.NUMERIC) && numericRank(keyType) <= numericRank(indexType);
        }
        return Type.subTypeOf(keyType, indexType);
    }

    private static boolean isString(final int type) {
        return Type.subTypeOf(type, Type.STRING);
    }

    private static boolean isStringLike(final int type) {
        return type == Type.UNTYPED_ATOMIC || isString(type) || Type.subTypeOf(type, Type.ANY_URI);
    }

    /**
     * The place of a numeric type in the promotion order: integer, decimal, float, double.
     *
     * @param type a numeric type
     * @return the rank, higher for a type that every lower one promotes to
     */
    private static int numericRank(final int type) {
        if (Type.subTypeOf(type, Type.INTEGER)) {
            return 0;
        }
        if (Type.subTypeOf(type, Type.DECIMAL)) {
            return 1;
        }
        if (Type.subTypeOf(type, Type.FLOAT)) {
            return 2;
        }
        return 3;
    }
}
