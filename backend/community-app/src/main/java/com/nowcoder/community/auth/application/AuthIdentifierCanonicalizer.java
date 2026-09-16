package com.nowcoder.community.auth.application;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Shared canonical form for rate-limit/quota identifiers: trimmed, case-folded,
 * NFKD-decomposed with combining marks stripped, so visually equivalent inputs
 * share one quota bucket.
 */
final class AuthIdentifierCanonicalizer {

    private AuthIdentifierCanonicalizer() {
    }

    static String canonicalize(String value) {
        String trimmed = value == null ? "" : value.trim();
        String caseFolded = trimmed.toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(caseFolded, Normalizer.Form.NFKD);
        StringBuilder canonical = new StringBuilder(decomposed.length());
        decomposed.codePoints()
                .filter(codePoint -> {
                    int type = Character.getType(codePoint);
                    return type != Character.NON_SPACING_MARK
                            && type != Character.COMBINING_SPACING_MARK
                            && type != Character.ENCLOSING_MARK;
                })
                .forEach(canonical::appendCodePoint);
        return canonical.toString();
    }
}
