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

package org.exist.xquery;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 Tests for {@link Dependency#getDependenciesName(int)}.

 @author eXist-db contributors
*/
public class DependencyTest {

       /**
        * UNKNOWN_DEPENDENCY returns "UNKNOWN".
        */
       @Test
     public void getDependenciesName_unknownReturnsUnknown() {
         assertEquals("UNKNOWN", Dependency.getDependenciesName(Dependency.UNKNOWN_DEPENDENCY));
     }

     /**
      * NO_DEPENDENCY returns "NO_DEPENDENCY".
      */
     @Test
     public void getDependenciesName_noDependencyReturnsNoDependency() {
         assertEquals("NO_DEPENDENCY", Dependency.getDependenciesName(Dependency.NO_DEPENDENCY));
     }

     /**
      * Each individual flag is correctly named.
      */
     @Test
     public void getDependenciesName_singleFlags() {
         assertEquals("[CONTEXT_SET]", Dependency.getDependenciesName(Dependency.CONTEXT_SET));
         assertEquals("[CONTEXT_ITEM]", Dependency.getDependenciesName(Dependency.CONTEXT_ITEM));
         assertEquals("[LOCAL_VARS]", Dependency.getDependenciesName(Dependency.LOCAL_VARS));
         assertEquals("[CONTEXT_VARS]", Dependency.getDependenciesName(Dependency.CONTEXT_VARS));
         assertEquals("[CONTEXT_POSITION]", Dependency.getDependenciesName(Dependency.CONTEXT_POSITION));
     }

     /**
      * Combined flags are joined with " | " and trimmed.
      */
     @Test
     public void getDependenciesName_multipleFlags() {
         assertEquals("[CONTEXT_SET | CONTEXT_ITEM]",
             Dependency.getDependenciesName(Dependency.CONTEXT_SET | Dependency.CONTEXT_ITEM));
         assertEquals("[LOCAL_VARS | CONTEXT_VARS]",
             Dependency.getDependenciesName(Dependency.LOCAL_VARS | Dependency.CONTEXT_VARS));
         assertEquals("[CONTEXT_SET | CONTEXT_ITEM | CONTEXT_POSITION]",
             Dependency.getDependenciesName(Dependency.CONTEXT_SET | Dependency.CONTEXT_ITEM | Dependency.CONTEXT_POSITION));
     }

     /**
      * All five flags together produce a single string.
      */
     @Test
     public void getDependenciesName_allFlags() {
         int all = Dependency.CONTEXT_SET | Dependency.CONTEXT_ITEM | Dependency.LOCAL_VARS
                 | Dependency.CONTEXT_VARS | Dependency.CONTEXT_POSITION;
         String name = Dependency.getDependenciesName(all);
         assertTrue(name.contains("CONTEXT_SET"));
         assertTrue(name.contains("CONTEXT_ITEM"));
         assertTrue(name.contains("LOCAL_VARS"));
         assertTrue(name.contains("CONTEXT_VARS"));
         assertTrue(name.contains("CONTEXT_POSITION"));
         assertFalse(name.endsWith(" | "));
     }

     /**
      * Zero returns "NO_DEPENDENCY".
      */
     @Test
     public void getDependenciesName_zeroIsNoDependency() {
         assertEquals("NO_DEPENDENCY", Dependency.getDependenciesName(0));
     }

     /**
      * VARS constant equals LOCAL_VARS | CONTEXT_VARS.
      */
     @Test
     public void getDependenciesName_varsConstant() {
         assertEquals("[LOCAL_VARS | CONTEXT_VARS]",
             Dependency.getDependenciesName(Dependency.VARS));
     }

     /**
      * DEFAULT_DEPENDENCIES equals CONTEXT_SET.
      */
     @Test
     public void getDependenciesName_defaultDependencies() {
         assertEquals("[CONTEXT_SET]", Dependency.getDependenciesName(Dependency.DEFAULT_DEPENDENCIES));
     }
}
