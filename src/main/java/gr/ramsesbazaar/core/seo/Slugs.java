package gr.ramsesbazaar.core.seo;

import gr.ramsesbazaar.core.entity.Product;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * Product page addresses: {@code /p/<name>-<id>}, e.g. {@code /p/asimenio-vrachioli-k3x9a1b2c3}.
 * The name part is the product name in Latin letters (Greek is transliterated) so it reads well in
 * Google results; the id at the end keeps the address unique. Renaming a product changes the name
 * part, and old addresses still resolve by their id.
 */
public final class Slugs {

    private static final int MAX_NAME = 60;

    // Two-letter Greek combinations first, then single letters (ELOT 743, simplified).
    private static final Map<String, String> PAIRS = Map.of(
            "ου", "ou", "αι", "ai", "ει", "ei", "οι", "oi", "γγ", "ng", "γκ", "gk", "μπ", "mp", "ντ", "nt");
    private static final Map<Character, String> LETTERS = Map.ofEntries(
            Map.entry('α', "a"), Map.entry('β', "v"), Map.entry('γ', "g"), Map.entry('δ', "d"),
            Map.entry('ε', "e"), Map.entry('ζ', "z"), Map.entry('η', "i"), Map.entry('θ', "th"),
            Map.entry('ι', "i"), Map.entry('κ', "k"), Map.entry('λ', "l"), Map.entry('μ', "m"),
            Map.entry('ν', "n"), Map.entry('ξ', "x"), Map.entry('ο', "o"), Map.entry('π', "p"),
            Map.entry('ρ', "r"), Map.entry('σ', "s"), Map.entry('ς', "s"), Map.entry('τ', "t"),
            Map.entry('υ', "y"), Map.entry('φ', "f"), Map.entry('χ', "ch"), Map.entry('ψ', "ps"),
            Map.entry('ω', "o"));

    private Slugs() {
    }

    public static String of(Product p) {
        return of(p.getName(), p.getId());
    }

    public static String of(String name, String id) {
        String n = nameSlug(name);
        return n.isEmpty() ? id : n + "-" + id;
    }

    /** True if {@code slug} names this product, now or under an earlier name. */
    public static boolean matches(String slug, String id) {
        return slug.equals(id) || slug.endsWith("-" + id);
    }

    static String nameSlug(String name) {
        if (name == null) return "";
        // Strip accents (ά → α, é → e), then lower-case.
        String s = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i + 1 < s.length()) {
                String pair = PAIRS.get(s.substring(i, i + 2));
                if (pair != null) {
                    out.append(pair);
                    i++;
                    continue;
                }
            }
            char c = s.charAt(i);
            String latin = LETTERS.get(c);
            if (latin != null) out.append(latin);
            else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) out.append(c);
            else out.append('-');
        }
        String slug = out.toString().replaceAll("-+", "-").replaceAll("^-|-$", "");
        if (slug.length() > MAX_NAME) slug = slug.substring(0, MAX_NAME).replaceAll("-[^-]*$", "");
        return slug.replaceAll("-$", "");
    }
}
