(:
    Module: examples setup
:)
module namespace setup="http://exist-db.org/xquery/admin-interface/setup";

declare namespace xdb="http://exist-db.org/xquery/xmldb";
declare namespace request="http://exist-db.org/xquery/request";
declare namespace util="http://exist-db.org/xquery/util";

declare function setup:main() as element() {
    <div class="panel">
        <div class="panel-head">Examples Setup</div>
        {
            let $action := request:request-parameter("action", ())
            return
                if($action) then
                    if($action eq "Import Example Data") then
                        setup:importLocal()
                    else if($action eq "Import Remote Files") then
                        setup:importFromURLs()
                    else
                        setup:page3()
                else
                    setup:page1()
        }
    </div>
};

declare function setup:importLocal() as element()+ {
	let $home := util:system-property("exist.home"),
		$pathSep := util:system-property("file.separator"),
		$dir :=
			if(ends-with($home, "WEB-INF")) then
				concat(substring-before($home, "WEB-INF"), "samples")
			else
				concat($home, $pathSep, "samples")
    return (
        setup:page2(),
        <div class="process">
            <h3>Actions:</h3>
            <ul>
            {
                setup:create-collection("/db", "shakespeare"),
                setup:create-collection("/db/shakespeare", "plays"),
                setup:store-files("/db/shakespeare/plays", $dir, 
                    ( "shakespeare/*.xml", "shakespeare/*.xsl" ),
                    "text/xml"
                ),
                setup:store-files("/db/shakespeare/plays", $dir,
                    "shakespeare/*.css", "text/css"),
                setup:create-collection("/db", "xinclude"),
                setup:store-files("/db/xinclude", $dir, 
                    ( "xinclude/*.xsl", "xinclude/*.xml"), "text/xml"),
                setup:store-files("/db/xinclude", $dir,
                    "xinclude/*.xq", "application/xquery"),
                setup:store-files("/db/xinclude", $dir,
                    "xinclude/*.jpg", "image/jpeg"),
                setup:store-files("/db/xinclude", $dir,
                    "xinclude/*.css", "text/css"),
                setup:create-collection("/db", "library"),
                setup:store-files("/db/library", $dir, "*.rdf", "text/xml"),
                setup:create-collection("/db", "mods"),
                setup:store-files("/db/mods", $dir, "mods/*.xml", "text/xml"),
                setup:store-files("/db", $dir, "*.xml", "text/xml"),
				setup:create-collection("/db/system/config", "db"),
				setup:create-collection("/db/system/config/db", "mondial"),
				setup:store-files("/db/system/config/db/mondial", $dir,
					"mondial.xconf", "text/xml")
            }
            </ul>
        </div>
    )
};

declare function setup:importFromURLs() as element()+ {
    (
        setup:page3(),
        <div class="process">
            <h3>Actions:</h3>
            <ul>
            {
                let $includeXmlad := request:request-parameter("xmlad", ()),
                    $includeMondial := request:request-parameter("mondial", ())
                return (
                    if($includeXmlad) then (
                        setup:create-collection("/db", "xmlad"),
                        setup:load-URL("/db/xmlad",
                            "http://mesh.dl.sourceforge.net/sourceforge/xmlad/xmlad.xml",
                            "xmlad.xml")
                    ) else (),
                    if($includeMondial) then (
                        setup:create-collection("/db", "mondial"),
                        setup:load-URL("/db/mondial",
                            "http://dbis.informatik.uni-goettingen.de/Mondial/mondial-europe.xml",
                            "mondial.xml")
                    ) else ()
                )
            }
            </ul>
        </div>
    )
};

declare function setup:load-URL($collection, $url, $docName) as element() {
	let $x := xdb:store($collection, $docName, xs:anyURI($url))
	return
    	<li>File {$docName} imported from url: {$url}</li>
};

declare function setup:store-files($collection, $home, $patterns, $mimeType) as element()* {
    let $stored := xdb:store-files-from-pattern($collection, $home, $patterns, $mimeType)
    for $doc in $stored
    return
        <li>Uploaded: {$doc}</li>
};

declare function setup:create-collection($parent, $name) as element() {
    let $col := xdb:create-collection($parent, $name)
    return
        <li class="high">Created collection: {util:collection-name($col)}</li>
};

declare function setup:page1() as element() {
    <form action="{request:encode-url(request:request-uri())}" method="POST">
        <p>eXist ships with a number of XQuery examples. Some of these
        require certain documents to be stored in the database. Clicking on the button 
        below will import the required data from the samples directory:</p>
        <input type="submit" name="action" value="Import Example Data"/>
        <input type="hidden" name="panel" value="setup"/>
    </form>
};

declare function setup:page2() as element() {
    <form action="{request:encode-url(request:request-uri())}" method="POST">
        <p>The XQuery examples also use some XML data not included with the distribution.
        I can try to download the corresponding data. Do you want me to do so?</p>
        
        <input type="checkbox" name="xmlad" checked="true"/>
        <a href="http://www.xml-acronym-demystifier.org">The XML Acronym Demystifier</a>
        (approx. 384K)<br/>
        
        <input type="checkbox" name="mondial"/>
        <a href="http://dbis.informatik.uni-goettingen.de/Mondial/">The Mondial Database (Europe)</a>
        (approx. 410K)
        
        <p><input type="submit" name="action" value="Import Remote Files"/>
        <input type="submit" name="action" value="Skip"/></p>
        <input type="hidden" name="panel" value="setup"/>
    </form>
};

declare function setup:page3() as element() {
    <p>Files have been loaded. You can now go to the
    <a href="../examples.xml">examples page</a>.</p>
};
