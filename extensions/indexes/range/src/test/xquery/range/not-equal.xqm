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
 : A comparison with != on a number, a date or a time is answered for the nodes that have a value, as the comparison
 : as written is: an element with no such value, or with other indexed names only, does not match. Each query runs over
 : the same data in a collection without an index and in one with a range index, and the answer has to be the one
 : without the index.
 :)
module namespace nt="http://exist-db.org/xquery/range/not-equal/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace stats="http://exist-db.org/xquery/profiling";

declare variable $nt:COLLECTIONS := ("none", "range");

declare variable $nt:CONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <range>
                <create qname="nen" type="xs:integer"/>
                <create qname="nex" type="xs:integer"/>
                <create qname="ned" type="xs:date"/>
                <create qname="nes" type="xs:string"/>
            </range>
        </index>
    </collection>;

declare variable $nt:XML :=
    <t>
        <e id="e1"><nen>5</nen><ned>2012-01-20</ned><nes>a</nes></e>
        <e id="e2"><nen>6</nen><ned>2012-01-21</ned><nes>b</nes></e>
        <e id="e3"><nex>1</nex><nes>c</nes></e>
        <e id="e4"><nen>7</nen><ned>2012-01-22</ned><nes>d</nes></e>
        <e id="e5"><nes>e</nes></e>
    </t>;

declare
    %test:setUp
function nt:setup() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    for $c in $nt:COLLECTIONS
    let $name := "ne-" || $c
    return (
        xmldb:create-collection("/db", $name),
        if ($c = "none") then () else (
            xmldb:create-collection("/db/system/config/db", $name),
            xmldb:store("/db/system/config/db/" || $name, "collection.xconf", $nt:CONF)
        ),
        xmldb:store("/db/" || $name, "t.xml", $nt:XML),
        xmldb:reindex("/db/" || $name)
    )
};

declare
    %test:tearDown
function nt:tearDown() {
    for $c in $nt:COLLECTIONS
    return (
        xmldb:remove("/db/ne-" || $c),
        if ($c = "none") then () else xmldb:remove("/db/system/config/db/ne-" || $c)
    )
};

(:~
 : The answer both collections give, or both of them if they differ.
 :)
declare function nt:same($predicate as xs:string) as xs:string {
    let $results := $nt:COLLECTIONS ! (
        let $r := util:eval("string-join(collection('/db/ne-" || . || "')//e[" || $predicate || "]/@id/string(), ',')")
        return if ($r = "") then "(none)" else $r)
    return
        if (count(distinct-values($results)) = 1)
        then $results[1]
        else string-join(for $i in 1 to count($results) return $nt:COLLECTIONS[$i] || ": " || $results[$i], " | ")
};

declare %test:assertEquals("e2,e4") function nt:integer-not-equal() { nt:same("nen != 5") };
declare %test:assertEquals("e1,e2,e4") function nt:integer-not-equal-a-value-nobody-has() { nt:same("nen != 8") };
declare %test:assertEquals("e1,e2,e4") function nt:date-not-equal-a-value-nobody-has() { nt:same("ned != xs:date('2012-02-01')") };
declare %test:assertEquals("e1,e4") function nt:date-not-equal() { nt:same("ned != xs:date('2012-01-21')") };

(: a string index answered != for its own field before; it still does :)

declare %test:assertEquals("e1,e3,e4,e5") function nt:string-not-equal() { nt:same("nes != 'b'") };

(: the other comparisons of the same index :)

declare %test:assertEquals("e2,e4") function nt:integer-greater-than() { nt:same("nen > 5") };
declare %test:assertEquals("e1") function nt:integer-equal() { nt:same("nen = 5") };

(: the index is used where it may answer :)

declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function nt:not-equal-uses-the-index() {
    collection("/db/ne-range")//e[nen != 5]/@id/string()
};
