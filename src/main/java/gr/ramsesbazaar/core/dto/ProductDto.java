package gr.ramsesbazaar.core.dto;

import gr.ramsesbazaar.core.entity.Product;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "Product", description = "A product in the shop catalogue. Field names match the shop front.")
public record ProductDto(
        @Schema(description = "Client-generated identifier", example = "k3x9a1b2c3")
        String id,

        @Schema(description = "Display name", example = "Ασημένιο βραχιόλι")
        String name,

        @Schema(description = "Category", allowableValues = {"jewelry", "clothes", "home", "incense", "collectibles"})
        String cat,

        @Schema(description = "Price in euros", example = "24.50")
        BigDecimal price,

        @Schema(description = "Badge shown on the card", allowableValues = {"", "new", "best", "sale"})
        String tag,

        @Schema(description = "Description")
        String desc,

        @Schema(description = "Whether the product can be ordered")
        Boolean stock,

        @Schema(description = "Photo URL. On save, a data: URL is stored and replaced by /api/images/<key>.")
        String image,

        @Schema(description = "Creation time in epoch milliseconds")
        Long createdAt
) {

    public static ProductDto from(Product p) {
        return new ProductDto(p.getId(), p.getName(), p.getCategory(), p.getPrice(), p.getTag(),
                p.getDescription(), p.isInStock(), p.getImage(), p.getCreatedAt());
    }
}
