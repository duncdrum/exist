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
package org.exist.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

/**
 * Runs the architecture rules over exist-core and the index modules, which are all on this module's test classpath.
 * The classes are imported once and shared by all included rule sets.
 *
 * <p>Each rule is frozen: the violations that exist today are recorded in {@code src/test/resources/archunit_store}
 * and only new ones fail. Fixing a recorded violation shrinks the store; a change that grows it needs a reason in
 * its pull request. See this module's {@code AGENTS.md} for refreezing.</p>
 */
@AnalyzeClasses(packages = "org.exist", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    @ArchTest
    static final ArchTests OPTIMIZER = ArchTests.in(OptimizerRules.class);

    @ArchTest
    static final ArchTests LOCALE = ArchTests.in(LocaleRules.class);
}
