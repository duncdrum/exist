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
 : A stop word that is given as a term of an ordered near query keeps its position, as the same word in the text of the
 : query does: the analyzer removes it from the index, and the gap it leaves is part of the query. Leading and trailing
 : ones and those of an unordered near query have no position to keep.
 :)
module namespace nst="http://exist-db.org/xquery/lucene/xml-near-stop-word-terms/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $nst:CONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <analyzer class="org.exist.indexing.lucene.analyzers.EnglishStopwordsStandardAnalyzer"/>
                <text qname="xns"/>
            </lucene>
        </index>
    </collection>;

declare variable $nst:XML :=
    <t>
        <xns id="a">the cat and the hat</xns>
        <xns id="b">cat hat</xns>
        <xns id="c">cat cat cat</xns>
        <xns id="d">cat sat on a mat</xns>
    </t>;

declare %test:setUp function nst:setup() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    xmldb:create-collection("/db", "xml-near-stop-word-terms"),
    xmldb:create-collection("/db/system/config/db", "xml-near-stop-word-terms"),
    xmldb:store("/db/system/config/db/xml-near-stop-word-terms", "collection.xconf", $nst:CONF),
    xmldb:store("/db/xml-near-stop-word-terms", "t.xml", $nst:XML),
    xmldb:reindex("/db/xml-near-stop-word-terms")
};

declare %test:tearDown function nst:tearDown() {
    xmldb:remove("/db/xml-near-stop-word-terms"),
    xmldb:remove("/db/system/config/db/xml-near-stop-word-terms")
};

declare function nst:ids($query as item()) as xs:string {
    let $r := string-join(collection("/db/xml-near-stop-word-terms")//xns[ft:query(., $query)]/@id/string(), ",")
    return if ($r = "") then "(none)" else $r
};

declare %test:assertEquals("a") function nst:text-form() { nst:ids(<query><near slop="0">cat and the hat</near></query>) };
declare %test:assertEquals("a") function nst:terms-with-two-stop-words() { nst:ids(<query><near slop="0"><term>cat</term><term>and</term><term>the</term><term>hat</term></near></query>) };
declare %test:assertEquals("(none)") function nst:terms-with-one-stop-word-too-few() { nst:ids(<query><near slop="0"><term>cat</term><term>and</term><term>hat</term></near></query>) };
declare %test:assertEquals("a") function nst:terms-with-one-stop-word-and-slop() { nst:ids(<query><near slop="1"><term>cat</term><term>and</term><term>hat</term></near></query>) };
declare %test:assertEquals("b") function nst:terms-without-stop-words() { nst:ids(<query><near slop="0"><term>cat</term><term>hat</term></near></query>) };
declare %test:assertEquals("b") function nst:leading-stop-word-has-no-position() { nst:ids(<query><near slop="0"><term>the</term><term>cat</term><term>hat</term></near></query>) };
declare %test:assertEquals("b") function nst:trailing-stop-word-has-no-position() { nst:ids(<query><near slop="0"><term>cat</term><term>hat</term><term>the</term></near></query>) };
declare %test:assertEquals("b") function nst:unordered-drops-the-stop-word() { nst:ids(<query><near slop="0" ordered="no"><term>hat</term><term>and</term><term>cat</term></near></query>) };
declare %test:assertEquals("(none)") function nst:stop-words-only() { nst:ids(<query><near slop="0"><term>the</term><term>and</term></near></query>) };
declare %test:assertEquals("a") function nst:inside-first() { nst:ids(<query><first end="6"><near slop="0"><term>cat</term><term>and</term><term>the</term><term>hat</term></near></first></query>) };
declare %test:assertEquals("(none)") function nst:first-of-a-stop-word-inside-near() { nst:ids(<query><near slop="1"><first end="2"><term>the</term></first><term>cat</term></near></query>) };
