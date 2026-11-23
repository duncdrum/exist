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
 : A comparison whose left operand is a path over in-memory nodes, where the right operand depends on the context: the
 : path returns a plain sequence, not a stored node set, and is compared as written.
 :)
module namespace gcim="http://exist-db.org/xquery/test/general-comparison-in-memory";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $gcim:AUCTIONS :=
    <open_auctions>
        <open_auction><bidder><increase>1</increase></bidder><bidder><increase>3</increase></bidder></open_auction>
        <open_auction><bidder><increase>5</increase></bidder><bidder><increase>6</increase></bidder></open_auction>
    </open_auctions>;

declare %test:assertEquals(1) function gcim:where-path-left() {
    count(
        for $b in $gcim:AUCTIONS/open_auction
        where $b/bidder[last()]/increase/text() >= zero-or-one($b/bidder[1]/increase/text()) * 2
        return $b
    )
};

(: XMark Q3's spelling :)
declare %test:assertEquals(1) function gcim:where-path-right() {
    count(
        for $b in $gcim:AUCTIONS/open_auction
        where zero-or-one($b/bidder[1]/increase/text()) * 2 <= $b/bidder[last()]/increase/text()
        return $b
    )
};

(: a value comparison casts untyped values to xs:string: text against text :)
declare %test:assertEquals(2) function gcim:where-value-comparison() {
    count(
        for $b in $gcim:AUCTIONS/open_auction
        where $b/bidder[last()]/increase/text() ge zero-or-one($b/bidder[1]/increase/text())
        return $b
    )
};

declare %test:assertError("XPTY0004") function gcim:where-value-comparison-several() {
    for $b in $gcim:AUCTIONS/open_auction
    where $b/bidder/increase/text() ge zero-or-one($b/bidder[1]/increase/text())
    return $b
};
