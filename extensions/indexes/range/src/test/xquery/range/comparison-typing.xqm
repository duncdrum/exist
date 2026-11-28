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
 : An index compares the stored text as the type it was configured with, and it may answer a comparison only if the
 : comparison compares the text as that type: a value comparison and a string function compare it as xs:string, a
 : general comparison as the type of its key. Each query runs over the same data in four collections (no index, a
 : legacy index, a range index on the element and range fields) and has to give the answer the collection without an
 : index gives: the answer of the comparison as written, or the error it raises.
 :)
module namespace ct="http://exist-db.org/xquery/range/comparison-typing/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace stats="http://exist-db.org/xquery/profiling";

declare variable $ct:COLLECTIONS := ("none", "legacy", "range", "fields");

declare variable $ct:LEGACY_CONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <create qname="ctdt" type="xs:dateTime"/>
            <create qname="ctn" type="xs:integer"/>
            <create qname="cts" type="xs:string"/>
        </index>
    </collection>;

declare variable $ct:RANGE_CONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <range>
                <create qname="ctdt" type="xs:dateTime"/>
                <create qname="ctn" type="xs:integer"/>
                <create qname="cts" type="xs:string"/>
            </range>
        </index>
    </collection>;

declare variable $ct:FIELDS_CONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <range>
                <create qname="cte">
                    <field name="ctdt" match="ctdt" type="xs:dateTime"/>
                    <field name="ctn" match="ctn" type="xs:integer"/>
                    <field name="cts" match="cts" type="xs:string"/>
                </create>
            </range>
        </index>
    </collection>;

(: e1 and e2 hold the same instant, written differently: a key typed as a dateTime finds both, a string finds one :)
declare variable $ct:XML :=
    <t>
        <cte id="e1"><ctdt>2012-01-20T10:00:00Z</ctdt><ctn>1</ctn><cts>a</cts></cte>
        <cte id="e2"><ctdt>2012-01-20T11:00:00+01:00</ctdt><ctn>2</ctn><cts>b</cts></cte>
        <cte id="e3"><ctdt>2013-02-04T10:00:00Z</ctdt><ctn>3</ctn><cts>c</cts></cte>
    </t>;

(: a converter normalises the stored text before it is indexed as a date: 1918 is the date 1918-01-01 :)
declare variable $ct:CONVERTER_CONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <range>
                <create qname="cls" type="xs:string" converter="org.exist.indexing.range.conversion.LowerCaseTestConverter"/>
                <create qname="ctl">
                    <field name="clf" match="clf" type="xs:string" converter="org.exist.indexing.range.conversion.LowerCaseTestConverter"/>
                </create>
                <create qname="cnb" type="xs:string" converter="org.exist.indexing.range.conversion.NullForBlankTestConverter"/>
                <create qname="ctcd" type="xs:date" converter="org.exist.indexing.range.conversion.DateConverter"/>
                <create qname="ctc">
                    <field name="ctcf" match="ctcf" type="xs:date" converter="org.exist.indexing.range.conversion.DateConverter"/>
                </create>
            </range>
        </index>
    </collection>;

declare variable $ct:CONVERTER_XML :=
    <t>
        <cnb id="n1">foo</cnb>
        <cnb id="n2">bar</cnb>
        <cnb id="n3"> </cnb>
        <ctc id="c1"><ctcd>1918</ctcd><ctcf>1918</ctcf></ctc>
        <ctc id="c2"><ctcd>800-12-1</ctcd><ctcf>800-12-1</ctcf></ctc>
        <ctc id="c3"><ctcd>2000-01-01</ctcd><ctcf>2000-01-01</ctcf></ctc>
        <ctl id="l1"><cls>Foo</cls><clf>Foo</clf></ctl>
        <ctl id="l2"><cls>foo</cls><clf>foo</clf></ctl>
        <ctl id="l3"><cls>BAR</cls><clf>BAR</clf></ctl>
        <ctl id="l4"><cls> foo </cls><clf> foo </clf></ctl>
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
    ),
    xmldb:create-collection("/db", "ct-converter"),
    xmldb:create-collection("/db/system/config/db", "ct-converter"),
    xmldb:store("/db/system/config/db/ct-converter", "collection.xconf", $ct:CONVERTER_CONF),
    xmldb:store("/db/ct-converter", "t.xml", $ct:CONVERTER_XML),
    xmldb:reindex("/db/ct-converter")
};

declare
    %test:tearDown
function ct:tearDown() {
    for $c in $ct:COLLECTIONS
    return (
        xmldb:remove("/db/ct-" || $c),
        if ($c = "none") then () else xmldb:remove("/db/system/config/db/ct-" || $c)
    ),
    xmldb:remove("/db/ct-converter"),
    xmldb:remove("/db/system/config/db/ct-converter")
};

declare function ct:run($collection as xs:string, $predicate as xs:string) as xs:string {
    let $result := util:eval(
        "declare variable $k := 2; declare variable $d := xs:dateTime('2012-01-20T10:30:00Z'); "
        || "declare variable $str := 'b'; declare variable $str2 := '2012-01-20T10:00:00Z'; "
        || "try { string-join(collection('" || $collection || "')//cte[" || $predicate || "]/@id/string(), ',') } "
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

declare %test:assertEquals("ERR err:XPTY0004") function ct:gt-datetime() { ct:same("ctdt gt xs:dateTime('2012-01-20T10:30:00Z')") };
declare %test:assertEquals("ERR err:XPTY0004") function ct:eq-datetime() { ct:same("ctdt eq xs:dateTime('2012-01-20T10:00:00Z')") };
declare %test:assertEquals("ERR err:XPTY0004") function ct:eq-integer() { ct:same("ctn eq 2") };
declare %test:assertEquals("ERR err:XPTY0004") function ct:gt-integer() { ct:same("ctn gt 1") };
declare %test:assertEquals("ERR err:XPTY0004") function ct:eq-double() { ct:same("ctn eq 2.0e0") };
declare %test:assertEquals("ERR err:XPTY0004") function ct:eq-integer-variable() { ct:same("ctn eq $k") };
declare %test:assertEquals("ERR err:XPTY0004") function ct:gt-datetime-variable() { ct:same("ctdt gt $d") };

(: a string key on a typed index compares strings, not instants or numbers :)

declare %test:assertEquals("e1") function ct:eq-string-on-datetime() { ct:same("ctdt eq '2012-01-20T10:00:00Z'") };
declare %test:assertEquals("e1") function ct:eq-string-variable-on-datetime() { ct:same("ctdt eq $str2") };
declare %test:assertEquals("(none)") function ct:eq-string-on-integer() { ct:same("ctn eq '02'") };
declare %test:assertEquals("e2") function ct:eq-string-on-integer-same-text() { ct:same("ctn eq '2'") };

(: a value comparison takes one item :)

declare %test:assertEquals("ERR err:XPTY0004") function ct:eq-two-keys() { ct:same("cts eq ('a','b')") };

(: a general comparison compares the text as the type of its key: a string key compares strings :)

declare %test:assertEquals("e1") function ct:general-eq-string-on-datetime() { ct:same("ctdt = '2012-01-20T10:00:00Z'") };
declare %test:assertEquals("e1") function ct:general-eq-string-variable-on-datetime() { ct:same("ctdt = $str2") };
declare %test:assertEquals("(none)") function ct:general-eq-string-on-integer() { ct:same("ctn = '02'") };
declare %test:assertEquals("e2") function ct:general-eq-string-on-integer-same-text() { ct:same("ctn = '2'") };
declare %test:assertEquals("ERR err:FORG0001") function ct:general-eq-date-on-datetime() { ct:same("ctdt = xs:date('2012-01-20')") };

(: a numeric key the index would round compares as written :)

declare %test:assertEquals("(none)") function ct:general-eq-decimal-on-integer() { ct:same("ctn = 2.5") };
declare %test:assertEquals("e1,e2") function ct:general-lt-decimal-on-integer() { ct:same("ctn < 2.5") };
declare %test:assertEquals("e2") function ct:general-eq-double-on-integer() { ct:same("ctn = 2.0e0") };

(: a string function compares the text as a string :)

declare %test:assertEquals("e1,e2") function ct:contains-on-datetime() { ct:same("contains(ctdt, '2012')") };
declare %test:assertEquals("e2") function ct:starts-with-on-integer() { ct:same("starts-with(ctn, '2')") };
declare %test:assertEquals("e2") function ct:starts-with-on-string() { ct:same("starts-with(cts, 'b')") };

(: what an index may answer stays the same :)

declare %test:assertEquals("e2") function ct:eq-string() { ct:same("cts eq 'b'") };
declare %test:assertEquals("e2") function ct:eq-untyped() { ct:same("cts eq xs:untypedAtomic('b')") };
declare %test:assertEquals("e2") function ct:eq-string-variable() { ct:same("cts eq $str") };
declare %test:assertEquals("e3") function ct:general-gt-datetime() { ct:same("ctdt > xs:dateTime('2012-01-20T10:30:00Z')") };
declare %test:assertEquals("e1,e2") function ct:general-eq-datetime() { ct:same("ctdt = xs:dateTime('2012-01-20T10:00:00Z')") };
declare %test:assertEquals("e2") function ct:general-eq-integer() { ct:same("ctn = 2") };

(: the index is used where it may answer, so that the rows above are not answered by a scan everywhere :)

declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'range'][@optimization-level eq 'OPTIMIZED'])")
function ct:legacy-answers-a-string-key() {
    collection("/db/ct-legacy")//cte[cts eq "b"]/@id/string()
};

declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'range'][@optimization-level eq 'OPTIMIZED'])")
function ct:legacy-answers-a-general-comparison() {
    collection("/db/ct-legacy")//cte[ctdt > xs:dateTime("2012-01-20T10:30:00Z")]/@id/string()
};

declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function ct:range-answers-a-string-key() {
    collection("/db/ct-range")//cte[cts eq "b"]/@id/string()
};

declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function ct:range-answers-a-general-comparison() {
    collection("/db/ct-range")//cte[ctdt > xs:dateTime("2012-01-20T10:30:00Z")]/@id/string()
};

declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'new-range'])")
function ct:fields-answer-a-string-key() {
    collection("/db/ct-fields")//cte[cts eq "b"]/@id/string()
};

declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'new-range'])")
function ct:fields-answer-a-general-comparison() {
    collection("/db/ct-fields")//cte[ctdt > xs:dateTime("2012-01-20T10:30:00Z")]/@id/string()
};

(:
 : An index with a converter finds what the converter makes of the stored text, which a comparison as written does not:
 : 1918 is the date 1918-01-01 to the index, and the text "1918" to a string comparison. A key typed as a date asks the
 : index the question it answers; a string key compares strings, as it does without a converter, and the index steps aside.
 :)

declare %test:assertEquals("c1") function ct:converter-date-key-finds-the-normalised-year() {
    collection("/db/ct-converter")//ctc[ctcd = xs:date("1918-01-01")]/@id/string()
};

declare %test:assertEquals("c2") function ct:converter-date-key-finds-the-filled-in-digits() {
    collection("/db/ct-converter")//ctc[ctcd = xs:date("0800-12-01")]/@id/string()
};

declare %test:assertEquals("c1") function ct:converter-field-date-key-finds-the-normalised-year() {
    collection("/db/ct-converter")//ctc[ctcf = xs:date("1918-01-01")]/@id/string()
};

declare %test:assertEquals("c3") function ct:converter-string-key-is-compared-as-written() {
    collection("/db/ct-converter")//ctc[ctcd = "2000-01-01"]/@id/string()
};

declare %test:assertEmpty function ct:converter-string-key-does-not-find-the-normalised-year() {
    collection("/db/ct-converter")//ctc[ctcd = "1918-01-01"]/@id/string()
};

declare %test:assertEmpty function ct:converter-field-string-key-does-not-find-the-normalised-year() {
    collection("/db/ct-converter")//ctc[ctcf = "1918-01-01"]/@id/string()
};

declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function ct:converter-answers-a-date-key() {
    collection("/db/ct-converter")//ctc[ctcd = xs:date("1918-01-01")]/@id/string()
};

declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'new-range'])")
function ct:converter-field-answers-a-date-key() {
    collection("/db/ct-converter")//ctc[ctcf = xs:date("1918-01-01")]/@id/string()
};

declare
    %test:stats
    %test:assertXPath("empty($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function ct:converter-steps-aside-for-a-string-key() {
    collection("/db/ct-converter")//ctc[ctcd = "1918-01-01"]/@id/string()
};

declare
    %test:stats
    %test:assertXPath("empty($result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED'])")
function ct:converter-field-steps-aside-for-a-string-key() {
    collection("/db/ct-converter")//ctc[ctcf = "1918-01-01"]/@id/string()
};

(:
 : A converter on a string index changes the text the index compares, here to lower case without the surrounding
 : whitespace. As with {@code case="no"}, that is the index's own comparison when the query names no collation. When the
 : query names one, the index steps aside and the collation compares the text as it is stored.
 :)

declare %test:assertEquals("l1,l2,l4") function ct:converter-string-answers-with-the-text-it-indexed() {
    string-join(collection("/db/ct-converter")//ctl[cls = "foo"]/@id/string(), ",")
};

declare %test:assertEquals("l1,l2,l4") function ct:converter-string-field-answers-with-the-text-it-indexed() {
    string-join(collection("/db/ct-converter")//ctl[clf = "foo"]/@id/string(), ",")
};

declare %test:assertEquals("l1,l2") function ct:converter-string-steps-aside-for-a-named-collation() {
    string-join(collection("/db/ct-converter")//ctl[starts-with(cls, "FO", "http://www.w3.org/2013/collation/UCA?lang=en;strength=primary")]/@id/string(), ",")
};

declare %test:assertEquals("l1,l2") function ct:converter-string-field-steps-aside-for-a-named-collation() {
    string-join(collection("/db/ct-converter")//ctl[starts-with(clf, "FO", "http://www.w3.org/2013/collation/UCA?lang=en;strength=primary")]/@id/string(), ",")
};

declare %test:assertEquals("l1,l2") function ct:converter-string-steps-aside-for-the-default-collation() {
    string-join(util:eval("declare default collation 'http://www.w3.org/2013/collation/UCA?lang=en;strength=primary'; collection('/db/ct-converter')//ctl[cls = 'FOO']/@id/string()"), ",")
};

(:
 : The key goes through the converter as the stored text did: the index holds "foo" for Foo, foo and " foo ", so the key
 : Foo, FOO or " foo " finds all three, whichever operator asks. A pattern is not text and is not converted.
 :)

declare %test:assertEquals("l1,l2,l4") function ct:converter-string-key-is-converted() {
    string-join(collection("/db/ct-converter")//ctl[cls = "Foo"]/@id/string(), ",")
};

declare %test:assertEquals("l1,l2,l4") function ct:converter-string-field-key-is-converted() {
    string-join(collection("/db/ct-converter")//ctl[clf = " FOO "]/@id/string(), ",")
};

declare %test:assertEquals("l3") function ct:converter-string-key-is-converted-for-not-equal() {
    string-join(collection("/db/ct-converter")//ctl[cls != "FOO"]/@id/string(), ",")
};

declare %test:assertEquals("l1,l2,l4") function ct:converter-string-key-is-converted-for-starts-with() {
    string-join(collection("/db/ct-converter")//ctl[starts-with(cls, "Fo")]/@id/string(), ",")
};

declare %test:assertEquals("l1,l2,l4") function ct:converter-string-key-is-converted-for-contains() {
    string-join(collection("/db/ct-converter")//ctl[contains(cls, "OO")]/@id/string(), ",")
};

declare %test:assertEquals("l1,l2,l4") function ct:converter-string-key-is-converted-for-ends-with() {
    string-join(collection("/db/ct-converter")//ctl[ends-with(cls, "OO")]/@id/string(), ",")
};

declare %test:assertEquals("l3") function ct:converter-string-key-is-converted-for-less-than() {
    string-join(collection("/db/ct-converter")//ctl[cls < "FOO"]/@id/string(), ",")
};

(:
 : A key that the converter does not index (here a blank text, which the converter leaves out of the index) is equal to no
 : indexed text, so a comparison with it finds nothing, and != finds every node the converter indexed. The stored blank
 : text is not indexed, so the index and the comparison as written differ here, by design of the converter.
 :)

declare %test:assertEquals("(none)") function ct:converter-key-not-indexed-finds-nothing() {
    let $r := string-join(collection("/db/ct-converter")//cnb[. = " "]/@id/string(), ",")
    return if ($r = "") then "(none)" else $r
};

declare %test:assertEquals("(none)") function ct:converter-key-not-indexed-starts-with-finds-nothing() {
    let $r := string-join(collection("/db/ct-converter")//cnb[starts-with(., " ")]/@id/string(), ",")
    return if ($r = "") then "(none)" else $r
};

declare %test:assertEquals("n1,n2") function ct:converter-key-not-indexed-not-equal-finds-the-indexed-nodes() {
    string-join(collection("/db/ct-converter")//cnb[. != " "]/@id/string(), ",")
};

