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

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Rules that keep production code independent of the JVM default locale. Included by {@link ArchitectureTest}.
 *
 * <p>A call that takes the default locale behaves differently on a Turkish, Arabic or Thai machine: {@code "INDENT"}
 * lowercases to {@code "ındent"}, a number is written with Arabic-Indic digits, a year in the Buddhist calendar. Pass
 * {@code Locale.ROOT} (or the locale the output is meant for) instead. Output that is only read by a person on a
 * console may use the default locale, but must say so by passing {@code Locale.getDefault()}.</p>
 *
 * <p>{@code String.format} and {@code printf} are not covered: ArchUnit cannot see the argument types, so it cannot
 * tell data from console output.</p>
 */
public final class LocaleRules {

    private LocaleRules() {
        // rules only
    }

    @ArchTest
    static final ArchRule CASE_MAPPING_NAMES_A_LOCALE = FreezingArchRule.freeze(noClasses()
            .should().callMethod(String.class, "toLowerCase")
            .orShould().callMethod(String.class, "toUpperCase")
            .allowEmptyShould(true)
            .as("case mapping of a String names its locale"));

    @ArchTest
    static final ArchRule FORMATS_NAME_A_LOCALE = FreezingArchRule.freeze(noClasses()
            .should().callConstructor(SimpleDateFormat.class)
            .orShould().callConstructor(SimpleDateFormat.class, String.class)
            .orShould().callConstructor(DecimalFormat.class)
            .orShould().callConstructor(DecimalFormat.class, String.class)
            .orShould().callMethod(NumberFormat.class, "getInstance")
            .orShould().callMethod(NumberFormat.class, "getNumberInstance")
            .orShould().callMethod(NumberFormat.class, "getIntegerInstance")
            .orShould().callMethod(NumberFormat.class, "getPercentInstance")
            .orShould().callMethod(NumberFormat.class, "getCurrencyInstance")
            .allowEmptyShould(true)
            .as("number and date formats name their locale"));
}
