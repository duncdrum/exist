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
 : A value comparison compares an untyped operand as xs:string, so an index never answers it with the type the index
 : was configured with. Each query runs over the same data in four collections (no index, a legacy index, a range index
 : on the element and range fields) and has to give the answer the collection without an index gives: the answer of the
 : comparison as written, or XPTY0004.
 :)
module namespace ct="http://exist-db.org/xquery/range/comparison-typing/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace stats="http://exist-db.org/xquery/profiling";

declare variable $ct:COLLECTIONS := ("none", "legacy", "range", "fields");

declare variable $ct:LEGACY_CONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <create qname="dt" type="xs:dateTime"/>
            <create qname="n" type="xs:integer"/>
            <create qname="s" type="xs:string"/>
        </index>
    </collection>;

declare variable $ct:RANGE_CONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <range>
                <create qname="dt" type="xs:dateTime"/>
                <create qname="n" type="xs:integer"/>
                <create qname="s" type="xs:string"/>
            </range>
        </index>
    </collection>;

declare variable $ct:FIELDS_CONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <range>
                <create qname="e">
                    <field name="dt" match="dt" type="xs:dateTime"/>
                    <field name="n" match="n" type="xs:integer"/>
                    <field name="s" match="s" type="xs:string"/>
                </create>
            </range>
        </index>
    </collection>;

(: e1 and e2 hold the same instant, written differently: a key typed as a dateTime finds both, a string finds one :)
declare variable $ct:XML :=
    <t>
        <e id="e1"><dt>2012-01-20T10:00:00Z</dt><n>1</n><s>a</s></e>
        <e id="e2"><dt>2012-01-20T11:00:00+01:00</dt><n>2</n><s>b</s></e>
        <e id="e3"><dt>2013-02-04T10:00:00Z</dt><n>3</n><s>c</s></e>
    </t>;

declare
    %test:setUp
function ct:setup() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    for $c in $ct:COLLECTIONS
    let $name := "ct-" || $c
    return (
        xmldb:create-collection("/db", $name),
        if ($c = "none") then () else (
            xmldb:create-collection("/db/system/config/db", $name),
            xmldb:store("/db/system/config/db/" || $name, "collection.xconf",
                switch ($c)
                case "legacy" return $ct:LEGACY_CONF
                case "range" return $ct:RANGE_CONF
                default return $ct:FIELDS_CONF)
        ),
        xmldb:store("/db/" || $name, "t.xml", $ct:XML),
        xmldb:reindex("/db/" || $name)
    )
};

declare
    %test:tearDown
function ct:tearDown() {
    for $c in $ct:COLLECTIONS
    return (
        xmldb:remove("/db/ct-" || $c),
        if ($c = "none") then () else xmldb:remove("/db/system/config/db/ct-" || $c)
    )
};

declare function ct:run($collection as xs:string, $predicate as xs:string) as xs:string {
    let $result := util:eval(
        "declare variable $k := 2; declare variable $d := xs:dateTime('2012-01-20T10:30:00Z'); "
        || "declare variable $str := 'b'; declare variable $str2 := '2012-01-20T10:00:00Z'; "
        || "try { string-join(collection('" || $collection || "')//e[" || $predicate || "]/@id/string(), ',') } "
        || "catch * { 'ERR ' || $err:code }")
    return if ($result = "") then "(none)" else $result
};

(:~
 : The answer all four collections give, or all of them if they differ.
 :)
declare function ct:same($predicate as xs:string) as xs:string {
    let $results := $ct:COLLECTIONS ! ct:run("/db/ct-" || ., $predicate)
    return
        if (count(distinct-values($results)) = 1)
        then $results[1]
        else string-join(for $i in 1 to count($results) return $ct:COLLECTIONS[$i] || ": " || $results[$i], " | ")
};

(: a typed key: the text is a string, which cannot be compared with a dateTime or a number :)

declare %test:assertEquals("ERR err:XPTY0004") function ct:gt-datetime() { ct:same("dt gt xs:dateTime('2012-01-20T10:30:00Z')") };
declare %test:assertEquals("ERR err:XPTY0004") function ct:eq-datetime() { ct:same("dt eq xs:dateTime('2012-01-20T10:00:00Z')") };
declare %test:assertEquals("ERR err:XPTY0004") function ct:eq-integer() { ct:same("n eq 2") };
declare %test:assertEquals("ERR err:XPTY0004") function ct:gt-integer() { ct:same("n gt 1") };
declare %test:assertEquals("ERR err:XPTY0004") function ct:eq-double() { ct:same("n eq 2.0e0") };
declare %test:assertEquals("ERR err:XPTY0004") function ct:eq-integer-variable() { ct:same("n eq $k") };
declare %test:assertEquals("ERR err:XPTY0004") function ct:gt-datetime-variable() { ct:same("dt gt $d") };

(: a string key on a typed index compares strings, not instants or numbers :)

declare %test:assertEquals("e1") function ct:eq-string-on-datetime() { ct:same("dt eq '2012-01-20T10:00:00Z'") };
declare %test:assertEquals("e1") function ct:eq-string-variable-on-datetime() { ct:same("dt eq $str2") };
declare %test:assertEquals("(none)") function ct:eq-string-on-integer() { ct:same("n eq '02'") };
declare %test:assertEquals("e2") function ct:eq-string-on-integer-same-text() { ct:same("n eq '2'") };

(: a value comparison takes one item :)

declare %test:assertEquals("ERR err:XPTY0004") function ct:eq-two-keys() { ct:same("s eq ('a','b')") };

(: what an index may answer stays the same :)

declare %test:assertEquals("e2") function ct:eq-string() { ct:same("s eq 'b'") };
declare %test:assertEquals("e2") function ct:eq-untyped() { ct:same("s eq xs:untypedAtomic('b')") };
declare %test:assertEquals("e2") function ct:eq-string-variable() { ct:same("s eq $str") };
declare %test:assertEquals("e3") function ct:general-gt-datetime() { ct:same("dt > xs:dateTime('2012-01-20T10:30:00Z')") };
declare %test:assertEquals("e1,e2") function ct:general-eq-datetime() { ct:same("dt = xs:dateTime('2012-01-20T10:00:00Z')") };
declare %test:assertEquals("e2") function ct:general-eq-integer() { ct:same("n = 2") };

(: the index is used where it may answer, so that the rows above are not answered by a scan everywhere :)

declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'range'][@optimization-level eq 'OPTIMIZED'])")
function ct:legacy-answers-a-string-key() {
    collection("/db/ct-legacy")//e[s eq "b"]/@id/string()
};

declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'range'][@optimization-level eq 'OPTIMIZED'])")
function ct:legacy-answers-a-general-comparison() {
    collection("/db/ct-legacy")//e[dt > xs:dateTime("2012-01-20T10:30:00Z")]/@id/string()
};

declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function ct:range-answers-a-string-key() {
    collection("/db/ct-range")//e[s eq "b"]/@id/string()
};

declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function ct:range-answers-a-general-comparison() {
    collection("/db/ct-range")//e[dt > xs:dateTime("2012-01-20T10:30:00Z")]/@id/string()
};

declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'new-range'])")
function ct:fields-answer-a-string-key() {
    collection("/db/ct-fields")//e[s eq "b"]/@id/string()
};

declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'new-range'])")
function ct:fields-answer-a-general-comparison() {
    collection("/db/ct-fields")//e[dt > xs:dateTime("2012-01-20T10:30:00Z")]/@id/string()
};
