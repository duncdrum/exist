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
package org.exist.repo;

import org.junit.Test;
import org.w3c.dom.Document;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PackageServiceParseXmlTest {

    private static final String SECRET = "top-secret-xxe-marker";

    /** Package descriptors come from (possibly untrusted) XAR archives; external entities must not resolve. */
    @Test
    public void doesNotResolveExternalEntities() throws Exception {
        final Path secret = Files.createTempFile("xxe-secret", ".txt");
        final Path descriptor = Files.createTempFile("expath-pkg", ".xml");
        try {
            Files.writeString(secret, SECRET, StandardCharsets.UTF_8);
            Files.writeString(descriptor, "<?xml version=\"1.0\"?><!DOCTYPE package [<!ENTITY xxe SYSTEM \""
                    + secret.toUri() + "\">]><package>&xxe;</package>", StandardCharsets.UTF_8);

            String text = null;
            try {
                final Document doc = PackageService.parseXml(descriptor);
                text = doc.getDocumentElement().getTextContent();
            } catch (final Exception rejected) {
                // refusing the document is an acceptable outcome too
            }
            assertTrue("External entity content must not be resolved", text == null || !text.contains(SECRET));
        } finally {
            Files.deleteIfExists(secret);
            Files.deleteIfExists(descriptor);
        }
    }

    @Test
    public void parsesNamespacedDescriptors() throws Exception {
        final Path descriptor = Files.createTempFile("expath-pkg", ".xml");
        try {
            Files.writeString(descriptor, "<package xmlns=\"http://expath.org/ns/pkg\" name=\"urn:x\"/>", StandardCharsets.UTF_8);
            final Document doc = PackageService.parseXml(descriptor);
            assertEquals("package", doc.getDocumentElement().getLocalName());
            assertFalse(doc.getDocumentElement().getNamespaceURI().isEmpty());
        } finally {
            Files.deleteIfExists(descriptor);
        }
    }
}
