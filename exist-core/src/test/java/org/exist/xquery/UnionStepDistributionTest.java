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

import org.exist.dom.QName;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.junit.jupiter.api.Test;

import javax.xml.XMLConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link UnionStepDistribution} on hand-built paths, including a step the optimizer has already wrapped in its
 * {@code (#exist:optimize#)} pragma, which must still count as a step. The queries in
 * {@link UnionStepDistributionOptimizerTest} cannot reach that case, because distribution runs before the optimizer
 * wraps any step below the union.
 */
public class UnionStepDistributionTest {

    private final XQueryContext context = new XQueryContext();

    @Test
    void distributesOverABranchWhoseStepIsWrappedInTheOptimizePragma() throws XPathException {
        final PathExpr outer = path(child("library"), parens(union(path(pragma(child("book"))), path(child("journal")))));

        assertTrue(UnionStepDistribution.distribute(outer));
        assertEquals(1, outer.getLength());
        assertInstanceOf(Union.class, outer.getExpression(0));
    }

    @Test
    void distributesWhenTheSuffixStepIsWrappedInTheOptimizePragma() throws XPathException {
        final PathExpr outer = path(child("library"), parens(union(path(child("book")), path(child("journal")))),
                pragma(child("title")));

        assertTrue(UnionStepDistribution.distribute(outer));
        assertInstanceOf(Union.class, outer.getExpression(0));
    }

    @Test
    void doesNotDistributeWhenThePragmaWrapsSomethingOtherThanAStep() throws XPathException {
        final Expression parens = parens(union(path(pragma(new LiteralValue(context, new StringValue("x")))),
                path(child("journal"))));
        final PathExpr outer = path(child("library"), parens);

        assertFalse(UnionStepDistribution.distribute(outer));
        assertEquals(2, outer.getLength());
        assertSame(parens, outer.getExpression(1));
    }

    @Test
    void doesNotDistributeWhenTheSuffixIsNotAStep() throws XPathException {
        final PathExpr outer = path(child("library"), parens(union(path(child("book")), path(child("journal")))),
                new LiteralValue(context, new StringValue("x")));

        assertFalse(UnionStepDistribution.distribute(outer));
        assertEquals(3, outer.getLength());
    }

    private LocationStep child(final String name) throws XPathException {
        return new LocationStep(context, Constants.CHILD_AXIS, new NameTest(Type.ELEMENT, new QName(name, XMLConstants.NULL_NS_URI)));
    }

    private ExtensionExpression pragma(final Expression expression) {
        final ExtensionExpression extension = new ExtensionExpression(context);
        extension.setExpression(expression);
        return extension;
    }

    private Union union(final PathExpr left, final PathExpr right) {
        return new Union(context, left, right);
    }

    /** The PathExpr the parser puts around a parenthesised expression. */
    private PathExpr parens(final Union union) {
        final PathExpr parens = new PathExpr(context);
        parens.add(union);
        return parens;
    }

    private PathExpr path(final Expression... steps) {
        final PathExpr path = new PathExpr(context);
        for (final Expression step : steps) {
            if (step instanceof final PathExpr nested) {
                path.addPath(nested);
            } else {
                path.add(step);
            }
        }
        return path;
    }
}
