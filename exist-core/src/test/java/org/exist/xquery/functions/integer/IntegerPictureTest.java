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

package org.exist.xquery.functions.integer;

import com.ibm.icu.text.RuleBasedNumberFormat;
import org.exist.xquery.XPathException;
import org.junit.Test;

import java.math.BigInteger;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class IntegerPictureTest {

    @Test(expected = XPathException.class) public void pictureEmpty() throws XPathException {
        IntegerPicture.fromString("");
    }

    @Test public void picture() throws XPathException {
        IntegerPicture picture = IntegerPicture.fromString("123,2345,34567,6789;00;c(variation)t");
        assertEquals("primary=123,2345,34567,6789;00::modifier=numbering=Cardinal::variation=variation::lettersequence=Traditional::regular=false::group=Group(0,3,,)::group=Group(0,4,,)::group=Group(0,5,,)::group=Group(0,4,;)::group=Group(0,2,)", picture.toString());
        picture = IntegerPicture.fromString("#23,345,567,789;c(variation)t");
        assertEquals("primary=#23,345,567,789::modifier=numbering=Cardinal::variation=variation::lettersequence=Traditional::regular=true::group=Group(0,3,,)", picture.toString());
        picture = IntegerPicture.fromString("123;345,567,789;c(variation)t");
        assertEquals("primary=123;345,567,789::modifier=numbering=Cardinal::variation=variation::lettersequence=Traditional::regular=false::group=Group(0,3,;)::group=Group(0,3,,)::group=Group(0,3,,)::group=Group(0,3,)", picture.toString());
        picture = IntegerPicture.fromString("123,2345,567,789;c(variation)t");
        assertEquals("primary=123,2345,567,789::modifier=numbering=Cardinal::variation=variation::lettersequence=Traditional::regular=false::group=Group(0,3,,)::group=Group(0,4,,)::group=Group(0,3,,)::group=Group(0,3,)", picture.toString());
        picture = IntegerPicture.fromString("#89;c(variation)t");
        assertEquals("primary=#89::modifier=numbering=Cardinal::variation=variation::lettersequence=Traditional::regular=true::group=Group(1,2,)", picture.toString());
        picture = IntegerPicture.fromString("#89|123;c(variation)t");
        assertEquals("primary=#89|123::modifier=numbering=Cardinal::variation=variation::lettersequence=Traditional::regular=true::group=Group(0,3,|)", picture.toString());
    }

    private String fmt(String pictureString, long value) throws XPathException {
        IntegerPicture picture = IntegerPicture.fromString(pictureString);
        return picture.formatInteger(BigInteger.valueOf(value), "en");
    }

    private String fmt(String pictureString, long value, String language) throws XPathException {
        IntegerPicture picture = IntegerPicture.fromString(pictureString);
        return picture.formatInteger(BigInteger.valueOf(value), language);
    }

    @Test public void format() throws XPathException {
        assertEquals("0", fmt("1", 0L));
        assertEquals("00", fmt("12", 0L));
        assertEquals("000", fmt("123", 0L));
        assertEquals("0,0", fmt("1,1", 0L));
        assertEquals("0,1", fmt("1,1", 1L));
    }

    @Test public void formatCardinalModifier() throws XPathException {
        //c for cardinal is the default, but we still need to recognize it
        assertEquals("1", fmt("1;c", 1L));
    }

    @Test public void formatNegative() throws XPathException {
        assertEquals("-1", fmt("1", -1L));
        assertEquals("-01", fmt("12", -1L));
        assertEquals("-001", fmt("123", -1L));
        assertEquals("-0,1", fmt("1,1", -1L));
        assertEquals("-0,1", fmt("1,1", -1L));
    }

    @Test public void formatRegular() throws XPathException {
        assertEquals("1,23,45,67,89", fmt("12,34", 123456789L));
        assertEquals("1,23,45,67,89", fmt("12,34,56", 123456789L));
        assertEquals("12345,67?89", fmt("12,34?56", 123456789L));
        assertEquals("123456,789", fmt("12,345", 123456789L));
        assertEquals("00,089", fmt("12,345", 89L));
    }

    @Test public void formatOptional() throws XPathException {
        assertEquals("009", fmt("#234", 9L));
        assertEquals("123456789", fmt("#234", 123456789L));
        assertEquals("000,0009", fmt("#234,1234", 9L));
        assertEquals("0009", fmt("####,1234", 9L));
        assertEquals("0,0009", fmt("###4,1234", 9L));
    }

    @Test public void formatOptionalOrdinal() throws XPathException {
        assertEquals("001st", fmt("#234;o", 1L));
        assertEquals("009th", fmt("#234;o", 9L));
        assertEquals("123456789th", fmt("#234;o", 123456789L));
        assertEquals("000,0009th", fmt("#234,1234;o", 9L));
        assertEquals("0009th", fmt("####,1234;o", 9L));
        assertEquals("0,0009th", fmt("###4,1234;o", 9L));
    }

    @Test public void formatDefaultFamily() throws XPathException {
        StringBuilder sb = new StringBuilder();
        sb.append("#234567");
        assertEquals(7, sb.length());
        assertEquals("000009", fmt(sb.toString(), 9L));
    }

    @Test public void formatNonDefaultDigitFamilies() throws XPathException {
        for (int family : new int[]{0x104a0,0x660,0x30,0x1e950,0x1e2f0}) {
            StringBuilder sb = new StringBuilder();
            sb.append("#");
            for (int i = 2; i < 5; i++) {
                char[] chars = Character.toChars(family + i);
                sb.append(chars);
            }
            sb.append("|");
            for (int i = 5; i < 8; i++) {
                char[] chars = Character.toChars(family + i);
                sb.append(chars);
            }
            String formatted = fmt(sb.toString(), 149L);
            System.out.println("Formatted:" + formatted);
            assertEquals(6 * Character.charCount(family) + 1, formatted.length());
            int pos = 0;
            int codePoint;
            for (int offset : new int[]{0, 0, 0}) {
                codePoint = Character.codePointAt(formatted, pos);
                assertEquals(family + offset, codePoint);
                pos += Character.toChars(family + offset).length;
            }
            codePoint = Character.codePointAt(formatted, pos);
            assertEquals(Character.codePointAt("|",0), codePoint);
            pos += 1;
            for (int offset : new int[]{1, 4, 9}) {
                codePoint = Character.codePointAt(formatted, pos);
                assertEquals(family + offset, codePoint);
                pos += Character.toChars(family + offset).length;
            }
        }
    }

    @Test public void conflictingDigitFamilies() throws XPathException {
        StringBuilder sb = new StringBuilder();
        for (int family : new int[]{0x104a0,0x30}) {
            char[] chars = Character.toChars(family + 3);
            sb.append(chars);
            sb.append(",");
        }
        try {
            String formatted = fmt(sb.toString(), 9L);
            fail("Conflicting digit families should throw an exception");
        } catch (XPathException xpe) {
            assertTrue(xpe.getDetailMessage().contains("multiple digit families"));
        }
    }

    @Test public void optionalSignsAfterMandatorySigns() throws XPathException {
        assertEquals("0|005", fmt("##|#3|456", 5L));
        assertEquals("0|05", fmt("##|#3|45", 5L));
        assertEquals("5|67|89", fmt("##|#3|45", 56789L));

        try {
            fmt("12,#45", 0L);
            fail("The picture " + "12,#45" + " should not be valid.");
        } catch (XPathException xpe) {
            assertTrue(xpe.getDetailMessage().contains("optional digit after mandatory"));
        }

        try {
            fmt("##|3#|45", 0L);
            fail("The picture " + "##|3#|45" + " should not be valid.");
        } catch (XPathException xpe) {
            assertTrue(xpe.getDetailMessage().contains("expected a digit grouping pattern"));
        }

        try {
            fmt("1#", 0L);
            fail("The picture " + "1#" + " should not be valid.");
        } catch (XPathException xpe) {
            assertTrue(xpe.getDetailMessage().contains("ends with a separator"));
        }

    }

    @Test public void separatorAtEndIsIllegal() throws XPathException {
        assertEquals("0+5", fmt("1+1", 5L));
        try {
            fmt("1+", 0L);
            fail("The picture " + "1+" + " should not be valid.");
        } catch (XPathException xpe) {
            assertTrue(xpe.getDetailMessage().contains("ends with a separator"));
        }
    }

    @Test public void separatorAtStartIsIllegal() throws XPathException {
        assertEquals("0+5", fmt("1+1", 5L));
        try {
            fmt("|1", 0L);
            fail("The picture " + "+1" + " should not be valid.");
        } catch (XPathException xpe) {
            assertTrue(xpe.getDetailMessage().contains("expected a digit grouping pattern"));
        }
    }

    @Test public void multiSeparator() throws XPathException {
        try {
            assertEquals("0|005", fmt("#3||456", 5L));
            fail("The picture " + "#3||456" + " should not be valid.");
        } catch (XPathException xpe) {
            assertTrue(xpe.getDetailMessage().contains("expected a digit grouping pattern at 3"));
        }
    }

    @Test public void alphaUpperDigitFormat() throws XPathException {
        assertEquals("E", fmt("A", 5L));
        assertEquals("Y", fmt("A", 25L));
        assertEquals("Z", fmt("A", 26L));
        assertEquals("AA", fmt("A", 27L));
        assertEquals("AB", fmt("A", 28L));
        assertEquals("AZ", fmt("A", 52L));
        assertEquals("BA", fmt("A", 53L));
        assertEquals("CA", fmt("A", 79L));
        assertEquals("ZZ", fmt("A", 702L));
        assertEquals("AAA", fmt("A", 703L));
        assertEquals("AAZ", fmt("A", 728L));
        assertEquals("ZZZ", fmt("A", 18278));
        assertEquals("AAAA", fmt("A", 18279));

        //out of range, format is "1L"
        assertEquals("0", fmt("A", 0L));
        assertEquals("-35", fmt("A", -35L));
    }

    @Test public void alphaLowerDigitFormat() throws XPathException {
        assertEquals("e", fmt("a", 5L));
        assertEquals("y", fmt("a", 25L));
        assertEquals("z", fmt("a", 26L));
        assertEquals("aa", fmt("a", 27L));
        assertEquals("ab", fmt("a", 28L));
        assertEquals("az", fmt("a", 52L));
        assertEquals("ba", fmt("a", 53L));
        assertEquals("ca", fmt("a", 79L));
        assertEquals("zz", fmt("a", 702L));
        assertEquals("aaa", fmt("a", 703L));
        assertEquals("aaz", fmt("a", 728L));
        assertEquals("zzz", fmt("a", 18278));
        assertEquals("aaaa", fmt("a", 18279));

        //out of range, format is "1L"
        assertEquals("0", fmt("a", 0L));
        assertEquals("-35", fmt("a", -35L));
    }

    @Test public void romanLowerDigitFormat() throws XPathException {
        assertEquals("v", fmt("i", 5L));
    }

    @Test public void romanUpperDigitFormat() throws XPathException {
        assertEquals("V", fmt("I", 5L));
        assertEquals("MDCCCLXVIII", fmt("I", 1868L));
        assertEquals("MCMLXXXIV", fmt("I", 1984L));
        assertEquals("-1", fmt("I", -1L));
        assertEquals("0", fmt("I", 0L));
        assertEquals("5984", fmt("I", 5984L));
    }

    @Test public void wordLowerDigitFormat() throws XPathException {
        assertEquals("five", fmt("w", 5L));
        assertEquals("fifteen", fmt("w", 15L));
        assertEquals("fünfzehn", fmt("w", 15L, "de"));
        assertEquals("two thousand five hundred ninety-eight", fmt("w", 2598L, "en"));
        assertEquals("zwei\u00ADtausend\u00ADfünf\u00ADhundert\u00ADacht\u00ADund\u00ADneunzig", fmt("w", 2598L, "de"));
    }

    @Test public void wordModifiers() throws XPathException {
        assertEquals("five", fmt("w", 5L));
        assertEquals("fifth", fmt("w;o", 5L));
        assertEquals("cinque", fmt("w;a", 5L, "it"));
        assertEquals("quinta", fmt("w;o", 5L, "it"));
        assertEquals("una", fmt("w;a", 1L, "it"));
        assertEquals("prima", fmt("w;o", 1L, "it"));
        assertEquals("eine", fmt("w;a", 1L, "de"));
        assertEquals("ein", fmt("w;c(neuter)a", 1L, "de"));
        assertEquals("eine", fmt("w;c", 1L, "de"));
        assertEquals("eine", fmt("w;c(feminine)", 1L, "de"));
        assertEquals("ein", fmt("w;c(masculine)", 1L, "de"));
        assertEquals("ein", fmt("w;c(neuter)", 1L, "de"));
        assertEquals("eine", fmt("w;c(vulcan)", 1L, "de"));
        assertEquals("erste", fmt("w;o", 1L, "de"));
        assertEquals("erstes", fmt("w;o(s)", 1L, "de"));
        assertEquals("erster", fmt("w;o(r)", 1L, "de"));
        assertEquals("ersten", fmt("w;o(n)", 1L, "de"));
        assertEquals("erste", fmt("w;o(z)", 1L, "de"));
        assertEquals("первая", fmt("w;o(z)", 1L, "ru"));
        assertEquals("одна", fmt("w;c(z)", 1L, "ru"));
        assertEquals("one", fmt("w;c(z)", 1L, "nonsense"));
        assertEquals("first", fmt("w;o(z)", 1L, "nonsense"));
        assertEquals("erster", fmt("w;o(%spellout-ordinal-r)", 1L, "de"));
        assertEquals("zweites", fmt("w;o(%spellout-ordinal-s)", 2L, "de"));
        assertEquals("zweiten", fmt("w;o(%spellout-ordinal-n)", 2L, "de"));
        assertEquals("zweiter", fmt("w;o(%spellout-ordinal-r)", 2L, "de"));
        assertEquals("eine", fmt("w;c(%spellout-cardinal-feminine)", 1L, "de"));
        assertEquals("ein", fmt("w;c(%spellout-cardinal-masculine)", 1L, "de"));
        assertEquals("ein", fmt("w;c(%spellout-cardinal-neuter)", 1L, "de"));
        assertEquals("zwei", fmt("w;c(%spellout-cardinal-feminine)", 2L, "de"));
        assertEquals("zwei", fmt("w;c(%spellout-cardinal-masculine)", 2L, "de"));
        assertEquals("zwei", fmt("w;c(%spellout-cardinal-neuter)", 2L, "de"));
        assertEquals("two", fmt("w;c(%spellout-cardinal-neuter)", 2L, "zz"));
        assertEquals("دو", fmt("w;o", 2L, "fa"));
    }

    @Test public void wordUpperDigitFormat() throws XPathException {
        assertEquals("FIVE", fmt("W", 5L));
        assertEquals("FIFTEEN", fmt("W", 15L));
        assertEquals("FIFTEEN", fmt("W", 15L, "unknown"));
        assertEquals("FÜNFZEHN", fmt("W", 15L, "de"));
    }

    @Test public void wordTitleCaseDigitFormat() throws XPathException {
        assertEquals("Five", fmt("Ww", 5L));
        assertEquals("Fifteen", fmt("Ww", 15L));
        assertEquals("Two thousand five hundred ninety-eight", fmt("Ww", 2598L, "en"));
    }

    @Test public void modifier() throws XPathException {
        FormatModifier formatModifier = new FormatModifier("c(maschile)t");
        assertEquals(FormatModifier.Numbering.Cardinal, formatModifier.numbering);
        assertEquals(FormatModifier.LetterSequence.Traditional, formatModifier.letterSequence);
        assertEquals("maschile", formatModifier.variation);
        formatModifier = new FormatModifier("ct");
        assertEquals(FormatModifier.Numbering.Cardinal, formatModifier.numbering);
        assertEquals(FormatModifier.LetterSequence.Traditional, formatModifier.letterSequence);
        formatModifier = new FormatModifier("ca");
        assertEquals(FormatModifier.Numbering.Cardinal, formatModifier.numbering);
        assertEquals(FormatModifier.LetterSequence.Alphabetic, formatModifier.letterSequence);
        formatModifier = new FormatModifier("ot");
        assertEquals(FormatModifier.Numbering.Ordinal, formatModifier.numbering);
        assertEquals(FormatModifier.LetterSequence.Traditional, formatModifier.letterSequence);
        formatModifier = new FormatModifier("oa");
        assertEquals(FormatModifier.Numbering.Ordinal, formatModifier.numbering);
        assertEquals(FormatModifier.LetterSequence.Alphabetic, formatModifier.letterSequence);
        formatModifier = new FormatModifier("c");
        assertEquals(FormatModifier.Numbering.Cardinal, formatModifier.numbering);
        assertEquals(FormatModifier.LetterSequence.Alphabetic, formatModifier.letterSequence);
        formatModifier = new FormatModifier("o");
        assertEquals(FormatModifier.Numbering.Ordinal, formatModifier.numbering);
        assertEquals(FormatModifier.LetterSequence.Alphabetic, formatModifier.letterSequence);
        formatModifier = new FormatModifier("a");
        assertEquals(FormatModifier.Numbering.Cardinal, formatModifier.numbering);
        assertEquals(FormatModifier.LetterSequence.Alphabetic, formatModifier.letterSequence);
        formatModifier = new FormatModifier("t");
        assertEquals(FormatModifier.Numbering.Cardinal, formatModifier.numbering);
        assertEquals(FormatModifier.LetterSequence.Traditional, formatModifier.letterSequence);
        formatModifier = new FormatModifier("c(hello)");
        assertEquals(FormatModifier.Numbering.Cardinal, formatModifier.numbering);
        assertEquals(FormatModifier.LetterSequence.Alphabetic, formatModifier.letterSequence);
        assertEquals("hello", formatModifier.variation);
    }

    private void modifierFail(String modifier) throws XPathException {
        try {
            FormatModifier formatModifier = new FormatModifier(modifier);
            fail("Format modifier " + modifier + " should throw a parse exception");
        } catch (XPathException e) {
            assertTrue(e.getMessage().contains("modifier"));
        }
    }

    @Test public void modifierFailTest() throws XPathException {
        modifierFail("b");
        modifierFail("ba");
        modifierFail("bt");
        modifierFail("(hello)");
        modifierFail("c(t");
        modifierFail("cv");
        modifierFail("av");
        modifierFail("ev");
        modifierFail("tc");
        modifierFail("ctc");
        modifierFail("ctt");
        modifierFail("c()t");
        modifierFail("c()");
    }

    @Test public void fromXQTS() throws XPathException {
        assertEquals("1500000", fmt("#", 1500000L));
    }

    /**
     * Investigation of what spellouts are available per-locale
     *
     * @Test
     */
    public void localesAndSpellouts() {
        List<String> iso639Alpha2Codes = Arrays.asList("aa", "ab", "ae", "af", "ak", "am", "an", "ar", "as", "av", "ay", "az",
                "ba", "be", "bg", "bi", "bm", "bn", "bo", "br", "bs",
                "ca", "ce", "ch", "co", "cr", "cs", "cu", "cv", "cy",
                "da", "de", "dv", "dz",
                "ee", "el", "en", "eo", "es", "et", "eu",
                "fa", "ff", "fi", "fj", "fo", "fr", "fy",
                "ga", "gd", "gl", "gn", "gu", "gv",
                "ha", "he", "hi", "ho", "hr", "ht", "hu", "hy", "hz",
                "ia", "id", "ie", "ig", "ii", "ik", "io", "is", "it", "iu",
                "ja", "jv",
                "ka", "kg", "ki", "kj", "kk", "kl", "km", "kn", "ko", "kr", "ks", "ku", "kv", "kw", "ky",
                "la", "lb", "lg", "li", "ln", "lo", "lt", "lu", "lv",
                "mg", "mh", "mi", "mk", "ml", "mn", "mr", "ms", "mt", "my",
                "na", "nb", "nd", "ne", "ng", "nl", "nn", "no", "nr", "nv", "ny",
                "oc", "oj", "om", "or", "os",
                "pa", "pi", "pl", "ps", "pt",
                "rm", "rn", "ro", "ru", "rw",
                "sa", "sc", "sd", "se", "sg", "si", "sk", "sl", "sm", "sn", "so", "sq", "sr", "ss", "st", "su", "sv", "sw",
                "ta", "te", "tg", "th", "ti", "tk", "tl", "tn", "to", "tr", "ts", "tt", "tw", "ty",
                "ug", "uk", "ur", "uz",
                "ve", "vi", "vo",
                "wa", "wo",
                "xh",
                "yi", "yo",
                "za", "zh", "zu");
        Set<String> global = new HashSet<>();
        for (String isoCode : iso639Alpha2Codes) {
            Locale locale = (new Locale.Builder()).setLanguage(isoCode).build();
            RuleBasedNumberFormat ruleBasedNumberFormat = new RuleBasedNumberFormat( locale, RuleBasedNumberFormat.SPELLOUT );
            int i = 0;
            Set<String> names = new HashSet<>();
            for (String ruleSetName : ruleBasedNumberFormat.getRuleSetNames()) {
                names.add(ruleSetName);
                global.add(ruleSetName);
            }
            boolean displayMissing = !names.contains("%spellout-ordinal") && !names.contains("%spellout-ordinal-masculine") && !names.contains("%spellout-ordinal-feminine");
            if (!names.contains("%spellout-cardinal") && !names.contains("%spellout-cardinal-masculine") && !names.contains("%spellout-cardinal-feminine")) {
                displayMissing = true;
            }
            if (displayMissing) {
                System.err.println(isoCode + " missing some spellouts --->>> ");
                for (String name : names) {
                    System.err.println(name);
                }
                System.err.println(isoCode + " <<<---");
            }
        }
        System.err.println("all spellouts --->>>");
        List<String> globalList = Arrays.asList(global.toArray(new String[]{}));
        Collections.sort(globalList);
        for (String name : globalList) {
            System.err.println(name);
        }
        System.err.println("<<<--- all spellouts");

    }
}
