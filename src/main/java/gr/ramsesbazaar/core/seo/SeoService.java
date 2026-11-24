package gr.ramsesbazaar.core.seo;

import gr.ramsesbazaar.core.config.AppProperties;
import gr.ramsesbazaar.core.entity.Product;
import gr.ramsesbazaar.core.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What search engines and link previews see. The shop front is a single-page app, so its nginx
 * asks this service for each product page's {@code <head>} tags and for the sitemap.
 */
@Service
@RequiredArgsConstructor
public class SeoService {

    private static final String SHOP = "Ramses Bazaar";
    private static final BigDecimal FREE_SHIPPING_AT = new BigDecimal("50");
    private static final String SHIPPING_COST = "3.50";
    private static final int MAX_DESCRIPTION = 160;
    private static final Map<String, String> CATEGORY_GR = Map.of(
            "jewelry", "Κοσμήματα", "clothes", "Ρούχα", "home", "Διακόσμηση",
            "incense", "Θυμιάματα", "collectibles", "Συλλεκτικά");

    // Labels and icons exactly as the shop front shows them in Greek (i18n/translations.ts).
    private static final Map<String, String> CATEGORY_LABEL_GR = Map.of(
            "jewelry", "Κοσμήματα", "clothes", "Ρούχα", "home", "Home Decor",
            "incense", "Incense", "collectibles", "Collectibles");
    private static final Map<String, String> CATEGORY_ICON = Map.of(
            "jewelry", "💎", "clothes", "👗", "home", "🏠", "incense", "🌿", "collectibles", "🗿");
    private static final Map<String, String> TAG_LABEL_GR = Map.of("new", "Νέο", "best", "Bestseller", "sale", "Sale");

    // Google's product taxonomy, by shop category (https://support.google.com/merchants/answer/6324436).
    private static final Map<String, String> GOOGLE_CATEGORY = Map.of(
            "jewelry", "Apparel & Accessories > Jewelry",
            // The shop's "clothes" also holds bags, wallets and scarves: let Google pick the exact type.
            "clothes", "Apparel & Accessories",
            "home", "Home & Garden > Decor",
            "incense", "Home & Garden > Decor > Home Fragrances > Incense",
            "collectibles", "Arts & Entertainment > Hobbies & Creative Arts > Collectibles");

    private final ProductRepository productRepository;
    private final AppProperties props;

    @Transactional(readOnly = true)
    public Optional<Product> find(String slug) {
        List<Product> all = productRepository.findAllByOrderByPositionAsc();
        return all.stream().filter(p -> Slugs.of(p).equals(slug)).findFirst()
                .or(() -> all.stream().filter(p -> Slugs.matches(slug, p.getId())).findFirst());
    }

    public String productUrl(Product p) {
        return props.siteUrl() + "/p/" + Slugs.of(p);
    }

    /** {@code <head>} tags for a product page. {@code apiOrigin} makes photo addresses absolute. */
    public String productHead(Product p, String apiOrigin) {
        String url = productUrl(p);
        String title = p.getName() + " | " + SHOP;
        String description = description(p);
        String image = imageUrl(p, apiOrigin);

        StringBuilder h = new StringBuilder();
        h.append("<title>").append(html(title)).append("</title>\n");
        meta(h, "name", "description", description);
        h.append("<link rel=\"canonical\" href=\"").append(html(url)).append("\" />\n");
        if (image != null) {
            // The photo is the page's largest element: start downloading it with the HTML.
            h.append("<link rel=\"preload\" as=\"image\" href=\"").append(html(image))
                    .append("\" fetchpriority=\"high\" />\n");
        }
        meta(h, "property", "og:type", "product");
        meta(h, "property", "og:site_name", SHOP);
        meta(h, "property", "og:locale", "el_GR");
        meta(h, "property", "og:title", title);
        meta(h, "property", "og:description", description);
        meta(h, "property", "og:url", url);
        if (image != null) {
            meta(h, "property", "og:image", image);
            meta(h, "property", "og:image:alt", p.getName());
        }
        meta(h, "property", "product:price:amount", p.getPrice().toPlainString());
        meta(h, "property", "product:price:currency", "EUR");
        meta(h, "name", "twitter:card", image != null ? "summary_large_image" : "summary");
        meta(h, "name", "twitter:title", title);
        meta(h, "name", "twitter:description", description);
        if (image != null) meta(h, "name", "twitter:image", image);
        h.append("<script type=\"application/ld+json\">").append(jsonLd(p, url, description, image))
                .append("</script>\n");
        // The same product as GET /api/products returns it, so the page can show it before the
        // catalogue request finishes.
        h.append("<script type=\"application/json\" id=\"product-data\">").append(productData(p))
                .append("</script>\n");
        return h.toString();
    }

    /**
     * The visible top of a product page, as the shop front's ProductPage component renders it in
     * Greek (keep the two in step: same elements, classes and labels). nginx puts it in the page
     * so the product shows before the app's JavaScript has loaded; the app then replaces it.
     */
    public String productBody(Product p, String apiOrigin) {
        String category = CATEGORY_LABEL_GR.getOrDefault(p.getCategory(), p.getCategory());
        String image = imageUrl(p, apiOrigin);
        StringBuilder b = new StringBuilder();
        b.append("<main id=\"main\"><section class=\"product-page\">");
        b.append("<nav class=\"breadcrumb\" aria-label=\"Διαδρομή\"><a href=\"/\">Αρχική</a>")
                .append("<span aria-hidden=\"true\">›</span><span>").append(html(category)).append("</span></nav>");
        b.append("<div class=\"product-detail\"><div class=\"product-detail-media\">");
        String tag = TAG_LABEL_GR.get(p.getTag());
        if (tag != null) {
            b.append("<span class=\"product-badge badge-").append(p.getTag()).append("\">").append(html(tag)).append("</span>");
        }
        if (image != null) {
            b.append("<button type=\"button\" class=\"product-zoom\"><img src=\"").append(html(image))
                    .append("\" alt=\"").append(html(p.getName()))
                    .append("\" width=\"800\" height=\"800\" fetchpriority=\"high\" decoding=\"async\"></button>");
        } else {
            b.append("<div class=\"product-img-placeholder\">")
                    .append(CATEGORY_ICON.getOrDefault(p.getCategory(), "")).append("</div>");
        }
        b.append("</div><div class=\"product-detail-info\">");
        b.append("<p class=\"product-category\">").append(html(category)).append("</p>");
        b.append("<h1 class=\"product-detail-name\">").append(html(p.getName())).append("</h1>");
        b.append("<p class=\"product-detail-price\">€").append(p.getPrice().toPlainString()).append("</p>");
        b.append(p.isInStock()
                ? "<p class=\"product-stock\">✓ Διαθέσιμο</p>"
                : "<p class=\"product-stock out\">Εκτός αποθέματος</p>");
        if (p.getDescription() != null && !p.getDescription().isEmpty()) {
            b.append("<p class=\"product-detail-desc\">").append(html(p.getDescription())).append("</p>");
        }
        b.append("<div class=\"product-detail-actions\">").append(p.isInStock()
                ? "<button type=\"button\" class=\"btn-order\">+ Καλάθι</button>"
                : "<button type=\"button\" class=\"btn-order\" disabled=\"\">Εξαντλήθηκε</button>").append("</div>");
        b.append("<p class=\"product-ship-note\">🚚 Δωρεάν αποστολή σε όλη την Ελλάδα για παραγγελίες από €")
                .append(FREE_SHIPPING_AT.toPlainString()).append("</p>");
        b.append("<a class=\"product-back\" href=\"/#shop\">← Πίσω στη συλλογή</a>");
        b.append("</div></div></section></main>");
        return b.toString();
    }

    /** Tags for an address that matches no product: tell search engines not to index it. */
    public String notFoundHead() {
        return "<title>" + html(SHOP) + "</title>\n<meta name=\"robots\" content=\"noindex\" />\n";
    }

    @Transactional(readOnly = true)
    public String sitemap(String apiOrigin) {
        StringBuilder x = new StringBuilder();
        x.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        x.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\" ")
                .append("xmlns:image=\"http://www.google.com/schemas/sitemap-image/1.1\">\n");
        x.append("  <url><loc>").append(xml(props.siteUrl() + "/")).append("</loc></url>\n");
        x.append("  <url><loc>").append(xml(props.siteUrl() + "/policies")).append("</loc></url>\n");
        for (Product p : productRepository.findAllByOrderByPositionAsc()) {
            x.append("  <url><loc>").append(xml(productUrl(p))).append("</loc>");
            String image = imageUrl(p, apiOrigin);
            if (image != null) {
                x.append("<image:image><image:loc>").append(xml(image)).append("</image:loc></image:image>");
            }
            x.append("</url>\n");
        }
        x.append("</urlset>\n");
        return x.toString();
    }

    /**
     * Google Merchant Center product feed (RSS 2.0 with the g: namespace), so products appear in
     * Google Shopping and image results. Shipping and returns are set once in the Merchant Center
     * account. Products without a photo are left out: Google rejects items without an image.
     * Handmade and imported pieces have no barcode, hence identifier_exists=no. There is no brand:
     * many products are other makers' (e.g. HEM incense), and the shop's name isn't their brand.
     */
    @Transactional(readOnly = true)
    public String merchantFeed(String apiOrigin) {
        StringBuilder x = new StringBuilder();
        x.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        x.append("<rss version=\"2.0\" xmlns:g=\"http://base.google.com/ns/1.0\">\n<channel>\n");
        x.append("<title>").append(xml(SHOP)).append("</title>\n");
        x.append("<link>").append(xml(props.siteUrl() + "/")).append("</link>\n");
        x.append("<description>").append(xml("Boho, ethnic & hippie gift shop in Kalamata since 1996"))
                .append("</description>\n");
        for (Product p : productRepository.findAllByOrderByPositionAsc()) {
            String image = imageUrl(p, apiOrigin);
            if (image == null) continue;
            x.append("<item>\n");
            feedField(x, "id", p.getId());
            feedField(x, "title", feedTitle(p.getName()));
            feedField(x, "description", feedDescription(p));
            feedField(x, "link", productUrl(p));
            feedField(x, "image_link", image);
            feedField(x, "availability", p.isInStock() ? "in_stock" : "out_of_stock");
            feedField(x, "price", p.getPrice().toPlainString() + " EUR");
            feedField(x, "condition", "new");
            feedField(x, "identifier_exists", "no");
            String googleCategory = GOOGLE_CATEGORY.get(p.getCategory());
            if (googleCategory != null) feedField(x, "google_product_category", googleCategory);
            feedField(x, "product_type", CATEGORY_GR.getOrDefault(p.getCategory(), p.getCategory()));
            x.append("</item>\n");
        }
        x.append("</channel>\n</rss>\n");
        return x.toString();
    }

    // Emoji and pictographs (and the joiners/selectors that build them). Google flags symbols in
    // titles as spam-like.
    private static final Pattern EMOJI = Pattern.compile("[\\p{So}\\p{Sk}\\x{FE0F}\\x{200D}\\x{20E3}]+");
    private static final Pattern HASHTAG = Pattern.compile("(?U)(^|\\s)#\\w+");

    /** The product name without emoji, which Merchant Center treats as promotional symbols. */
    static String feedTitle(String name) {
        String clean = EMOJI.matcher(name).replaceAll(" ").replaceAll("\\s+", " ").trim();
        return clean.isEmpty() ? name.trim() : clean;
    }

    /**
     * The description without hashtags (promotional text to Merchant Center). With none written,
     * a plain sentence naming the product and category; Google doesn't want prices in it.
     */
    private static String feedDescription(Product p) {
        String d = p.getDescription() == null ? "" : HASHTAG.matcher(p.getDescription()).replaceAll("$1");
        d = d.replaceAll("[ \\t]+", " ").replaceAll(" *\\n *", "\n").trim();
        if (!d.isEmpty()) return d;
        return feedTitle(p.getName()) + " – " + CATEGORY_GR.getOrDefault(p.getCategory(), "δώρα")
                + " από το Ramses Bazaar, gift shop στην Καλαμάτα από το 1996.";
    }

    private static void feedField(StringBuilder x, String name, String value) {
        x.append("  <g:").append(name).append('>').append(xml(value)).append("</g:").append(name).append(">\n");
    }

    private String description(Product p) {
        String d = p.getDescription() == null ? "" : p.getDescription().replaceAll("\\s+", " ").trim();
        if (d.isEmpty()) {
            d = p.getName() + " – " + CATEGORY_GR.getOrDefault(p.getCategory(), "δώρα")
                    + " από το Ramses Bazaar, Καλαμάτα. €" + p.getPrice().toPlainString() + ".";
        }
        if (d.length() <= MAX_DESCRIPTION) return d;
        String cut = d.substring(0, MAX_DESCRIPTION - 1);
        int space = cut.lastIndexOf(' ');
        return (space > 100 ? cut.substring(0, space) : cut) + "…";
    }

    private static String imageUrl(Product p, String apiOrigin) {
        String img = p.getImage();
        if (img == null || img.isEmpty()) return null;
        if (img.startsWith("http://") || img.startsWith("https://")) return img;
        if (img.startsWith("/")) return apiOrigin + img;
        return null; // data: URLs are never stored, but never put one in a meta tag either
    }

    private static String productData(Product p) {
        return "{\"id\":" + json(p.getId())
                + ",\"name\":" + json(p.getName())
                + ",\"cat\":" + json(p.getCategory())
                + ",\"price\":" + p.getPrice().toPlainString()
                + ",\"tag\":" + json(p.getTag())
                + ",\"desc\":" + json(p.getDescription() == null ? "" : p.getDescription())
                + ",\"stock\":" + p.isInStock()
                + ",\"image\":" + (p.getImage() == null ? "null" : json(p.getImage()))
                + ",\"createdAt\":" + p.getCreatedAt()
                + ",\"slug\":" + json(Slugs.of(p)) + "}";
    }

    private String jsonLd(Product p, String url, String description, String image) {
        boolean freeShipping = p.getPrice().compareTo(FREE_SHIPPING_AT) >= 0;
        StringBuilder j = new StringBuilder();
        j.append("{\"@context\":\"https://schema.org\",\"@type\":\"Product\"");
        j.append(",\"name\":").append(json(p.getName()));
        j.append(",\"description\":").append(json(description));
        j.append(",\"sku\":").append(json(p.getId()));
        j.append(",\"category\":").append(json(CATEGORY_GR.getOrDefault(p.getCategory(), p.getCategory())));
        if (image != null) j.append(",\"image\":[").append(json(image)).append("]");
        j.append(",\"brand\":{\"@type\":\"Brand\",\"name\":").append(json(SHOP)).append("}");
        j.append(",\"offers\":{\"@type\":\"Offer\"");
        j.append(",\"url\":").append(json(url));
        j.append(",\"priceCurrency\":\"EUR\",\"price\":").append(json(p.getPrice().toPlainString()));
        j.append(",\"availability\":\"https://schema.org/").append(p.isInStock() ? "InStock" : "OutOfStock").append("\"");
        j.append(",\"itemCondition\":\"https://schema.org/NewCondition\"");
        j.append(",\"seller\":{\"@type\":\"Organization\",\"name\":").append(json(SHOP)).append("}");
        j.append(",\"shippingDetails\":{\"@type\":\"OfferShippingDetails\"");
        j.append(",\"shippingRate\":{\"@type\":\"MonetaryAmount\",\"currency\":\"EUR\",\"value\":")
                .append(json(freeShipping ? "0" : SHIPPING_COST)).append("}");
        j.append(",\"shippingDestination\":{\"@type\":\"DefinedRegion\",\"addressCountry\":\"GR\"}}");
        j.append("}}");
        return j.toString();
    }

    private static void meta(StringBuilder h, String attr, String key, String value) {
        h.append("<meta ").append(attr).append("=\"").append(key).append("\" content=\"")
                .append(html(value)).append("\" />\n");
    }

    static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    static String xml(String s) {
        return html(s);
    }

    /** A JSON string literal that is also safe inside an HTML {@code <script>} element. */
    static String json(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '<' -> out.append("\\u003c");
                case '>' -> out.append("\\u003e");
                case '&' -> out.append("\\u0026");
                default -> {
                    if (c < 0x20 || c == ' ' || c == ' ') out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
}
