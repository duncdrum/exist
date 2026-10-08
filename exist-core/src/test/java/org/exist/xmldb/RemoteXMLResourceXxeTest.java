/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xmldb;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xmldb.api.base.XMLDBException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RemoteXMLResourceXxeTest {

    private static final String SECRET = "top-secret-xxe-marker";

    private static RemoteXMLResource newResource() throws XMLDBException {
        final RemoteCollection parent = createMock(RemoteCollection.class);
        expect(parent.getPathURI()).andReturn(XmldbURI.create("/db")).anyTimes();
        replay(parent);
        return new RemoteXMLResource(parent, -1, -1, XmldbURI.create("test.xml"), Optional.empty(), Optional.empty());
    }

    private static String rootText(final Node node) {
        final Element root = node instanceof Document doc ? doc.getDocumentElement() : (Element) node;
        return root.getTextContent();
    }

    /** The content is received from a (possibly untrusted) server; external entities must not resolve. */
    @Test
    public void getContentAsDOMDoesNotResolveExternalEntities() throws Exception {
        final Path secret = Files.createTempFile("xxe-secret", ".txt");
        try {
            Files.writeString(secret, SECRET, StandardCharsets.UTF_8);
            final RemoteXMLResource resource = newResource();
            resource.setContent("<?xml version=\"1.0\"?><!DOCTYPE root [<!ENTITY xxe SYSTEM \""
                    + secret.toUri() + "\">]><root>&xxe;</root>");

            String text = null;
            try {
                text = rootText(resource.getContentAsDOM());
            } catch (final XMLDBException rejected) {
                // refusing the document is an acceptable outcome too
            }
            assertTrue("External entity content must not be resolved", text == null || !text.contains(SECRET));
        } finally {
            Files.deleteIfExists(secret);
        }
    }

    @Test
    public void getContentAsDOMStillExpandsInternalEntities() throws Exception {
        final RemoteXMLResource resource = newResource();
        resource.setContent("<?xml version=\"1.0\"?><!DOCTYPE root [<!ENTITY e \"inline\">]><root>&e;</root>");
        assertEquals("inline", rootText(resource.getContentAsDOM()));
    }

    @Test
    public void getContentAsSAXDoesNotResolveExternalEntities() throws Exception {
        final Path secret = Files.createTempFile("xxe-secret", ".txt");
        try {
            Files.writeString(secret, SECRET, StandardCharsets.UTF_8);
            final RemoteXMLResource resource = newResource();
            resource.setContent("<?xml version=\"1.0\"?><!DOCTYPE root [<!ENTITY xxe SYSTEM \""
                    + secret.toUri() + "\">]><root>&xxe;</root>");

            final StringBuilder text = new StringBuilder();
            try {
                resource.getContentAsSAX(new org.xml.sax.helpers.DefaultHandler() {
                    @Override
                    public void characters(final char[] ch, final int start, final int length) {
                        text.append(ch, start, length);
                    }
                });
            } catch (final XMLDBException rejected) {
                // refusing the document is an acceptable outcome too
            }
            assertTrue("External entity content must not be resolved", !text.toString().contains(SECRET));
        } finally {
            Files.deleteIfExists(secret);
        }
    }
}
