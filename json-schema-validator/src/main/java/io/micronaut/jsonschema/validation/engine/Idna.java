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
            int[] unicode = idnLabel(label);
            if (unicode == null) {
                return false;
            }
            int asciiLength = isAscii(unicode) ? unicode.length : 4 + encode(unicode).length();
            if (asciiLength > MAX_LABEL) {
                return false;
            }
            unicodeLabels.add(unicode);
            length += asciiLength + 1;
        }
        return length - 1 <= MAX_HOST && satisfiesBidiRule(unicodeLabels);
    }

    /**
     * Maps and validates one label of an internationalized host name.
     *
     * @param label The label
     * @return The Unicode form of the label, or null if invalid
     */
    private static int @Nullable [] idnLabel(String label) {
        String mapped = label.isEmpty() ? "" : map(label);
        if (mapped.isEmpty()) {
            return null;
        }
        if (isAscii(mapped)) {
            return isLdhLabel(mapped) ? checkReservedLabel(mapped) : null;
        }
        int[] unicode = mapped.codePoints().toArray();
        return isULabel(unicode) ? unicode : null;
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
        if (n == 0 || cps[0] == '-' || cps[n - 1] == '-' || (n >= 4 && cps[2] == '-' && cps[3] == '-') || isMark(cps[0])) {
            return false;
        }
        LabelScripts scripts = new LabelScripts();
        for (int i = 0; i < n; i++) {
            if (!isValidInContext(cps, i, scripts)) {
                return false;
            }
        }
        return scripts.isValid();
    }

    /**
     * Checks a code point, including the contextual rules (CONTEXTJ and CONTEXTO) of RFC 5892 appendix A.
     */
    private static boolean isValidInContext(int[] cps, int i, LabelScripts scripts) {
        int cp = cps[i];
        return switch (cp) {
            // ZERO WIDTH NON-JOINER
            case 0x200C -> followsVirama(cps, i) || zeroWidthNonJoinerContext(cps, i);
            // ZERO WIDTH JOINER
            case 0x200D -> followsVirama(cps, i);
            // MIDDLE DOT: must be between two 'l'
            case 0x00B7 -> i > 0 && i < cps.length - 1 && cps[i - 1] == 'l' && cps[i + 1] == 'l';
            // GREEK KERAIA: must be followed by Greek
            case 0x0375 -> i < cps.length - 1 && Character.UnicodeScript.of(cps[i + 1]) == Character.UnicodeScript.GREEK;
            // HEBREW GERESH / GERSHAYIM: must be preceded by Hebrew
            case 0x05F3, 0x05F4 -> i > 0 && Character.UnicodeScript.of(cps[i - 1]) == Character.UnicodeScript.HEBREW;
            // KATAKANA MIDDLE DOT: requires Hiragana, Katakana or Han in the label
            case 0x30FB -> scripts.katakanaMiddleDot();
            default -> isPermitted(cp) && scripts.record(cp);
        };
    }

    private static boolean followsVirama(int[] cps, int i) {
        return i > 0 && isVirama(cps[i - 1]);
    }

    private static boolean isPermitted(int cp) {
        if (cp < 0x80) {
            return (cp >= 'a' && cp <= 'z') || (cp >= '0' && cp <= '9') || cp == '-';
        }
        return switch (cp) {
            case 0x00DF, 0x03C2, 0x06FD, 0x06FE, 0x0F0B, 0x3007 -> true;
            case 0x0640, 0x07FA, 0x302E, 0x302F, 0x3031, 0x3032, 0x3033, 0x3034, 0x3035, 0x303B -> false;
            default -> switch (Character.getType(cp)) {
                case Character.LOWERCASE_LETTER, Character.OTHER_LETTER, Character.MODIFIER_LETTER,
                     Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.DECIMAL_DIGIT_NUMBER -> true;
                default -> false;
            };
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
        boolean bidiDomain = labels.stream().anyMatch(Idna::hasRightToLeft);
        return !bidiDomain || labels.stream().allMatch(Idna::satisfiesBidiRule);
    }

    private static boolean hasRightToLeft(int[] label) {
        for (int cp : label) {
            byte d = Character.getDirectionality(cp);
            if (isRightToLeft(d) || d == Character.DIRECTIONALITY_ARABIC_NUMBER) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRightToLeft(byte directionality) {
        return directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT || directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC;
    }

    private static boolean satisfiesBidiRule(int[] label) {
        byte first = Character.getDirectionality(label[0]);
        boolean rtl = isRightToLeft(first);
        if (!rtl && first != Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
            return false;
        }
        boolean europeanNumber = false;
        boolean arabicNumber = false;
        byte last = first;
        for (int cp : label) {
            byte d = Character.getDirectionality(cp);
            if (!isAllowedInLabel(d, rtl)) {
                return false;
            }
            europeanNumber |= d == Character.DIRECTIONALITY_EUROPEAN_NUMBER;
            arabicNumber |= d == Character.DIRECTIONALITY_ARABIC_NUMBER;
            if (d != Character.DIRECTIONALITY_NONSPACING_MARK) {
                last = d;
            }
        }
        if (rtl) {
            return !(europeanNumber && arabicNumber) && (isRightToLeft(last)
                || last == Character.DIRECTIONALITY_EUROPEAN_NUMBER || last == Character.DIRECTIONALITY_ARABIC_NUMBER);
        }
        return last == Character.DIRECTIONALITY_LEFT_TO_RIGHT || last == Character.DIRECTIONALITY_EUROPEAN_NUMBER;
    }

    private static boolean isAllowedInLabel(byte directionality, boolean rtl) {
        return switch (directionality) {
            case Character.DIRECTIONALITY_EUROPEAN_NUMBER, Character.DIRECTIONALITY_EUROPEAN_NUMBER_SEPARATOR,
                 Character.DIRECTIONALITY_COMMON_NUMBER_SEPARATOR, Character.DIRECTIONALITY_EUROPEAN_NUMBER_TERMINATOR,
                 Character.DIRECTIONALITY_OTHER_NEUTRALS, Character.DIRECTIONALITY_BOUNDARY_NEUTRAL,
                 Character.DIRECTIONALITY_NONSPACING_MARK -> true;
            case Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
                 Character.DIRECTIONALITY_ARABIC_NUMBER -> rtl;
            case Character.DIRECTIONALITY_LEFT_TO_RIGHT -> !rtl;
            default -> false;
        };
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
        return new PunycodeDecoder(input, delimiter > 0 ? delimiter + 1 : 0).decode(output);
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
            long m = smallestAtLeast(input, n);
            delta += (m - n) * (handled + 1);
            n = m;
            for (int cp : input) {
                if (cp < n) {
                    delta++;
                } else if (cp == n) {
                    appendInteger(output, delta, bias);
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

    private static long smallestAtLeast(int[] input, long n) {
        long m = Long.MAX_VALUE;
        for (int cp : input) {
            if (cp >= n && cp < m) {
                m = cp;
            }
        }
        return m;
    }

    /**
     * Appends a generalized variable-length integer.
     */
    private static void appendInteger(StringBuilder output, long value, int bias) {
        long q = value;
        for (int k = BASE; ; k += BASE) {
            int t = threshold(k, bias);
            if (q < t) {
                break;
            }
            output.append(digit((int) (t + (q - t) % (BASE - t))));
            q = (q - t) / (BASE - t);
        }
        output.append(digit((int) q));
    }

    private static char digit(int d) {
        return (char) (d < 26 ? 'a' + d : '0' + d - 26);
    }

    /**
     * Tracks the label-wide contextual rules.
     */
    private static final class LabelScripts {
        private boolean katakanaMiddleDot;
        private boolean hiraganaKatakanaHan;
        private boolean arabicIndic;
        private boolean extendedArabicIndic;

        boolean katakanaMiddleDot() {
            katakanaMiddleDot = true;
            return true;
        }

        boolean record(int cp) {
            if (cp >= 0x0660 && cp <= 0x0669) {
                arabicIndic = true;
            } else if (cp >= 0x06F0 && cp <= 0x06F9) {
                extendedArabicIndic = true;
            }
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            hiraganaKatakanaHan |= script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HAN;
            return true;
        }

        boolean isValid() {
            return !(katakanaMiddleDot && !hiraganaKatakanaHan) && !(arabicIndic && extendedArabicIndic);
        }
    }

    /**
     * The state of the Punycode decoding loop (RFC 3492 section 6.2).
     */
    private static final class PunycodeDecoder {
        private final String input;
        private int pos;
        private long n = INITIAL_N;
        private long i;
        private int bias = INITIAL_BIAS;

        PunycodeDecoder(String input, int start) {
            this.input = input;
            this.pos = start;
        }

        int @Nullable [] decode(List<Integer> output) {
            while (pos < input.length()) {
                long oldi = i;
                if (!readInteger()) {
                    return null;
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
            return output.stream().mapToInt(Integer::intValue).toArray();
        }

        /**
         * Reads one generalized variable-length integer and adds it to {@link #i}.
         */
        private boolean readInteger() {
            long w = 1;
            for (int k = BASE; pos < input.length(); k += BASE) {
                int digit = digitValue(input.charAt(pos++));
                if (digit < 0) {
                    return false;
                }
                i += digit * w;
                int t = threshold(k, bias);
                if (i > Integer.MAX_VALUE || digit < t) {
                    return i <= Integer.MAX_VALUE;
                }
                w *= BASE - t;
                if (w > Integer.MAX_VALUE) {
                    return false;
                }
            }
            return false;
        }
    }
}
