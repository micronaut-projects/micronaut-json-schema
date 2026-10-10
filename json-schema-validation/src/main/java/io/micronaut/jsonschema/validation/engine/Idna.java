/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jsonschema.validation.engine;

import org.jspecify.annotations.Nullable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Host name validation following RFC 1123 and IDNA2008 (RFC 5890-5893), including Punycode (RFC 3492)
 * and the Bidi rule. The derived property values of RFC 5892 are approximated from Unicode general categories.
 *
 * @author Graeme Rocher
 * @since 2.3.2
 */
final class Idna {

    private static final int BASE = 36;
    private static final int TMIN = 1;
    private static final int TMAX = 26;
    private static final int SKEW = 38;
    private static final int DAMP = 700;
    private static final int INITIAL_BIAS = 72;
    private static final int INITIAL_N = 128;
    private static final int MAX_LABEL = 63;
    private static final int MAX_HOST = 253;

    private Idna() {
    }

    /**
     * Validates an RFC 1123 host name.
     *
     * @param host The host name
     * @param idna Whether labels using the {@code xn--} prefix must be valid IDNA2008 A-labels
     * @return true if valid
     */
    static boolean isHostname(String host, boolean idna) {
        if (host.isEmpty() || host.length() > MAX_HOST) {
            return false;
        }
        String[] labels = host.split("\\.", -1);
        List<int[]> unicodeLabels = idna ? new ArrayList<>(labels.length) : null;
        for (String label : labels) {
            if (!isLdhLabel(label)) {
                return false;
            }
            if (idna) {
                int[] unicode = checkReservedLabel(label);
                if (unicode == null) {
                    return false;
                }
                unicodeLabels.add(unicode);
            }
        }
        return !idna || satisfiesBidiRule(unicodeLabels);
    }

    /**
     * Validates an internationalized host name (IDNA2008).
     *
     * @param host The host name
     * @return true if valid
     */
    static boolean isIdnHostname(String host) {
        if (host.isEmpty()) {
            return false;
        }
        String[] labels = host.split("[.。．｡]", -1);
        List<int[]> unicodeLabels = new ArrayList<>(labels.length);
        int length = 0;
        for (String label : labels) {
            if (label.isEmpty()) {
                return false;
            }
            String ascii;
            int[] unicode;
            String mapped = map(label);
            if (mapped.isEmpty()) {
                return false;
            }
            if (isAscii(mapped)) {
                if (!isLdhLabel(mapped)) {
                    return false;
                }
                unicode = checkReservedLabel(mapped);
                if (unicode == null) {
                    return false;
                }
                ascii = mapped;
            } else {
                unicode = mapped.codePoints().toArray();
                if (!isULabel(unicode)) {
                    return false;
                }
                ascii = "xn--" + encode(unicode);
            }
            if (ascii.length() > MAX_LABEL) {
                return false;
            }
            unicodeLabels.add(unicode);
            length += ascii.length() + 1;
        }
        return length - 1 <= MAX_HOST && satisfiesBidiRule(unicodeLabels);
    }

    /**
     * Approximates the UTS #46 mapping: removes ignored code points, applies compatibility normalization and lower-cases.
     */
    private static String map(String label) {
        StringBuilder sb = new StringBuilder(label.length());
        label.codePoints().filter(cp -> !isIgnored(cp)).forEach(sb::appendCodePoint);
        String normalized = Normalizer.normalize(sb, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return Normalizer.normalize(normalized, Normalizer.Form.NFC);
    }

    private static boolean isIgnored(int cp) {
        return cp == 0x00AD || cp == 0x034F || cp == 0x200B || cp == 0x2060 || cp == 0xFEFF
            || (cp >= 0x180B && cp <= 0x180D) || (cp >= 0xFE00 && cp <= 0xFE0F) || (cp >= 0xE0100 && cp <= 0xE01EF);
    }

    /**
     * Checks labels with hyphens in the third and fourth position: only valid A-labels are allowed.
     *
     * @param label The ASCII label
     * @return The Unicode form of the label, or null if invalid
     */
    private static int @Nullable [] checkReservedLabel(String label) {
        if (label.length() < 4 || label.charAt(2) != '-' || label.charAt(3) != '-') {
            return label.codePoints().toArray();
        }
        if (!label.regionMatches(true, 0, "xn", 0, 2)) {
            return null;
        }
        String encoded = label.substring(4).toLowerCase(Locale.ROOT);
        int[] decoded = decode(encoded);
        if (decoded == null || decoded.length == 0 || isAscii(decoded) || !isULabel(decoded) || !encode(decoded).equals(encoded)) {
            return null;
        }
        return decoded;
    }

    private static boolean isLdhLabel(String label) {
        if (label.isEmpty() || label.length() > MAX_LABEL || label.charAt(0) == '-' || label.charAt(label.length() - 1) == '-') {
            return false;
        }
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-')) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) >= 0x80) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAscii(int[] cps) {
        for (int cp : cps) {
            if (cp >= 0x80) {
                return false;
            }
        }
        return true;
    }

    // ---- U-label validation (RFC 5891 section 5.4, RFC 5892) ----

    private static boolean isULabel(int[] cps) {
        int n = cps.length;
        if (n == 0 || cps[0] == '-' || cps[n - 1] == '-' || (n >= 4 && cps[2] == '-' && cps[3] == '-')) {
            return false;
        }
        if (isMark(cps[0])) {
            return false;
        }
        boolean katakanaMiddleDot = false;
        boolean hiraganaKatakanaHan = false;
        boolean arabicIndic = false;
        boolean extendedArabicIndic = false;
        for (int i = 0; i < n; i++) {
            int cp = cps[i];
            switch (cp) {
                case 0x200C -> {
                    if (!(i > 0 && isVirama(cps[i - 1])) && !zeroWidthNonJoinerContext(cps, i)) {
                        return false;
                    }
                }
                case 0x200D -> {
                    if (!(i > 0 && isVirama(cps[i - 1]))) {
                        return false;
                    }
                }
                case 0x00B7 -> {
                    if (i == 0 || i == n - 1 || cps[i - 1] != 'l' || cps[i + 1] != 'l') {
                        return false;
                    }
                }
                case 0x0375 -> {
                    if (i == n - 1 || Character.UnicodeScript.of(cps[i + 1]) != Character.UnicodeScript.GREEK) {
                        return false;
                    }
                }
                case 0x05F3, 0x05F4 -> {
                    if (i == 0 || Character.UnicodeScript.of(cps[i - 1]) != Character.UnicodeScript.HEBREW) {
                        return false;
                    }
                }
                case 0x30FB -> katakanaMiddleDot = true;
                default -> {
                    if (!isPermitted(cp)) {
                        return false;
                    }
                    if (cp >= 0x0660 && cp <= 0x0669) {
                        arabicIndic = true;
                    } else if (cp >= 0x06F0 && cp <= 0x06F9) {
                        extendedArabicIndic = true;
                    }
                    Character.UnicodeScript script = Character.UnicodeScript.of(cp);
                    if (script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA
                        || script == Character.UnicodeScript.HAN) {
                        hiraganaKatakanaHan = true;
                    }
                }
            }
        }
        return !(katakanaMiddleDot && !hiraganaKatakanaHan) && !(arabicIndic && extendedArabicIndic);
    }

    private static boolean isPermitted(int cp) {
        if (cp < 0x80) {
            return (cp >= 'a' && cp <= 'z') || (cp >= '0' && cp <= '9') || cp == '-';
        }
        switch (cp) {
            case 0x00DF, 0x03C2, 0x06FD, 0x06FE, 0x0F0B, 0x3007:
                return true;
            case 0x0640, 0x07FA, 0x302E, 0x302F, 0x3031, 0x3032, 0x3033, 0x3034, 0x3035, 0x303B:
                return false;
            default:
                break;
        }
        int type = Character.getType(cp);
        return switch (type) {
            case Character.LOWERCASE_LETTER, Character.OTHER_LETTER, Character.MODIFIER_LETTER,
                 Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.DECIMAL_DIGIT_NUMBER -> true;
            default -> false;
        };
    }

    private static boolean isMark(int cp) {
        int type = Character.getType(cp);
        return type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.COMBINING_SPACING_MARK;
    }

    private static boolean isVirama(int cp) {
        if (Character.getType(cp) != Character.NON_SPACING_MARK && Character.getType(cp) != Character.COMBINING_SPACING_MARK) {
            return false;
        }
        String name = Character.getName(cp);
        return name != null && name.contains("VIRAMA");
    }

    /**
     * Approximates the ZERO WIDTH NON-JOINER regular expression of RFC 5892 appendix A.1:
     * a joining character before and after, skipping transparent (non-spacing mark) characters.
     */
    private static boolean zeroWidthNonJoinerContext(int[] cps, int index) {
        int before = index - 1;
        while (before >= 0 && Character.getType(cps[before]) == Character.NON_SPACING_MARK) {
            before--;
        }
        int after = index + 1;
        while (after < cps.length && Character.getType(cps[after]) == Character.NON_SPACING_MARK) {
            after++;
        }
        return before >= 0 && after < cps.length && isJoining(cps[before]) && isJoining(cps[after]);
    }

    private static boolean isJoining(int cp) {
        if (!Character.isLetter(cp)) {
            return false;
        }
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        return script == Character.UnicodeScript.ARABIC || script == Character.UnicodeScript.SYRIAC
            || script == Character.UnicodeScript.NKO || script == Character.UnicodeScript.MONGOLIAN
            || script == Character.UnicodeScript.MANDAIC || script == Character.UnicodeScript.PHAGS_PA
            || script == Character.UnicodeScript.MANICHAEAN || script == Character.UnicodeScript.PSALTER_PAHLAVI
            || script == Character.UnicodeScript.ADLAM || script == Character.UnicodeScript.HANIFI_ROHINGYA
            || script == Character.UnicodeScript.SOGDIAN;
    }

    // ---- Bidi rule (RFC 5893) ----

    private static boolean satisfiesBidiRule(List<int[]> labels) {
        boolean bidiDomain = false;
        for (int[] label : labels) {
            for (int cp : label) {
                byte d = Character.getDirectionality(cp);
                if (d == Character.DIRECTIONALITY_RIGHT_TO_LEFT || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
                    || d == Character.DIRECTIONALITY_ARABIC_NUMBER) {
                    bidiDomain = true;
                    break;
                }
            }
        }
        if (!bidiDomain) {
            return true;
        }
        for (int[] label : labels) {
            if (!satisfiesBidiRule(label)) {
                return false;
            }
        }
        return true;
    }

    private static boolean satisfiesBidiRule(int[] label) {
        byte first = Character.getDirectionality(label[0]);
        boolean rtl;
        if (first == Character.DIRECTIONALITY_RIGHT_TO_LEFT || first == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
            rtl = true;
        } else if (first == Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
            rtl = false;
        } else {
            return false;
        }
        boolean europeanNumber = false;
        boolean arabicNumber = false;
        byte last = first;
        for (int cp : label) {
            byte d = Character.getDirectionality(cp);
            boolean allowed = switch (d) {
                case Character.DIRECTIONALITY_EUROPEAN_NUMBER, Character.DIRECTIONALITY_EUROPEAN_NUMBER_SEPARATOR,
                     Character.DIRECTIONALITY_COMMON_NUMBER_SEPARATOR, Character.DIRECTIONALITY_EUROPEAN_NUMBER_TERMINATOR,
                     Character.DIRECTIONALITY_OTHER_NEUTRALS, Character.DIRECTIONALITY_BOUNDARY_NEUTRAL,
                     Character.DIRECTIONALITY_NONSPACING_MARK -> true;
                case Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
                     Character.DIRECTIONALITY_ARABIC_NUMBER -> rtl;
                case Character.DIRECTIONALITY_LEFT_TO_RIGHT -> !rtl;
                default -> false;
            };
            if (!allowed) {
                return false;
            }
            if (d == Character.DIRECTIONALITY_EUROPEAN_NUMBER) {
                europeanNumber = true;
            } else if (d == Character.DIRECTIONALITY_ARABIC_NUMBER) {
                arabicNumber = true;
            }
            if (d != Character.DIRECTIONALITY_NONSPACING_MARK) {
                last = d;
            }
        }
        if (rtl) {
            return !(europeanNumber && arabicNumber)
                && (last == Character.DIRECTIONALITY_RIGHT_TO_LEFT || last == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
                || last == Character.DIRECTIONALITY_EUROPEAN_NUMBER || last == Character.DIRECTIONALITY_ARABIC_NUMBER);
        }
        return last == Character.DIRECTIONALITY_LEFT_TO_RIGHT || last == Character.DIRECTIONALITY_EUROPEAN_NUMBER;
    }

    // ---- Punycode (RFC 3492) ----

    private static int adapt(long delta, int points, boolean first) {
        long d = first ? delta / DAMP : delta / 2;
        d += d / points;
        int k = 0;
        while (d > ((BASE - TMIN) * TMAX) / 2) {
            d /= BASE - TMIN;
            k += BASE;
        }
        return (int) (k + (BASE - TMIN + 1) * d / (d + SKEW));
    }

    private static int threshold(int k, int bias) {
        if (k <= bias) {
            return TMIN;
        }
        return Math.min(k - bias, TMAX);
    }

    /**
     * Decodes a Punycode string (without the ACE prefix).
     *
     * @param input The encoded string
     * @return The code points or null if the input is invalid
     */
    static int @Nullable [] decode(String input) {
        List<Integer> output = new ArrayList<>();
        int delimiter = input.lastIndexOf('-');
        for (int j = 0; j < Math.max(delimiter, 0); j++) {
            char c = input.charAt(j);
            if (c >= 0x80) {
                return null;
            }
            output.add((int) c);
        }
        long n = INITIAL_N;
        long i = 0;
        int bias = INITIAL_BIAS;
        int in = delimiter > 0 ? delimiter + 1 : 0;
        while (in < input.length()) {
            long oldi = i;
            long w = 1;
            for (int k = BASE; ; k += BASE) {
                if (in >= input.length()) {
                    return null;
                }
                int digit = digitValue(input.charAt(in++));
                if (digit < 0) {
                    return null;
                }
                i += digit * w;
                if (i > Integer.MAX_VALUE) {
                    return null;
                }
                int t = threshold(k, bias);
                if (digit < t) {
                    break;
                }
                w *= BASE - t;
                if (w > Integer.MAX_VALUE) {
                    return null;
                }
            }
            int size = output.size() + 1;
            bias = adapt(i - oldi, size, oldi == 0);
            n += i / size;
            if (n > Character.MAX_CODE_POINT) {
                return null;
            }
            i %= size;
            output.add((int) i, (int) n);
            i++;
        }
        int[] result = new int[output.size()];
        for (int j = 0; j < result.length; j++) {
            result[j] = output.get(j);
        }
        return result;
    }

    private static int digitValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0' + 26;
        }
        if (c >= 'a' && c <= 'z') {
            return c - 'a';
        }
        if (c >= 'A' && c <= 'Z') {
            return c - 'A';
        }
        return -1;
    }

    /**
     * Encodes code points as Punycode (without the ACE prefix).
     *
     * @param input The code points
     * @return The encoded string
     */
    static String encode(int[] input) {
        StringBuilder output = new StringBuilder();
        for (int cp : input) {
            if (cp < 0x80) {
                output.append((char) cp);
            }
        }
        int basic = output.length();
        int handled = basic;
        if (basic > 0) {
            output.append('-');
        }
        long n = INITIAL_N;
        long delta = 0;
        int bias = INITIAL_BIAS;
        while (handled < input.length) {
            long m = Long.MAX_VALUE;
            for (int cp : input) {
                if (cp >= n && cp < m) {
                    m = cp;
                }
            }
            delta += (m - n) * (handled + 1);
            n = m;
            for (int cp : input) {
                if (cp < n) {
                    delta++;
                }
                if (cp == n) {
                    long q = delta;
                    for (int k = BASE; ; k += BASE) {
                        int t = threshold(k, bias);
                        if (q < t) {
                            break;
                        }
                        output.append(digit((int) (t + (q - t) % (BASE - t))));
                        q = (q - t) / (BASE - t);
                    }
                    output.append(digit((int) q));
                    bias = adapt(delta, handled + 1, handled == basic);
                    delta = 0;
                    handled++;
                }
            }
            delta++;
            n++;
        }
        return output.toString();
    }

    private static char digit(int d) {
        return (char) (d < 26 ? 'a' + d : '0' + d - 26);
    }
}
