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
 : The key of fn:contains and fn:ends-with is a literal: the characters that mean something in a lucene wildcard
 : term, * ? and \, are characters of the key. Each query runs over the same data in a collection without an index and
 : in one with a range index, and the answer has to be the one without the index.
 :)
module namespace wk="http://exist-db.org/xquery/range/wildcard-keys/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace stats="http://exist-db.org/xquery/profiling";

declare variable $wk:COLLECTIONS := ("none", "range");

declare variable $wk:CONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <range>
                <create qname="wks" type="xs:string"/>
            </range>
        </index>
    </collection>;

declare variable $wk:XML :=
    <t>
        <e id="e1"><wks>a*b</wks></e>
        <e id="e2"><wks>axb</wks></e>
        <e id="e3"><wks>a?b</wks></e>
        <e id="e4"><wks>a\b</wks></e>
        <e id="e5"><wks>ab</wks></e>
    </t>;

declare
    %test:setUp
function wk:setup() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    for $c in $wk:COLLECTIONS
    let $name := "wk-" || $c
    return (
        xmldb:create-collection("/db", $name),
        if ($c = "none") then () else (
            xmldb:create-collection("/db/system/config/db", $name),
            xmldb:store("/db/system/config/db/" || $name, "collection.xconf", $wk:CONF)
        ),
        xmldb:store("/db/" || $name, "t.xml", $wk:XML),
        xmldb:reindex("/db/" || $name)
    )
};

declare
    %test:tearDown
function wk:tearDown() {
    for $c in $wk:COLLECTIONS
    return (
        xmldb:remove("/db/wk-" || $c),
        if ($c = "none") then () else xmldb:remove("/db/system/config/db/wk-" || $c)
    )
};

(:~
 : The answer both collections give, or both of them if they differ.
 :)
declare function wk:same($predicate as xs:string) as xs:string {
    let $results := $wk:COLLECTIONS ! (
        let $r := util:eval("string-join(collection('/db/wk-" || . || "')//e[" || $predicate || "]/@id/string(), ',')")
        return if ($r = "") then "(none)" else $r)
    return
        if (count(distinct-values($results)) = 1)
        then $results[1]
        else string-join(for $i in 1 to count($results) return $wk:COLLECTIONS[$i] || ": " || $results[$i], " | ")
};

declare %test:assertEquals("e1") function wk:contains-star() { wk:same("contains(wks, 'a*b')") };
declare %test:assertEquals("e3") function wk:contains-question-mark() { wk:same("contains(wks, 'a?b')") };
declare %test:assertEquals("e4") function wk:contains-backslash() { wk:same("contains(wks, 'a\b')") };
declare %test:assertEquals("e1") function wk:ends-with-star() { wk:same("ends-with(wks, '*b')") };
declare %test:assertEquals("e3") function wk:ends-with-question-mark() { wk:same("ends-with(wks, '?b')") };
declare %test:assertEquals("e4") function wk:ends-with-backslash() { wk:same("ends-with(wks, '\b')") };

(: what a key without such characters finds stays the same :)

declare %test:assertEquals("e1,e2,e3,e4,e5") function wk:contains-plain() { wk:same("contains(wks, 'b')") };
declare %test:assertEquals("e2") function wk:contains-inside() { wk:same("contains(wks, 'x')") };
declare %test:assertEquals("e1,e2,e3,e4,e5") function wk:ends-with-plain() { wk:same("ends-with(wks, 'b')") };
declare %test:assertEquals("e1") function wk:starts-with-star() { wk:same("starts-with(wks, 'a*')") };

(: the index is used where it may answer :)

declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function wk:contains-uses-the-index() {
    collection("/db/wk-range")//e[contains(wks, "a*b")]/@id/string()
};
