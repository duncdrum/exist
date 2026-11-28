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
 : Reference checks: every fn:matches the range index answers must give the answer of fn:matches over
 : the same values without an index. The patterns and flags are generated from building blocks, the
 : values cover the characters on which Lucene's and XPath's regular expressions are known to differ
 : (case variants, newlines, digits and word characters outside ASCII, supplementary characters, and
 : Lucene's operators). Each indexed query is compiled with literal arguments, so that the optimizer
 : rewrites it to the range index; the reference is the same fn:matches call on in-memory copies.
 :
 : The same is checked for contains, starts-with and ends-with with keys that hold the characters a wildcard query
 : reads as syntax, and for the general comparisons with a string key and with a number, where the elements that
 : have no value in the index must not be found by != (https://github.com/eXist-db/exist/issues/7085 and 7086).
 :)
module namespace rm = "http://exist-db.org/xquery/test/range/reference-matches";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

declare variable $rm:COLLECTION := "/db/reference-matches";
declare variable $rm:CONFIG := "/db/system/config" || $rm:COLLECTION;

declare variable $rm:VALUES := (
    "ham", "Ham", "HAM", "hamburg", "bacon", "",
    "a" || codepoints-to-string(10) || "b", "tab" || codepoints-to-string(9) || "here",
    "k", "K", codepoints-to-string(8490), "s", "S", codepoints-to-string(383),
    "i", "I", codepoints-to-string(304), codepoints-to-string(305), "straße", "STRASSE",
    "x_y", "x y", "123", codepoints-to-string((1633, 1634, 1635)),
    codepoints-to-string(128512), "a" || codepoints-to-string(128512) || "b", codepoints-to-string(119964),
    "$5", "a.b", "a*b", "a&amp;b", "~x", "#1", "@home", "&lt;3&gt;", '"q"', "a\b", "aaa", "ab", "ba"
);

(: the building blocks: literals, classes, quantifiers, Lucene's operators, constructs Lucene lacks :)
declare variable $rm:ATOMS := (
    "ham", "a", "b", "k", "s", "i", ".", ".*", ".+", "a?", "a{2}", "\d", "\w", "\s", "\S", "[a-c]", "[^a]",
    "[a-z-[aeiou]]", "\p{Lu}", "\P{L}", "(a|b)", "a|b", "&amp;", "~", "#", "@", "&lt;3&gt;", '"', "\.", "\\",
    "\n", "\t", "x y", "ß", "", "\$"
);

(: the blocks the range index serves itself, not handing them to fn:matches :)
declare variable $rm:SERVED := (
    "ham", "a", "b", "k", "s", "i", ".", ".*", ".+", "a?", "a{2}", "[a-c]", "[^a]", "(a|b)", "&amp;", "~", "#", "@",
    "&lt;3&gt;", '"', "\.", "\\", "x y", "ß", "", "\$"
);

declare variable $rm:FLAGS := ("", "i", "s", "m", "x", "q", "is");

(: the keys of contains, starts-with, ends-with and the general comparisons with a string: the characters of a wildcard
   query, of Lucene's regular expressions, a supplementary character, and the empty string :)
declare variable $rm:KEYS := (
    "a", "b", "ab", "ba", "*", "?", "\", "a*b", "a?b", "a\b", "*b", "?b", "b*", "a*", "ham", "ß", "x y", ".", "$",
    "&amp;", "~", "#", "@", "&lt;", '"', codepoints-to-string(128512), ""
);

(: numbers for the general comparisons, and the keys to compare them with, some that no value has :)
declare variable $rm:NUMBERS := (1, 2, 5, -3, 100);
declare variable $rm:NUMBER-KEYS := ($rm:NUMBERS, 0, 7);

declare function rm:patterns($atoms as xs:string*) as xs:string* {
    distinct-values((
        for $a in $atoms
        return ($a, "^" || $a, $a || "$", "^" || $a || "$"),
        for $a in $atoms[position() le 12], $b in $atoms[position() le 12]
        return ($a || $b, "^" || $a || ".*" || $b || "$")
    ))
};

declare
    %test:setUp
function rm:setup() {
    let $_ := xmldb:create-collection("/db", substring-after($rm:COLLECTION, "/db/"))
    let $_ := xmldb:create-collection("/db/system/config/db", substring-after($rm:COLLECTION, "/db/"))
    let $_ := xmldb:store($rm:CONFIG, "collection.xconf",
        <collection xmlns="http://exist-db.org/collection-config/1.0">
            <index><range><create qname="v" type="xs:string"/><create qname="n" type="xs:integer"/></range></index>
        </collection>)
    (: each value in a v of its own w: fn:matches on the child v is what the range index serves itself, through
       the translator; on . the predicate is left to fn:matches' own index path :)
    (: numbers in an m of their own, next to an m that holds only a v and one that holds nothing: elements that are
       indexed under another name, or not at all, and have no n :)
    return xmldb:store($rm:COLLECTION, "values.xml", <values>{
        $rm:VALUES ! <w><v>{ . }</v></w>,
        $rm:NUMBERS ! <m><n>{ . }</n></m>,
        <m><v>x</v></m>, <m/>
    }</values>)
};

declare
    %test:tearDown
function rm:teardown() {
    xmldb:remove($rm:COLLECTION),
    xmldb:remove($rm:CONFIG)
};

declare %private function rm:literal($s as xs:string) as xs:string {
    "'" || replace(replace($s, "&amp;", "&amp;amp;"), "'", "''") || "'"
};

(: the answer, or the error code: an invalid pattern must be an error on both sides :)
declare %private function rm:indexed($pattern as xs:string, $flags as xs:string) as xs:string {
    try {
        string(util:eval("count(collection(" || rm:literal($rm:COLLECTION) || ")//w[matches(v, "
            || rm:literal($pattern) || ", " || rm:literal($flags) || ")])"))
    } catch * {
        "error " || local-name-from-QName($err:code)
    }
};

declare %private function rm:reference($pattern as xs:string, $flags as xs:string) as xs:string {
    try {
        string(count($rm:VALUES[matches(., $pattern, $flags)]))
    } catch * {
        "error " || local-name-from-QName($err:code)
    }
};

(: the differences, as "pattern | flags | index | reference" :)
declare %private function rm:differences($patterns as xs:string*, $flags as xs:string*) as xs:string* {
    for $pattern in $patterns, $f in $flags
    let $indexed := rm:indexed($pattern, $f)
    let $reference := rm:reference($pattern, $f)
    where $indexed ne $reference
    return string-join(($pattern, $f, $indexed, $reference), " | ")
};

(: the patterns the range index serves itself, under the flags it serves (none and i) :)
declare
    %test:assertEmpty
function rm:index-matches-like-fn-matches() {
    rm:differences(rm:patterns($rm:SERVED), ("", "i"))
};

(: every pattern under every flag, including those the index hands to fn:matches; fn:matches' own index path
   evaluates them with Java's regular expressions, see https://github.com/eXist-db/exist/pull/6747 :)
declare
    %test:assertEmpty
function rm:index-or-fallback-matches-like-fn-matches() {
    rm:differences(rm:patterns($rm:ATOMS), $rm:FLAGS)
};

(: a string function or comparison with a key: the count, or the error code :)
declare %private function rm:count-indexed($before as xs:string, $key as xs:string, $after as xs:string) as xs:string {
    try {
        string(util:eval("count(collection(" || rm:literal($rm:COLLECTION) || ")//w[" || $before || rm:literal($key) || $after || "])"))
    } catch * {
        "error " || local-name-from-QName($err:code)
    }
};

declare %private function rm:count-reference($function as xs:string, $key as xs:string) as xs:string {
    try {
        string(switch ($function)
            case "contains" return count($rm:VALUES[contains(., $key)])
            case "starts-with" return count($rm:VALUES[starts-with(., $key)])
            default return count($rm:VALUES[ends-with(., $key)]))
    } catch * {
        "error " || local-name-from-QName($err:code)
    }
};

(: contains, starts-with and ends-with with a key that the index turns into a wildcard or prefix query :)
declare
    %test:assertEmpty
function rm:index-string-functions-like-fn() {
    for $function in ("contains", "starts-with", "ends-with"), $key in $rm:KEYS
    let $indexed := rm:count-indexed($function || "(v, ", $key, ")")
    let $reference := rm:count-reference($function, $key)
    where $indexed ne $reference
    return string-join(($function, $key, $indexed, $reference), " | ")
};

(: != and = on a string index: != is the nodes of the field that hold another value :)
declare
    %test:assertEmpty
function rm:index-string-comparisons-like-general-comparison() {
    for $key in $rm:KEYS
    let $indexed := (rm:count-indexed("v != ", $key, ""), rm:count-indexed("v = ", $key, ""))
    let $reference := (string(count($rm:VALUES[. != $key])), string(count($rm:VALUES[. = $key])))
    where not(deep-equal($indexed, $reference))
    return string-join(($key, string-join($indexed, ","), string-join($reference, ",")), " | ")
};

(: the six general comparisons on a number index: a != must not find an m that has no n, or only a v :)
declare
    %test:assertEmpty
function rm:index-number-comparisons-like-general-comparison() {
    for $operator in ("=", "!=", "&lt;", "&lt;=", "&gt;", "&gt;="), $key in $rm:NUMBER-KEYS
    let $indexed := try { string(util:eval("count(collection(" || rm:literal($rm:COLLECTION) || ")//m[n " || $operator || " " || $key || "])")) }
                    catch * { "error " || local-name-from-QName($err:code) }
    let $reference := try { string(util:eval("count(" || "(" || string-join($rm:NUMBERS ! string(.), ",") || ")[. " || $operator || " " || $key || "])")) }
                      catch * { "error " || local-name-from-QName($err:code) }
    where $indexed ne $reference
    return string-join(($operator, string($key), $indexed, $reference), " | ")
};

(: not vacuous: the keys tell the values apart, and != finds some elements and not others :)
declare
    %test:assertTrue
function rm:the-key-reference-is-not-trivial() {
    let $contains := for $key in $rm:KEYS return rm:count-reference("contains", $key)
    let $not-equal := for $key in $rm:NUMBER-KEYS return count($rm:NUMBERS[. != $key])
    return count(distinct-values($contains)) gt 5 and count(distinct-values($not-equal)) gt 1 and count($rm:NUMBERS) lt count($rm:NUMBERS) + 3
};

(: not vacuous: the served patterns match different numbers of values, so the comparison tells them apart :)
declare
    %test:assertTrue
function rm:the-reference-is-not-trivial() {
    let $answers := for $pattern in rm:patterns($rm:SERVED), $f in ("", "i") return rm:reference($pattern, $f)
    return count(distinct-values($answers)) gt 10 and count($answers[. ne "0"]) gt 100
};
