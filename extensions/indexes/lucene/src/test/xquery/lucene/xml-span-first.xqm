(:
 : eXist-db Open Source Native XML Database
 : Copyright (C) 2001 The eXist-db Authors
 :
 : info@exist-db.org
 : http://www.exist-db.org
 :
 : This library is free software; you can redistribute it and/or
 : modify it under the terms of the GNU Lesser General Public
 : License as published by the Free Software Foundation; either
 : version 2.1 of the License, or (at your option) any later version.
 :
 : This library is distributed in the hope that it will be useful,
 : but WITHOUT ANY WARRANTY; without even the implied warranty of
 : MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 : Lesser General Public License for more details.
 :
 : You should have received a copy of the GNU Lesser General Public
 : License along with this library; if not, write to the Free Software
 : Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 :)
xquery version "3.1";

(:~
 : A first query over one child element, or over a child that analyzes to nothing, is built as a query like the one
 : over several children, not as a near query that needs two clauses.
 :)
module namespace sf="http://exist-db.org/xquery/lucene/xml-span-first/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $sf:CONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <analyzer class="org.exist.indexing.lucene.analyzers.EnglishStopwordsStandardAnalyzer"/>
                <text qname="xsf"/>
            </lucene>
        </index>
    </collection>;

declare variable $sf:XML :=
    <t>
        <xsf id="a">the cat and the hat</xsf>
        <xsf id="b">cat hat</xsf>
        <xsf id="c">cat cat cat</xsf>
        <xsf id="d">cat sat on a mat</xsf>
    </t>;

declare %test:setUp function sf:setup() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    xmldb:create-collection("/db", "xml-span-first"),
    xmldb:create-collection("/db/system/config/db", "xml-span-first"),
    xmldb:store("/db/system/config/db/xml-span-first", "collection.xconf", $sf:CONF),
    xmldb:store("/db/xml-span-first", "t.xml", $sf:XML),
    xmldb:reindex("/db/xml-span-first")
};

declare %test:tearDown function sf:tearDown() {
    xmldb:remove("/db/xml-span-first"),
    xmldb:remove("/db/system/config/db/xml-span-first")
};

declare function sf:ids($query as item()) as xs:string {
    let $r := string-join(collection("/db/xml-span-first")//xsf[ft:query(., $query)]/@id/string(), ",")
    return if ($r = "") then "(none)" else $r
};

declare %test:assertEquals("a,b,c,d") function sf:first-of-text() { sf:ids(<query><first end="2">cat</first></query>) };
declare %test:assertEquals("b,c,d") function sf:first-of-text-end-one() { sf:ids(<query><first end="1">cat</first></query>) };
declare %test:assertEquals("a,b,c,d") function sf:first-of-one-term() { sf:ids(<query><first end="2"><term>cat</term></first></query>) };
declare %test:assertEquals("b") function sf:first-of-one-near() { sf:ids(<query><first end="3"><near slop="0"><term>cat</term><term>hat</term></near></first></query>) };
declare %test:assertEquals("b") function sf:first-of-two-terms() { sf:ids(<query><first end="3"><term>cat</term><term>hat</term></first></query>) };
declare %test:assertEquals("(none)") function sf:first-of-a-stop-word-term() { sf:ids(<query><first end="2"><term>the</term></first></query>) };
