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

import io.micronaut.json.tree.JsonNode;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

/**
 * URI and JSON Pointer helpers used for schema identification and reference resolution.
 *
 * @author Graeme Rocher
 * @since 2.3.2
 */
final class Uris {

    private Uris() {
    }

    /**
     * @param uri The URI
     * @return Whether the URI has a scheme
     */
    static boolean isAbsolute(String uri) {
        int colon = uri.indexOf(':');
        if (colon <= 0) {
            return false;
        }
        if (!isAsciiLetter(uri.charAt(0))) {
            return false;
        }
        for (int i = 1; i < colon; i++) {
            char c = uri.charAt(i);
            if (!(isAsciiLetter(c) || (c >= '0' && c <= '9') || c == '+' || c == '-' || c == '.')) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /**
     * Resolves a reference against a base URI (RFC 3986 section 5).
     *
     * @param base The base URI, may be null
     * @param ref The reference
     * @return The resolved URI
     */
    static String resolve(@Nullable String base, String ref) {
        if (isAbsolute(ref)) {
            return normalize(ref);
        }
        if (base == null || base.isEmpty()) {
            return ref;
        }
        if (ref.isEmpty()) {
            return stripFragment(base);
        }
        if (ref.charAt(0) == '#') {
            return stripFragment(base) + ref;
        }
        try {
            URI baseUri = new URI(base);
            if (baseUri.isOpaque()) {
                return ref;
            }
            if (baseUri.getRawAuthority() != null && (baseUri.getRawPath() == null || baseUri.getRawPath().isEmpty())) {
                baseUri = new URI(baseUri.getScheme() + "://" + baseUri.getRawAuthority() + "/");
            }
            return baseUri.resolve(new URI(ref)).toString();
        } catch (URISyntaxException | IllegalArgumentException e) {
            String stripped = stripFragment(base);
            int slash = stripped.lastIndexOf('/');
            return (slash >= 0 ? stripped.substring(0, slash + 1) : "") + ref;
        }
    }

    private static String normalize(String uri) {
        if (uri.indexOf("/.") < 0) {
            return uri;
        }
        try {
            return new URI(uri).normalize().toString();
        } catch (URISyntaxException e) {
            return uri;
        }
    }

    /**
     * @param uri The URI
     * @return The URI without its fragment
     */
    static String stripFragment(String uri) {
        int hash = uri.indexOf('#');
        return hash >= 0 ? uri.substring(0, hash) : uri;
    }

    /**
     * @param uri The URI
     * @return The fragment (without '#'), or null if there is none
     */
    static @Nullable String fragment(String uri) {
        int hash = uri.indexOf('#');
        return hash >= 0 ? uri.substring(hash + 1) : null;
    }

    /**
     * Decodes percent-encoded octets in a URI fragment.
     *
     * @param s The string
     * @return The decoded string
     */
    static String percentDecode(String s) {
        if (s.indexOf('%') < 0) {
            return s;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                int hi = Character.digit(s.charAt(i + 1), 16);
                int lo = Character.digit(s.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    out.write((hi << 4) + lo);
                    i += 2;
                    continue;
                }
            }
            int cp = s.codePointAt(i);
            byte[] bytes = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8);
            out.write(bytes, 0, bytes.length);
            i += Character.charCount(cp) - 1;
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    /**
     * Escapes a JSON pointer reference token.
     *
     * @param token The token
     * @return The escaped token
     */
    static String escapePointerToken(String token) {
        if (token.indexOf('~') < 0 && token.indexOf('/') < 0) {
            return token;
        }
        return token.replace("~", "~0").replace("/", "~1");
    }

    /**
     * Navigates a JSON pointer from the given node.
     *
     * @param root The root node
     * @param pointer The (already percent-decoded) pointer
     * @return The target node or null if it does not exist
     */
    static @Nullable JsonNode navigate(JsonNode root, String pointer) {
        if (pointer.isEmpty()) {
            return root;
        }
        if (pointer.charAt(0) != '/') {
            return null;
        }
        JsonNode current = root;
        for (String token : pointerTokens(pointer)) {
            if (current.isObject()) {
                current = current.get(token);
            } else if (current.isArray()) {
                int index = parseIndex(token);
                current = index < 0 ? null : current.get(index);
            } else {
                return null;
            }
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /**
     * @param pointer The pointer, starting with '/'
     * @return The unescaped reference tokens
     */
    static String[] pointerTokens(String pointer) {
        String[] tokens = pointer.substring(1).split("/", -1);
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.indexOf('~') >= 0) {
                tokens[i] = token.replace("~1", "/").replace("~0", "~");
            }
        }
        return tokens;
    }

    static int parseIndex(String token) {
        if (token.isEmpty() || token.length() > 9 || (token.length() > 1 && token.charAt(0) == '0')) {
            return -1;
        }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c < '0' || c > '9') {
                return -1;
            }
        }
        return Integer.parseInt(token);
    }
}
