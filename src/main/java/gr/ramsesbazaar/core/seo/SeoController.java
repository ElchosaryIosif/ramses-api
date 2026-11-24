package gr.ramsesbazaar.core.seo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Duration;

/**
 * Read by the shop front's nginx, not by browsers: it puts these into the pages it serves so
 * search engines and link previews see each product without running JavaScript.
 */
@RestController
@RequestMapping("/api/seo")
@RequiredArgsConstructor
@Tag(name = "SEO", description = "Sitemap and product page tags for search engines")
public class SeoController {

    private static final MediaType HTML = new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8);
    private static final MediaType XML = new MediaType("application", "xml", java.nio.charset.StandardCharsets.UTF_8);

    private final SeoService seoService;

    @Operation(summary = "Sitemap", description = "The home page and every product page, with photos.")
    @GetMapping("/sitemap.xml")
    public ResponseEntity<String> sitemap() {
        return ResponseEntity.ok()
                .contentType(XML)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(seoService.sitemap(apiOrigin()));
    }

    @Operation(summary = "Product page head tags",
            description = "Title, description, canonical address, link-preview tags and Product structured data. "
                    + "An unknown address gets a noindex tag.")
    @GetMapping("/product/{slug}")
    public ResponseEntity<String> productHead(@PathVariable String slug) {
        String body = seoService.find(slug)
                .map(p -> seoService.productHead(p, apiOrigin()))
                .orElseGet(seoService::notFoundHead);
        return ResponseEntity.ok()
                .contentType(HTML)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(body);
    }

    @Operation(summary = "Product page body",
            description = "The visible top of the product page (photo, name, price), shown before the app loads. "
                    + "404 for an unknown address.")
    @GetMapping("/product-body/{slug}")
    public ResponseEntity<String> productBody(@PathVariable String slug) {
        return seoService.find(slug)
                .map(p -> ResponseEntity.ok()
                        .contentType(HTML)
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                        .body(seoService.productBody(p, apiOrigin())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** This API's public origin, from the request (Railway's proxy headers give the scheme). */
    private static String apiOrigin() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().replacePath(null).build().toUriString();
    }
}
