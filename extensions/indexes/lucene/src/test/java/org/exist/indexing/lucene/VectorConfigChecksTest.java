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
package org.exist.indexing.lucene;

import org.exist.dom.QName;
import org.exist.util.DatabaseConfigurationException;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The checks on a collection's vector field configuration.
 */
class VectorConfigChecksTest {

    private static LuceneConfig parse(final String lucene) throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        final Element root = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(lucene))).getDocumentElement();
        return new LuceneConfig(root.getChildNodes(), Map.of());
    }

    private static String config(final String... indexes) {
        return "<lucene>" + String.join("", indexes) + "</lucene>";
    }

    private static String text(final String qname, final String... vectorFields) {
        return "<text qname='" + qname + "'>" + String.join("", vectorFields) + "</text>";
    }

    private static String vector(final String name, final int dimension) {
        return "<vector-field name='" + name + "' dimension='" + dimension + "'/>";
    }

    private static boolean configures(final LuceneConfig config, final String qname) throws Exception {
        return config.getIndexConfigForQName(new QName(qname)) != null;
    }

    @Test
    void sameNameAtOneDimensionInTwoIndexesIsAccepted() throws Exception {
        final LuceneConfig config = parse(config(
                text("a", vector("embedding", 4)), text("b", vector("embedding", 4))));
        assertNotNull(config.getIndexConfigForQName(new QName("a")));
        assertNotNull(config.getIndexConfigForQName(new QName("b")));
    }

    @Test
    void sameNameAtTwoDimensionsInTwoIndexesRejectsTheSecond() throws Exception {
        final LuceneConfig config = parse(config(
                text("a", vector("embedding", 384)), text("b", vector("embedding", 385))));
        assertNotNull(config.getIndexConfigForQName(new QName("a")));
        assertNull(config.getIndexConfigForQName(new QName("b")));
    }

    @Test
    void sameNameAtTwoDimensionsInOneIndexRejectsTheIndex() throws Exception {
        final LuceneConfig config = parse(config(
                text("a", vector("embedding", 384), vector("embedding", 385))));
        assertFalse(configures(config, "a"));
    }

    @Test
    void differentNamesAtDifferentDimensionsAreAccepted() throws Exception {
        final LuceneConfig config = parse(config(
                text("a", vector("title-embedding", 384), vector("body-embedding", 4))));
        assertNotNull(config.getIndexConfigForQName(new QName("a")));
    }
}
