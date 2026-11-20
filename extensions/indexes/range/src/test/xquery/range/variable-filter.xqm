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

(:
 : A comparison in a predicate on a variable is answered by the index like the same comparison on the path (#873, #5912):
 : let $i := collection(...)//w return $i[@k = 'k7'] reads like collection(...)//w[@k = 'k7']. The range rewriter
 : rewrites the comparison to its lookup, which keeps the comparison to fall back on, and the optimizer pre-selects
 : for the variable's nodes. Nothing moves, so the answer must not change for any value of the variable: nodes of a
 : collection with and without an index, nodes in memory, atomic values, and operands that depend on the context.
 :)
module namespace rvf="http://exist-db.org/xquery/range/variable-filter/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace stats="http://exist-db.org/xquery/profiling";

declare variable $rvf:C := "/db/rvf-range";
declare variable $rvf:BARE := "/db/rvf-bare";

declare variable $rvf:CONFIG :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"><range>
            <create qname="@k" type="xs:string"/>
            <create qname="@rn" type="xs:integer"/>
            <create qname="id" type="xs:string"/>
        </range></index>
    </collection>;

declare
    %test:setUp
function rvf:setup() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    xmldb:create-collection("/db/system/config/db", "rvf-range"),
    xmldb:store("/db/system/config/db/rvf-range", "collection.xconf", $rvf:CONFIG),
    xmldb:create-collection("/db", "rvf-range"),
    xmldb:store($rvf:C, "d1.xml", <doc>{for $i in 1 to 20 return <w k="k{$i}" rn="{$i}"><id>id-{$i}</id></w>}</doc>),
    xmldb:store($rvf:C, "d2.xml", <doc>{for $i in 1 to 5 return <w k="j{$i}" rn="{$i}"><id>jd-{$i}</id></w>}</doc>),
    xmldb:create-collection("/db", "rvf-bare"),
    xmldb:store($rvf:BARE, "b.xml", <doc>{for $i in 1 to 3 return <w k="k{$i}" rn="{$i}"><id>id-{$i}</id></w>}</doc>),
    xmldb:store($rvf:BARE, "e.xml", <doc/>)
};

declare
    %test:tearDown
function rvf:teardown() {
    xmldb:remove($rvf:C),
    xmldb:remove($rvf:BARE),
    xmldb:remove("/db/system/config/db/rvf-range")
};

(: the index answers the comparison, pre-selected for the nodes of the variable :)
declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function rvf:attribute-uses-index() {
    let $i := collection($rvf:C)//w
    return $i[@k = 'k7']
};

declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function rvf:number-uses-index() {
    let $i := collection($rvf:C)//w
    return $i[@rn < 3]
};

declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function rvf:child-uses-index() {
    let $i := collection($rvf:C)//w
    return $i[id = 'id-7']
};

declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function rvf:in-function-call-uses-index() {
    let $i := collection($rvf:C)//w
    return count($i[@k = 'k7'])
};

declare %test:assertEquals("k7") function rvf:attribute-answer() {
    let $i := collection($rvf:C)//w
    return $i[@k = 'k7']/@k/string()
};

declare %test:assertEquals(4) function rvf:number-answer() {
    let $i := collection($rvf:C)//w
    return count($i[@rn < 3])
};

declare %test:assertEquals("id-7") function rvf:child-answer() {
    let $i := collection($rvf:C)//w
    return $i[id = 'id-7']/id/string()
};

declare %test:assertEquals(1) function rvf:in-function-call-answer() {
    let $i := collection($rvf:C)//w
    return count($i[@k = 'k7'])
};

(: a function whose body is the filter on a parameter, the usual helper: the parameter is the variable :)
declare function rvf:pick($n as element()*, $k as xs:string) { $n[@k = $k] };
declare function rvf:pick-untyped($n, $k) { $n[@k = $k] };
declare function rvf:pick-child($n as element()*, $k) { $n[id = $k] };

declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function rvf:function-parameter-uses-index() {
    count(rvf:pick(collection($rvf:C)//w, 'k7'))
};

declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function rvf:untyped-parameter-uses-index() {
    count(rvf:pick-untyped(collection($rvf:C)//w, 'k7'))
};

declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function rvf:child-parameter-uses-index() {
    count(rvf:pick-child(collection($rvf:C)//w, 'id-7'))
};

declare %test:assertEquals(1) function rvf:function-parameter-answer() {
    count(rvf:pick(collection($rvf:C)//w, 'k7'))
};

declare %test:assertEquals("k7") function rvf:function-parameter-in-memory() {
    rvf:pick(<r><w k="k7"/><w k="k8"/></r>//w, 'k7')/@k/string()
};

declare %test:assertEquals(2) function rvf:function-parameter-mixed-configurations() {
    count(rvf:pick((collection($rvf:C)//w, collection($rvf:BARE)//w), 'k2'))
};

(: the function is called again, over nodes of another kind: the first call leaves nothing behind (#7058) :)
declare %test:assertEquals(0, 1, 2) function rvf:function-parameter-called-again() {
    count(rvf:pick((), 'k2')),
    count(rvf:pick(collection($rvf:C)//w, 'k2')),
    count(rvf:pick((collection($rvf:C)//w, collection($rvf:BARE)//w), 'k2'))
};

(: #7058: the variable is empty in the first iteration, a stored document without a w :)
declare %test:assertEquals(0, 1) function rvf:empty-then-hit-in-a-loop() {
    for $m in (doc($rvf:BARE || "/e.xml")/doc, doc($rvf:C || "/d1.xml")/doc)
    let $d := $m/w
    return count($d[@k = 'k7'])
};

(: the key is a variable, bound by let or by a loop :)
declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function rvf:variable-key-uses-index() {
    let $k := 'k7'
    let $i := collection($rvf:C)//w
    return count($i[@k = $k])
};

declare %test:assertEquals(1, 1, 0) function rvf:loop-key() {
    for $k in ('k7', 'j2', 'none')
    let $i := collection($rvf:C)//w
    return count($i[@k = $k])
};

(: the variable is used again :)
declare %test:assertEquals("k7", 25) function rvf:used-twice() {
    let $i := collection($rvf:C)//w
    return ($i[@k = 'k7']/@k/string(), count($i))
};

declare %test:assertEquals("k7") function rvf:typed-binding() {
    let $i as element(w)* := collection($rvf:C)//w
    return $i[@k = 'k7']/@k/string()
};

(: (//w)[1] is one element, whichever document comes first; the predicate on the step would find k7 :)
declare %test:assertEquals(1) function rvf:filtered-input() {
    let $i := (collection($rvf:C)//w)[1]
    return count($i[@k = ('k1', 'j1')])
};

declare %test:assertEmpty function rvf:filtered-input-no-match() {
    let $i := (collection($rvf:C)//w)[1]
    return $i[@k = 'k7']
};

(: last() is the size of the whole sequence, 25: no rn is 25 :)
declare %test:assertEmpty function rvf:size-operand() {
    let $i := collection($rvf:C)//w
    return $i[@rn = last()]
};

(: an operand of the comparison that depends on the item filtered: every w has its own k :)
declare %test:assertEquals(25) function rvf:context-operand() {
    let $i := collection($rvf:C)//w
    return count($i[@k = string(@k)])
};

declare %test:assertEquals(25) function rvf:position-operand() {
    let $i := collection($rvf:C)//w
    return count($i[@rn >= position() - 25])
};

(: the same operand on a path, with a collection without the index as the reference :)
declare %test:assertEquals(3) function rvf:path-context-operand-no-index() {
    count(collection($rvf:BARE)//w[@k = string(@k)])
};

(: not this change, #6956: the step rewrite evaluates an operand that depends on the item once, and finds nothing; 25 is right :)
declare %test:pending("#6956: an operand that depends on the item is evaluated once by the lookup of a step") %test:assertEquals(25) function rvf:path-context-operand() {
    count(collection($rvf:C)//w[@k = string(@k)])
};

(: nodes in memory, no index to ask :)
declare %test:assertEquals("k7") function rvf:in-memory-nodes() {
    let $i := <r><w k="k7"/><w k="k8"/></r>//w
    return $i[@k = 'k7']/@k/string()
};

(: atomic values :)
declare %test:assertEquals(2) function rvf:atomic-values() {
    let $v := (1, 2, 3)
    return $v[. = 2]
};

(: nodes of a collection without the index and of one with it in one variable :)
declare %test:assertEquals(2) function rvf:mixed-configurations() {
    let $i := (collection($rvf:C)//w, collection($rvf:BARE)//w)
    return count($i[@k = 'k2'])
};

declare %test:assertEquals(1) function rvf:collection-without-index() {
    let $i := collection($rvf:BARE)//w
    return count($i[@k = 'k2'])
};
