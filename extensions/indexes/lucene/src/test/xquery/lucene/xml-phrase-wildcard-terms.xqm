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
 : A wildcard term in an XML phrase finds what the wildcard form finds: the pattern is read with the wildcard syntax of
 : Lucene and nothing else in it has a meaning, and the literal parts are normalized by the analyzer.
 : See https://github.com/eXist-db/exist/issues/7088
 :)
module namespace pw="http://exist-db.org/xquery/lucene/xml-phrase-wildcard-terms/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $pw:CONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <analyzer class="org.exist.indexing.lucene.analyzers.EnglishStopwordsStandardAnalyzer"/>
                <analyzer id="keyword" class="org.apache.lucene.analysis.core.KeywordAnalyzer"/>
                <text qname="xpwk" analyzer="keyword"/>
                <text qname="xpwh"/>
            </lucene>
        </index>
    </collection>;

declare variable $pw:XML :=
    <t>
        <xpwk id="k1">a&amp;b1</xpwk>
        <xpwk id="k2">a~b1</xpwk>
        <xpwk id="k3">a#b1</xpwk>
        <xpwk id="k4">a&lt;b1</xpwk>
        <xpwk id="k5">a"b1</xpwk>
        <xpwk id="k6">axb1</xpwk>
        <xpwh id="h">Hamlet speaks</xpwh>
    </t>;

declare %test:setUp function pw:setup() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    xmldb:create-collection("/db", "xml-phrase-wildcard-terms"),
    xmldb:create-collection("/db/system/config/db", "xml-phrase-wildcard-terms"),
    xmldb:store("/db/system/config/db/xml-phrase-wildcard-terms", "collection.xconf", $pw:CONF),
    xmldb:store("/db/xml-phrase-wildcard-terms", "t.xml", $pw:XML),
    xmldb:reindex("/db/xml-phrase-wildcard-terms")
};

declare %test:tearDown function pw:tearDown() {
    xmldb:remove("/db/xml-phrase-wildcard-terms"),
    xmldb:remove("/db/system/config/db/xml-phrase-wildcard-terms")
};

declare function pw:ids($qname as xs:string, $query as item()) as xs:string {
    let $r := string-join(collection("/db/xml-phrase-wildcard-terms")/t/*[local-name() = $qname][ft:query(., $query)]/@id/string(), ",")
    return if ($r = "") then "(none)" else $r
};

(: the phrase form and the wildcard form of a pattern, for each character that the old code read as syntax :)

declare %test:assertEquals("k1") function pw:phrase-term-ampersand() { pw:ids("xpwk", <query><phrase><term>a&amp;b*</term></phrase></query>) };
declare %test:assertEquals("k1") function pw:wildcard-ampersand() { pw:ids("xpwk", <query><wildcard>a&amp;b*</wildcard></query>) };
declare %test:assertEquals("k2") function pw:phrase-term-tilde() { pw:ids("xpwk", <query><phrase><term>a~b*</term></phrase></query>) };
declare %test:assertEquals("k3") function pw:phrase-term-hash() { pw:ids("xpwk", <query><phrase><term>a#b*</term></phrase></query>) };
declare %test:assertEquals("k4") function pw:phrase-term-less-than() { pw:ids("xpwk", <query><phrase><term>a&lt;b*</term></phrase></query>) };
declare %test:assertEquals("k5") function pw:phrase-term-quote() { pw:ids("xpwk", <query><phrase><term>a"b*</term></phrase></query>) };

(: a pattern with only the wildcard syntax, and the literal part folded by the analyzer as the wildcard form folds it :)

declare %test:assertEquals("k1,k2,k3,k4,k5,k6") function pw:phrase-term-star() { pw:ids("xpwk", <query><phrase><term>a*b1</term></phrase></query>) };
declare %test:assertEquals("k6") function pw:phrase-term-question-mark() { pw:ids("xpwk", <query><phrase><term>ax?1</term></phrase></query>) };
declare %test:assertEquals("h") function pw:wildcard-folds-case() { pw:ids("xpwh", <query><wildcard>Ham*</wildcard></query>) };
declare %test:assertEquals("h") function pw:phrase-term-folds-case() { pw:ids("xpwh", <query><phrase><term>Ham*</term></phrase></query>) };
