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
package org.exist.webdav.exceptions;

/**
 * Class that represents a situation that a file cannot be created  because the collection
 * does not exist.
 *
 * @author wessels
 */
public class DocumentNotLockedException extends Exception {

    private static final long serialVersionUID = -4907184035845864493L;

    public DocumentNotLockedException() {
        super();
    }

    public DocumentNotLockedException(Throwable inner) {
        super(inner);
    }

    public DocumentNotLockedException(String message) {
        super(message);
    }

    public DocumentNotLockedException(String message, Throwable cause) {
        super(message, cause);
    }

}
