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
 : A path on the right-hand side of a general comparison: 'k7' = @k is turned into @k = 'k7', inverting an ordering
 : relation, so the index answers either spelling. Truncating comparisons keep their order, and value comparisons keep
 : the path on the right, where XPTY0004 for several items is still raised (#7002).
 :)
module namespace rrhs="http://exist-db.org/xquery/range/rhs-path/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace stats="http://exist-db.org/xquery/profiling";

declare variable $rrhs:DATA :=
    <doc>{
        for $i in 1 to 20 return <w k="k{$i}" n="{$i}">v{$i}</w>,
        <a><b>v</b><b>w</b></a>
    }</doc>;

(: collection name -> index configuration; "none" has no configuration :)
declare variable $rrhs:CONFIGS := map {
    "rrhs-range": <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"><range>
            <create qname="w" type="xs:string"/>
            <create qname="@k" type="xs:string"/>
            <create qname="@n" type="xs:integer"/>
            <create qname="b" type="xs:string"/>
        </range></index>
    </collection>,
    "rrhs-legacy": <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <create qname="w" type="xs:string"/>
            <create qname="@k" type="xs:string"/>
            <create qname="@n" type="xs:integer"/>
            <create qname="b" type="xs:string"/>
        </index>
    </collection>,
    "rrhs-none": ()
};

declare
    %test:setUp
function rrhs:setup() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    for $name in map:keys($rrhs:CONFIGS)
    return (
        if (exists($rrhs:CONFIGS($name))) then (
            xmldb:create-collection("/db/system/config/db", $name),
            xmldb:store("/db/system/config/db/" || $name, "collection.xconf", $rrhs:CONFIGS($name))
        ) else (),
        xmldb:create-collection("/db", $name),
        xmldb:store("/db/" || $name, "d.xml", $rrhs:DATA)
    )
};

declare
    %test:tearDown
function rrhs:teardown() {
    for $name in map:keys($rrhs:CONFIGS)
    return (
        xmldb:remove("/db/" || $name),
        if (xmldb:collection-available("/db/system/config/db/" || $name)) then xmldb:remove("/db/system/config/db/" || $name) else ()
    )
};

declare %test:assertEquals("k7") function rrhs:attribute-range() {
    collection("/db/rrhs-range")//w['k7' = @k]/@k/string()
};

declare
    %test:stats
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function rrhs:attribute-range-uses-index() {
    collection("/db/rrhs-range")//w['k7' = @k]
};

declare %test:assertEquals("v7") function rrhs:element-range() {
    collection("/db/rrhs-range")//w['v7' = .]/string()
};

declare
    %test:stats
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function rrhs:element-range-uses-index() {
    collection("/db/rrhs-range")//w['v7' = .]
};

(: 15 < @n is @n > 15 :)
declare %test:assertEquals(16, 17, 18, 19, 20) function rrhs:less-than-range() {
    collection("/db/rrhs-range")//w[15 < @n]/@n/xs:integer(.)
};

declare
    %test:stats
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function rrhs:less-than-range-uses-index() {
    collection("/db/rrhs-range")//w[15 < @n]
};

declare %test:assertEquals(16, 17, 18, 19, 20) function rrhs:less-than-legacy() {
    collection("/db/rrhs-legacy")//w[15 < @n]/@n/xs:integer(.)
};

declare
    %test:stats
    %test:assertXPath("$result//stats:index[@type eq 'range'][@optimization-level eq 'OPTIMIZED']")
function rrhs:less-than-legacy-uses-index() {
    collection("/db/rrhs-legacy")//w[15 < @n]
};

declare %test:assertEquals(5) function rrhs:less-than-none() {
    count(collection("/db/rrhs-none")//w[15 < @n])
};

(: does 'k10x' start with @k: k1 and k10 :)
declare %test:assertEquals("k1", "k10") function rrhs:starts-with-literal-first-range() {
    collection("/db/rrhs-range")//w[starts-with('k10x', @k)]/@k/string()
};

declare %test:assertEquals("k1", "k10") function rrhs:starts-with-literal-first-legacy() {
    collection("/db/rrhs-legacy")//w[starts-with('k10x', @k)]/@k/string()
};

declare %test:assertEquals(1) function rrhs:eq-right-range() {
    count(collection("/db/rrhs-range")//w['k7' eq @k])
};

(: the value comparison keeps the path on the right: an a with two b raises XPTY0004 :)
declare %test:assertError("XPTY0004") function rrhs:eq-right-several-range() {
    collection("/db/rrhs-range")//a['v' eq b]
};

declare %test:assertError("XPTY0004") function rrhs:eq-right-several-legacy() {
    collection("/db/rrhs-legacy")//a['v' eq b]
};

declare %test:assertError("XPTY0004") function rrhs:eq-right-several-none() {
    collection("/db/rrhs-none")//a['v' eq b]
};

declare %test:assertEquals(3) function rrhs:atomic-context() {
    (1, 2, 3)[2 < .]
};
