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
        return h.toString();
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
