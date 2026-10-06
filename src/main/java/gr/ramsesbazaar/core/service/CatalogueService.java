package gr.ramsesbazaar.core.service;

import gr.ramsesbazaar.core.config.ApiException;
import gr.ramsesbazaar.core.config.CatalogueConflictException;
import gr.ramsesbazaar.core.dto.CatalogueDto;
import gr.ramsesbazaar.core.dto.ProductDto;
import gr.ramsesbazaar.core.entity.Catalogue;
import gr.ramsesbazaar.core.entity.Product;
import gr.ramsesbazaar.core.repository.CatalogueRepository;
import gr.ramsesbazaar.core.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CatalogueService {

    public static final Set<String> CATEGORIES = Set.of("jewelry", "clothes", "home", "incense", "collectibles");
    private static final Set<String> TAGS = Set.of("", "new", "best", "sale");
    private static final Pattern ID = Pattern.compile("^[a-zA-Z0-9_-]{4,40}$");
    private static final BigDecimal MAX_PRICE = new BigDecimal("100000");
    private static final int MAX_PRODUCTS = 2000;

    private final CatalogueRepository catalogueRepository;
    private final ProductRepository productRepository;
    private final ImageService imageService;

    @Transactional(readOnly = true)
    public CatalogueDto get() {
        Catalogue c = catalogueRepository.findById(Catalogue.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("catalogue row missing"));
        return new CatalogueDto(c.getRev(), products());
    }

    /**
     * Replaces the whole catalogue, but only if nobody else saved since {@code baseRev} was read.
     * Otherwise the current catalogue is returned with a 409 and the admin panel replays its
     * pending edits on top of it.
     */
    @Transactional
    public CatalogueDto save(Integer baseRev, List<ProductDto> incoming) {
        if (incoming == null) throw new ApiException(HttpStatus.BAD_REQUEST, "bad_products");
        if (incoming.size() > MAX_PRODUCTS) throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "too_large");

        Catalogue c = catalogueRepository.lockSingleton()
                .orElseThrow(() -> new IllegalStateException("catalogue row missing"));
        if (baseRev == null || baseRev != c.getRev()) {
            throw new CatalogueConflictException(new CatalogueDto(c.getRev(), products()));
        }

        List<Product> next = new ArrayList<>(incoming.size());
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < incoming.size(); i++) {
            Product p = sanitize(incoming.get(i), i);
            if (!ids.add(p.getId())) throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "duplicate_id");
            next.add(p);
        }
        for (Product p : next) p.setImage(imageService.store(p.getImage()));

        productRepository.deleteAllInBatch();
        productRepository.flush();
        productRepository.saveAll(next);

        Set<String> keep = new HashSet<>();
        next.forEach(p -> ImageService.keyOf(p.getImage()).ifPresent(keep::add));
        imageService.deleteAllExcept(keep);

        c.setRev(c.getRev() + 1);
        c.setUpdatedAt(Instant.now());
        return new CatalogueDto(c.getRev(), next.stream().map(ProductDto::from).toList());
    }

    private List<ProductDto> products() {
        return productRepository.findAllByOrderByPositionAsc().stream().map(ProductDto::from).toList();
    }

    private static Product sanitize(ProductDto d, int position) {
        if (d == null) throw invalid();
        String id = trim(d.id(), 40);
        String name = trim(d.name(), 160);
        if (!ID.matcher(id).matches() || name.isEmpty() || !CATEGORIES.contains(d.cat())) throw invalid();
        if (d.price() == null || d.price().signum() < 0 || d.price().compareTo(MAX_PRICE) > 0) throw invalid();

        Product p = new Product();
        p.setId(id);
        p.setPosition(position);
        p.setName(name);
        p.setCategory(d.cat());
        p.setPrice(d.price().setScale(2, RoundingMode.HALF_UP));
        p.setTag(TAGS.contains(d.tag()) ? d.tag() : "");
        p.setDescription(trim(d.desc(), 2000));
        p.setInStock(!Boolean.FALSE.equals(d.stock()));
        p.setImage(d.image() == null || d.image().isEmpty() ? null : d.image());
        p.setCreatedAt(d.createdAt() != null ? d.createdAt() : System.currentTimeMillis());
        return p;
    }

    private static String trim(String v, int max) {
        if (v == null) return "";
        String t = v.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "invalid_product");
    }
}
