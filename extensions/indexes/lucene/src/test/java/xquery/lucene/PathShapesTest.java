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

import java.io.IOException;
import java.nio.file.Path;

import org.exist.test.pathshapes.PathShapes;
import org.exist.test.pathshapes.PathShapes.Matrix;
import org.junit.jupiter.api.Test;

/**
 * The Lucene functions over the shapes a path can have: the matrix {@code path-shapes.xqm} is generated from, see {@link PathShapes}.
 */
class PathShapesTest {

    @Test
    void luceneIsGenerated() throws IOException {
        PathShapes.check(lucene(), Path.of("src/test/xquery/lucene/path-shapes.xqm"));
    }

    static Matrix lucene() {
        final Matrix matrix = new Matrix("lucene")
                .index("<lucene><text qname=\"item\"><field name=\"ps-title\" expression=\"*:t\"/><vector-field name=\"ps-vector\" expression=\"*:embedding\" dimension=\"3\" similarity=\"cosine\" encoding=\"text\"/></text><text qname=\"other\"><field name=\"ps-title\" expression=\"*:t\"/><vector-field name=\"ps-vector\" expression=\"*:embedding\" dimension=\"3\" similarity=\"cosine\" encoding=\"text\"/></text><text qname=\"sec\"><field name=\"ps-title\" expression=\"*:t\"/><vector-field name=\"ps-vector\" expression=\"*:embedding\" dimension=\"3\" similarity=\"cosine\" encoding=\"text\"/></text><text qname=\"part\"><field name=\"ps-title\" expression=\"*:t\"/><vector-field name=\"ps-vector\" expression=\"*:embedding\" dimension=\"3\" similarity=\"cosine\" encoding=\"text\"/></text><text qname=\"h:body\"><field name=\"ps-title\" expression=\"*:t\"/><vector-field name=\"ps-vector\" expression=\"*:embedding\" dimension=\"3\" similarity=\"cosine\" encoding=\"text\"/></text><text qname=\"t\"/><text qname=\"sub\"><field name=\"ps-sub-title\" expression=\"st\"/><vector-field name=\"ps-sub-vector\" expression=\"embedding\" dimension=\"3\" similarity=\"cosine\" encoding=\"text\"/></text><text qname=\"@a\"><field name=\"ps-title\" expression=\".\"/></text><text qname=\"@v\"><vector-field name=\"ps-attribute-vector\" expression=\".\" dimension=\"3\" similarity=\"cosine\" encoding=\"text\"/></text></lucene>")
                .withVectors();

        matrix.section("ft:query", "query", "ft:query(., \"hit\")", PathShapes.STEP, """
                desc                    1 2 3 #3
                child                   1 2 3 #3
                desc-after-child        1 2 3 #3
                after-wildcard          1 2 3 #3
                element-test            1 2 3 #3
                dos                     1 2 3 #3
                self                    1 2 3 #3
                wildcard                1 2 3 o1 o2 o3 #6
                wildcard-desc           1 2 3 o1 o2 o3 s1 s2 s3 #15
                node                    1 2 3 o1 o2 o3 #6
                node-desc               1 2 3 o1 o2 o3 s1 s2 s3 #15
                element                 1 2 3 o1 o2 o3 #6
                other-child             o1 o2 o3 #3
                other-desc              o1 o2 o3 #3
                ns-child                1 2 3 #3
                ns-wildcard             1 2 3 #3
                any-ns                  1 2 3 #3
                union                   1 2 3 o1 o2 o3 #6
                paren-step              1 2 3 #3
                paren-path              1 2 3 #3
                variable                1 2 3 #3
                two-filters             2 3 #2
                typed-argument          1 2 3 #3
                typed-variable          1 2 3 #3
                parent                  1 2 3 #3
                ancestor                1 2 3 #3
                following-sibling       o1 o2 o3 #3
                preceding-sibling       1 2 3 #3
                nested-child            t1 t2 t3 #3
                nested-desc             c1 c2 c3 t1 t2 t3 #6
                nested-any              c1 c2 c3 t1 t2 t3 #6
                nested-first            t1 t2 t3 #3
                pred-before             2 3 #2
                pred-after              2 3 #2
                narrowed                3 #1
                in-predicate            1 2 3 #3
                for                     1 2 #2
                doc-filter              #3
                doc-variable            #3
                doc-self                #3
                doc-one                 #1
                doc-root                #3
                doc-root-step           #3
                from-doc-variable       1 2 3 #3
                desc-from-doc-variable  1 2 3 #3
                text                    #0
                text-desc               #0
                comment                 #0
                comment-desc            #0
                pi                      #0
                attribute-no-index      #0
                attribute               1 2 3 #3
                attribute-desc          1 2 3 #3
                attribute-wildcard      1 2 3 #3
                attribute-vector        #0
                attribute-vector-desc   #0
                unindexed               #0
                """)
                .note("unindexed", "An index function finds nothing on a node that no configuration indexes");
        matrix.section("ft:query-field", "field", "ft:query-field(\"ps-title\", \"hit\")", PathShapes.STEP, """
                desc                    1 2 3 #3
                child                   1 2 3 #3
                desc-after-child        1 2 3 #3
                after-wildcard          1 2 3 #3
                element-test            1 2 3 #3
                dos                     1 2 3 #3
                self                    1 2 3 #3
                wildcard                1 2 3 o1 o2 o3 #6
                wildcard-desc           1 2 3 o1 o2 o3 #9
                node                    1 2 3 o1 o2 o3 #6
                node-desc               1 2 3 o1 o2 o3 #9
                element                 1 2 3 o1 o2 o3 #6
                other-child             o1 o2 o3 #3
                other-desc              o1 o2 o3 #3
                ns-child                1 2 3 #3
                ns-wildcard             1 2 3 #3
                any-ns                  1 2 3 #3
                union                   1 2 3 o1 o2 o3 #6
                paren-step              1 2 3 #3
                paren-path              1 2 3 #3
                variable                1 2 3 #3
                two-filters             2 3 #2
                typed-argument          1 2 3 #3
                typed-variable          1 2 3 #3
                parent                  1 2 3 #3
                ancestor                1 2 3 #3
                following-sibling       o1 o2 o3 #3
                preceding-sibling       1 2 3 #3
                nested-child            t1 t2 t3 #3
                nested-desc             c1 c2 c3 t1 t2 t3 #6
                nested-any              c1 c2 c3 t1 t2 t3 #6
                nested-first            t1 t2 t3 #3
                pred-before             2 3 #2
                pred-after              2 3 #2
                narrowed                3 #1
                in-predicate            1 2 3 #3
                for                     1 2 #2
                doc-filter              #3
                doc-variable            #3
                doc-self                #3
                doc-one                 #1
                doc-root                #3
                doc-root-step           #3
                from-doc-variable       1 2 3 #3
                desc-from-doc-variable  1 2 3 #3
                text                    #0
                text-desc               #0
                comment                 #0
                comment-desc            #0
                pi                      #0
                attribute-no-index      #0
                attribute               1 2 3 #3
                attribute-desc          1 2 3 #3
                attribute-wildcard      1 2 3 #3
                attribute-vector        #0
                attribute-vector-desc   #0
                unindexed               #0
                """)
                .note("unindexed", "An index function finds nothing on a node that no configuration indexes")
                .pending("wildcard-desc", "Elements that only contain a match are dropped, see https://github.com/eXist-db/exist/issues/6996")
                .pending("node-desc", "Elements that only contain a match are dropped, see https://github.com/eXist-db/exist/issues/6996");
        matrix.section("ft:query-vector", "vector", "ft:query-vector(., [1, 0, 0], 3)", PathShapes.STEP, """
                desc                    1 2 3 #3
                child                   1 2 3 #3
                desc-after-child        1 2 3 #3
                after-wildcard          1 2 3 #3
                element-test            1 2 3 #3
                dos                     1 2 3 #3
                self                    1 2 3 #3
                wildcard                1 2 3 o1 o2 o3 #6
                wildcard-desc           1 2 3 o1 o2 o3 s1 s2 s3 #9
                node                    1 2 3 o1 o2 o3 #6
                node-desc               1 2 3 o1 o2 o3 s1 s2 s3 #9
                element                 1 2 3 o1 o2 o3 #6
                other-child             o1 o2 o3 #3
                other-desc              o1 o2 o3 #3
                ns-child                1 2 3 #3
                ns-wildcard             1 2 3 #3
                any-ns                  1 2 3 #3
                union                   1 2 3 o1 o2 o3 #6
                paren-step              1 2 3 #3
                paren-path              1 2 3 #3
                variable                1 2 3 #3
                two-filters             2 3 4 #3
                typed-argument          1 2 3 #3
                typed-variable          1 2 3 #3
                parent                  1 2 3 #3
                ancestor                1 2 3 #3
                following-sibling       o1 o2 o3 #3
                preceding-sibling       1 2 3 #3
                nested-child            t10 t8 t9 #3
                nested-desc             c1 c2 c3 #3
                nested-any              c1 c2 c3 #3
                nested-first            t10 t8 t9 #3
                pred-before             2 3 4 #3
                pred-after              2 3 #2
                narrowed                3 4 5 #3
                in-predicate            1 2 3 #3
                for                     1 2 #2
                doc-filter              #3
                doc-variable            #3
                doc-self                #3
                doc-one                 #1
                doc-root                #3
                doc-root-step           #10
                from-doc-variable       1 2 3 #3
                desc-from-doc-variable  1 2 3 #3
                text                    #0
                text-desc               #0
                comment                 #0
                comment-desc            #0
                pi                      #0
                attribute-no-index      #0
                attribute               #0
                attribute-desc          #0
                attribute-wildcard      1 2 3 #3
                attribute-vector        1 2 3 #3
                attribute-vector-desc   1 2 3 #3
                unindexed               #0
                """)
                .note("unindexed", "An index function finds nothing on a node that no configuration indexes")
                .note("doc-root-step", "A function step runs once per context node, so k applies within each document")
                .note("wildcard", "k applies per name for now; with #6970 these names share one vector space and are ranked together")
                .note("wildcard-desc", "k applies per name for now; with #6970 these names share one vector space and are ranked together")
                .note("node", "k applies per name for now; with #6970 these names share one vector space and are ranked together")
                .note("node-desc", "k applies per name for now; with #6970 these names share one vector space and are ranked together")
                .note("element", "k applies per name for now; with #6970 these names share one vector space and are ranked together")
                .note("union", "k applies per name for now; with #6970 these names share one vector space and are ranked together")
                .pending("nested-child", "Nested ranking: a node with its own vector is ranked by it, see https://github.com/eXist-db/exist/issues/6978")
                .pending("nested-first", "Nested ranking: a node with its own vector is ranked by it, see https://github.com/eXist-db/exist/issues/6978");
        matrix.section("ft:query-field-vector", "field-vector", "ft:query-field-vector(\"ps-vector\", [1, 0, 0], 3)", PathShapes.STEP, """
                desc                    1 2 3 #3
                child                   1 2 3 #3
                desc-after-child        1 2 3 #3
                after-wildcard          1 2 3 #3
                element-test            1 2 3 #3
                dos                     1 2 3 #3
                self                    1 2 3 #3
                wildcard                1 2 o1 #3
                wildcard-desc           1 2 o1 #5
                node                    1 2 o1 #3
                node-desc               1 2 o1 #5
                element                 1 2 o1 #3
                other-child             o1 o2 o3 #3
                other-desc              o1 o2 o3 #3
                ns-child                1 2 3 #3
                ns-wildcard             1 2 3 #3
                any-ns                  1 2 3 #3
                union                   1 2 o1 #3
                paren-step              1 2 3 #3
                paren-path              1 2 3 #3
                variable                1 2 3 #3
                two-filters             2 3 4 #3
                typed-argument          1 2 3 #3
                typed-variable          1 2 3 #3
                parent                  1 2 3 #3
                ancestor                1 2 3 #3
                following-sibling       o1 o2 o3 #3
                preceding-sibling       1 2 3 #3
                nested-child            t10 t8 t9 #3
                nested-desc             c1 c2 c3 #3
                nested-any              c1 c2 c3 #3
                nested-first            t10 t8 t9 #3
                pred-before             2 3 4 #3
                pred-after              2 3 #2
                narrowed                3 4 5 #3
                in-predicate            1 2 3 #3
                for                     1 2 #2
                doc-filter              #2
                doc-variable            #2
                doc-self                #2
                doc-one                 #1
                doc-root                #2
                doc-root-step           #10
                from-doc-variable       1 2 3 #3
                desc-from-doc-variable  1 2 3 #3
                text                    #0
                text-desc               #0
                comment                 #0
                comment-desc            #0
                pi                      #0
                attribute-no-index      #0
                attribute               #0
                attribute-desc          #0
                attribute-wildcard      #0
                attribute-vector        #0
                attribute-vector-desc   #0
                unindexed               #0
                """)
                .note("unindexed", "An index function finds nothing on a node that no configuration indexes")
                .note("doc-root-step", "A function step runs once per context node, so k applies within each document")
                .pending("wildcard-desc", "Elements that only contain a match are dropped, see https://github.com/eXist-db/exist/issues/6996")
                .pending("node-desc", "Elements that only contain a match are dropped, see https://github.com/eXist-db/exist/issues/6996")
                .pending("nested-child", "Nested ranking: a node with its own vector is ranked by it, see https://github.com/eXist-db/exist/issues/6978")
                .pending("nested-first", "Nested ranking: a node with its own vector is ranked by it, see https://github.com/eXist-db/exist/issues/6978");
        matrix.section("ft:query on a child", "query-child", "ft:query(t, \"hit\")", PathShapes.ARGUMENT, """
                desc               1 2 3 #3
                child              1 2 3 #3
                desc-after-child   1 2 3 #3
                self               1 2 3 #3
                wildcard           1 2 3 o1 o2 o3 #6
                wildcard-desc      1 2 3 o1 o2 o3 #6
                union              1 2 3 o1 o2 o3 #6
                variable           1 2 3 #3
                two-filters        2 3 #2
                typed-argument     1 2 3 #3
                parent             1 2 3 #3
                following-sibling  o1 o2 o3 #3
                nested-child       #0
                nested-desc        c1 c2 c3 #3
                pred-before        2 3 #2
                narrowed           3 #1
                for                1 2 #2
                doc-filter         #0
                """);
        matrix.section("ft:query on a descendant path", "query-desc", "ft:query(.//t, \"hit\")", PathShapes.ARGUMENT, """
                desc               1 2 3 #3
                child              1 2 3 #3
                desc-after-child   1 2 3 #3
                self               1 2 3 #3
                wildcard           1 2 3 o1 o2 o3 #6
                wildcard-desc      1 2 3 o1 o2 o3 #9
                union              1 2 3 o1 o2 o3 #6
                variable           1 2 3 #3
                two-filters        2 3 #2
                typed-argument     1 2 3 #3
                parent             1 2 3 #3
                following-sibling  o1 o2 o3 #3
                nested-child       t1 t2 t3 #3
                nested-desc        c1 c2 c3 t1 t2 t3 #6
                pred-before        2 3 #2
                narrowed           3 #1
                for                1 2 #2
                doc-filter         #3
                """)
                .pending("wildcard-desc", "With the optimizer the wrap elements are dropped, see https://github.com/eXist-db/exist/issues/6988; without it, nested ones, see https://github.com/eXist-db/exist/issues/6935");
        matrix.section("ft:query on an attribute", "query-attribute", "ft:query(@a, \"hit\")", PathShapes.ARGUMENT, """
                desc               1 2 3 #3
                child              1 2 3 #3
                desc-after-child   1 2 3 #3
                self               1 2 3 #3
                wildcard           1 2 3 #3
                wildcard-desc      1 2 3 #3
                union              1 2 3 #3
                variable           1 2 3 #3
                two-filters        2 3 #2
                typed-argument     1 2 3 #3
                parent             1 2 3 #3
                following-sibling  #0
                nested-child       #0
                nested-desc        #0
                pred-before        2 3 #2
                narrowed           3 #1
                for                1 2 #2
                doc-filter         #0
                """);
        matrix.partialSection("ft:query-field on a field of an element below", "sub-field", "ft:query-field(\"ps-sub-title\", \"hit\")", """
                desc               1 2 3 #3
                child              1 2 3 #3
                wildcard           1 2 3 #3
                variable           1 2 3 #3
                for                1 2 #2
                doc-filter         #3
                from-doc-variable  1 2 3 #3
                """);
        matrix.partialSection("ft:query-field-vector on a field of an element below", "sub-field-vector", "ft:query-field-vector(\"ps-sub-vector\", [1, 0, 0], 3)", """
                desc               1 2 3 #3
                child              1 2 3 #3
                wildcard           1 2 3 #3
                variable           1 2 3 #3
                for                1 2 #2
                doc-filter         #3
                from-doc-variable  1 2 3 #3
                """);
        matrix.partialSection("ft:query-field-vector on a field of attributes", "attribute-field-vector", "ft:query-field-vector(\"ps-attribute-vector\", [1, 0, 0], 3)", """
                desc                   1 2 3 #3
                child                  1 2 3 #3
                wildcard               1 2 3 #3
                variable               1 2 3 #3
                for                    1 2 #2
                doc-filter             #3
                from-doc-variable      1 2 3 #3
                attribute-wildcard     1 2 3 #3
                attribute-vector       1 2 3 #3
                attribute-vector-desc  1 2 3 #3
                """);
        matrix.raw("""
                (: Fast forms stay pre-selected, and forms that cannot be stay evaluated as written: the answers cannot show a
                   lost optimization. Without the optimizer, the fast group is on the known failures list. :)
                declare %test:stats %test:assertXPath("exists($result//stats:index[@type eq 'lucene'][@optimization-level eq 'OPTIMIZED'])") function ps:fast--query-desc() { collection($ps:COLLECTION || "/flat")//item[ft:query(., "hit")] };
                declare %test:stats %test:assertXPath("exists($result//stats:index[@type eq 'lucene'][@optimization-level eq 'OPTIMIZED'])") function ps:fast--query-child() { collection($ps:COLLECTION || "/flat")/wrap/item[ft:query(., "hit")] };
                declare %test:stats %test:assertXPath("exists($result//stats:index[@type eq 'lucene'][@optimization-level eq 'OPTIMIZED'])") function ps:fast--query-of-a-child() { collection($ps:COLLECTION || "/flat")//item[ft:query(t, "hit")] };
                declare %test:stats %test:assertXPath("exists($result//stats:index[@type eq 'lucene'][@optimization-level eq 'OPTIMIZED'])") function ps:fast--field() { collection($ps:COLLECTION || "/flat")//item[ft:query-field("ps-title", "hit")] };
                declare %test:stats %test:assertXPath("exists($result//stats:index[@type eq 'lucene-vector'][@optimization-level eq 'OPTIMIZED'])") function ps:fast--vector-desc() { collection($ps:COLLECTION || "/flat")//item[ft:query-vector(., [1, 0, 0], 3)] };
                declare %test:stats %test:assertXPath("exists($result//stats:index[@type eq 'lucene-vector'][@optimization-level eq 'OPTIMIZED'])") function ps:fast--vector-variable() { let $i := collection($ps:COLLECTION || "/flat")/wrap/item return $i[ft:query-vector(., [1, 0, 0], 3)] };
                declare %test:stats %test:assertXPath("exists($result//stats:index[@type eq 'lucene-vector'][@optimization-level eq 'OPTIMIZED'])") function ps:fast--field-vector-on-the-step() { collection($ps:COLLECTION || "/flat")//sub[ft:query-field-vector("ps-sub-vector", [1, 0, 0], 3)] };
                (: A vector search is pre-selected only where its search looks among exactly the nodes the step selects: a named
                   descendant step from documents, or a variable's value. Other steps are evaluated as written, with the same answer. :)
                declare %test:stats %test:assertXPath("exists($result//stats:index[@type eq 'lucene-vector']) and empty($result//stats:index[@type eq 'lucene-vector'][@optimization-level eq 'OPTIMIZED'])") function ps:as-written--vector-child() { collection($ps:COLLECTION || "/flat")/wrap/item[ft:query-vector(., [1, 0, 0], 3)] };
                declare %test:stats %test:assertXPath("exists($result//stats:index[@type eq 'lucene-vector']) and empty($result//stats:index[@type eq 'lucene-vector'][@optimization-level eq 'OPTIMIZED'])") function ps:as-written--vector-wildcard() { collection($ps:COLLECTION || "/flat")/wrap/*[ft:query-vector(., [1, 0, 0], 3)] };
                declare %test:stats %test:assertXPath("exists($result//stats:index[@type eq 'lucene-vector']) and empty($result//stats:index[@type eq 'lucene-vector'][@optimization-level eq 'OPTIMIZED'])") function ps:as-written--field-vector-below-the-step() { collection($ps:COLLECTION || "/flat")//item[ft:query-field-vector("ps-sub-vector", [1, 0, 0], 3)] };
                declare %test:stats %test:assertXPath("exists($result//stats:index[@type eq 'lucene-vector']) and empty($result//stats:index[@type eq 'lucene-vector'][@optimization-level eq 'OPTIMIZED'])") function ps:as-written--field-vector-shared() { collection($ps:COLLECTION || "/flat")//item[ft:query-field-vector("ps-vector", [1, 0, 0], 3)] };
                """);
        return matrix;
    }
}
