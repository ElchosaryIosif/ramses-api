package gr.ramsesbazaar.core.controller;

import gr.ramsesbazaar.core.service.ImageService;
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

import java.time.Duration;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
@Tag(name = "Images", description = "Product photos")
public class ImageController {

    private final ImageService imageService;

    @Operation(summary = "Get a product photo", description = "Keys are content hashes, so responses never change.")
    @GetMapping("/{key}")
    public ResponseEntity<byte[]> get(@PathVariable String key) {
        return imageService.find(key)
                .map(img -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(img.getContentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .body(img.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
