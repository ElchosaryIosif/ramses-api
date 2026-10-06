package gr.ramsesbazaar.core.service;

import gr.ramsesbazaar.core.config.ApiException;
import gr.ramsesbazaar.core.entity.ProductImage;
import gr.ramsesbazaar.core.repository.ProductImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Photos live in Postgres, addressed by a hash of their bytes, and are served as immutable. */
@Service
@RequiredArgsConstructor
public class ImageService {

    public static final String URL_PREFIX = "/api/images/";
    public static final Pattern KEY = Pattern.compile("^[a-f0-9]{16,64}$");
    private static final Pattern DATA_URL =
            Pattern.compile("^data:(image/(?:jpeg|png|webp|gif));base64,([A-Za-z0-9+/=]+)$");
    private static final int MAX_BYTES = 4 * 1024 * 1024;

    private final ProductImageRepository repository;

    /**
     * Returns the URL to store for an image: a data: URL is saved and replaced by its
     * {@code /api/images/<key>} URL; references to existing images and https URLs pass through.
     */
    @Transactional
    public String store(String image) {
        if (image == null || image.isEmpty()) return null;
        Matcher m = DATA_URL.matcher(image);
        if (m.matches()) {
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(m.group(2));
            } catch (IllegalArgumentException e) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "invalid_product");
            }
            if (bytes.length > MAX_BYTES) throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "image_too_large");
            String key = HexFormat.of().formatHex(sha256(bytes)).substring(0, 40);
            if (!repository.existsById(key)) {
                ProductImage img = new ProductImage();
                img.setImageKey(key);
                img.setContentType(m.group(1));
                img.setData(bytes);
                repository.save(img);
            }
            return URL_PREFIX + key;
        }
        if (image.startsWith(URL_PREFIX) && KEY.matcher(image.substring(URL_PREFIX.length())).matches()) return image;
        if (image.startsWith("https://")) return image;
        throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "invalid_product");
    }

    @Transactional(readOnly = true)
    public Optional<ProductImage> find(String key) {
        return KEY.matcher(key).matches() ? repository.findById(key) : Optional.empty();
    }

    @Transactional
    public void deleteAllExcept(Set<String> keep) {
        var orphans = repository.findAllKeys().stream().filter(k -> !keep.contains(k)).toList();
        if (!orphans.isEmpty()) repository.deleteAllByIdInBatch(orphans);
    }

    public static Optional<String> keyOf(String imageUrl) {
        return imageUrl != null && imageUrl.startsWith(URL_PREFIX)
                ? Optional.of(imageUrl.substring(URL_PREFIX.length()))
                : Optional.empty();
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
