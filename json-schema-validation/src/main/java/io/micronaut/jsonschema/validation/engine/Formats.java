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

import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validators for the JSON Schema {@code format} keyword.
 *
 * @author Graeme Rocher
 * @since 2.3.2
 */
final class Formats {

    private static final Pattern DATE = Pattern.compile("^([0-9]{4})-([0-9]{2})-([0-9]{2})$");
    private static final Pattern TIME = Pattern.compile("^([0-9]{2}):([0-9]{2}):([0-9]{2})(\\.[0-9]+)?([Zz]|([+-])([0-9]{2}):([0-9]{2}))$");
    private static final String DUR_DATE = "(?:[0-9]+D|[0-9]+M(?:[0-9]+D)?|[0-9]+Y(?:[0-9]+M(?:[0-9]+D)?)?)";
    private static final String DUR_TIME = "T(?:[0-9]+H(?:[0-9]+M(?:[0-9]+S)?)?|[0-9]+M(?:[0-9]+S)?|[0-9]+S)";
    private static final Pattern DURATION = Pattern.compile(
        "^P(?:" + DUR_DATE + "(?:" + DUR_TIME + ")?|" + DUR_TIME + "|[0-9]+W)$");
    private static final Pattern UUID = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern RELATIVE_JSON_POINTER = Pattern.compile("^(0|[1-9][0-9]*)(#|(/.*)?)$", Pattern.DOTALL);
    private static final String ATEXT = "!#$%&'*+-/=?^_`{|}~";
    private static final String SUB_DELIMS = "!$&'()*+,;=";
    private static final String TEMPLATE_OPERATORS = "+#./;?&=,!@|";
    private static final Pattern IP_FUTURE = Pattern.compile("[vV][0-9A-Fa-f]+\\.[A-Za-z0-9\\-._~!$&'()*+,;=:]+");
    private static final Pattern INVALID_POINTER_ESCAPE = Pattern.compile("~(?![01])");

    private Formats() {
    }

    /**
     * @param format The format name
     * @param dialect The dialect of the schema
     * @return The validator for the format, or null if the format is unknown
     */
    static @Nullable Predicate<String> forName(String format, Dialect dialect) {
        boolean idna = dialect.atLeast(Dialect.DRAFT_7);
        return switch (format) {
            case "date-time" -> Formats::isDateTime;
            case "date" -> Formats::isDate;
            case "time" -> Formats::isTime;
            case "duration" -> s -> DURATION.matcher(s).matches();
            case "email" -> s -> isEmail(s, false, idna);
            case "idn-email" -> s -> isEmail(s, true, true);
            case "hostname" -> s -> Idna.isHostname(s, idna);
            case "idn-hostname" -> Idna::isIdnHostname;
            case "ipv4" -> Formats::isIpv4;
            case "ipv6" -> Formats::isIpv6;
            case "uri" -> s -> isUri(s, false, false);
            case "uri-reference" -> s -> isUri(s, true, false);
            case "iri" -> s -> isUri(s, false, true);
            case "iri-reference" -> s -> isUri(s, true, true);
            case "uri-template" -> Formats::isUriTemplate;
            case "uuid" -> s -> UUID.matcher(s).matches();
            case "json-pointer" -> Formats::isJsonPointer;
            case "relative-json-pointer" -> Formats::isRelativeJsonPointer;
            case "regex" -> EcmaRegex::isValid;
            default -> null;
        };
    }

    /**
     * @param format The format name
     * @return The message suffix describing the format
     */
    static String description(String format) {
        return switch (format) {
            case "date" -> " must be a valid RFC 3339 full-date";
            case "date-time" -> " must be a valid RFC 3339 date-time";
            case "duration" -> " must be a valid ISO 8601 duration";
            case "email" -> " must be a valid RFC 5321 Mailbox";
            case "ipv4" -> " must be a valid RFC 2673 IP address";
            case "ipv6" -> " must be a valid RFC 4291 IP address";
            case "idn-email" -> " must be a valid RFC 6531 Mailbox";
            case "idn-hostname" -> " must be a valid RFC 5890 internationalized hostname";
            case "iri" -> " must be a valid RFC 3987 IRI";
            case "iri-reference" -> " must be a valid RFC 3987 IRI-reference";
            case "uri" -> " must be a valid RFC 3986 URI";
            case "uri-reference" -> " must be a valid RFC 3986 URI-reference";
            case "uri-template" -> " must be a valid RFC 6570 URI Template";
            case "uuid" -> " must be a valid RFC 4122 UUID";
            case "regex" -> " must be a valid ECMA-262 regular expression";
            case "time" -> " must be a valid RFC 3339 time";
            case "hostname" -> " must be a valid RFC 1123 host name";
            case "json-pointer" -> " must be a valid RFC 6901 JSON Pointer";
            case "relative-json-pointer" -> " must be a valid IETF Relative JSON Pointer";
            default -> "";
        };
    }

    // ---- dates and times (RFC 3339) ----

    static boolean isDate(String s) {
        Matcher m = DATE.matcher(s);
        return m.matches() && isValidDate(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
    }

    private static boolean isValidDate(int year, int month, int day) {
        if (month < 1 || month > 12 || day < 1) {
            return false;
        }
        int max = switch (month) {
            case 2 -> (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) ? 29 : 28;
            case 4, 6, 9, 11 -> 30;
            default -> 31;
        };
        return day <= max;
    }

    static boolean isTime(String s) {
        Matcher m = TIME.matcher(s);
        if (!m.matches()) {
            return false;
        }
        int hour = Integer.parseInt(m.group(1));
        int minute = Integer.parseInt(m.group(2));
        int second = Integer.parseInt(m.group(3));
        if (hour > 23 || minute > 59 || second > 60) {
            return false;
        }
        int offset = 0;
        if (m.group(6) != null) {
            int offsetHour = Integer.parseInt(m.group(7));
            int offsetMinute = Integer.parseInt(m.group(8));
            if (offsetHour > 23 || offsetMinute > 59) {
                return false;
            }
            offset = (offsetHour * 60 + offsetMinute) * ("-".equals(m.group(6)) ? -1 : 1);
        }
        if (second == 60) {
            int utcMinutes = Math.floorMod(hour * 60 + minute - offset, 24 * 60);
            return utcMinutes == 23 * 60 + 59;
        }
        return true;
    }

    static boolean isDateTime(String s) {
        int t = s.indexOf('T');
        if (t < 0) {
            t = s.indexOf('t');
        }
        return t > 0 && isDate(s.substring(0, t)) && isTime(s.substring(t + 1));
    }

    // ---- network ----

    static boolean isIpv4(String s) {
        String[] parts = s.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || (part.length() > 1 && part.charAt(0) == '0')) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (c < '0' || c > '9') {
                    return false;
                }
            }
            if (Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    static boolean isIpv6(String s) {
        int doubleColon = s.indexOf("::");
        if (s.isEmpty() || (doubleColon >= 0 && s.indexOf("::", doubleColon + 1) >= 0)) {
            return false;
        }
        if (doubleColon < 0) {
            return countGroups(s, true) == 8;
        }
        int head = countGroups(s.substring(0, doubleColon), false);
        int tail = countGroups(s.substring(doubleColon + 2), true);
        return head >= 0 && tail >= 0 && head + tail <= 7;
    }

    /**
     * @return The number of 16-bit groups, or -1 if invalid
     */
    private static int countGroups(String part, boolean allowIpv4Suffix) {
        if (part.isEmpty()) {
            return 0;
        }
        String[] groups = part.split(":", -1);
        int count = 0;
        for (int i = 0; i < groups.length; i++) {
            int size = groupSize(groups[i], allowIpv4Suffix && i == groups.length - 1);
            if (size < 0) {
                return -1;
            }
            count += size;
        }
        return count;
    }

    private static int groupSize(String group, boolean allowIpv4) {
        if (allowIpv4 && group.indexOf('.') >= 0) {
            return isIpv4(group) ? 2 : -1;
        }
        return isHexGroup(group) ? 1 : -1;
    }

    private static boolean isHexGroup(String group) {
        if (group.isEmpty() || group.length() > 4) {
            return false;
        }
        for (int i = 0; i < group.length(); i++) {
            if (!isHexDigit(group.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    // ---- email (RFC 5321 / RFC 6531) ----

    static boolean isEmail(String s, boolean international, boolean idna) {
        int at = s.lastIndexOf('@');
        if (at <= 0 || at == s.length() - 1) {
            return false;
        }
        String local = s.substring(0, at);
        String domain = s.substring(at + 1);
        if (!isLocalPart(local, international)) {
            return false;
        }
        if (domain.startsWith("[") && domain.endsWith("]")) {
            String literal = domain.substring(1, domain.length() - 1);
            if (literal.regionMatches(true, 0, "IPv6:", 0, 5)) {
                return isIpv6(literal.substring(5));
            }
            return isIpv4(literal);
        }
        return international ? Idna.isIdnHostname(domain) : Idna.isHostname(domain, idna);
    }

    private static boolean isLocalPart(String local, boolean international) {
        if (local.length() >= 2 && local.charAt(0) == '"' && local.charAt(local.length() - 1) == '"') {
            return isQuotedString(local.substring(1, local.length() - 1), international);
        }
        if (local.charAt(0) == '.' || local.charAt(local.length() - 1) == '.' || local.contains("..")) {
            return false;
        }
        return local.chars().allMatch(c -> c == '.' || isAtext(c, international));
    }

    private static boolean isAtext(int c, boolean international) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
            || ATEXT.indexOf(c) >= 0 || (international && c >= 0x80);
    }

    private static boolean isQuotedString(String content, boolean international) {
        boolean escaped = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (escaped) {
                if (c < 0x20 || c > 0x7E) {
                    return false;
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"' || (c < 0x20 && c != '\t') || (c > 0x7E && !international)) {
                return false;
            }
        }
        return !escaped;
    }

    // ---- URIs (RFC 3986) and IRIs (RFC 3987) ----

    static boolean isUri(String s, boolean allowRelative, boolean iri) {
        int schemeEnd = schemeEnd(s);
        boolean hasScheme = schemeEnd > 0;
        if (!hasScheme && !allowRelative) {
            return false;
        }
        int pos = hasScheme ? schemeEnd + 1 : 0;
        int fragmentStart = s.indexOf('#', pos);
        int end = fragmentStart >= 0 ? fragmentStart : s.length();
        int queryStart = s.indexOf('?', pos);
        if (queryStart > end) {
            queryStart = -1;
        }
        String hier = s.substring(pos, queryStart >= 0 ? queryStart : end);
        return isHierPart(hier, hasScheme, iri)
            && (queryStart < 0 || isValidChars(s.substring(queryStart + 1, end), "/:@?", iri, true))
            && (fragmentStart < 0 || isValidChars(s.substring(fragmentStart + 1), "/:@?", iri, false));
    }

    /**
     * @return The index of the colon that ends a valid scheme, or -1 if the URI has no scheme
     */
    private static int schemeEnd(String s) {
        int colon = -1;
        for (int i = 0; i < s.length() && colon < 0; i++) {
            char c = s.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                return -1;
            }
            if (c == ':') {
                colon = i;
            }
        }
        return colon > 0 && isScheme(s.substring(0, colon)) ? colon : -1;
    }

    private static boolean isHierPart(String hier, boolean hasScheme, boolean iri) {
        String path = hier;
        if (hier.startsWith("//")) {
            int pathStart = hier.indexOf('/', 2);
            String authority = pathStart >= 0 ? hier.substring(2, pathStart) : hier.substring(2);
            if (!isAuthority(authority, iri)) {
                return false;
            }
            path = pathStart >= 0 ? hier.substring(pathStart) : "";
        } else if (!hasScheme) {
            int slash = path.indexOf('/');
            String firstSegment = slash >= 0 ? path.substring(0, slash) : path;
            if (firstSegment.indexOf(':') >= 0) {
                return false;
            }
        }
        return isValidChars(path, "/:@", iri, false);
    }

    private static boolean isScheme(String scheme) {
        if (scheme.isEmpty() || !isAsciiLetter(scheme.charAt(0))) {
            return false;
        }
        for (int i = 1; i < scheme.length(); i++) {
            char c = scheme.charAt(i);
            if (!(isAsciiLetter(c) || (c >= '0' && c <= '9') || c == '+' || c == '-' || c == '.')) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAuthority(String authority, boolean iri) {
        int at = authority.lastIndexOf('@');
        if (at >= 0 && !isValidChars(authority.substring(0, at), ":", iri, false)) {
            return false;
        }
        String hostPort = authority.substring(at + 1);
        if (hostPort.startsWith("[")) {
            return isIpLiteralHostPort(hostPort);
        }
        int colon = hostPort.lastIndexOf(':');
        if (colon >= 0 && !isDigits(hostPort.substring(colon + 1))) {
            return false;
        }
        return isValidChars(colon >= 0 ? hostPort.substring(0, colon) : hostPort, "", iri, false);
    }

    private static boolean isIpLiteralHostPort(String hostPort) {
        int close = hostPort.indexOf(']');
        if (close < 0) {
            return false;
        }
        String literal = hostPort.substring(1, close);
        boolean valid = literal.startsWith("v") || literal.startsWith("V") ? IP_FUTURE.matcher(literal).matches() : isIpv6(literal);
        String rest = hostPort.substring(close + 1);
        return valid && (rest.isEmpty() || (rest.charAt(0) == ':' && isDigits(rest.substring(1))));
    }

    private static boolean isDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidChars(String s, String extra, boolean iri, boolean allowPrivate) {
        int i = 0;
        while (i < s.length()) {
            int length = validCharLength(s, i, extra, iri, allowPrivate);
            if (length <= 0) {
                return false;
            }
            i += length;
        }
        return true;
    }

    /**
     * @return The number of chars of the valid character (or percent-encoded octet) at the index, or -1 if invalid
     */
    private static int validCharLength(String s, int index, String extra, boolean iri, boolean allowPrivate) {
        char c = s.charAt(index);
        if (c == '%') {
            return isPercentEncoded(s, index) ? 3 : -1;
        }
        if (c >= 0x80) {
            int cp = s.codePointAt(index);
            return iri && (isUcsChar(cp) || (allowPrivate && isPrivate(cp))) ? Character.charCount(cp) : -1;
        }
        return isUnreserved(c) || SUB_DELIMS.indexOf(c) >= 0 || extra.indexOf(c) >= 0 ? 1 : -1;
    }

    private static boolean isPercentEncoded(String s, int index) {
        return index + 2 < s.length() && isHexDigit(s.charAt(index + 1)) && isHexDigit(s.charAt(index + 2));
    }

    private static boolean isUcsChar(int cp) {
        return (cp >= 0xA0 && cp <= 0xD7FF) || (cp >= 0xF900 && cp <= 0xFDCF) || (cp >= 0xFDF0 && cp <= 0xFFEF)
            || (cp >= 0x10000 && cp <= 0xEFFFD && (cp & 0xFFFE) != 0xFFFE);
    }

    private static boolean isPrivate(int cp) {
        return (cp >= 0xE000 && cp <= 0xF8FF) || (cp >= 0xF0000 && cp <= 0xFFFFD) || (cp >= 0x100000 && cp <= 0x10FFFD);
    }

    private static boolean isUnreserved(char c) {
        return isAsciiLetter(c) || (c >= '0' && c <= '9') || c == '-' || c == '.' || c == '_' || c == '~';
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    // ---- URI templates (RFC 6570) ----

    static boolean isUriTemplate(String s) {
        int i = 0;
        while (i < s.length()) {
            int length = templatePartLength(s, i);
            if (length <= 0) {
                return false;
            }
            i += length;
        }
        return true;
    }

    /**
     * @return The length of the expression, literal or percent-encoded octet at the index, or -1 if invalid
     */
    private static int templatePartLength(String s, int index) {
        char c = s.charAt(index);
        if (c == '{') {
            int close = s.indexOf('}', index);
            return close >= 0 && isTemplateExpression(s.substring(index + 1, close)) ? close + 1 - index : -1;
        }
        if (c == '%') {
            return isPercentEncoded(s, index) ? 3 : -1;
        }
        boolean literal = c != '}' && c > 0x20 && !(c >= 0x7F && c <= 0x9F) && "\"<>\\^`|".indexOf(c) < 0;
        return literal ? 1 : -1;
    }

    private static boolean isTemplateExpression(String expression) {
        String vars = !expression.isEmpty() && TEMPLATE_OPERATORS.indexOf(expression.charAt(0)) >= 0 ? expression.substring(1) : expression;
        if (vars.isEmpty()) {
            return false;
        }
        for (String varspec : vars.split(",", -1)) {
            if (!isVarspec(varspec)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isVarspec(String varspec) {
        if (varspec.endsWith("*")) {
            return isVarname(varspec.substring(0, varspec.length() - 1));
        }
        int colon = varspec.indexOf(':');
        if (colon < 0) {
            return isVarname(varspec);
        }
        String prefix = varspec.substring(colon + 1);
        boolean validPrefix = !prefix.isEmpty() && prefix.length() <= 4 && prefix.charAt(0) != '0' && isDigits(prefix);
        return validPrefix && isVarname(varspec.substring(0, colon));
    }

    /**
     * Checks {@code varname = varchar *( ["."] varchar )} where {@code varchar = ALPHA / DIGIT / "_" / pct-encoded}.
     */
    private static boolean isVarname(String name) {
        int i = 0;
        boolean expectVarchar = true;
        while (i < name.length()) {
            char c = name.charAt(i);
            int length;
            if (c == '.') {
                length = expectVarchar ? -1 : 1;
            } else if (c == '%') {
                length = isPercentEncoded(name, i) ? 3 : -1;
            } else {
                length = isAsciiLetter(c) || (c >= '0' && c <= '9') || c == '_' ? 1 : -1;
            }
            if (length < 0) {
                return false;
            }
            expectVarchar = c == '.';
            i += length;
        }
        return !name.isEmpty() && !expectVarchar;
    }

    // ---- JSON pointers ----

    static boolean isJsonPointer(String s) {
        return s.isEmpty() || (s.charAt(0) == '/' && !INVALID_POINTER_ESCAPE.matcher(s).find());
    }

    static boolean isRelativeJsonPointer(String s) {
        Matcher m = RELATIVE_JSON_POINTER.matcher(s);
        if (!m.matches()) {
            return false;
        }
        String pointer = m.group(3);
        return pointer == null || isJsonPointer(pointer);
    }
}
