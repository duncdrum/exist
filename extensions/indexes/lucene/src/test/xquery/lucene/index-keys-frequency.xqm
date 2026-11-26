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
 : The frequency that util:index-keys gives for a term of the full-text index is the number of times the term occurs,
 : and not the number of nodes that hold it. See https://github.com/eXist-db/exist/issues/7089
 :)
module namespace ikf="http://exist-db.org/xquery/lucene/index-keys-frequency/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $ikf:CONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index><lucene><text qname="ikfp"/></lucene></index>
    </collection>;

declare variable $ikf:XML :=
    <t>
        <ikfp>cat cat cat</ikfp>
        <ikfp>cat hat</ikfp>
        <ikfp>hat</ikfp>
    </t>;

declare %test:setUp function ikf:setup() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    xmldb:create-collection("/db", "index-keys-frequency"),
    xmldb:create-collection("/db/system/config/db", "index-keys-frequency"),
    xmldb:store("/db/system/config/db/index-keys-frequency", "collection.xconf", $ikf:CONF),
    xmldb:store("/db/index-keys-frequency", "t.xml", $ikf:XML),
    xmldb:reindex("/db/index-keys-frequency")
};

declare %test:tearDown function ikf:tearDown() {
    xmldb:remove("/db/index-keys-frequency"),
    xmldb:remove("/db/system/config/db/index-keys-frequency")
};

declare function ikf:key($key as xs:string, $options as item()*) as element(t) {
    <t term="{$key}" frequency="{$options[1]}"/>
};

declare function ikf:frequency($term as xs:string) as xs:string {
    string(collection("/db/index-keys-frequency")/util:index-keys-by-qname(xs:QName("ikfp"), $term,
        util:function(xs:QName("ikf:key"), 2), 10, "lucene-index")[@term = $term]/@frequency)
};

(: a term that occurs once in each node it is in :)
declare %test:assertEquals("2") function ikf:one-occurrence-in-each-node() { ikf:frequency("hat") };

(: cat occurs three times in one node and once in another: four times, in two nodes :)
declare %test:pending("The frequency is the number of nodes, see https://github.com/eXist-db/exist/issues/7089")
        %test:assertEquals("4") function ikf:several-occurrences-in-a-node() { ikf:frequency("cat") };
