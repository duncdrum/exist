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

import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.EXistXQueryService;
import org.exist.xquery.util.ExpressionDumper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.CompiledExpression;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks where {@link AndPredicateSplitter} rewrites {@code [a and b]} into {@code [a][b]} and where it leaves the
 * expression unchanged. Each case also checks that the query gives the same answer with and without the optimizer.
 */
public class AndPredicateSplitterTest {

    private static final String OPTIMIZE = "declare option exist:optimize 'enable=yes'; ";
    private static final String NO_OPTIMIZE = "declare option exist:optimize 'enable=no'; ";
    private static final String COLLECTION_NAME = "and-predicate-splitter-test";
    private static final String DOC = "let $d := doc('/db/" + COLLECTION_NAME + "/books.xml') return ";

    @RegisterExtension
    public static final ExistXmldbEmbeddedServer server = new ExistXmldbEmbeddedServer(false, true, true);

    @BeforeAll
    static void store() throws XMLDBException {
        final CollectionManagementService cms = server.getRoot().getService(CollectionManagementService.class);
        final Collection collection = cms.createCollection(COLLECTION_NAME);
        final XMLResource resource = collection.createResource("books.xml", XMLResource.class);
        resource.setContent("""
                <library>
                  <book><title>One</title><author>Alice</author></book>
                  <book><title>Two</title></book>
                  <book><author>Bob</author><chapter/></book>
                </library>""");
        collection.storeResource(resource);
    }

    @AfterAll
    static void remove() throws XMLDBException {
        server.getRoot().getService(CollectionManagementService.class).removeCollection(COLLECTION_NAME);
    }

    @Test
    void splitsAnAndAtTheTopOfAStepPredicate() throws XMLDBException {
        final PathExpr optimized = optimize("$d//book[title and author]");

        assertEquals(List.of(2), predicateCountsOfStepsNamed(optimized, "book"), ExpressionDumper.dump(optimized));
        assertEquals(0, countAnds(optimized), ExpressionDumper.dump(optimized));
        assertSameAnswer("$d//book[title and author]");
    }

    @Test
    void leavesAnAndInAPredicateOfAFilteredExpression() throws XMLDBException {
        final PathExpr optimized = optimize("($d//book)[title and author]");

        assertEquals(1, countAnds(optimized), ExpressionDumper.dump(optimized));
        assertSameAnswer("($d//book)[title and author]");
    }

    @Test
    void leavesAnAndThatIsAStepOfALongerPath() throws XMLDBException {
        final PathExpr optimized = optimize("$d//book[chapter/(title and author)]");

        assertEquals(1, countAnds(optimized), ExpressionDumper.dump(optimized));
        assertSameAnswer("$d//book[chapter/(title and author)]");
    }

    @Test
    void leavesAnAndInsideAFunctionCall() throws XMLDBException {
        final PathExpr optimized = optimize("$d//book[boolean(title and author)]");

        assertEquals(1, countAnds(optimized), ExpressionDumper.dump(optimized));
        assertSameAnswer("$d//book[boolean(title and author)]");
    }

    @Test
    void leavesAnAndInsideAConditional() throws XMLDBException {
        final PathExpr optimized = optimize("$d//book[if (title and author) then true() else chapter]");

        assertEquals(1, countAnds(optimized), ExpressionDumper.dump(optimized));
        assertSameAnswer("$d//book[if (title and author) then true() else chapter]");
    }

    private static PathExpr optimize(final String body) throws XMLDBException {
        final EXistXQueryService service = server.getRoot().getService(EXistXQueryService.class);
        final CompiledExpression compiled = service.compile(OPTIMIZE + DOC + body);
        // execute() triggers analyze + optimize on the compiled tree
        service.execute(compiled);
        return (PathExpr) compiled;
    }

    private static void assertSameAnswer(final String body) throws XMLDBException {
        final XQueryService service = server.getRoot().getService(XQueryService.class);
        assertEquals(service.query(NO_OPTIMIZE + DOC + "count(" + body + ")").getResource(0).getContent(),
                service.query(OPTIMIZE + DOC + "count(" + body + ")").getResource(0).getContent(), body);
    }

    private static List<Integer> predicateCountsOfStepsNamed(final Expression root, final String localName) {
        final List<Integer> counts = new ArrayList<>();
        root.accept(new DefaultExpressionVisitor() {
            @Override
            public void visitLocationStep(final LocationStep step) {
                if (step.getTest().getName() != null && localName.equals(step.getTest().getName().getLocalPart())) {
                    counts.add(step.getPredicates() == null ? 0 : step.getPredicates().length);
                }
                super.visitLocationStep(step);
            }
        });
        return counts;
    }

    private static int countAnds(final Expression root) {
        final int[] count = {0};
        root.accept(new DefaultExpressionVisitor() {
            @Override
            public void visitAndExpr(final OpAnd and) {
                count[0]++;
                super.visitAndExpr(and);
            }
        });
        return count[0];
    }
}
