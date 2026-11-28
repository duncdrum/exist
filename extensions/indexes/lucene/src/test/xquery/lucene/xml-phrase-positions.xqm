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
 : An XML phrase or near query built from text has the positions the indexed text has: the analyzer removes stop words,
 : and the gap they leave is part of the query, as it is when the same phrase is written as a query string.
 : See https://github.com/eXist-db/exist/issues/7087
 :)
module namespace pp="http://exist-db.org/xquery/lucene/xml-phrase-positions/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $pp:CONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <analyzer class="org.exist.indexing.lucene.analyzers.EnglishStopwordsStandardAnalyzer"/>
                <text qname="xpp"/>
            </lucene>
        </index>
    </collection>;

declare variable $pp:XML :=
    <t>
        <xpp id="a">the cat and the hat</xpp>
        <xpp id="b">cat hat</xpp>
        <xpp id="c">cat cat cat</xpp>
        <xpp id="d">cat sat on a mat</xpp>
    </t>;

declare %test:setUp function pp:setup() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    xmldb:create-collection("/db", "xml-phrase-positions"),
    xmldb:create-collection("/db/system/config/db", "xml-phrase-positions"),
    xmldb:store("/db/system/config/db/xml-phrase-positions", "collection.xconf", $pp:CONF),
    xmldb:store("/db/xml-phrase-positions", "t.xml", $pp:XML),
    xmldb:reindex("/db/xml-phrase-positions")
};

declare %test:tearDown function pp:tearDown() {
    xmldb:remove("/db/xml-phrase-positions"),
    xmldb:remove("/db/system/config/db/xml-phrase-positions")
};

declare function pp:ids($query as item()) as xs:string {
    let $r := string-join(collection("/db/xml-phrase-positions")//xpp[ft:query(., $query)]/@id/string(), ",")
    return if ($r = "") then "(none)" else $r
};

declare %test:assertEquals("a") function pp:xml-phrase-with-stop-words() { pp:ids(<query><phrase>cat and the hat</phrase></query>) };
declare %test:assertEquals("a") function pp:string-phrase-with-stop-words() { pp:ids('"cat and the hat"') };
declare %test:assertEquals("b") function pp:xml-phrase-adjacent() { pp:ids(<query><phrase>cat hat</phrase></query>) };
declare %test:assertEquals("b") function pp:string-phrase-adjacent() { pp:ids('"cat hat"') };
declare %test:assertEquals("a,b") function pp:xml-phrase-with-slop() { pp:ids(<query><phrase slop="2">cat hat</phrase></query>) };
declare %test:assertEquals("(none)") function pp:xml-phrase-of-stop-words-only() { pp:ids(<query><phrase>the and</phrase></query>) };
declare %test:assertEquals("a") function pp:xml-near-with-stop-words() { pp:ids(<query><near slop="0">cat and the hat</near></query>) };
declare %test:assertEquals("b") function pp:xml-near-adjacent() { pp:ids(<query><near slop="0">cat hat</near></query>) };
declare %test:assertEquals("a,b") function pp:xml-near-with-slop() { pp:ids(<query><near slop="2">cat hat</near></query>) };
