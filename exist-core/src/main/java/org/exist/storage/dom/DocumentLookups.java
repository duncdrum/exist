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
package org.exist.storage.dom;

/**
 * The by-id lookups of nodes of one document that were not answered by an entry of their own, counted only while the
 * statistics per document are switched on.
 *
 * @param documentId the id of the document
 * @param uri the path of the document when it was first counted: a document that is moved or renamed afterwards keeps its
 *     first name here until the counters are reset
 * @param lookups the lookups that read forward from an entry or climbed to an ancestor
 * @param recordsScanned the records they read from entries
 */
public record DocumentLookups(int documentId, String uri, long lookups, long recordsScanned) {
}
