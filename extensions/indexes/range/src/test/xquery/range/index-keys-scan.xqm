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
 : What a scan of the range index gives (util:index-keys) over many terms, with a maximum, and over documents stored one
 : after the other. The scan is written
 : once in each of the two index modules, and this pins what they do before the code is shared.
 :)
module namespace rks="http://exist-db.org/xquery/range/test/index-keys-scan";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $rks:ROOT := "/db/index-keys-scan-range";
declare variable $rks:CONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <index><range><create qname="p" type="xs:string"/></range></index>
    </collection>;

declare %test:setUp function rks:setup() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    xmldb:create-collection("/db", "index-keys-scan-range"),
    xmldb:create-collection("/db/system/config/db", "index-keys-scan-range"),
    xmldb:store("/db/system/config/db/index-keys-scan-range", "collection.xconf", $rks:CONF)
};

declare %test:tearDown function rks:tearDown() {
    xmldb:remove($rks:ROOT),
    xmldb:remove("/db/system/config/db/index-keys-scan-range")
};

(: a collection of its own below the root, with one document for each of the given word lists, each word an element p :)
declare function rks:store($name as xs:string, $docs as array(xs:string*)) as xs:string {
    let $coll := xmldb:create-collection($rks:ROOT, $name)
    let $stored :=
        for $d at $i in $docs?*
        return xmldb:store($coll, "d" || $i || ".xml", <t>{ for $w in $d return <p>{$w}</p> }</t>)
    return $coll
};

declare function rks:key($key as xs:string, $options as item()*) as element(t) {
    <t term="{$key}" frequency="{$options[1]}"/>
};

(: the terms the scan gives, as term:frequency, in the order given :)
declare function rks:scan($coll as xs:string, $start as xs:string, $max as xs:integer) as xs:string* {
    collection($coll)/util:index-keys-by-qname(xs:QName("p"), $start, util:function(xs:QName("rks:key"), 2), $max, "range-index")
        ! (@term || ":" || @frequency)
};

declare function rks:words($n as xs:integer) as xs:string* {
    for $i in 1 to $n return "w" || format-integer($i, "000")
};

declare function rks:all($coll as xs:string) as xs:string {
    string-join(rks:scan($coll, "", 100), " ")
};

declare %test:assertEquals(300, "w001:1", "w300:1")
function rks:many-terms() {
    let $c := rks:store("many", [rks:words(300)])
    let $s := rks:scan($c, "", 1000)
    return (count($s), $s[1], $s[last()])
};

(: the start value is the start of a term: w15 is w150 to w159 :)
declare %test:assertEquals("w150=1|w15=10|w1=100|w299=1|x=0")
function rks:start-value() {
    let $c := rks:store("start", [rks:words(300)])
    return string-join(for $st in ("w150", "w15", "w1", "w299", "x") return $st || "=" || count(rks:scan($c, $st, 1000)), "|")
};

declare %test:assertEquals("w001:1 w002:1 w003:1")
function rks:max-stops-the-scan() {
    let $c := rks:store("max", [rks:words(300)])
    return string-join(rks:scan($c, "", 3), " ")
};

(: three documents stored one after the other, which Lucene may write as several segments: a term counts in every one :)
declare %test:assertEquals("cat:3 dog:2 emu:2")
function rks:terms-are-added-up-over-documents() {
    let $c := rks:store("added", [("cat", "dog"), ("cat", "emu"), ("cat", "dog", "emu")])
    return rks:all($c)
};

(: GitHub #4805: with a maximum, the terms that are given have their count over all documents :)
declare %test:assertEquals("cat:3 dog:2")
function rks:max-and-several-documents() {
    let $c := rks:store("maxdocs", [("cat", "dog"), ("cat", "emu"), ("cat", "dog", "emu")])
    return string-join(rks:scan($c, "", 2), " ")
};



(: a document that is stored again replaces the terms of the old one :)
