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
 : A character class of an ngram wildcard query holds code points, not UTF-16 units: a class that contains a
 : supplementary character matches it, as the same class of fn:matches does.
 : See https://github.com/eXist-db/exist/issues/7090
 :)
module namespace cc="http://exist-db.org/xquery/ngram/char-class-supplementary/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $cc:SMP := codepoints-to-string(120120);

declare variable $cc:CONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index><ngram qname="ccp"/></index>
    </collection>;

declare variable $cc:XML :=
    <t>
        <ccp id="a">x{$cc:SMP}y</ccp>
        <ccp id="b">xay</ccp>
        <ccp id="c">xzy</ccp>
        <ccp id="d">x{$cc:SMP}{$cc:SMP}y</ccp>
    </t>;

declare %test:setUp function cc:setup() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    xmldb:create-collection("/db", "char-class-supplementary"),
    xmldb:create-collection("/db/system/config/db", "char-class-supplementary"),
    xmldb:store("/db/system/config/db/char-class-supplementary", "collection.xconf", $cc:CONF),
    xmldb:store("/db/char-class-supplementary", "t.xml", $cc:XML),
    xmldb:reindex("/db/char-class-supplementary")
};

declare %test:tearDown function cc:tearDown() {
    xmldb:remove("/db/char-class-supplementary"),
    xmldb:remove("/db/system/config/db/char-class-supplementary")
};

declare function cc:ids($nodes as node()*) as xs:string {
    let $r := string-join($nodes/@id/string(), ",")
    return if ($r = "") then "(none)" else $r
};

declare %test:assertEquals("a,b") function cc:as-written-class-with-supplementary() {
    cc:ids(collection("/db/char-class-supplementary")//ccp[matches(., "x[a" || $cc:SMP || "]y")])
};

declare %test:assertEquals("a,b") function cc:class-with-supplementary() {
    cc:ids(collection("/db/char-class-supplementary")//ccp[ngram:wildcard-contains(., "x[a" || $cc:SMP || "]y")])
};

declare %test:assertEquals("a") function cc:class-of-one-supplementary() {
    cc:ids(collection("/db/char-class-supplementary")//ccp[ngram:wildcard-contains(., "x[" || $cc:SMP || "]y")])
};

declare %test:assertEquals("b") function cc:class-without-supplementary() {
    cc:ids(collection("/db/char-class-supplementary")//ccp[ngram:wildcard-contains(., "x[ab]y")])
};
