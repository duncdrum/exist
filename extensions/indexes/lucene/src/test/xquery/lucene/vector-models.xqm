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
 : The sub-collections of a collection configure the vectors of one field name and dimension from different
 : models: the vectors are not comparable and the query vector carries no model, so the call is refused.
 : The models are never loaded, the call is refused from the configurations.
 :)
module namespace t="http://exist-db.org/xquery/test/vector-models";

declare namespace test="http://exist-db.org/xquery/xqsuite";

import module namespace ft="http://exist-db.org/xquery/lucene";

declare variable $t:ROOT := "/db/test-vector-models";
declare variable $t:VEC := [1.0, 0.0, 0.0, 0.0];

declare function t:xconf($model as xs:string?) as element() {
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <text qname="div">
                    {
                        if (exists($model)) then
                            <vector-field name="embedding" expression="." dimension="4" embedding="local" model="{$model}"/>
                        else
                            <vector-field name="embedding" expression="embedding" dimension="4" encoding="text"/>
                    }
                </text>
            </lucene>
        </index>
    </collection>
};

declare function t:sub-collection($parent as xs:string, $name as xs:string, $model as xs:string?) {
    let $coll := xmldb:create-collection($parent, $name)
    let $conf := xmldb:create-collection("/db/system/config" || $parent, $name)
    let $_ := xmldb:store($conf, "collection.xconf", t:xconf($model))
    return xmldb:store($coll, $name || ".xml", <doc><div><embedding>1.0 0.0 0.0 0.0</embedding></div></doc>)
};

declare
    %test:setUp
function t:setup() {
    let $_ := xmldb:create-collection("/db/system/config", "db")
    for $layout in (
        ["two-models", ["first", "model-a"], ["second", "model-b"]],
        ["one-model", ["first", "model-a"], ["second", "model-a"]],
        ["model-and-vectors", ["first", "model-a"], ["second", ()]]
    )
    let $parent := xmldb:create-collection("/db", substring-after($t:ROOT, "/db/") || "-" || $layout(1))
    let $_ := xmldb:create-collection("/db/system/config/db", substring-after($parent, "/db/"))
    return (
        t:sub-collection($parent, $layout(2)(1), $layout(2)(2)),
        t:sub-collection($parent, $layout(3)(1), $layout(3)(2))
    )
};

declare
    %test:tearDown
function t:teardown() {
    for $layout in ("two-models", "one-model", "model-and-vectors")
    let $parent := $t:ROOT || "-" || $layout
    return (
        xmldb:remove($parent),
        xmldb:remove("/db/system/config" || $parent)
    )
};

declare
    %test:assertError("EXXQDYFT0008")
function t:two-models-by-field() {
    count(collection($t:ROOT || "-two-models")//div[ft:query-field-vector("embedding", $t:VEC, 10)])
};

declare
    %test:assertError("EXXQDYFT0008")
function t:two-models-by-step() {
    count(collection($t:ROOT || "-two-models")//div[ft:query-vector(., $t:VEC, 10)])
};

(: one collection alone has one model :)
declare
    %test:assertEquals(0)
function t:two-models-one-collection() {
    count(collection($t:ROOT || "-two-models/first")//div[ft:query-vector(., $t:VEC, 10)])
};

declare
    %test:assertEquals(0)
function t:one-model() {
    count(collection($t:ROOT || "-one-model")//div[ft:query-vector(., $t:VEC, 10)])
};

(: vectors stored as they are say nothing of a model: the one precomputed vector is found :)
declare
    %test:assertEquals(1)
function t:model-and-precomputed() {
    count(collection($t:ROOT || "-model-and-vectors")//div[ft:query-vector(., $t:VEC, 10)])
};
